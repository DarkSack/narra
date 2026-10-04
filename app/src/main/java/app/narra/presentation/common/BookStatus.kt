package app.narra.presentation.common

import app.narra.core.designsystem.component.StatusTone
import app.narra.core.ui.formatDuration
import app.narra.core.ui.formatPercent
import app.narra.core.ui.plural
import app.narra.core.ui.toErrorText
import app.narra.domain.model.Book
import app.narra.domain.model.BookState

/** Cómo se describe el estado de un libro en tarjetas y listas: siempre concreto, nunca "Procesando…". */
data class BookStatusInfo(val label: String, val tone: StatusTone)

fun Book.statusInfo(): BookStatusInfo = when (state) {
    BookState.IDLE, BookState.ANALYZING -> BookStatusInfo(
        if (analysis.pagesTotal > 0) "Analizando · página ${analysis.pagesDone} de ${analysis.pagesTotal}" else "Analizando el libro",
        StatusTone.PROCESSING,
    )
    BookState.OCR -> BookStatusInfo(
        "Reconociendo texto · página ${analysis.ocrPagesDone} de ${analysis.ocrPagesTotal}",
        StatusTone.PROCESSING,
    )
    BookState.PARSING -> BookStatusInfo("Organizando capítulos", StatusTone.PROCESSING)
    BookState.READY -> BookStatusInfo("Listo para revisar", StatusTone.NEUTRAL)
    BookState.GENERATING -> BookStatusInfo(
        if (chaptersAvailable > 0) "$chaptersAvailable de $chapterCount capítulos listos" else "Generando el primer capítulo",
        StatusTone.PROCESSING,
    )
    BookState.PAUSED -> BookStatusInfo("En pausa · $chaptersAvailable de $chapterCount capítulos", StatusTone.NEUTRAL)
    BookState.COMPLETED -> when {
        isCompleted -> BookStatusInfo("Terminado", StatusTone.AVAILABLE)
        isStarted -> BookStatusInfo("${formatPercent(listenedFraction)} escuchado", StatusTone.AVAILABLE)
        else -> BookStatusInfo("Disponible", StatusTone.AVAILABLE)
    }
    BookState.ERROR -> BookStatusInfo(error?.kind?.toErrorText()?.title ?: "Necesita atención", StatusTone.ATTENTION)
}

/** "5 h 12 min · 19 capítulos". */
fun Book.metaLine(): String = buildList {
    if (durationMs > 0) add(if (state == BookState.COMPLETED) formatDuration(durationMs) else "≈ ${formatDuration(durationMs)}")
    if (chapterCount > 0) add(plural(chapterCount, "capítulo", "capítulos"))
    if (chapterCount == 0 && pageCount > 0) add(plural(pageCount, "página", "páginas"))
}.joinToString(" · ")

/** El libro ya se puede escuchar, aunque siga generándose. */
val Book.isListenable: Boolean get() = hasPlayableAudio
