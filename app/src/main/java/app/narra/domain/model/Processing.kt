package app.narra.domain.model

import kotlinx.serialization.Serializable

/** Causa de un fallo, traducida a un mensaje humano en la capa de presentación. */
enum class ErrorKind {
    STORAGE_FULL,
    FILE_MISSING,
    PDF_ENCRYPTED,
    PDF_CORRUPT,
    PDF_EMPTY,
    NO_TEXT,
    OCR_FAILED,
    TTS_UNAVAILABLE,
    VOICE_UNAVAILABLE,
    SYNTHESIS_FAILED,
    ENCODING_FAILED,
    NETWORK,

    /** No se pudo escribir en la carpeta elegida al exportar. */
    EXPORT_FAILED,
    UNKNOWN,
}

data class ProcessingError(val kind: ErrorKind, val detail: String? = null)

/** Error con causa conocida que viaja por las capas sin perder su significado. */
class NarraException(val kind: ErrorKind, message: String? = null, cause: Throwable? = null) :
    Exception(message ?: kind.name, cause) {
    val error: ProcessingError get() = ProcessingError(kind, message)
}

enum class JobState { QUEUED, RUNNING, PAUSED, DONE, FAILED, CANCELLED }

data class ProcessingJob(
    val bookId: String,
    val state: JobState,
    val enqueuedAt: Long,
    val attempts: Int,
    /** El usuario pidió escuchar este capítulo: se genera antes que los demás. */
    val priorityChapterId: Long?,
    val currentChapterId: Long?,
    val currentSegmentIndex: Int,
    /** Caracteres por segundo medidos en este dispositivo, para estimar cuánto falta. */
    val throughputCharsPerSecond: Float,
    val error: ProcessingError?,
)

/** Qué se detectó al analizar el PDF. Se muestra en la pantalla de revisión. */
@Serializable
data class AnalysisReport(
    val chapterSource: ChapterSource = ChapterSource.PAGES,
    val headings: Int = 0,
    val headersRemoved: Int = 0,
    val footersRemoved: Int = 0,
    val pageNumbersRemoved: Int = 0,
    val footnotes: Int = 0,
    val images: Int = 0,
    val tables: Int = 0,
    val lists: Int = 0,
    val quotes: Int = 0,
    val dialogues: Int = 0,
    val references: Int = 0,
    /** Páginas escaneadas que siguen sin texto (no se reconocieron o están en blanco). */
    val scannedPages: Int = 0,
    val hyphenationsFixed: Int = 0,
    /** Páginas cuyo texto se obtuvo por reconocimiento óptico. */
    val ocrPages: Int = 0,
)

/** De dónde salió la lista de capítulos, de más a menos fiable. */
enum class ChapterSource { OUTLINE, TABLE_OF_CONTENTS, HEADINGS, PAGES, MANUAL }

data class ListeningProgress(
    val bookId: String,
    val segmentId: Long?,
    val chapterId: Long?,
    val chapterTitle: String?,
    val positionMs: Long,
    val listenedMs: Long,
    val completed: Boolean,
    val updatedAt: Long,
)

/** Estado de una exportación del audio de un libro a una carpeta del teléfono. */
sealed interface ExportStatus {
    /** [total] es 0 mientras se prepara. */
    data class Running(val done: Int, val total: Int) : ExportStatus

    data class Done(val folderName: String, val files: Int, val chaptersMissing: Int) : ExportStatus

    data class Failed(val kind: ErrorKind) : ExportStatus
}
