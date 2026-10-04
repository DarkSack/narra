package app.narra.presentation.text

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.narra.domain.model.Paragraph
import app.narra.domain.repository.BookRepository
import app.narra.presentation.navigation.ChapterTextRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** `null` mientras carga. */
@HiltViewModel
class ChapterTextViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    books: BookRepository,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<ChapterTextRoute>()
    val title: String = route.title

    private val _paragraphs = MutableStateFlow<List<Paragraph>?>(null)
    val paragraphs: StateFlow<List<Paragraph>?> = _paragraphs.asStateFlow()

    init {
        viewModelScope.launch { _paragraphs.value = books.getChapterText(route.chapterId) }
    }
}
