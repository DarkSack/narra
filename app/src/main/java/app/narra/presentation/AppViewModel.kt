package app.narra.presentation

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.narra.core.ui.ErrorText
import app.narra.core.ui.toErrorText
import app.narra.domain.model.AppSettings
import app.narra.domain.repository.BookRepository
import app.narra.domain.repository.ImportRepository
import app.narra.domain.repository.ImportResult
import app.narra.domain.repository.PlaybackRepository
import app.narra.domain.repository.SettingsRepository
import app.narra.playback.PlaybackController
import app.narra.playback.PlaybackPosition
import app.narra.presentation.components.MiniPlayerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AppEvent {
    /** Libro recién importado: se abre su revisión. */
    data class ReviewImport(val bookId: String) : AppEvent

    /** Abrir la ficha de un libro (por ejemplo, desde una notificación). */
    data class ShowBook(val bookId: String) : AppEvent
    data class Duplicate(val uri: Uri, val bookId: String, val title: String) : AppEvent
    data class ImportFailed(val error: ErrorText) : AppEvent
    data object OpenPlayer : AppEvent
}

/** Estado global de la interfaz: ajustes (tema, estilo) y el mini reproductor. */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val bookRepository: BookRepository,
    private val playbackRepository: PlaybackRepository,
    private val playback: PlaybackController,
    private val importRepository: ImportRepository,
) : ViewModel() {

    /** `null` hasta leer los ajustes: evita mostrar un tema equivocado durante un instante. */
    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val miniPlayer: StateFlow<MiniPlayerState?> = playback.snapshot
        .map { it.bookId }
        .distinctUntilChanged()
        .flatMapLatest { bookId ->
            if (bookId == null) {
                flowOf(null)
            } else {
                combine(
                    bookRepository.observeBook(bookId),
                    playbackRepository.observePlayableTracks(bookId),
                    playback.snapshot,
                    playback.positionUpdates(MINI_PLAYER_TICK_MS).onStart { emit(PlaybackPosition(null, 0, 0)) },
                ) { book, tracks, snapshot, position ->
                    if (book == null) return@combine null
                    val index = tracks.indexOfFirst { it.segmentId == position.segmentId }
                    val listened = if (index >= 0) tracks.take(index).sumOf { it.durationMs } + position.positionMs else 0L
                    MiniPlayerState(
                        bookId = book.id,
                        title = book.title,
                        author = book.author,
                        coverPath = book.coverPath,
                        chapterTitle = snapshot.chapterTitle,
                        progress = if (book.durationMs > 0) listened.toFloat() / book.durationMs else 0f,
                        isPlaying = snapshot.playWhenReady,
                        isBuffering = snapshot.isBuffering,
                        waitingForAudio = snapshot.waitingForAudio,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val _events = Channel<AppEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** Importación en curso: la interfaz muestra un indicador y evita dobles toques. */
    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    fun importPdf(uri: Uri, allowDuplicate: Boolean = false) {
        if (_importing.value) return
        _importing.value = true
        viewModelScope.launch {
            val result = importRepository.importPdf(uri, allowDuplicate)
            _importing.value = false
            _events.send(
                when (result) {
                    is ImportResult.Imported -> AppEvent.ReviewImport(result.bookId)
                    is ImportResult.AlreadyInLibrary -> AppEvent.Duplicate(uri, result.bookId, result.title)
                    is ImportResult.Failed -> AppEvent.ImportFailed(result.kind.toErrorText())
                },
            )
        }
    }

    fun togglePlayPause() = playback.togglePlayPause()

    /** Desde la notificación o la pantalla de bloqueo. */
    fun showBook(bookId: String) {
        _events.trySend(AppEvent.ShowBook(bookId))
    }

    fun openPlayer() {
        _events.trySend(AppEvent.OpenPlayer)
    }

    fun completeOnboarding() {
        viewModelScope.launch { settingsRepository.update { it.copy(onboardingCompleted = true) } }
    }

    private companion object {
        const val MINI_PLAYER_TICK_MS = 1_000L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
