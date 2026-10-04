package app.narra.work

import app.narra.data.db.NarraDatabase
import app.narra.di.ApplicationScope
import app.narra.domain.model.BookState
import app.narra.domain.repository.ProcessingQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Al arrancar, vuelve a poner en marcha lo que se quedó a medias (por ejemplo, si se forzó el
 * cierre de la app): análisis interrumpidos y la cola de generación.
 */
@Singleton
class ProcessingRecovery @Inject constructor(
    private val db: NarraDatabase,
    private val queue: ProcessingQueue,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    fun resume() {
        scope.launch {
            db.bookDao().idsInStates(IMPORTING_STATES).forEach { queue.analyze(it) }
            queue.resumePending()
        }
    }

    private companion object {
        val IMPORTING_STATES = listOf(BookState.IDLE, BookState.ANALYZING, BookState.OCR, BookState.PARSING)
    }
}
