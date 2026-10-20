package app.narra.update

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.narra.BuildConfig
import app.narra.R
import app.narra.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Canal interno (APK fuera de Play): busca la última versión publicada en GitHub Releases,
 * la descarga sola con Wi-Fi y avisa para instalarla. Android siempre pide confirmar la instalación.
 */
@Singleton
class AppUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val _ready = MutableStateFlow(false)

    /** Hay una versión nueva descargada, lista para instalarse. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val apk get() = File(context.cacheDir, "updates/narra.apk")

    /** La búsqueda al abrir la app y la diaria pueden coincidir: no deben escribir el mismo archivo a la vez. */
    private val lock = Mutex()

    /** Al abrir la app: muestra lo ya descargado y busca ahora y una vez al día (APK de ~50 MB: solo con Wi-Fi). */
    fun check(activity: Activity) {
        scope.launch(Dispatchers.IO) { refresh() }
        val wifi = Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build()
        val work = WorkManager.getInstance(activity)
        work.enqueueUniqueWork(
            WORK_NOW,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<UpdateWorker>().setConstraints(wifi).build(),
        )
        work.enqueueUniquePeriodicWork(
            WORK_DAILY,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<UpdateWorker>(1, TimeUnit.DAYS).setConstraints(wifi).build(),
        )
    }

    /** Abre el instalador de Android. La primera vez pide permitir instalar apps desde Narra. */
    fun install() {
        context.startActivity(installIntent())
    }

    /** Descarga la última versión si es más nueva que la instalada y avisa. */
    suspend fun downloadLatest() = lock.withLock {
        withContext(Dispatchers.IO) {
            val release = fetchLatest() ?: return@withContext
            val asset = release.apk ?: return@withContext
            if (!isNewer(release.version, BuildConfig.VERSION_NAME)) return@withContext
            if (downloaded()?.versionName == release.version) return@withContext refresh()

            apk.parentFile?.mkdirs()
            val part = File(apk.path + ".part")
            open(asset.url).inputStream.use { input -> part.outputStream().use { input.copyTo(it) } }
            if (part.length() != asset.size || !part.renameTo(apk)) {
                part.delete()
                throw IOException("Descarga incompleta de ${asset.name}")
            }
            refresh()
            if (_ready.value) notifyReady(release.version)
        }
    }

    /** `null` si el repositorio aún no tiene versiones publicadas. */
    private fun fetchLatest(): GitHubRelease? {
        val connection = open(LATEST_RELEASE_URL)
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        if (connection.responseCode == HttpURLConnection.HTTP_NOT_FOUND) return null
        return parseRelease(connection.inputStream.bufferedReader().use { it.readText() })
    }

    private fun open(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = TIMEOUT_MS
        readTimeout = TIMEOUT_MS
    }

    /** Solo vale un APK de Narra más nuevo que el instalado; cualquier otro se borra. */
    private fun refresh() {
        val info = downloaded()
        val valid = info != null && info.packageName == context.packageName &&
            PackageInfoCompat.getLongVersionCode(info) > BuildConfig.VERSION_CODE
        if (!valid) apk.delete()
        _ready.value = valid
    }

    @Suppress("DEPRECATION") // La variante con PackageInfoFlags es de Android 13+.
    private fun downloaded() = if (apk.exists()) context.packageManager.getPackageArchiveInfo(apk.path, 0) else null

    private fun installIntent() = Intent(Intent.ACTION_VIEW)
        .setDataAndType(FileProvider.getUriForFile(context, "${context.packageName}.updates", apk), APK_MIME)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun notifyReady(version: String) {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted) return
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("Actualizaciones")
                .setDescription("Aviso cuando hay una versión nueva de Narra lista para instalarse.")
                .build(),
        )
        val install = PendingIntent.getActivity(
            context,
            0,
            installIntent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        manager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Narra $version está lista")
                .setContentText("Toca para instalarla. Tus libros y tu progreso se conservan.")
                .setContentIntent(install)
                .setAutoCancel(true)
                .build(),
        )
    }

    private companion object {
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/DarkSack/narra/releases/latest"
        const val APK_MIME = "application/vnd.android.package-archive"
        const val TIMEOUT_MS = 30_000
        const val WORK_NOW = "app-update-now"
        const val WORK_DAILY = "app-update-daily"
        const val CHANNEL = "updates"
        const val NOTIFICATION_ID = 3001
    }
}
