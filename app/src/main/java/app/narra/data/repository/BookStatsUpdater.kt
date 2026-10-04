package app.narra.data.repository

import androidx.room.withTransaction
import app.narra.data.db.NarraDatabase
import app.narra.data.db.SegmentTotals
import app.narra.domain.model.ChapterState
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Recalcula los contadores de capítulos y libros (segmentos listos, duración, tamaño) a partir
 * de los segmentos y pistas. Se guardan desnormalizados para que la biblioteca pueda ordenar y
 * filtrar sin consultas pesadas.
 */
@Singleton
class BookStatsUpdater @Inject constructor(private val db: NarraDatabase) {

    suspend fun refreshChapter(chapterId: Long) {
        val chapter = db.chapterDao().get(chapterId) ?: return
        val book = db.bookDao().get(chapter.bookId) ?: return
        val totals = db.chapterDao().totals(chapterId)
        db.chapterDao().setStats(
            id = chapterId,
            segmentCount = totals.segmentCount,
            segmentsAvailable = totals.segmentsAvailable,
            charCount = totals.totalChars,
            durationMs = totals.estimatedDurationMs(book.speechRate),
        )
        val state = when {
            !chapter.included -> ChapterState.SKIPPED
            totals.segmentCount > 0 && totals.segmentsAvailable == totals.segmentCount -> ChapterState.AVAILABLE
            chapter.state == ChapterState.ERROR || chapter.state == ChapterState.PROCESSING -> chapter.state
            totals.segmentsAvailable > 0 -> ChapterState.PROCESSING
            else -> ChapterState.PENDING
        }
        if (state != chapter.state) db.chapterDao().setState(chapterId, state, chapter.errorKind, chapter.errorDetail)
    }

    suspend fun refreshBook(bookId: String) = db.withTransaction {
        val book = db.bookDao().get(bookId) ?: return@withTransaction
        val chapters = db.chapterDao().getAll(bookId)
        chapters.forEach { refreshChapter(it.id) }
        val refreshed = db.chapterDao().getAll(bookId).filter { it.included }
        val totals = db.segmentDao().bookTotals(bookId)
        db.bookDao().setStats(
            id = bookId,
            chapterCount = refreshed.size,
            chaptersAvailable = refreshed.count { it.state == ChapterState.AVAILABLE },
            segmentCount = totals.segmentCount,
            segmentsAvailable = totals.segmentsAvailable,
            totalChars = totals.totalChars,
            durationMs = totals.estimatedDurationMs(book.speechRate),
            generatedDurationMs = totals.generatedDurationMs,
            audioSizeBytes = totals.audioSizeBytes,
        )
    }

    companion object {
        /** Ritmo medio de una voz sintética en español a velocidad 1×. */
        const val DEFAULT_CHARS_PER_SECOND = 15.0

        /**
         * Duración real de lo generado + lo pendiente estimado. En cuanto hay audio generado,
         * la estimación se calibra con el ritmo real de la voz elegida.
         */
        fun SegmentTotals.estimatedDurationMs(speechRate: Float): Long {
            val pendingChars = (totalChars - generatedChars).coerceAtLeast(0)
            val msPerChar = if (generatedChars > 0 && generatedDurationMs > 0) {
                generatedDurationMs.toDouble() / generatedChars
            } else {
                1000.0 / (DEFAULT_CHARS_PER_SECOND * speechRate.coerceAtLeast(0.1f))
            }
            return generatedDurationMs + (pendingChars * msPerChar).toLong()
        }
    }
}
