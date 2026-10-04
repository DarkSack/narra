package app.narra.domain.model

import kotlinx.serialization.Serializable

enum class SegmentState { PENDING, PROCESSING, AVAILABLE, ERROR }

/** Tipo de contenido. Permite pausas distintas y, en el futuro, voces por personaje. */
enum class SegmentKind { NARRATION, HEADING, DIALOGUE, QUOTE, LIST }

/** Párrafo dentro de un segmento: la unidad mínima que se envía al motor de voz. */
@Serializable
data class Paragraph(
    val text: String,
    val kind: SegmentKind = SegmentKind.NARRATION,
    /** Hablante detectado (null = narrador). Preparado para asignar voces por personaje. */
    val speaker: String? = null,
)

data class Segment(
    val id: Long,
    val bookId: String,
    val chapterId: Long,
    val index: Int,
    val paragraphs: List<Paragraph>,
    val state: SegmentState,
    val attempts: Int,
    val error: ProcessingError?,
) {
    val charCount: Int get() = paragraphs.sumOf { it.text.length }
}

/** Archivo de audio generado para un segmento. El audio nunca se guarda en la base de datos. */
data class AudioTrack(
    val segmentId: Long,
    val bookId: String,
    val chapterId: Long,
    val path: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val format: AudioFormat,
)

/** Elemento de la cola de reproducción: un segmento ya generado, con su capítulo. */
data class PlayableTrack(
    val segmentId: Long,
    val chapterId: Long,
    val chapterIndex: Int,
    val chapterTitle: String,
    val segmentIndex: Int,
    val path: String,
    val durationMs: Long,
)
