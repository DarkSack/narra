package app.narra.presentation.importing

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.narra.domain.model.AnalysisReport
import app.narra.domain.model.Book
import app.narra.domain.model.BookMetadata
import app.narra.domain.model.BookState
import app.narra.domain.model.Chapter
import app.narra.domain.model.ErrorKind
import app.narra.domain.model.NarraException
import app.narra.domain.repository.BookRepository
import app.narra.domain.repository.ProcessingQueue
import app.narra.presentation.navigation.ImportRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ImportUiState(
    val loading: Boolean = true,
    val book: Book? = null,
    val chapters: List<Chapter> = emptyList(),
    val report: AnalysisReport? = null,
) {
    val isAnalyzing: Boolean get() = book?.state?.isImporting == true
    val isReady: Boolean get() = book != null && !isAnalyzing && book.state != BookState.ERROR
    val includedCount: Int get() = chapters.count { it.included }

    /** Aún no se ha empezado a crear el audio: la revisión termina con "Crear audiolibro". */
    val isFirstReview: Boolean get() = book?.state == BookState.READY && book.segmentsAvailable == 0
}

sealed interface ImportEvent {
    data object Deleted : ImportEvent
    data class Message(val text: String) : ImportEvent

    /** La creación del audio empezó (o continuó): se pasa a la ficha del libro. */
    data object Started : ImportEvent

    /** Edición terminada sin nada nuevo que generar. */
    data object Done : ImportEvent
}

@HiltViewModel
class ImportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val books: BookRepository,
    private val queue: ProcessingQueue,
) : ViewModel() {

    val bookId: String = savedStateHandle.toRoute<ImportRoute>().bookId

    private val _events = Channel<ImportEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** El informe solo cambia cuando termina un análisis. */
    private val report = books.observeBook(bookId)
        .map { it?.state }
        .distinctUntilChanged()
        .mapLatest { state -> if (state != null && !state.isImporting) books.getAnalysisReport(bookId) else null }

    val state: StateFlow<ImportUiState> = combine(
        books.observeBook(bookId),
        books.observeChapters(bookId),
        report,
    ) { book, chapters, report ->
        ImportUiState(loading = false, book = book, chapters = chapters, report = report)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ImportUiState())

    /** Repite el análisis; si lo que falló fue el reconocimiento de texto, lo repite también. */
    fun retry() = queue.analyze(bookId, restart = true, ocr = state.value.book?.error?.kind == ErrorKind.OCR_FAILED)

    /** Reconoce el texto de las páginas escaneadas ([pages], desde 0; null = todas) y vuelve a analizar el libro. */
    fun runOcr(pages: IntRange?) = launch {
        books.setOcrPages(bookId, pages)
        queue.analyze(bookId, restart = true, ocr = true)
    }

    fun rename(chapter: Chapter, title: String) = launch { books.renameChapter(chapter.id, title) }

    fun setIncluded(chapter: Chapter, included: Boolean) = launch { books.setChapterIncluded(bookId, chapter.id, included) }

    fun move(chapter: Chapter, offset: Int) = launch { books.moveChapter(bookId, chapter.id, offset) }

    fun includeAll(included: Boolean) = launch {
        state.value.chapters.filter { it.included != included }.forEach { books.setChapterIncluded(bookId, it.id, included) }
    }

    fun saveMetadata(metadata: BookMetadata) = launch {
        books.updateMetadata(bookId, metadata)
        _events.send(ImportEvent.Message("Datos del libro guardados."))
    }

    fun setCover(uri: Uri) = launch {
        try {
            books.setCover(bookId, uri)
        } catch (e: NarraException) {
            _events.send(ImportEvent.Message("No se pudo usar esa imagen como portada."))
        }
    }

    /**
     * Crea el audiolibro. Si el libro ya tenía audio y la edición incluyó capítulos nuevos,
     * genera solo lo que falta; si no hay nada que hacer, simplemente cierra la revisión.
     */
    fun create() = launch {
        val book = state.value.book ?: return@launch
        val pending = book.segmentCount > book.segmentsAvailable
        when {
            book.state == BookState.READY || (book.state == BookState.COMPLETED && pending) -> {
                queue.generate(bookId)
                _events.send(ImportEvent.Started)
            }
            else -> _events.send(ImportEvent.Done)
        }
    }

    fun delete() = launch {
        books.deleteBook(bookId)
        _events.send(ImportEvent.Deleted)
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
