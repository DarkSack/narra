package app.narra.update

import android.app.Activity
import android.content.Context
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Canal Play: actualización flexible de Google Play. Play muestra su aviso, descarga en segundo
 * plano mientras se usa la app y Narra ofrece reiniciar para instalar.
 */
@Singleton
class AppUpdater @Inject constructor(@ApplicationContext context: Context) {
    private val manager = AppUpdateManagerFactory.create(context)

    private val _ready = MutableStateFlow(false)

    /** Hay una versión nueva descargada, lista para instalarse. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    init {
        manager.registerListener { state -> if (state.installStatus() == InstallStatus.DOWNLOADED) _ready.value = true }
    }

    /** Al abrir la app. Sin Play Store o si la app no vino de Play, la consulta falla y no pasa nada. */
    fun check(activity: Activity) {
        manager.appUpdateInfo.addOnSuccessListener { info ->
            when {
                info.installStatus() == InstallStatus.DOWNLOADED -> _ready.value = true
                info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                    info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) ->
                    manager.startUpdateFlow(info, activity, AppUpdateOptions.defaultOptions(AppUpdateType.FLEXIBLE))
            }
        }
    }

    /** Instala lo descargado; Play reinicia Narra. */
    fun install() {
        manager.completeUpdate()
    }
}
