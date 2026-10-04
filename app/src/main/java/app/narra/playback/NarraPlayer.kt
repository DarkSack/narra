package app.narra.playback

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/**
 * Envoltorio del reproductor con la semántica de un audiolibro:
 * - "Anterior/Siguiente" (auriculares, notificación, pantalla de bloqueo) saltan de capítulo,
 *   no de segmento: los segmentos son un detalle interno.
 * - Los saltos atrás/adelante usan los segundos configurados por el usuario.
 */
@UnstableApi
class NarraPlayer(
    player: Player,
    private val skipBackMs: () -> Long,
    private val skipForwardMs: () -> Long,
) : ForwardingPlayer(player) {

    override fun getSeekBackIncrement(): Long = skipBackMs()

    override fun getSeekForwardIncrement(): Long = skipForwardMs()

    override fun seekBack() {
        seekWithinBook(-skipBackMs())
    }

    override fun seekForward() {
        seekWithinBook(skipForwardMs())
    }

    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon()
            .add(COMMAND_SEEK_TO_NEXT)
            .add(COMMAND_SEEK_TO_PREVIOUS)
            .add(COMMAND_SEEK_BACK)
            .add(COMMAND_SEEK_FORWARD)
            .build()

    override fun isCommandAvailable(command: Int): Boolean = availableCommands.contains(command)

    override fun seekToNext() {
        val target = nextChapterStart() ?: return
        seekTo(target, 0)
    }

    override fun seekToPrevious() {
        val current = currentMediaItemIndex
        if (current < 0) return
        val chapterStart = chapterStartIndex(current)
        val positionInChapter = durationBetween(chapterStart, current) + currentPosition
        val target = if (positionInChapter > RESTART_CHAPTER_THRESHOLD_MS) {
            chapterStart
        } else {
            previousChapterStart(chapterStart) ?: chapterStart
        }
        seekTo(target, 0)
    }

    /** Salta [deltaMs] cruzando segmentos si hace falta (un salto de 30 s no se queda corto). */
    private fun seekWithinBook(deltaMs: Long) {
        var index = currentMediaItemIndex
        if (index < 0) return
        var position = currentPosition + deltaMs
        while (position < 0 && index > 0) {
            index--
            position += itemDuration(index)
        }
        while (index < mediaItemCount - 1 && position > itemDuration(index)) {
            position -= itemDuration(index)
            index++
        }
        val clamped = position.coerceIn(0, itemDuration(index).coerceAtLeast(0))
        if (index == currentMediaItemIndex) seekTo(clamped) else seekTo(index, clamped)
    }

    fun chapterIndexAt(itemIndex: Int): Int =
        getMediaItemAt(itemIndex).mediaMetadata.extras?.getInt(PlaybackContract.ITEM_CHAPTER_INDEX) ?: 0

    private fun chapterStartIndex(itemIndex: Int): Int {
        val chapter = chapterIndexAt(itemIndex)
        var start = itemIndex
        while (start > 0 && chapterIndexAt(start - 1) == chapter) start--
        return start
    }

    private fun nextChapterStart(): Int? {
        val current = currentMediaItemIndex
        if (current < 0) return null
        val chapter = chapterIndexAt(current)
        return (current + 1 until mediaItemCount).firstOrNull { chapterIndexAt(it) != chapter }
    }

    private fun previousChapterStart(chapterStart: Int): Int? =
        if (chapterStart <= 0) null else chapterStartIndex(chapterStart - 1)

    private fun durationBetween(fromIndex: Int, untilIndex: Int): Long =
        (fromIndex until untilIndex).sumOf { itemDuration(it) }

    fun itemDuration(index: Int): Long = getMediaItemAt(index).durationMs()

    companion object {
        /** Si llevas más de esto en el capítulo, "Anterior" vuelve a su inicio. */
        const val RESTART_CHAPTER_THRESHOLD_MS = 3_000L
    }
}

internal fun MediaItem.durationMs(): Long = mediaMetadata.durationMs ?: 0L

internal fun MediaItem.chapterId(): Long? =
    mediaMetadata.extras?.getLong(PlaybackContract.ITEM_CHAPTER_ID, -1L)?.takeIf { it >= 0 }
