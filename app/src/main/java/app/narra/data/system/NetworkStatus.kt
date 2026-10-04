package app.narra.data.system

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import app.narra.domain.model.NetworkPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Si la conexión actual permite usar una voz en línea según lo que eligió el usuario. */
fun interface NetworkStatus {
    fun allows(policy: NetworkPolicy): Boolean
}

@Singleton
class AndroidNetworkStatus @Inject constructor(@ApplicationContext private val context: Context) : NetworkStatus {
    private val connectivity get() = context.getSystemService(ConnectivityManager::class.java)

    override fun allows(policy: NetworkPolicy): Boolean {
        val manager = connectivity ?: return false
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        val online = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        return when (policy) {
            NetworkPolicy.ANY -> online
            NetworkPolicy.WIFI_ONLY -> online && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        }
    }
}
