package app.narra.domain.tts

import app.narra.domain.model.TtsProviderInfo
import app.narra.domain.model.Voice
import app.narra.domain.model.VoiceSettings
import java.io.File

/**
 * Motor de voz intercambiable. Hoy solo existe el del sistema Android; un proveedor en la nube
 * implementaría la misma interfaz y declararía en [TtsProviderInfo.capabilities] que envía texto
 * fuera del teléfono, para que la interfaz pida consentimiento antes de usarlo.
 */
interface TtsProvider {
    val info: TtsProviderInfo

    /**
     * Abre una sesión con el motor. Cada usuario (la generación, la vista previa de voces) abre
     * la suya y la cierra al terminar: así ninguno libera el motor mientras otro lo usa.
     */
    suspend fun open(): TtsSession
}

interface TtsSession : AutoCloseable {
    /** Longitud máxima de texto que acepta una sola llamada. */
    val maxInputChars: Int

    suspend fun voices(): List<Voice>

    /**
     * Escribe en [target] un WAV con [text] dicho con [voice]. Lanza
     * [app.narra.domain.model.NarraException] con la causa si el motor no puede hacerlo.
     */
    suspend fun synthesize(text: String, voice: VoiceSettings, target: File)

    /** Dice [text] por el altavoz y vuelve al terminar. Se usa para escuchar una voz antes de elegirla. */
    suspend fun speak(text: String, voice: VoiceSettings)

    /** Interrumpe lo que esté diciendo o sintetizando. */
    fun stop()
}

/** Proveedores disponibles, por id. */
interface TtsProviders {
    val all: List<TtsProvider>
    fun get(id: String): TtsProvider?
}
