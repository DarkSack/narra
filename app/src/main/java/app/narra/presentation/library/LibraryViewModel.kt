package app.narra.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.narra.domain.model.AppSettings
import app.narra.domain.model.Book
import app.narra.domain.model.BookState
import app.narra.domain.model.LibraryFilter
import app.narra.domain.model.LibrarySort
import app.narra.domain.model.LibraryView
import app.narra.domain.repository.BookRepository
import app.narra.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale
import javax.inject.Inject

data class LibraryUiState(
    val loading: Boolean = true,
    val books: List<Book> = emptyList(),
    val totalBooks: Int = 0,
    val counts: Map<LibraryFilter, Int> = emptyMap(),
    val filter: LibraryFilter = LibraryFilter.ALL,
    val sort: LibrarySort = LibrarySort.RECENT,
    val view: LibraryView = LibraryView.GRID,
    val query: String = "",
) {
    val isSearching: Boolean get() = query.isNotBlank()
}

@OptIn(FlowPreview::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val books: BookRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** Ids de libros cuyo índice contiene la búsqueda (además de título y autor). */
    private val chapterMatches = _query
        .debounce(SEARCH_DEBOUNCE_MS)
        .distinctUntilChanged()
        .mapLatest { q -> if (q.length >= MIN_CHAPTER_QUERY) books.bookIdsWithChapterMatching(q.trim()) else emptySet() }
        .onStart { emit(emptySet()) }

    private val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY }

    val state: StateFlow<LibraryUiState> = combine(
        books.observeBooks(),
        settings.settings,
        _query,
        chapterMatches,
    ) { all, prefs, query, chapterIds ->
        val searched = if (query.isBlank()) all else all.filter { it.matches(query, chapterIds) }
        LibraryUiState(
            loading = false,
            books = searched.filter { it.passes(prefs.libraryFilter) }.sortedWith(comparator(prefs.librarySort)),
            totalBooks = all.size,
            counts = LibraryFilter.entries.associateWith { f -> searched.count { it.passes(f) } },
            filter = prefs.libraryFilter,
            sort = prefs.librarySort,
            view = prefs.libraryView,
            query = query,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LibraryUiState())

    fun setQuery(value: String) {
        _query.value = value
    }

    fun setFilter(filter: LibraryFilter) = save { it.copy(libraryFilter = filter) }

    fun setSort(sort: LibrarySort) = save { it.copy(librarySort = sort) }

    fun setView(view: LibraryView) = save { it.copy(libraryView = view) }

    fun toggleFavorite(book: Book) {
        viewModelScope.launch { books.setFavorite(book.id, !book.isFavorite) }
    }

    private fun save(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settings.update(transform) }
    }

    private fun Book.matches(query: String, chapterIds: Set<String>): Boolean {
        val q = query.trim()
        return title.contains(q, ignoreCase = true) ||
            author?.contains(q, ignoreCase = true) == true ||
            metadata.keywords.any { it.contains(q, ignoreCase = true) } ||
            id in chapterIds
    }

    private fun Book.passes(filter: LibraryFilter): Boolean = when (filter) {
        LibraryFilter.ALL -> true
        LibraryFilter.PROCESSING -> state.isImporting || state.isWorking || state == BookState.PAUSED || state == BookState.READY
        LibraryFilter.AVAILABLE -> hasPlayableAudio && !isCompleted
        LibraryFilter.COMPLETED -> isCompleted
        LibraryFilter.FAVORITES -> isFavorite
        LibraryFilter.ERRORS -> state == BookState.ERROR
    }

    private fun comparator(sort: LibrarySort): Comparator<Book> = when (sort) {
        LibrarySort.RECENT -> compareByDescending<Book> { maxOf(it.lastPlayedAt ?: 0L, it.importedAt) }
        LibrarySort.TITLE -> Comparator { a, b -> collator.compare(a.title, b.title) }
        LibrarySort.AUTHOR -> Comparator<Book> { a, b ->
            when {
                a.author == null && b.author == null -> 0
                a.author == null -> 1
                b.author == null -> -1
                else -> collator.compare(a.author, b.author)
            }
        }.then { a, b -> collator.compare(a.title, b.title) }
        LibrarySort.PROGRESS -> compareByDescending { it.listenedFraction }
        LibrarySort.DURATION -> compareByDescending { it.durationMs }
        LibrarySort.IMPORTED -> compareByDescending { it.importedAt }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val SEARCH_DEBOUNCE_MS = 250L
        const val MIN_CHAPTER_QUERY = 3
    }
}
