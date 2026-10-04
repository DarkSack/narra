package app.narra.presentation.storage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.narra.core.ui.formatBytes
import app.narra.domain.model.Book
import app.narra.domain.model.StorageUsage
import app.narra.domain.repository.BookRepository
import app.narra.domain.repository.StorageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Cuánto ocupa un libro: el PDF importado y su audio. */
data class BookUsage(val book: Book, val bytes: Long)

data class StorageUiState(
    val usage: StorageUsage? = null,
    /** Libros de mayor a menor espacio ocupado. */
    val books: List<BookUsage> = emptyList(),
    val clearing: Boolean = false,
)

@HiltViewModel
class StorageViewModel @Inject constructor(
    books: BookRepository,
    private val storage: StorageRepository,
) : ViewModel() {
    private val usage = MutableStateFlow<StorageUsage?>(null)
    private val clearing = MutableStateFlow(false)

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    val state: StateFlow<StorageUiState> = combine(
        // Borrar el audio de un libro cambia lo que ocupa: se vuelve a medir.
        books.observeBooks().onEach { refresh() },
        usage,
        clearing,
    ) { list, current, isClearing ->
        StorageUiState(
            usage = current,
            books = list.map { BookUsage(it, it.fileSizeBytes + it.audioSizeBytes) }.filter { it.bytes > 0 }.sortedByDescending { it.bytes },
            clearing = isClearing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), StorageUiState())

    private fun refresh() {
        viewModelScope.launch { usage.value = storage.usage() }
    }

    fun clearTemporaryFiles() {
        if (clearing.value) return
        clearing.value = true
        viewModelScope.launch {
            val freed = storage.clearTemporaryFiles()
            usage.value = storage.usage()
            clearing.value = false
            _messages.send(if (freed > 0) "Liberaste ${formatBytes(freed)}." else "No había nada que se pudiera borrar ahora.")
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
