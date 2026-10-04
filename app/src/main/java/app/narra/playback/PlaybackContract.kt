package app.narra.playback

import android.os.Bundle
import androidx.media3.session.SessionCommand

/**
 * Comandos propios entre la interfaz y [PlaybackService]. Solo los acepta la propia app;
 * otros controladores (Android Auto, Bluetooth, pantalla de bloqueo) usan los comandos estándar.
 */
internal object PlaybackContract {
    const val CMD_LOAD_BOOK = "app.narra.LOAD_BOOK"
    const val CMD_PLAY_CHAPTER = "app.narra.PLAY_CHAPTER"
    const val CMD_SLEEP_TIMER = "app.narra.SLEEP_TIMER"

    const val ARG_BOOK_ID = "bookId"
    const val ARG_CHAPTER_ID = "chapterId"
    const val ARG_PLAY = "play"
    const val ARG_SLEEP_MINUTES = "minutes"

    /** Valor de [ARG_SLEEP_MINUTES] para "al terminar el capítulo". */
    const val SLEEP_END_OF_CHAPTER = -1
    const val SLEEP_OFF = 0

    // Extras de sesión que el servicio publica para la interfaz.
    const val EXTRA_BOOK_ID = "extra.bookId"
    const val EXTRA_SLEEP_END_ELAPSED = "extra.sleepEndElapsed"
    const val EXTRA_SLEEP_END_OF_CHAPTER = "extra.sleepEndOfChapter"
    const val EXTRA_WAITING_FOR_AUDIO = "extra.waitingForAudio"

    // Extras de cada MediaItem.
    const val ITEM_CHAPTER_ID = "item.chapterId"
    const val ITEM_CHAPTER_INDEX = "item.chapterIndex"

    val loadBook = SessionCommand(CMD_LOAD_BOOK, Bundle.EMPTY)
    val playChapter = SessionCommand(CMD_PLAY_CHAPTER, Bundle.EMPTY)
    val sleepTimer = SessionCommand(CMD_SLEEP_TIMER, Bundle.EMPTY)
    val all = listOf(loadBook, playChapter, sleepTimer)
}
