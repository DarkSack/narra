package app.narra.presentation.common

import app.narra.core.ui.plural
import app.narra.domain.model.Book
import app.narra.domain.model.BookState
import app.narra.domain.model.Chapter
import app.narra.domain.model.JobState
import app.narra.domain.model.ProcessingJob
import app.narra.presentation.components.ProcessingSummary

/**
 * Redacta qué está pasando con un libro en proceso. Nunca "Procesando…": siempre qué parte,
 * cuánto falta y qué se puede hacer ya.
 */
fun summarizeProcessing(book: Book, chapters: List<Chapter>, job: ProcessingJob?): ProcessingSummary {
    val included = chapters.filter { it.included }
    val availability = book.chaptersAvailable.takeIf { it > 0 }?.let {
        if (it == 1) "1 capítulo disponible para escuchar" else "$it capítulos disponibles para escuchar"
    }
    return when (book.state) {
        BookState.IDLE, BookState.ANALYZING -> ProcessingSummary(
            book = book,
            headline = "Analizando el libro",
            detail = book.analysis.pagesTotal.takeIf { it > 0 }?.let { "Página ${book.analysis.pagesDone} de $it" },
            availability = null,
            fraction = book.analysis.fraction,
            etaMs = null,
            isPaused = false,
        )
        BookState.OCR -> ProcessingSummary(
            book = book,
            headline = "Reconociendo el texto",
            detail = "Página ${book.analysis.ocrPagesDone} de ${book.analysis.ocrPagesTotal}",
            availability = null,
            fraction = book.analysis.ocrFraction,
            etaMs = null,
            isPaused = false,
        )
        BookState.PARSING -> ProcessingSummary(
            book = book,
            headline = "Organizando los capítulos",
            detail = plural(book.pageCount, "página", "páginas"),
            availability = null,
            fraction = 1f,
            etaMs = null,
            isPaused = false,
        )
        else -> {
            val current = included.firstOrNull { it.id == job?.currentChapterId }
                ?: included.firstOrNull { it.segmentsAvailable < it.segmentCount }
            val number = current?.let { included.indexOf(it) + 1 }
            val paused = book.state == BookState.PAUSED
            // Otro libro está ocupando el motor de voz: este espera su turno.
            val waiting = !paused && job?.state == JobState.QUEUED
            val headline = when {
                waiting -> "En cola"
                number == null -> if (paused) "En pausa" else "Generando audio"
                paused -> "En pausa · capítulo $number de ${included.size}"
                else -> "Generando capítulo $number de ${included.size}"
            }
            val detail = current?.takeIf { it.segmentCount > 0 }?.let {
                "Segmento ${(it.segmentsAvailable + 1).coerceAtMost(it.segmentCount)} de ${it.segmentCount}"
            }
            ProcessingSummary(
                book = book,
                headline = headline,
                detail = detail,
                availability = availability,
                fraction = book.generationFraction,
                etaMs = if (paused || waiting) null else estimateRemainingMs(book, job),
                isPaused = paused,
            )
        }
    }
}

/** Tiempo de procesamiento restante según el ritmo medido en este teléfono. */
fun estimateRemainingMs(book: Book, job: ProcessingJob?): Long? {
    val throughput = job?.throughputCharsPerSecond ?: return null
    if (throughput <= 0f) return null
    val remainingChars = book.totalChars * (1f - book.generationFraction)
    return (remainingChars / throughput * MS_PER_SECOND).toLong()
}

private const val MS_PER_SECOND = 1_000f
