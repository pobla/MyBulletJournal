package com.pobla.cuaderno.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import com.pobla.cuaderno.BuildConfig
import com.pobla.cuaderno.R
import com.pobla.cuaderno.data.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val versionCode: Int, val downloadUrl: String, val sizeBytes: Long) : UpdateState
    data class Downloading(val versionCode: Int, val progress: Float) : UpdateState
    data class ReadyToInstall(val versionCode: Int) : UpdateState
    data class Error(val message: String) : UpdateState
}

/**
 * Busca APKs nuevos en la release "debug-latest" de GitHub (la publica el workflow),
 * los descarga y lanza el instalador del sistema. Android siempre pide confirmar la instalación.
 */
class UpdateManager(
    private val context: Context,
    private val settings: AppSettings,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** Versión que el diálogo automático ya ofreció (para no repetirlo en la misma sesión). */
    var promptedVersion by mutableIntStateOf(0)

    private var job: Job? = null

    val currentVersionCode: Int get() = BuildConfig.VERSION_CODE
    val currentVersionName: String get() = BuildConfig.VERSION_NAME

    private val apkFile: File get() = File(File(context.cacheDir, "updates"), "update.apk")

    /** Al abrir la app: como mucho una comprobación al día. */
    fun checkIfDue() {
        val now = System.currentTimeMillis()
        if (now - settings.lastUpdateCheck < ONE_DAY_MS) return
        check()
    }

    fun check() {
        if (job?.isActive == true) return
        job = scope.launch {
            _state.value = UpdateState.Checking
            _state.value = try {
                val release = withContext(Dispatchers.IO) { fetchLatestRelease() }
                settings.lastUpdateCheck = System.currentTimeMillis()
                if (release != null && release.versionCode > currentVersionCode) {
                    UpdateState.Available(release.versionCode, release.url, release.size)
                } else {
                    UpdateState.UpToDate
                }
            } catch (e: Exception) {
                UpdateState.Error(context.getString(R.string.update_error_check, e.message ?: e.javaClass.simpleName))
            }
        }
    }

    fun download() {
        val available = _state.value as? UpdateState.Available ?: return
        if (job?.isActive == true) return
        job = scope.launch {
            _state.value = UpdateState.Downloading(available.versionCode, 0f)
            _state.value = try {
                withContext(Dispatchers.IO) {
                    downloadTo(available.downloadUrl, apkFile, available.sizeBytes) { progress ->
                        _state.value = UpdateState.Downloading(available.versionCode, progress)
                    }
                }
                UpdateState.ReadyToInstall(available.versionCode)
            } catch (e: Exception) {
                UpdateState.Error(context.getString(R.string.update_error_download, e.message ?: e.javaClass.simpleName))
            }
        }
    }

    /**
     * Abre el instalador. Si falta el permiso de "instalar apps desconocidas", abre esos ajustes;
     * al volver, hay que pulsar "Instalar" otra vez.
     */
    fun install(activityContext: Context) {
        if (_state.value !is UpdateState.ReadyToInstall || !apkFile.exists()) return
        val pm = activityContext.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !pm.canRequestPackageInstalls()) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${activityContext.packageName}"),
            )
            activityContext.startActivity(intent)
            return
        }
        val uri = FileProvider.getUriForFile(activityContext, "${activityContext.packageName}.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            activityContext.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            _state.value = UpdateState.Error(context.getString(R.string.update_error_installer))
        }
    }

    fun dismiss() {
        if (_state.value !is UpdateState.Downloading) _state.value = UpdateState.Idle
    }

    private data class Release(val versionCode: Int, val url: String, val size: Long)

    private fun fetchLatestRelease(): Release? {
        val connection = (URL(RELEASE_API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "CuadernoDeBolsillo")
        }
        try {
            if (connection.responseCode == HttpURLConnection.HTTP_NOT_FOUND) return null
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                error("HTTP ${connection.responseCode}")
            }
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val assets = json.getJSONArray("assets")
            var best: Release? = null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val match = APK_NAME.matchEntire(asset.getString("name")) ?: continue
                val code = match.groupValues[1].toInt()
                if (best == null || code > best.versionCode) {
                    best = Release(code, asset.getString("browser_download_url"), asset.optLong("size", -1))
                }
            }
            return best
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadTo(url: String, target: File, expectedSize: Long, onProgress: (Float) -> Unit) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, "${target.name}.part")
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "CuadernoDeBolsillo")
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) error("HTTP ${connection.responseCode}")
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: expectedSize
            connection.inputStream.use { input ->
                tmp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read = 0L
                    var lastReported = 0f
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        read += n
                        if (total > 0) {
                            val p = (read.toFloat() / total).coerceIn(0f, 1f)
                            if (p - lastReported >= 0.01f) {
                                lastReported = p
                                onProgress(p)
                            }
                        }
                    }
                }
            }
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val RELEASE_API = "https://api.github.com/repos/pobla/MyBulletJournal/releases/tags/debug-latest"
        val APK_NAME = Regex("""cuaderno-de-bolsillo-(\d+)\.apk""")
        const val ONE_DAY_MS = 24L * 60 * 60 * 1000
    }
}
