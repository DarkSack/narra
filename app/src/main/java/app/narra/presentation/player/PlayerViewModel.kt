package app.narra.presentation.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.narra.domain.model.AppSettings
import app.narra.domain.model.Book
import app.narra.domain.model.BookState
import app.narra.domain.model.Chapter
import app.narra.domain.model.PlayableTrack
import app.narra.domain.repository.BookRepository
import app.narra.domain.repository.PlaybackRepository
import app.narra.domain.repository.ProcessingQueue
import app.narra.domain.repository.SettingsRepository
import app.narra.playback.PlaybackController
import app.narra.playback.PlaybackPosition
import app.narra.playback.PlaybackSnapshot
import app.narra.presentation.common.PlaybackTimeline
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Lo que cambia poco: libro, capítulos y pistas disponibles. */
private data class BookContext(val book: Book?, val chapters: List<Chapter>, val tracks: List<PlayableTrack>)

data class PlayerUiState(
    val loading: Boolean = true,
    val book: Book? = null,
    val chapters: List<Chapter> = emptyList(),
    val snapshot: PlaybackSnapshot = PlaybackSnapshot(),
    val timeline: PlaybackTimeline? = null,
    val skipBackSeconds: Int = AppSettings().skipBackSeconds,
    val skipForwardSeconds: Int = AppSettings().skipForwardSeconds,
    /** Pistas disponibles, para traducir una posición del capítulo a un segmento. */
    val tracks: List<PlayableTrack> = emptyList(),
) {
    val includedChapters: List<Chapter> get() = chapters.filter { it.included }
    val hasPrevious: Boolean get() = timeline != null
    val hasNext: Boolean
        get() {
            val current = timeline?.chapterId ?: return false
            val index = includedChapters.indexOfFirst { it.id == current }
            return index >= 0 && includedChapters.drop(index + 1).any { it.isPlayable }
        }
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val books: BookRepository,
    private val playbackRepository: PlaybackRepository,
    private val settings: SettingsRepository,
    private val playback: PlaybackController,
    private val queue: ProcessingQueue,
) : ViewModel() {

    private val context: Flow<BookContext> = playback.snapshot
        .map { it.bookId }
        .distinctUntilChanged()
        .flatMapLatest { bookId ->
            if (bookId == null) {
                flowOf(BookContext(null, emptyList(), emptyList()))
            } else {
                combine(
                    books.observeBook(bookId),
                    books.observeChapters(bookId),
                    playbackRepository.observePlayableTracks(bookId),
                ) { book, chapters, tracks -> BookContext(book, chapters, tracks) }
            }
        }

    val state: StateFlow<PlayerUiState> = combine(
        context,
        playback.snapshot,
        playback.positionUpdates(POSITION_TICK_MS).onStart { emit(PlaybackPosition(null, 0, 0)) },
        settings.settings,
    ) { ctx, snapshot, position, prefs ->
        val segmentId = position.segmentId ?: snapshot.segmentId
        PlayerUiState(
            loading = !snapshot.connected,
            book = ctx.book,
            chapters = ctx.chapters,
            snapshot = snapshot,
            timeline = PlaybackTimeline.compute(ctx.tracks, ctx.chapters, segmentId, position.positionMs),
            skipBackSeconds = prefs.skipBackSeconds,
            skipForwardSeconds = prefs.skipForwardSeconds,
            tracks = ctx.tracks,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PlayerUiState())

    fun togglePlayPause() = playback.togglePlayPause()
    fun seekBack() = playback.seekBack()
    fun seekForward() = playback.seekForward()
    fun nextChapter() = playback.nextChapter()
    fun previousChapter() = playback.previousChapter()
    fun setSpeed(speed: Float) = playback.setSpeed(speed)
    fun setVolume(volume: Float) = playback.setVolume(volume)
    fun setSleepTimer(minutes: Int) = playback.setSleepTimer(minutes)

    fun playChapter(chapter: Chapter) {
        val bookId = state.value.book?.id ?: return
        playback.playChapter(bookId, chapter.id)
    }

    /** Un capítulo que aún no tiene audio pasa al principio de la cola de creación. */
    fun prioritize(chapter: Chapter) {
        val book = state.value.book ?: return
        if (book.state != BookState.GENERATING && book.state != BookState.PAUSED) return
        viewModelScope.launch {
            queue.prioritizeChapter(book.id, chapter.id)
            if (book.state == BookState.PAUSED) queue.resume(book.id)
        }
    }

    /** Salta a un punto del capítulo actual; no permite ir más allá de lo ya generado. */
    fun seekInChapter(fraction: Float) {
        val timeline = state.value.timeline ?: return
        val chapterId = timeline.chapterId ?: return
        val target = (timeline.chapterDurationMs * fraction).toLong().coerceAtMost(timeline.chapterAvailableMs)
        val (segmentId, positionMs) = PlaybackTimeline.locate(state.value.tracks, chapterId, target) ?: return
        playback.seekToSegment(segmentId, positionMs)
    }

    private companion object {
        const val POSITION_TICK_MS = 500L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
