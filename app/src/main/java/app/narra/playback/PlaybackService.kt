package app.narra.playback

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.core.os.bundleOf
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import app.narra.MainActivity
import app.narra.R
import app.narra.di.ApplicationScope
import app.narra.domain.model.AppSettings
import app.narra.domain.model.Book
import app.narra.domain.model.BookState
import app.narra.domain.model.ChapterEndBehavior
import app.narra.domain.model.ListeningProgress
import app.narra.domain.model.PlayableTrack
import app.narra.domain.repository.BookRepository
import app.narra.domain.repository.PlaybackRepository
import app.narra.domain.repository.SettingsRepository
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * Reproduce los segmentos ya generados de un libro con ExoPlayer. Se mantiene con la pantalla
 * apagada, se integra con la notificación multimedia, la pantalla de bloqueo y los auriculares,
 * y guarda el progreso por sí mismo. Mientras el libro se sigue generando, los segmentos nuevos
 * se añaden a la cola sin cortar lo que suena.
 */
@UnstableApi
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var bookRepository: BookRepository
    @Inject lateinit var playbackRepository: PlaybackRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    private val scope = MainScope()
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var player: NarraPlayer
    private var session: MediaSession? = null
    private lateinit var settings: StateFlow<AppSettings>

    private var book: Book? = null
    private var tracksJob: Job? = null
    private var bookJob: Job? = null
    private var waitingForAudio = false
    private var sleepTimer: SleepTimer? = null

    override fun onCreate() {
        super.onCreate()
        settings = settingsRepository.settings.stateIn(scope, SharingStarted.Eagerly, AppSettings())

        exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player = NarraPlayer(
            exoPlayer,
            skipBackMs = { settings.value.skipBackSeconds * MS_PER_SECOND },
            skipForwardMs = { settings.value.skipForwardSeconds * MS_PER_SECOND },
        )
        exoPlayer.addListener(PlayerEvents())
        sleepTimer = SleepTimer(scope, exoPlayer) { publishExtras() }

        session = MediaSession.Builder(this, player)
            .setSessionActivity(openAppIntent())
            .setCallback(SessionCallback())
            .setMediaButtonPreferences(mediaButtons(settings.value))
            .build()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(PLAYBACK_CHANNEL_ID)
                .setChannelName(R.string.playback_channel_name)
                .build()
                .apply { setSmallIcon(R.drawable.ic_notification) },
        )

        // Los botones de salto reflejan los segundos elegidos en Ajustes.
        scope.launch {
            settings.collect { session?.setMediaButtonPreferences(mediaButtons(it)) }
        }
        // Guardado periódico mientras suena, por si el sistema mata el proceso.
        scope.launch {
            while (isActive) {
                delay(PROGRESS_SAVE_INTERVAL_MS)
                if (exoPlayer.isPlaying) saveProgress()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!exoPlayer.playWhenReady || exoPlayer.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        saveProgress()
        sleepTimer?.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        scope.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ Carga de libros

    private suspend fun loadBook(bookId: String, chapterId: Long?, play: Boolean) {
        val current = book
        if (current?.id == bookId && chapterId == null && exoPlayer.mediaItemCount > 0) {
            if (play) exoPlayer.play()
            return
        }
        if (current != null) saveProgress()

        val loaded = bookRepository.getBook(bookId) ?: return
        book = loaded
        waitingForAudio = false
        val tracks = playbackRepository.playableTracks(bookId)
        val progress = playbackRepository.getProgress(bookId)

        val items = tracks.map { it.toMediaItem(loaded) }
        val startIndex = when {
            chapterId != null -> tracks.indexOfFirst { it.chapterId == chapterId }.coerceAtLeast(0)
            progress != null && !progress.completed -> tracks.indexOfFirst { it.segmentId == progress.segmentId }.coerceAtLeast(0)
            else -> 0
        }
        val startPosition = if (chapterId == null && progress != null && !progress.completed &&
            tracks.getOrNull(startIndex)?.segmentId == progress.segmentId
        ) {
            progress.positionMs
        } else {
            0L
        }

        exoPlayer.setMediaItems(items, startIndex, startPosition)
        exoPlayer.setPlaybackSpeed(settings.value.defaultSpeed)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = play && items.isNotEmpty()
        waitingForAudio = items.isEmpty()
        if (waitingForAudio && play) exoPlayer.playWhenReady = true
        publishExtras()
        watchBook(bookId)
    }

    private suspend fun playChapter(chapterId: Long) {
        val index = (0 until exoPlayer.mediaItemCount).firstOrNull {
            exoPlayer.getMediaItemAt(it).chapterId() == chapterId
        } ?: return
        exoPlayer.seekTo(index, 0)
        exoPlayer.play()
    }

    /** Sigue los cambios del libro: segmentos nuevos, metadatos y borrado. */
    private fun watchBook(bookId: String) {
        tracksJob?.cancel()
        bookJob?.cancel()
        tracksJob = scope.launch {
            playbackRepository.observePlayableTracks(bookId).collect { tracks -> syncPlaylist(bookId, tracks) }
        }
        bookJob = scope.launch {
            bookRepository.observeBook(bookId).collect { updated ->
                if (updated == null) {
                    // El libro se eliminó mientras sonaba.
                    exoPlayer.stop()
                    exoPlayer.clearMediaItems()
                    book = null
                    publishExtras()
                } else {
                    book = updated
                }
            }
        }
    }

    /** Inserta o quita segmentos en su lugar sin interrumpir el que suena. */
    private fun syncPlaylist(bookId: String, tracks: List<PlayableTrack>) {
        val loaded = book ?: return
        if (loaded.id != bookId) return
        val desired = tracks.map { it.segmentId.toString() }
        val desiredSet = desired.toSet()

        for (i in exoPlayer.mediaItemCount - 1 downTo 0) {
            if (exoPlayer.getMediaItemAt(i).mediaId !in desiredSet) exoPlayer.removeMediaItem(i)
        }
        val wasEndedAt = if (exoPlayer.playbackState == Player.STATE_ENDED) exoPlayer.currentMediaItemIndex else -1
        val countBefore = exoPlayer.mediaItemCount
        tracks.forEachIndexed { index, track ->
            val existing = if (index < exoPlayer.mediaItemCount) exoPlayer.getMediaItemAt(index).mediaId else null
            if (existing != desired[index]) exoPlayer.addMediaItem(index, track.toMediaItem(loaded))
        }

        val added = exoPlayer.mediaItemCount > countBefore
        if (added && waitingForAudio && exoPlayer.playWhenReady) {
            // Se había alcanzado el final de lo generado: continuar con lo nuevo.
            waitingForAudio = false
            val next = if (wasEndedAt >= 0) (wasEndedAt + 1).coerceAtMost(exoPlayer.mediaItemCount - 1) else 0
            exoPlayer.seekTo(next, 0)
            if (exoPlayer.playbackState == Player.STATE_IDLE) exoPlayer.prepare()
            publishExtras()
        }
    }

    // ------------------------------------------------------------------ Progreso

    private fun currentProgress(): ListeningProgress? {
        val loaded = book ?: return null
        if (exoPlayer.mediaItemCount == 0) return null
        val index = exoPlayer.currentMediaItemIndex.coerceAtLeast(0)
        val item = exoPlayer.getMediaItemAt(index)
        val position = exoPlayer.currentPosition.coerceAtLeast(0)
        val before = (0 until index).sumOf { exoPlayer.getMediaItemAt(it).durationMs() }
        val atEnd = exoPlayer.playbackState == Player.STATE_ENDED && index == exoPlayer.mediaItemCount - 1
        return ListeningProgress(
            bookId = loaded.id,
            segmentId = item.mediaId.toLongOrNull(),
            chapterId = item.chapterId(),
            chapterTitle = item.mediaMetadata.title?.toString(),
            positionMs = position,
            listenedMs = before + position,
            completed = atEnd && loaded.state == BookState.COMPLETED,
            updatedAt = System.currentTimeMillis(),
        )
    }

    private fun saveProgress() {
        val progress = currentProgress() ?: return
        appScope.launch { playbackRepository.saveProgress(progress) }
    }

    // ------------------------------------------------------------------ Eventos

    private inner class PlayerEvents : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) saveProgress()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            saveProgress()
            if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) return
            val index = exoPlayer.currentMediaItemIndex
            if (index <= 0) return
            val chapterChanged = player.chapterIndexAt(index) != player.chapterIndexAt(index - 1)
            if (!chapterChanged) return
            val timer = sleepTimer
            if (timer != null && timer.endOfChapter) {
                exoPlayer.pause()
                timer.cancel()
            } else if (settings.value.chapterEndBehavior == ChapterEndBehavior.PAUSE) {
                exoPlayer.pause()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                val loaded = book
                waitingForAudio = loaded != null && loaded.state != BookState.COMPLETED
                saveProgress()
                publishExtras()
            }
        }
    }

    private fun publishExtras() {
        val timer = sleepTimer
        session?.setSessionExtras(
            bundleOf(
                PlaybackContract.EXTRA_BOOK_ID to book?.id,
                PlaybackContract.EXTRA_WAITING_FOR_AUDIO to waitingForAudio,
                PlaybackContract.EXTRA_SLEEP_END_ELAPSED to (timer?.endElapsedRealtime ?: 0L),
                PlaybackContract.EXTRA_SLEEP_END_OF_CHAPTER to (timer?.endOfChapter == true),
            ),
        )
    }

    // ------------------------------------------------------------------ Sesión

    private inner class SessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val builder = MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            if (controller.packageName == packageName) {
                val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                PlaybackContract.all.forEach { commands.add(it) }
                builder.setAvailableSessionCommands(commands.build())
            }
            return builder.build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> = scope.future {
            when (customCommand.customAction) {
                PlaybackContract.CMD_LOAD_BOOK -> {
                    val bookId = args.getString(PlaybackContract.ARG_BOOK_ID)
                    if (bookId != null) {
                        val chapterId = args.getLong(PlaybackContract.ARG_CHAPTER_ID, -1L).takeIf { it >= 0 }
                        loadBook(bookId, chapterId, args.getBoolean(PlaybackContract.ARG_PLAY, true))
                    }
                }
                PlaybackContract.CMD_PLAY_CHAPTER -> {
                    val chapterId = args.getLong(PlaybackContract.ARG_CHAPTER_ID, -1L)
                    if (chapterId >= 0) playChapter(chapterId)
                }
                PlaybackContract.CMD_SLEEP_TIMER -> {
                    sleepTimer?.set(args.getInt(PlaybackContract.ARG_SLEEP_MINUTES, PlaybackContract.SLEEP_OFF))
                    publishExtras()
                }
            }
            SessionResult(SessionResult.RESULT_SUCCESS)
        }

        // Reanudar desde la pantalla de bloqueo o los auriculares tras reiniciar el teléfono.
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
            val bookId = playbackRepository.observeLastPlayedBookId().first()
                ?: throw IllegalStateException("No hay nada que reanudar")
            val loaded = bookRepository.getBook(bookId) ?: throw IllegalStateException("Libro no encontrado")
            book = loaded
            val tracks = playbackRepository.playableTracks(bookId)
            val progress = playbackRepository.getProgress(bookId)
            val index = tracks.indexOfFirst { it.segmentId == progress?.segmentId }.coerceAtLeast(0)
            watchBook(bookId)
            MediaSession.MediaItemsWithStartPosition(
                tracks.map { it.toMediaItem(loaded) },
                index,
                progress?.positionMs ?: 0L,
            )
        }
    }

    private fun PlayableTrack.toMediaItem(book: Book): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(chapterTitle)
            .setArtist(book.author ?: book.title)
            .setAlbumTitle(book.title)
            .setAlbumArtist(book.author)
            .setDurationMs(durationMs)
            .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK_CHAPTER)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setExtras(
                bundleOf(
                    PlaybackContract.ITEM_CHAPTER_ID to chapterId,
                    PlaybackContract.ITEM_CHAPTER_INDEX to chapterIndex,
                ),
            )
            .apply { book.coverPath?.let { setArtworkUri(Uri.fromFile(File(it))) } }
            .build()
        return MediaItem.Builder()
            .setMediaId(segmentId.toString())
            .setUri(Uri.fromFile(File(path)))
            .setMediaMetadata(metadata)
            .build()
    }

    private fun mediaButtons(settings: AppSettings): List<CommandButton> = listOf(
        CommandButton.Builder(skipBackIcon(settings.skipBackSeconds))
            .setPlayerCommand(Player.COMMAND_SEEK_BACK)
            .setDisplayName("Retroceder ${settings.skipBackSeconds} segundos")
            .setSlots(CommandButton.SLOT_BACK_SECONDARY)
            .build(),
        CommandButton.Builder(skipForwardIcon(settings.skipForwardSeconds))
            .setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
            .setDisplayName("Avanzar ${settings.skipForwardSeconds} segundos")
            .setSlots(CommandButton.SLOT_FORWARD_SECONDARY)
            .build(),
    )

    private fun skipBackIcon(seconds: Int) = when (seconds) {
        5 -> CommandButton.ICON_SKIP_BACK_5
        10 -> CommandButton.ICON_SKIP_BACK_10
        15 -> CommandButton.ICON_SKIP_BACK_15
        30 -> CommandButton.ICON_SKIP_BACK_30
        else -> CommandButton.ICON_SKIP_BACK
    }

    private fun skipForwardIcon(seconds: Int) = when (seconds) {
        5 -> CommandButton.ICON_SKIP_FORWARD_5
        10 -> CommandButton.ICON_SKIP_FORWARD_10
        15 -> CommandButton.ICON_SKIP_FORWARD_15
        30 -> CommandButton.ICON_SKIP_FORWARD_30
        else -> CommandButton.ICON_SKIP_FORWARD
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_PLAYER)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        private const val MS_PER_SECOND = 1_000L
        private const val PROGRESS_SAVE_INTERVAL_MS = 10_000L

        /** Canal propio, con nombre en español; sustituye al genérico de Media3. */
        const val PLAYBACK_CHANNEL_ID = "playback"
    }
}

/**
 * Temporizador de apagado: baja el volumen en los últimos segundos y pausa.
 * "Al terminar el capítulo" lo resuelve [PlaybackService] al cambiar de capítulo.
 */
@UnstableApi
internal class SleepTimer(
    private val scope: CoroutineScope,
    private val player: ExoPlayer,
    private val onChange: () -> Unit,
) {
    var endElapsedRealtime: Long? = null
        private set
    var endOfChapter: Boolean = false
        private set
    private var job: Job? = null
    private var volumeBeforeFade = 1f

    fun set(minutes: Int) {
        cancel()
        when {
            minutes == PlaybackContract.SLEEP_END_OF_CHAPTER -> endOfChapter = true
            minutes > 0 -> start(minutes * MS_PER_MINUTE)
        }
        onChange()
    }

    fun cancel() {
        job?.cancel()
        job = null
        if (endElapsedRealtime != null) player.volume = volumeBeforeFade
        endElapsedRealtime = null
        endOfChapter = false
        onChange()
    }

    private fun start(durationMs: Long) {
        val end = SystemClock.elapsedRealtime() + durationMs
        endElapsedRealtime = end
        volumeBeforeFade = player.volume
        job = scope.launch {
            while (isActive) {
                val remaining = end - SystemClock.elapsedRealtime()
                if (remaining <= 0) break
                if (remaining < FADE_OUT_MS) {
                    player.volume = volumeBeforeFade * (remaining.toFloat() / FADE_OUT_MS)
                }
                delay(if (remaining < FADE_OUT_MS) FADE_STEP_MS else remaining.coerceAtMost(TICK_MS))
            }
            player.pause()
            player.volume = volumeBeforeFade
            endElapsedRealtime = null
            job = null
            onChange()
        }
    }

    private companion object {
        const val MS_PER_MINUTE = 60_000L
        const val FADE_OUT_MS = 10_000L
        const val FADE_STEP_MS = 200L
        const val TICK_MS = 1_000L
    }
}
