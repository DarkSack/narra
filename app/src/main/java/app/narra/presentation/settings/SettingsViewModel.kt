package app.narra.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.narra.domain.model.AppSettings
import app.narra.domain.model.StorageUsage
import app.narra.domain.repository.ProcessingQueue
import app.narra.domain.repository.SettingsRepository
import app.narra.domain.repository.StorageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val storage: StorageRepository,
    private val queue: ProcessingQueue,
) : ViewModel() {

    val settings: StateFlow<AppSettings?> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val _storage = MutableStateFlow<StorageUsage?>(null)

    /** Resumen para la entrada «Almacenamiento». */
    val storageUsage: StateFlow<StorageUsage?> = _storage.asStateFlow()

    /** Se mide al volver a la pantalla: borrar audio en otra pantalla cambia el total. */
    fun refreshStorage() {
        viewModelScope.launch { _storage.value = storage.usage() }
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            val before = repository.settings.first()
            repository.update(transform)
            // Permitir datos móviles puede desbloquear libros que esperaban al Wi-Fi.
            if (transform(before).networkPolicy != before.networkPolicy) queue.resumePending()
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
