package app.narra.domain.model

enum class ChapterState { PENDING, PROCESSING, AVAILABLE, ERROR, SKIPPED }

data class Chapter(
    val id: Long,
    val bookId: String,
    val index: Int,
    val title: String,
    /** 0 = capítulo, 1 = subcapítulo… Se usa para sangrar el índice. */
    val level: Int,
    val startPage: Int,
    val endPage: Int,
    val included: Boolean,
    val state: ChapterState,
    val charCount: Int,
    val segmentCount: Int,
    val segmentsAvailable: Int,
    val durationMs: Long,
    val error: ProcessingError?,
) {
    val generationFraction: Float
        get() = if (segmentCount > 0) segmentsAvailable.toFloat() / segmentCount else 0f

    val isPlayable: Boolean get() = included && segmentsAvailable > 0
}

/** Avance de escucha dentro de un capítulo, para el índice. */
data class ChapterListening(val chapterId: Long, val listenedMs: Long, val isCurrent: Boolean)
