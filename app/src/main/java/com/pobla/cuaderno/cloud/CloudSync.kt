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

sealed interface SyncStatus {
    data object Idle : SyncStatus
    data object Syncing : SyncStatus
    data class Error(val message: String) : SyncStatus
}

/**
 * Copia en la nube con Firebase. Room sigue siendo la fuente de verdad: aquí solo se SUBE
 * a Firestore lo que cambió desde la última copia, en `users/{uid}/…`. Las reglas de
 * Firestore hacen que solo esa cuenta pueda leer y escribir sus datos.
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
        }
        _status.value = SyncStatus.Syncing
        try {
            val startedAt = System.currentTimeMillis()
            val since = settings.syncWatermark
            val dao = db.journalDao()
            val firestore = Firebase.firestore
            val root = firestore.collection("users").document(user.uid)
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
                writes += root.collection("entries").document(e.id.toString()) to mapOf(
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
