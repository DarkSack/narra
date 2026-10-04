package app.narra.presentation.book

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.narra.core.ui.toErrorText
import app.narra.domain.model.Book
import app.narra.domain.model.BookState
import app.narra.domain.model.Chapter
import app.narra.domain.model.ChapterState
import app.narra.domain.model.ExportStatus
import app.narra.domain.model.ProcessingJob
import app.narra.domain.repository.AudiobookExports
import app.narra.domain.repository.BookRepository
import app.narra.domain.repository.PlaybackRepository
import app.narra.domain.repository.ProcessingQueue
import app.narra.domain.repository.SettingsRepository
import app.narra.playback.PlaybackController
import app.narra.presentation.common.PlaybackTimeline
import app.narra.presentation.common.summarizeProcessing
import app.narra.presentation.components.ProcessingSummary
import app.narra.presentation.navigation.BookRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BookDetailsUiState(
    val loading: Boolean = true,
    /** El libro dejó de existir (se borró desde otra pantalla). */
    val missing: Boolean = false,
    val book: Book? = null,
    val chapters: List<Chapter> = emptyList(),
    val processing: ProcessingSummary? = null,
    /** Capítulo donde se quedó la escucha y cuánto de él se ha escuchado. */
    val currentChapterId: Long? = null,
    val currentChapterFraction: Float = 0f,
    val isPlayingThisBook: Boolean = false,
) {
    val includedChapters: List<Chapter> get() = chapters.filter { it.included }

    /** Hay al menos un capítulo con todo su audio: se puede exportar. */
    val canExport: Boolean get() = includedChapters.any { it.state == ChapterState.AVAILABLE }

    /** El libro está en la cola de generación (en marcha o en pausa). */
    val isGenerating: Boolean get() = book?.state == BookState.GENERATING || book?.state == BookState.PAUSED

    /** Fracción escuchada de cada capítulo: los anteriores al actual cuentan como escuchados. */
    fun listenedFraction(chapter: Chapter): Float {
        val current = currentChapterId ?: return if (book?.isCompleted == true) 1f else 0f
        val currentIndex = includedChapters.indexOfFirst { it.id == current }
        val index = includedChapters.indexOfFirst { it.id == chapter.id }
        return when {
            book?.isCompleted == true -> 1f
            index < 0 || currentIndex < 0 -> 0f
            index < currentIndex -> 1f
            index == currentIndex -> currentChapterFraction
            else -> 0f
        }
    }
}

sealed interface BookDetailsEvent {
    data object Deleted : BookDetailsEvent
    data class Message(val text: String) : BookDetailsEvent
}

@HiltViewModel
class BookDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val books: BookRepository,
    private val playbackRepository: PlaybackRepository,
    private val playback: PlaybackController,
    private val queue: ProcessingQueue,
    private val exports: AudiobookExports,
    private val settings: SettingsRepository,
) : ViewModel() {

    val bookId: String = savedStateHandle.toRoute<BookRoute>().bookId

    private val _events = Channel<BookDetailsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val state: StateFlow<BookDetailsUiState> = combine(
        books.observeBook(bookId),
        books.observeChapters(bookId),
        books.observeJob(bookId),
        playbackRepository.observePlayableTracks(bookId),
        playback.snapshot,
    ) { book, chapters, job, tracks, snapshot ->
        if (book == null) return@combine BookDetailsUiState(loading = false, missing = true)
        val progress = book.progress
        val timeline = progress?.segmentId?.let { PlaybackTimeline.compute(tracks, chapters, it, progress.positionMs) }
        BookDetailsUiState(
            loading = false,
            book = book,
            chapters = chapters,
            processing = book.processingSummary(chapters, job),
            currentChapterId = if (snapshot.bookId == book.id) snapshot.chapterId ?: progress?.chapterId else progress?.chapterId,
            currentChapterFraction = timeline?.chapterFraction ?: 0f,
            isPlayingThisBook = snapshot.bookId == book.id && snapshot.playWhenReady,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), BookDetailsUiState())

    private val _export = MutableStateFlow<ExportStatus?>(null)

    /** Exportación en curso o terminada de este libro. */
    val export: StateFlow<ExportStatus?> = _export.asStateFlow()

    init {
        viewModelScope.launch {
            exports.observe(bookId).collect { status ->
                // Solo se avisa de lo que termina mientras se mira: no de exportaciones de otros días.
                if (_export.value is ExportStatus.Running) exportFinishedMessage(status)?.let { _events.send(BookDetailsEvent.Message(it)) }
                _export.value = status
            }
        }
    }

    private fun exportFinishedMessage(status: ExportStatus?): String? = when (status) {
        is ExportStatus.Done -> buildString {
            append(if (status.files == 1) "Se guardó 1 archivo" else "Se guardaron ${status.files} archivos")
            append(" en «${status.folderName}».")
            if (status.chaptersMissing > 0) {
                append(if (status.chaptersMissing == 1) " Falta 1 capítulo por crear." else " Faltan ${status.chaptersMissing} capítulos por crear.")
            }
        }
        is ExportStatus.Failed -> status.kind.toErrorText().let { "${it.title}. ${it.explanation}" }
        is ExportStatus.Running, null -> null
    }

    /** Copia el audio terminado a [folder], elegida con el selector del sistema. */
    fun export(folder: Uri) {
        exports.export(bookId, folder)
    }

    private fun Book.processingSummary(chapters: List<Chapter>, job: ProcessingJob?): ProcessingSummary? =
        if (state.isImporting || state.isWorking || state == BookState.PAUSED) summarizeProcessing(this, chapters, job) else null

    /** El libro se estaba creando sin audio todavía cuando se abrió (o se miró por última vez) la ficha. */
    private var waitingForFirstAudio: Boolean? = null

    /**
     * La pantalla avisa cuando cambia si hay audio que escuchar. Con «Escuchar en cuanto esté
     * listo», el primer capítulo empieza a sonar al terminarse, salvo que ya suene otra cosa.
     */
    fun onPlayableChanged(playable: Boolean) {
        val current = state.value
        val book = current.book ?: return
        val wasWaiting = waitingForFirstAudio
        if (!playable) {
            waitingForFirstAudio = current.isGenerating
            return
        }
        waitingForFirstAudio = false
        if (wasWaiting != true) return
        viewModelScope.launch {
            if (!settings.settings.first().autoPlayWhenReady || playback.snapshot.value.playWhenReady) return@launch
            playback.playBook(bookId)
            _events.send(BookDetailsEvent.Message("Ya puedes escuchar «${book.title}». Empezamos por el principio."))
        }
    }

    fun play() {
        val current = state.value
        if (current.isPlayingThisBook) playback.pause() else playback.playBook(bookId)
    }

    fun playChapter(chapter: Chapter) {
        val current = state.value
        when {
            current.isPlayingThisBook && current.currentChapterId == chapter.id -> playback.pause()
            chapter.isPlayable -> playback.playChapter(bookId, chapter.id)
            else -> prioritize(chapter)
        }
    }

    /** Un capítulo sin audio pasa al principio de la cola. */
    private fun prioritize(chapter: Chapter) {
        if (!state.value.isGenerating) return
        val paused = state.value.book?.state == BookState.PAUSED
        viewModelScope.launch {
            queue.prioritizeChapter(bookId, chapter.id)
            if (paused) queue.resume(bookId)
            _events.send(BookDetailsEvent.Message("«${chapter.title}» se generará a continuación."))
        }
    }

    fun togglePause() {
        val summary = state.value.processing ?: return
        viewModelScope.launch { if (summary.isPaused) queue.resume(bookId) else queue.pause(bookId) }
    }

    fun cancelGeneration() {
        viewModelScope.launch {
            queue.cancelGeneration(bookId)
            _events.send(BookDetailsEvent.Message("Creación detenida. Lo ya generado se conserva."))
        }
    }

    fun retry() {
        viewModelScope.launch { queue.retry(bookId) }
    }

    fun toggleFavorite() {
        val book = state.value.book ?: return
        viewModelScope.launch { books.setFavorite(book.id, !book.isFavorite) }
    }

    fun deleteAudio() {
        viewModelScope.launch {
            stopIfPlaying()
            books.deleteAudio(bookId)
            _events.send(BookDetailsEvent.Message("Audio eliminado. El libro y sus capítulos se conservan."))
        }
    }

    fun deleteBook() {
        viewModelScope.launch {
            stopIfPlaying()
            books.deleteBook(bookId)
            _events.send(BookDetailsEvent.Deleted)
        }
    }

    private fun stopIfPlaying() {
        if (playback.snapshot.value.bookId == bookId) playback.pause()
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
