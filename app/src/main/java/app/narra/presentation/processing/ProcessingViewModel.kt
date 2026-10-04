package app.narra.presentation.processing

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.narra.domain.model.Book
import app.narra.domain.model.BookState
import app.narra.domain.model.Chapter
import app.narra.domain.model.JobState
import app.narra.domain.model.ProcessingJob
import app.narra.domain.repository.BookRepository
import app.narra.domain.repository.ProcessingQueue
import app.narra.playback.PlaybackController
import app.narra.presentation.common.summarizeProcessing
import app.narra.presentation.components.ProcessingSummary
import app.narra.presentation.navigation.ProcessingRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Un libro de la cola de generación, con su título para mostrarlo. */
data class QueueEntry(val job: ProcessingJob, val book: Book)

data class ProcessingUiState(
    val loading: Boolean = true,
    val book: Book? = null,
    val chapters: List<Chapter> = emptyList(),
    val job: ProcessingJob? = null,
    val summary: ProcessingSummary? = null,
    val queue: List<QueueEntry> = emptyList(),
) {
    val includedChapters: List<Chapter> get() = chapters.filter { it.included }

    val isPaused: Boolean get() = job?.state == JobState.PAUSED

    val isActive: Boolean get() = job?.state == JobState.QUEUED || job?.state == JobState.RUNNING

    /** Libros que se procesarán antes que este. */
    val booksAhead: Int
        get() {
            if (!isActive) return 0
            val active = queue.filter { it.job.state == JobState.QUEUED || it.job.state == JobState.RUNNING }
            return active.indexOfFirst { it.book.id == book?.id }.coerceAtLeast(0)
        }
}

sealed interface ProcessingEvent {
    data class Message(val text: String) : ProcessingEvent
}

@HiltViewModel
class ProcessingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    books: BookRepository,
    private val queue: ProcessingQueue,
    private val playback: PlaybackController,
) : ViewModel() {
    val bookId: String = savedStateHandle.toRoute<ProcessingRoute>().bookId

    private val _events = Channel<ProcessingEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val state: StateFlow<ProcessingUiState> = combine(
        books.observeBook(bookId),
        books.observeChapters(bookId),
        books.observeJob(bookId),
        queue.observeQueue(),
        books.observeBooks(),
    ) { book, chapters, job, jobs, library ->
        val byId = library.associateBy { it.id }
        ProcessingUiState(
            loading = false,
            book = book,
            chapters = chapters,
            job = job,
            summary = book?.let { summarizeProcessing(it, chapters, job) },
            queue = jobs.mapNotNull { queued -> byId[queued.bookId]?.let { QueueEntry(queued, it) } },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ProcessingUiState())

    fun togglePause() = launch { if (state.value.isPaused) queue.resume(bookId) else queue.pause(bookId) }

    fun cancel() = launch {
        queue.cancelGeneration(bookId)
        _events.send(ProcessingEvent.Message("Creación detenida. Lo ya generado se conserva."))
    }

    fun retry() = launch { queue.retry(bookId) }

    fun moveToFront() = launch {
        queue.moveToFront(bookId)
        _events.send(ProcessingEvent.Message("Este libro será el siguiente."))
    }

    fun prioritize(chapter: Chapter) = launch {
        queue.prioritizeChapter(bookId, chapter.id)
        if (state.value.isPaused) queue.resume(bookId)
        _events.send(ProcessingEvent.Message("«${chapter.title}» se generará a continuación."))
    }

    /** Empieza a escuchar lo que ya esté listo mientras se genera el resto. */
    fun listen() {
        val book = state.value.book ?: return
        if (book.state == BookState.COMPLETED || book.hasPlayableAudio) playback.playBook(book.id)
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
