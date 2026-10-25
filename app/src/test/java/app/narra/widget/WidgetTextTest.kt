package app.narra.widget

import app.narra.domain.model.AnalysisProgress
import app.narra.domain.model.Book
import app.narra.domain.model.BookMetadata
import app.narra.domain.model.BookState
import app.narra.domain.model.ListeningProgress
import app.narra.domain.model.VoiceSettings
import app.narra.presentation.common.toContinue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetTextTest {

    private val hour = 3_600_000L

    private fun book(
        id: String,
        playedAt: Long? = null,
        importedAt: Long = 0,
        listenedMs: Long = 0,
        completed: Boolean = false,
        available: Int = 1,
        state: BookState = BookState.COMPLETED,
    ) = Book(
        id = id, metadata = BookMetadata("Libro $id"), coverPath = null, sourceFileName = "$id.pdf",
        fileSizeBytes = 1, pageCount = 10, importedAt = importedAt, lastPlayedAt = playedAt, isFavorite = false,
        state = state, error = null, voice = VoiceSettings(), chapterCount = 2, chaptersAvailable = available,
        segmentCount = 4, segmentsAvailable = available * 2, totalChars = 1_000,
        durationMs = 4 * hour, generatedDurationMs = 4 * hour, audioSizeBytes = 1,
        analysis = AnalysisProgress(pagesDone = 1, pagesTotal = 1),
        progress = if (listenedMs > 0 || completed) {
            ListeningProgress(id, 1, 1, "Capítulo 1", 0, listenedMs, completed, 0)
        } else {
            null
        },
    )

    @Test
    fun continuesTheLastPlayedUnfinishedBook() {
        val books = listOf(
            book("viejo", playedAt = 1, listenedMs = hour),
            book("reciente", playedAt = 5, listenedMs = hour),
            book("terminado", playedAt = 9, completed = true),
        )
        assertEquals("reciente", books.toContinue()?.id)
    }

    @Test
    fun withNothingStartedOffersTheNewestListenableBook() {
        val books = listOf(book("a", importedAt = 1), book("b", importedAt = 2), book("sin audio", importedAt = 3, available = 0))
        assertEquals("b", books.toContinue()?.id)
        assertNull(emptyList<Book>().toContinue())
    }

    @Test
    fun statusSaysHowFarAlongYouAre() {
        assertEquals("25 % escuchado · Quedan 3 h", book("x", listenedMs = hour).widgetStatus())
        assertEquals("Terminado", book("x", completed = true).widgetStatus())
        assertEquals("Disponible", book("x").widgetStatus())
        assertEquals("1 de 2 capítulos listos", book("x", state = BookState.GENERATING).widgetStatus())
    }
}
