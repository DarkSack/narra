package app.narra.presentation.common

import app.narra.domain.model.Chapter
import app.narra.domain.model.PlayableTrack

/**
 * Posición de escucha expresada como la piensa el usuario: dentro del capítulo y del libro,
 * no dentro de un segmento interno.
 */
data class PlaybackTimeline(
    val chapterId: Long?,
    val chapterTitle: String?,
    val chapterNumber: Int,
    val chapterPositionMs: Long,
    /** Lo ya generado del capítulo: hasta aquí se puede saltar. */
    val chapterAvailableMs: Long,
    /** Duración estimada del capítulo completo (igual a la disponible si ya está generado). */
    val chapterDurationMs: Long,
    val bookPositionMs: Long,
) {
    val chapterFraction: Float
        get() = if (chapterDurationMs > 0) (chapterPositionMs.toFloat() / chapterDurationMs).coerceIn(0f, 1f) else 0f
    val chapterAvailableFraction: Float
        get() = if (chapterDurationMs > 0) (chapterAvailableMs.toFloat() / chapterDurationMs).coerceIn(0f, 1f) else 0f
    val chapterRemainingMs: Long get() = (chapterDurationMs - chapterPositionMs).coerceAtLeast(0)

    companion object {
        fun compute(
            tracks: List<PlayableTrack>,
            chapters: List<Chapter>,
            segmentId: Long?,
            positionInSegmentMs: Long,
        ): PlaybackTimeline? {
            val currentIndex = tracks.indexOfFirst { it.segmentId == segmentId }
            if (currentIndex < 0) return null
            val current = tracks[currentIndex]
            val chapterTracks = tracks.filter { it.chapterId == current.chapterId }
            val beforeInChapter = chapterTracks.takeWhile { it.segmentId != segmentId }.sumOf { it.durationMs }
            val available = chapterTracks.sumOf { it.durationMs }
            val chapter = chapters.firstOrNull { it.id == current.chapterId }
            val includedChapters = chapters.filter { it.included }
            return PlaybackTimeline(
                chapterId = current.chapterId,
                chapterTitle = current.chapterTitle,
                chapterNumber = includedChapters.indexOfFirst { it.id == current.chapterId } + 1,
                chapterPositionMs = beforeInChapter + positionInSegmentMs,
                chapterAvailableMs = available,
                chapterDurationMs = maxOf(available, chapter?.durationMs ?: 0L),
                bookPositionMs = tracks.take(currentIndex).sumOf { it.durationMs } + positionInSegmentMs,
            )
        }

        /** Traduce una posición dentro del capítulo a (segmento, posición en el segmento). */
        fun locate(tracks: List<PlayableTrack>, chapterId: Long, chapterPositionMs: Long): Pair<Long, Long>? {
            val chapterTracks = tracks.filter { it.chapterId == chapterId }
            if (chapterTracks.isEmpty()) return null
            var remaining = chapterPositionMs.coerceAtLeast(0)
            for (track in chapterTracks) {
                if (remaining < track.durationMs) return track.segmentId to remaining
                remaining -= track.durationMs
            }
            val last = chapterTracks.last()
            return last.segmentId to last.durationMs
        }
    }
}
