package app.narra.domain.model

/** Estado global de un libro, de la importación a la escucha. */
enum class BookState {
    IDLE,
    ANALYZING,
    OCR,
    PARSING,
    READY,
    GENERATING,
    PAUSED,
    COMPLETED,
    ERROR,
    ;

    /** Hay trabajo en curso o pendiente que el usuario no ha detenido. */
    val isWorking: Boolean get() = this == ANALYZING || this == OCR || this == PARSING || this == GENERATING

    /** Todavía no hay capítulos definitivos: el libro está en la fase de importación. */
    val isImporting: Boolean get() = this == IDLE || this == ANALYZING || this == OCR || this == PARSING
}

data class BookMetadata(
    val title: String,
    val author: String? = null,
    val description: String? = null,
    val language: String? = null,
    val publisher: String? = null,
    val publishedDate: String? = null,
    val isbn: String? = null,
    val keywords: List<String> = emptyList(),
)

data class AnalysisProgress(
    val pagesDone: Int = 0,
    val pagesTotal: Int = 0,
    val scannedPages: Int = 0,
    val ocrPagesDone: Int = 0,
    val ocrPagesTotal: Int = 0,
) {
    val fraction: Float get() = if (pagesTotal > 0) pagesDone.toFloat() / pagesTotal else 0f
    val ocrFraction: Float get() = if (ocrPagesTotal > 0) ocrPagesDone.toFloat() / ocrPagesTotal else 0f
}

data class Book(
    val id: String,
    val metadata: BookMetadata,
    val coverPath: String?,
    val sourceFileName: String,
    val fileSizeBytes: Long,
    val pageCount: Int,
    val importedAt: Long,
    val lastPlayedAt: Long?,
    val isFavorite: Boolean,
    val state: BookState,
    val error: ProcessingError?,
    val voice: VoiceSettings,
    val chapterCount: Int,
    val chaptersAvailable: Int,
    val segmentCount: Int,
    val segmentsAvailable: Int,
    val totalChars: Int,
    /** Mejor estimación: duración real de lo generado + estimación de lo que falta. */
    val durationMs: Long,
    val generatedDurationMs: Long,
    val audioSizeBytes: Long,
    val analysis: AnalysisProgress,
    val progress: ListeningProgress?,
) {
    val title: String get() = metadata.title
    val author: String? get() = metadata.author

    val listenedFraction: Float
        get() = when {
            progress == null -> 0f
            progress.completed -> 1f
            durationMs <= 0 -> 0f
            else -> (progress.listenedMs.toFloat() / durationMs).coerceIn(0f, 1f)
        }

    val remainingMs: Long get() = (durationMs - (progress?.listenedMs ?: 0)).coerceAtLeast(0)

    val generationFraction: Float
        get() = if (segmentCount > 0) segmentsAvailable.toFloat() / segmentCount else 0f

    val hasPlayableAudio: Boolean get() = segmentsAvailable > 0

    val isCompleted: Boolean get() = progress?.completed == true

    val isStarted: Boolean get() = (progress?.listenedMs ?: 0) > 0
}
