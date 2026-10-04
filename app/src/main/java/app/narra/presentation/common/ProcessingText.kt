package app.narra.presentation.common

import app.narra.core.ui.plural
import app.narra.domain.model.Book
import app.narra.domain.model.BookState
import app.narra.domain.model.Chapter
import app.narra.domain.model.ChapterState
import app.narra.domain.model.ErrorKind
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
            detail = book.analysis.pagesTotal.takeIf { it > 0 }?.let { "${book.analysis.pagesDone} de $it páginas" },
            availability = null,
            fraction = book.analysis.fraction,
            etaMs = null,
            isPaused = false,
        )
        BookState.OCR -> ProcessingSummary(
            book = book,
            headline = "Reconociendo el texto",
            detail = "${book.analysis.ocrPagesDone} de ${book.analysis.ocrPagesTotal} páginas",
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
            // El cursor del trabajo apunta al último segmento terminado: el capítulo en curso es
            // el que se está generando o, si aún no empezó, el siguiente con partes pendientes.
            val pending = { chapter: Chapter -> chapter.segmentsAvailable < chapter.segmentCount }
            val current = included.firstOrNull { it.state == ChapterState.PROCESSING && pending(it) }
                ?: included.firstOrNull { it.id == job?.priorityChapterId && pending(it) }
                ?: included.firstOrNull(pending)
            val number = current?.let { included.indexOf(it) + 1 }
            val paused = book.state == BookState.PAUSED
            // Otro libro está ocupando el motor de voz, o la voz en línea espera conexión.
            val waiting = !paused && job?.state == JobState.QUEUED
            val needsNetwork = waiting && job?.error?.kind == ErrorKind.NETWORK
            val headline = when {
                needsNetwork -> "Esperando conexión"
                waiting -> "En cola"
                number == null -> if (paused) "En pausa" else "Generando audio"
                paused -> "En pausa · capítulo $number de ${included.size}"
                else -> "Generando capítulo $number de ${included.size}"
            }
            val detail = if (needsNetwork) {
                "La voz elegida necesita internet. Seguirá sola cuando te conectes."
            } else {
                current?.takeIf { it.segmentCount > 0 }?.let {
                    "Segmento ${(it.segmentsAvailable + 1).coerceAtMost(it.segmentCount)} de ${it.segmentCount}"
                }
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
