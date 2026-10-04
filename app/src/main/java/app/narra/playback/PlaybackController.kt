package app.narra.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SleepTimerState {
    data object Off : SleepTimerState
    data object EndOfChapter : SleepTimerState
    data class Running(val endElapsedRealtime: Long) : SleepTimerState {
        val remainingMs: Long get() = (endElapsedRealtime - SystemClock.elapsedRealtime()).coerceAtLeast(0)
    }
}

/** Estado de reproducción que necesita la interfaz (no incluye la posición, que cambia continuamente). */
data class PlaybackSnapshot(
    val connected: Boolean = false,
    val bookId: String? = null,
    val segmentId: Long? = null,
    val chapterId: Long? = null,
    val chapterTitle: String? = null,
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = false,
    val isBuffering: Boolean = false,
    val waitingForAudio: Boolean = false,
    val speed: Float = 1f,
    val volume: Float = 1f,
    val sleepTimer: SleepTimerState = SleepTimerState.Off,
    val errorMessage: String? = null,
) {
    val isActive: Boolean get() = bookId != null
}

data class PlaybackPosition(val segmentId: Long?, val positionMs: Long, val segmentDurationMs: Long)

/**
 * Puente entre la interfaz y [PlaybackService]: mantiene un [MediaController] conectado mientras
 * la app está visible y expone su estado como flujos.
 */
@Singleton
class PlaybackController @Inject constructor(@ApplicationContext private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private val _snapshot = MutableStateFlow(PlaybackSnapshot())
    val snapshot: StateFlow<PlaybackSnapshot> = _snapshot.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()
    }

    private val sessionListener = object : MediaController.Listener {
        override fun onExtrasChanged(controller: MediaController, extras: Bundle) = refresh()
    }

    /** Llamar en `onStart` de la actividad. Es idempotente. */
    @OptIn(UnstableApi::class)
    fun connect() {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val pending = MediaController.Builder(context, token).setListener(sessionListener).buildAsync()
        future = pending
        scope.launch {
            val connected = runCatching { pending.await() }.getOrNull() ?: run {
                future = null
                return@launch
            }
            controller = connected
            connected.addListener(listener)
            refresh()
        }
    }

    /** Llamar en `onStop`: la reproducción sigue en el servicio. */
    fun disconnect() {
        controller?.removeListener(listener)
        future?.let(MediaController::releaseFuture)
        future = null
        controller = null
        _snapshot.value = _snapshot.value.copy(connected = false)
    }

    private suspend fun awaitController(): MediaController? {
        controller?.let { return it }
        connect()
        return runCatching { future?.await() }.getOrNull()
    }

    private fun refresh() {
        val c = controller ?: return
        val extras = c.sessionExtras
        val item: MediaItem? = c.currentMediaItem
        val sleepEnd = extras.getLong(PlaybackContract.EXTRA_SLEEP_END_ELAPSED, 0L)
        _snapshot.value = PlaybackSnapshot(
            connected = true,
            bookId = extras.getString(PlaybackContract.EXTRA_BOOK_ID),
            segmentId = item?.mediaId?.toLongOrNull(),
            chapterId = item?.chapterId(),
            chapterTitle = item?.mediaMetadata?.title?.toString(),
            isPlaying = c.isPlaying,
            playWhenReady = c.playWhenReady,
            isBuffering = c.playbackState == Player.STATE_BUFFERING,
            waitingForAudio = extras.getBoolean(PlaybackContract.EXTRA_WAITING_FOR_AUDIO, false),
            speed = c.playbackParameters.speed,
            volume = c.volume,
            sleepTimer = when {
                extras.getBoolean(PlaybackContract.EXTRA_SLEEP_END_OF_CHAPTER, false) -> SleepTimerState.EndOfChapter
                sleepEnd > 0 -> SleepTimerState.Running(sleepEnd)
                else -> SleepTimerState.Off
            },
            errorMessage = c.playerError?.let { "No se pudo reproducir este fragmento." },
        )
    }

    /** Posición actual, emitida con frecuencia mientras alguien la observa. */
    fun positionUpdates(intervalMs: Long = POSITION_INTERVAL_MS): Flow<PlaybackPosition> = flow {
        while (true) {
            val c = controller
            if (c != null && c.mediaItemCount > 0) {
                emit(
                    PlaybackPosition(
                        segmentId = c.currentMediaItem?.mediaId?.toLongOrNull(),
                        positionMs = c.currentPosition.coerceAtLeast(0),
                        segmentDurationMs = c.currentMediaItem?.durationMs() ?: 0L,
                    ),
                )
            }
            delay(intervalMs)
        }
    }.distinctUntilChanged().flowOn(Dispatchers.Main)

    // ------------------------------------------------------------------ Acciones

    fun playBook(bookId: String, chapterId: Long? = null, play: Boolean = true) = command { c ->
        c.sendCustomCommand(
            PlaybackContract.loadBook,
            bundleOf(
                PlaybackContract.ARG_BOOK_ID to bookId,
                PlaybackContract.ARG_CHAPTER_ID to (chapterId ?: -1L),
                PlaybackContract.ARG_PLAY to play,
            ),
        )
    }

    fun playChapter(bookId: String, chapterId: Long) {
        if (_snapshot.value.bookId == bookId) {
            command { it.sendCustomCommand(PlaybackContract.playChapter, bundleOf(PlaybackContract.ARG_CHAPTER_ID to chapterId)) }
        } else {
            playBook(bookId, chapterId)
        }
    }

    fun togglePlayPause() = command { c ->
        when {
            c.playbackState == Player.STATE_ENDED && !_snapshot.value.waitingForAudio -> {
                c.seekToDefaultPosition(0)
                c.play()
            }
            c.playWhenReady -> c.pause()
            else -> {
                if (c.playbackState == Player.STATE_IDLE) c.prepare()
                c.play()
            }
        }
    }

    fun pause() = command { it.pause() }

    fun seekBack() = command { it.seekBack() }

    fun seekForward() = command { it.seekForward() }

    fun nextChapter() = command { it.seekToNext() }

    fun previousChapter() = command { it.seekToPrevious() }

    /** Salta a un punto concreto de un segmento ya generado. */
    fun seekToSegment(segmentId: Long, positionMs: Long) = command { c ->
        val index = (0 until c.mediaItemCount).firstOrNull { c.getMediaItemAt(it).mediaId == segmentId.toString() }
        if (index != null) c.seekTo(index, positionMs.coerceAtLeast(0))
    }

    fun setSpeed(speed: Float) = command { it.setPlaybackSpeed(speed) }

    fun setVolume(volume: Float) = command { it.volume = volume.coerceIn(0f, 1f) }

    fun setSleepTimer(minutes: Int) = command { c ->
        c.sendCustomCommand(PlaybackContract.sleepTimer, bundleOf(PlaybackContract.ARG_SLEEP_MINUTES to minutes))
    }

    private fun command(block: (MediaController) -> Unit) {
        scope.launch {
            val c = awaitController() ?: return@launch
            block(c)
            refresh()
        }
    }

    private companion object {
        const val POSITION_INTERVAL_MS = 250L
    }
}
