package app.narra.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import app.narra.data.generation.AudioGenerator
import app.narra.data.generation.GenerationEvent
import app.narra.data.generation.QueueResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Procesa la cola de generación en primer plano, con una notificación que muestra el avance.
 * Si el sistema lo detiene, WorkManager lo reprograma y la cola sigue donde estaba.
 */
@HiltWorker
class GenerationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val generator: AudioGenerator,
    private val notifications: GenerationNotifications,
    private val queue: WorkManagerProcessingQueue,
    private val background: BackgroundPolicy,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Con la creación limitada a la app abierta, la cola sigue al volver a Narra.
        if (!background.allowsWork()) return Result.success()
        val result = generator.runQueue { event ->
            when (event) {
                is GenerationEvent.Progress -> promote(notifications.foregroundInfo(event))
                is GenerationEvent.Finished -> notifications.finished(event)
                is GenerationEvent.Failed -> notifications.failed(event)
            }
        }
        if (result is QueueResult.NeedsNetwork) queue.waitForNetwork(result.policy)
        return Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = notifications.foregroundInfo(null)

    /**
     * Android 12+ no deja iniciar un servicio en primer plano desde segundo plano en algunos
     * casos. Entonces se sigue trabajando sin él: WorkManager reanudará la cola si lo detiene.
     */
    private suspend fun promote(info: ForegroundInfo) {
        try {
            setForeground(info)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Sin servicio en primer plano; se continúa en segundo plano", e)
        }
    }

    companion object {
        const val UNIQUE_NAME = "generation"

        /** Despertar aparte que espera la conexión para las voces en línea. */
        const val NETWORK_UNIQUE_NAME = "generation-network"
        private const val TAG = "GenerationWorker"
    }
}
