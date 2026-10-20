package app.narra.update

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException

/** Busca y descarga la versión nueva en segundo plano (ver [AppUpdater]). */
@HiltWorker
class UpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val updater: AppUpdater,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        updater.downloadLatest()
        Result.success()
    } catch (e: IOException) {
        Log.w(TAG, "No se pudo buscar o descargar la versión nueva", e)
        if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
    }

    private companion object {
        const val TAG = "UpdateWorker"
        const val MAX_RETRIES = 3
    }
}

/** Lo que Narra usa de la respuesta de `GET /repos/{owner}/{repo}/releases/latest` de GitHub. */
@Serializable
internal data class GitHubRelease(
    @SerialName("tag_name") val tag: String,
    val assets: List<Asset> = emptyList(),
) {
    @Serializable
    data class Asset(
        val name: String,
        val size: Long,
        @SerialName("browser_download_url") val url: String,
    )

    /** La etiqueta es la versión con una «v» delante: `v3.1.0`. */
    val version: String get() = tag.removePrefix("v")
    val apk: Asset? get() = assets.firstOrNull { it.name.endsWith(".apk") }
}

private val json = Json { ignoreUnknownKeys = true }

internal fun parseRelease(body: String): GitHubRelease = json.decodeFromString(body)

/** Compara número por número («3.10.0» es más nueva que «3.9.1»); lo que no es número cuenta como 0. */
internal fun isNewer(version: String, current: String): Boolean {
    val a = version.split('.').map { it.toIntOrNull() ?: 0 }
    val b = current.split('.').map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(a.size, b.size)) {
        val diff = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
        if (diff != 0) return diff > 0
    }
    return false
}
