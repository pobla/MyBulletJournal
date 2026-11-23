package com.pobla.cuaderno.cloud

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.pobla.cuaderno.BuildConfig
import com.pobla.cuaderno.CuadernoApp
import com.pobla.cuaderno.data.AppSettings
import com.pobla.cuaderno.data.db.AppDatabase
import com.pobla.cuaderno.model.symbolFor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import androidx.room.withTransaction
import com.google.firebase.firestore.DocumentSnapshot
import com.pobla.cuaderno.data.db.DayEntity
import com.pobla.cuaderno.data.db.EntryEntity
import com.pobla.cuaderno.data.db.HabitCheckEntity
import com.pobla.cuaderno.data.db.HabitEntity
import com.pobla.cuaderno.model.EntryType
import com.pobla.cuaderno.model.TaskStatus
import com.pobla.cuaderno.data.db.MonthEntryEntity

sealed interface SyncStatus {
    data object Idle : SyncStatus
    data object Syncing : SyncStatus
    data class Error(val message: String) : SyncStatus
}

/**
 * Sincronización con Firebase en los dos sentidos, en `users/{uid}/…`:
 * 1. BAJA lo cambiado en la nube (p. ej. desde la web) y lo aplica si es más reciente.
 * 2. SUBE lo cambiado en el móvil desde la última copia.
 * Gana el cambio más reciente (updatedAt). Room sigue siendo la fuente de verdad de la app y
 * las reglas de Firestore hacen que solo esa cuenta pueda leer y escribir sus datos.
 */
class CloudSync(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: AppSettings,
) {
    private val _user = MutableStateFlow<FirebaseUser?>(null)
    val user: StateFlow<FirebaseUser?> = _user.asStateFlow()

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private val mutex = Mutex()

    /** Inicializa Firebase con los datos del proyecto (sin el plugin google-services). */
    fun init() {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setProjectId(FirebaseConfig.PROJECT_ID)
                    .setApplicationId(FirebaseConfig.ANDROID_APP_ID)
                    .setApiKey(FirebaseConfig.API_KEY)
                    .setGcmSenderId(FirebaseConfig.SENDER_ID)
                    .setStorageBucket(FirebaseConfig.STORAGE_BUCKET)
                    .build(),
            )
        }
        val auth = Firebase.auth
        _user.value = auth.currentUser
        auth.addAuthStateListener { _user.value = it.currentUser }
    }

    // ---- Cuenta ----

    /** Abre el selector de cuentas de Google y entra en Firebase con la elegida. */
    suspend fun signIn(activityContext: Context) {
        val option = GetSignInWithGoogleOption.Builder(FirebaseConfig.WEB_CLIENT_ID).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val result = CredentialManager.create(activityContext).getCredential(activityContext, request)
        val credential = result.credential
        check(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Unexpected credential type"
        }
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
        requestSync()
        schedulePeriodic()
    }

    suspend fun signOut(activityContext: Context) {
        Firebase.auth.signOut()
        runCatching { CredentialManager.create(activityContext).clearCredentialState(ClearCredentialStateRequest()) }
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK)
    }

    // ---- Sincronización ----

    /** Copia pronto (cuando haya conexión). Se llama tras cada cambio. */
    fun requestSync() {
        if (Firebase.auth.currentUser == null) return
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkConstraint())
            .setInitialDelay(2, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(ONE_TIME_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    /** Red de seguridad: una copia al día aunque no se toque nada. */
    fun schedulePeriodic() {
        if (Firebase.auth.currentUser == null) return
        val request = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.DAYS)
            .setConstraints(networkConstraint())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private fun networkConstraint() =
        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Sube a Firestore todo lo cambiado desde la última copia. Devuelve false si falló. */
    suspend fun pushChanges(): Boolean = mutex.withLock { push() }

    private suspend fun push(): Boolean {
        val user = Firebase.auth.currentUser ?: return true
        if (settings.syncUid != user.uid) {
            settings.syncUid = user.uid
            settings.syncWatermark = -1L
            settings.pullWatermark = -1L
        }
        _status.value = SyncStatus.Syncing
        try {
            val firestore = Firebase.firestore
            val root = firestore.collection("users").document(user.uid)
            // 1) Bajar primero lo cambiado fuera (la web); así el último cambio gana.
            pull(root)

            val startedAt = System.currentTimeMillis()
            val since = settings.syncWatermark
            val dao = db.journalDao()
            val writes = mutableListOf<Pair<DocumentReference, Map<String, Any?>>>()

            dao.daysChangedSince(since).forEach { d ->
                writes += root.collection("days").document(d.epochDay.toString()) to mapOf(
                    "epochDay" to d.epochDay,
                    "date" to LocalDate.ofEpochDay(d.epochDay).toString(),
                    "summary" to d.summary,
                    "summaryUpdatedAt" to d.summaryUpdatedAt,
                    "transcribed" to d.transcribed,
                    "lastTranscribedAt" to d.lastTranscribedAt,
                    "updatedAt" to d.updatedAt,
                )
            }
            dao.entriesChangedSince(since).forEach { e ->
                writes += root.collection("entries").document(e.remoteId ?: e.id.toString()) to mapOf(
                    "id" to e.id,
                    "epochDay" to e.epochDay,
                    "date" to LocalDate.ofEpochDay(e.epochDay).toString(),
                    "type" to e.type.name,
                    "status" to e.status.name,
                    "symbol" to symbolFor(e.type, e.status),
                    "text" to e.text,
                    "position" to e.position,
                    "createdAt" to e.createdAt,
                    "updatedAt" to e.updatedAt,
                    "deleted" to e.deleted,
                    "migratedTo" to e.migratedTo,
                )
            }
            dao.monthEntriesChangedSince(since).forEach { m ->
                writes += root.collection("monthEntries").document(m.remoteId ?: m.id.toString()) to mapOf(
                    "id" to m.id,
                    "ym" to m.ym,
                    "type" to m.type.name,
                    "status" to m.status.name,
                    "symbol" to symbolFor(m.type, m.status),
                    "text" to m.text,
                    "position" to m.position,
                    "createdAt" to m.createdAt,
                    "updatedAt" to m.updatedAt,
                    "deleted" to m.deleted,
                    "migratedTo" to m.migratedTo,
                )
            }
            dao.habitsChangedSince(since).forEach { h ->
                writes += root.collection("habits").document(h.id.toString()) to mapOf(
                    "id" to h.id,
                    "name" to h.name,
                    "position" to h.position,
                    "archived" to h.archived,
                    "updatedAt" to h.updatedAt,
                )
            }
            dao.habitChecksChangedSince(since).forEach { c ->
                writes += root.collection("habitChecks").document("${c.epochDay}_${c.habitId}") to mapOf(
                    "epochDay" to c.epochDay,
                    "date" to LocalDate.ofEpochDay(c.epochDay).toString(),
                    "habitId" to c.habitId,
                    "done" to c.done,
                    "updatedAt" to c.updatedAt,
                )
            }

            withTimeout(TIMEOUT_MS) {
                // Lotes de 400 (Firestore admite hasta 500 escrituras por lote).
                writes.chunked(400).forEach { chunk ->
                    val batch = firestore.batch()
                    chunk.forEach { (ref, data) -> batch.set(ref, data) }
                    batch.commit().await()
                }
                root.set(
                    mapOf("lastSyncAt" to startedAt, "appVersion" to BuildConfig.VERSION_NAME),
                    SetOptions.merge(),
                ).await()
            }
            settings.syncWatermark = startedAt
            settings.setLastSyncAt(System.currentTimeMillis())
            _status.value = SyncStatus.Idle
            return true
        } catch (e: CancellationException) {
            _status.value = SyncStatus.Idle
            throw e
        } catch (e: Exception) {
            _status.value = SyncStatus.Error(e.message ?: e.javaClass.simpleName)
            return false
        }
    }

    /** Aplica en Room lo que cambió en Firestore desde la última bajada (si es más reciente). */
    private suspend fun pull(root: DocumentReference) {
        val since = settings.pullWatermark
        var maxSeen = since
        suspend fun fetch(name: String): List<DocumentSnapshot> = withTimeout(TIMEOUT_MS) {
            root.collection(name).whereGreaterThan("updatedAt", since).get().await().documents
        }
        val habits = fetch("habits")
        val days = fetch("days")
        val entries = fetch("entries")
        val checks = fetch("habitChecks")
        val monthEntries = fetch("monthEntries")
        val dao = db.journalDao()

        db.withTransaction {
            // Hábitos primero (las marcas dependen de ellos)
            for (d in habits) {
                val updatedAt = d.getLong("updatedAt") ?: continue
                maxSeen = maxOf(maxSeen, updatedAt)
                val id = d.getLong("id") ?: d.id.toLongOrNull() ?: continue
                val local = dao.getHabit(id)
                if (local != null && updatedAt <= local.updatedAt) continue
                val name = d.getString("name") ?: local?.name ?: continue
                dao.upsertHabit(
                    HabitEntity(
                        id = id,
                        name = name,
                        position = (d.getLong("position") ?: local?.position?.toLong() ?: 0L).toInt(),
                        archived = d.getBoolean("archived") ?: false,
                        updatedAt = updatedAt,
                    ),
                )
            }
            for (d in days) {
                val updatedAt = d.getLong("updatedAt") ?: continue
                maxSeen = maxOf(maxSeen, updatedAt)
                val epochDay = d.getLong("epochDay") ?: d.id.toLongOrNull() ?: continue
                val local = dao.getDay(epochDay)
                if (local != null && updatedAt <= local.updatedAt) continue
                dao.upsertDay(
                    DayEntity(
                        epochDay = epochDay,
                        summary = d.getString("summary") ?: local?.summary.orEmpty(),
                        summaryUpdatedAt = d.getLong("summaryUpdatedAt") ?: local?.summaryUpdatedAt ?: 0L,
                        transcribed = d.getBoolean("transcribed") ?: false,
                        lastTranscribedAt = d.getLong("lastTranscribedAt") ?: local?.lastTranscribedAt,
                        updatedAt = updatedAt,
                    ),
                )
            }
            for (d in entries) {
                val updatedAt = d.getLong("updatedAt") ?: continue
                maxSeen = maxOf(maxSeen, updatedAt)
                // Las de la app usan su id numérico como documento; las de la web, un id propio.
                val numericId = d.id.toLongOrNull()
                val local = dao.getEntryByRemoteId(d.id) ?: numericId?.let { dao.getEntry(it) }
                if (local != null && updatedAt <= local.updatedAt) continue
                val epochDay = d.getLong("epochDay") ?: continue
                val type = d.getString("type")?.let { t -> EntryType.entries.firstOrNull { it.name == t } } ?: continue
                val status = d.getString("status")?.let { s -> TaskStatus.entries.firstOrNull { it.name == s } }
                    ?: TaskStatus.OPEN
                val text = d.getString("text").orEmpty()
                val deleted = d.getBoolean("deleted") ?: false
                val position = d.getLong("position")?.toInt() ?: local?.position ?: dao.nextEntryPosition(epochDay)
                if (local != null) {
                    dao.updateEntry(
                        local.copy(
                            epochDay = epochDay, type = type, status = status, text = text,
                            position = position, deleted = deleted, updatedAt = updatedAt,
                            migratedTo = d.getLong("migratedTo"),
                        ),
                    )
                } else {
                    dao.upsertEntry(
                        EntryEntity(
                            id = numericId ?: 0,
                            epochDay = epochDay,
                            type = type,
                            status = status,
                            text = text,
                            position = position,
                            createdAt = d.getLong("createdAt") ?: updatedAt,
                            updatedAt = updatedAt,
                            deleted = deleted,
                            remoteId = if (numericId == null) d.id else null,
                            migratedTo = d.getLong("migratedTo"),
                        ),
                    )
                }
            }
            for (d in checks) {
                val updatedAt = d.getLong("updatedAt") ?: continue
                maxSeen = maxOf(maxSeen, updatedAt)
                val epochDay = d.getLong("epochDay") ?: continue
                val habitId = d.getLong("habitId") ?: continue
                if (dao.getHabit(habitId) == null) continue
                val local = dao.getHabitCheck(epochDay, habitId)
                if (local != null && updatedAt <= local.updatedAt) continue
                dao.upsertHabitCheck(HabitCheckEntity(epochDay, habitId, d.getBoolean("done") ?: false, updatedAt))
            }
        }
        db.withTransaction {
            for (d in monthEntries) {
                val updatedAt = d.getLong("updatedAt") ?: continue
                maxSeen = maxOf(maxSeen, updatedAt)
                val numericId = d.id.toLongOrNull()
                val local = dao.getMonthEntryByRemoteId(d.id) ?: numericId?.let { dao.getMonthEntry(it) }
                if (local != null && updatedAt <= local.updatedAt) continue
                val ym = d.getLong("ym")?.toInt() ?: continue
                val type = d.getString("type")?.let { t -> EntryType.entries.firstOrNull { it.name == t } } ?: continue
                val status = d.getString("status")?.let { s -> TaskStatus.entries.firstOrNull { it.name == s } }
                    ?: TaskStatus.OPEN
                val position = d.getLong("position")?.toInt() ?: local?.position ?: dao.nextMonthPosition(ym)
                val migratedTo = d.getLong("migratedTo")?.toInt()
                if (local != null) {
                    dao.updateMonthEntry(
                        local.copy(
                            ym = ym, type = type, status = status, text = d.getString("text").orEmpty(),
                            position = position, deleted = d.getBoolean("deleted") ?: false,
                            updatedAt = updatedAt, migratedTo = migratedTo,
                        ),
                    )
                } else {
                    dao.upsertMonthEntry(
                        MonthEntryEntity(
                            id = numericId ?: 0, ym = ym, type = type, status = status,
                            text = d.getString("text").orEmpty(), position = position,
                            createdAt = d.getLong("createdAt") ?: updatedAt, updatedAt = updatedAt,
                            deleted = d.getBoolean("deleted") ?: false,
                            remoteId = if (numericId == null) d.id else null, migratedTo = migratedTo,
                        ),
                    )
                }
            }
        }
        settings.pullWatermark = maxSeen
    }

    companion object {
        private const val ONE_TIME_WORK = "copia_nube"
        private const val PERIODIC_WORK = "copia_nube_diaria"
        private const val TIMEOUT_MS = 60_000L
    }
}

class SyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val cloud = (applicationContext as CuadernoApp).container.cloud
        return if (cloud.pushChanges()) Result.success() else Result.retry()
    }
}
