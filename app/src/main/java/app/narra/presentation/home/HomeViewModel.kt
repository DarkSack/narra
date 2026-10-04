package app.narra.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.narra.domain.model.Book
import app.narra.domain.model.BookState
import app.narra.domain.repository.BookRepository
import app.narra.domain.repository.ProcessingQueue
import app.narra.playback.PlaybackController
import app.narra.presentation.common.isListenable
import app.narra.presentation.common.summarizeProcessing
import app.narra.presentation.components.ProcessingSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class ContinueListening(val book: Book, val chapterTitle: String?)

data class HomeUiState(
    val loading: Boolean = true,
    val greeting: String = "",
    val headline: String = "",
    val continueListening: ContinueListening? = null,
    val processing: List<ProcessingSummary> = emptyList(),
    val toReview: List<Book> = emptyList(),
    val needsAttention: List<Book> = emptyList(),
    val recentlyListened: List<Book> = emptyList(),
    val recentlyAdded: List<Book> = emptyList(),
    val totalBooks: Int = 0,
) {
    val isEmpty: Boolean get() = !loading && totalBooks == 0
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val books: BookRepository,
    private val playback: PlaybackController,
    private val queue: ProcessingQueue,
) : ViewModel() {

    val state: StateFlow<HomeUiState> = books.observeBooks()
        .flatMapLatest { all -> processingSummaries(all).map { processing -> buildState(all, processing) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState())

    private fun processingSummaries(all: List<Book>): Flow<List<ProcessingSummary>> {
        val working = all.filter { it.state.isImporting || it.state.isWorking || it.state == BookState.PAUSED }
        if (working.isEmpty()) return flowOf(emptyList())
        val flows = working.map { book ->
            combine(books.observeChapters(book.id), books.observeJob(book.id)) { chapters, job ->
                summarizeProcessing(book, chapters, job)
            }
        }
        return combine(flows) { it.toList() }.distinctUntilChanged()
    }

    private fun buildState(all: List<Book>, processing: List<ProcessingSummary>): HomeUiState {
        val current = all
            .filter { it.lastPlayedAt != null && it.isListenable && !it.isCompleted }
            .maxByOrNull { it.lastPlayedAt ?: 0L }
            ?: all.filter { it.isListenable && !it.isStarted }.maxByOrNull { it.importedAt }
        val listened = all.filter { it.lastPlayedAt != null && it.id != current?.id }
            .sortedByDescending { it.lastPlayedAt }
            .take(SHELF_SIZE)
        val added = all.filter { it.id != current?.id }
            .sortedByDescending { it.importedAt }
            .take(SHELF_SIZE)
        return HomeUiState(
            loading = false,
            greeting = greetingForNow(),
            headline = headline(current != null, processing.isNotEmpty(), all.isEmpty()),
            continueListening = current?.let { ContinueListening(it, it.progress?.chapterTitle) },
            processing = processing,
            toReview = all.filter { it.state == BookState.READY },
            needsAttention = all.filter { it.state == BookState.ERROR },
            recentlyListened = listened,
            recentlyAdded = added,
            totalBooks = all.size,
        )
    }

    fun play(book: Book) = playback.playBook(book.id)

    fun togglePause(bookId: String, paused: Boolean) {
        viewModelScope.launch { if (paused) queue.resume(bookId) else queue.pause(bookId) }
    }

    private fun greetingForNow(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in MORNING_START until AFTERNOON_START -> "Buenos días"
        in AFTERNOON_START until NIGHT_START -> "Buenas tardes"
        else -> "Buenas noches"
    }

    /** Varía con el día para que la portada no repita siempre la misma frase. */
    private fun headline(hasCurrent: Boolean, hasProcessing: Boolean, empty: Boolean): String {
        val day = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        return when {
            empty -> "Tu biblioteca empieza aquí"
            hasCurrent -> CONTINUE_HEADLINES[day % CONTINUE_HEADLINES.size]
            hasProcessing -> "Tu próximo audiolibro está en camino"
            else -> "Tu biblioteca"
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val SHELF_SIZE = 12
        const val MORNING_START = 5
        const val AFTERNOON_START = 12
        const val NIGHT_START = 20
        val CONTINUE_HEADLINES = listOf("Continúa escuchando", "Retoma tu historia", "Donde lo dejaste")
    }
}
