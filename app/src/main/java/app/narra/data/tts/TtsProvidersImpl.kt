package app.narra.data.tts

import app.narra.domain.tts.TtsProvider
import app.narra.domain.tts.TtsProviders
import javax.inject.Inject
import javax.inject.Singleton

/** Registro de motores de voz. Añadir uno nuevo es enlazarlo en el mapa de Hilt. */
@Singleton
class TtsProvidersImpl @Inject constructor(
    private val providers: Map<String, @JvmSuppressWildcards TtsProvider>,
) : TtsProviders {
    override val all: List<TtsProvider> get() = providers.values.sortedBy { it.info.displayName }

    override fun get(id: String): TtsProvider? = providers[id]
}
