package app.narra.work

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.narra.di.ApplicationScope
import app.narra.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ajuste «Crear audio con la app cerrada». Si está desactivado, la creación se detiene al salir
 * de Narra y continúa al volver; lo ya generado se conserva y el segmento a medias se repite.
 */
@Singleton
class BackgroundPolicy @Inject constructor(
    private val settings: SettingsRepository,
    private val queue: WorkManagerProcessingQueue,
    @param:ApplicationScope private val scope: CoroutineScope,
) : DefaultLifecycleObserver {

    /** Hay alguna pantalla de Narra a la vista. */
    val isAppVisible: Boolean
        get() = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    /** Si se puede crear audio ahora mismo según el ajuste y la visibilidad de la app. */
    suspend fun allowsWork(): Boolean = isAppVisible || settings.settings.first().backgroundProcessing

    fun register() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        scope.launch { queue.resumePending() }
    }

    override fun onStop(owner: LifecycleOwner) {
        scope.launch { if (!settings.settings.first().backgroundProcessing) queue.stopProcessor() }
    }
}
