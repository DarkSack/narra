package app.narra.plus

import android.app.Activity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Canal interno: el APK repartido a mano no puede cobrar por Google Play (no lleva su firma),
 * así que Narra Plus viene activo para quien lo instala.
 */
@Singleton
class Plus @Inject constructor() {
    val active: StateFlow<Boolean> = MutableStateFlow(true)
    val price: StateFlow<String?> = MutableStateFlow(null)

    fun refresh() = Unit

    @Suppress("UNUSED_PARAMETER")
    fun buy(activity: Activity) = Unit
}
