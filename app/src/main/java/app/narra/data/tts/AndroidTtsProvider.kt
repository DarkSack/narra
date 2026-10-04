package app.narra.data.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import app.narra.domain.model.ErrorKind
import app.narra.domain.model.NarraException
import app.narra.domain.model.TtsCapabilities
import app.narra.domain.model.TtsProviderInfo
import app.narra.domain.model.Voice
import app.narra.domain.model.VoiceGender
import app.narra.domain.model.VoiceSettings
import app.narra.domain.tts.TtsProvider
import app.narra.domain.tts.TtsSession
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import android.speech.tts.Voice as SystemVoice

/** Motor de voz del sistema (Google, Samsung, RHVoice…). Todo ocurre en el teléfono. */
@Singleton
class AndroidTtsProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : TtsProvider {

    override val info = TtsProviderInfo(
        id = VoiceSettings.ANDROID_TTS_PROVIDER_ID,
        displayName = "Voces del teléfono",
        description = "Usa el motor de voz instalado en Android. Las voces sin conexión no envían el texto a ningún sitio.",
        capabilities = TtsCapabilities(
            supportsPitch = true,
            supportsRate = true,
            requiresNetwork = false,
            sendsTextToThirdParty = false,
            maxInputChars = TextToSpeech.getMaxSpeechInputLength(),
        ),
    )

    override suspend fun open(): TtsSession {
        val ready = CompletableDeferred<Int>()
        val engine = withContext(Dispatchers.Main) { TextToSpeech(context) { status -> ready.complete(status) } }
        val status = withTimeoutOrNull(INIT_TIMEOUT_MS) { ready.await() }
        if (status != TextToSpeech.SUCCESS) {
            engine.shutdown()
            throw NarraException(ErrorKind.TTS_UNAVAILABLE, "init=$status")
        }
        return AndroidTtsSession(engine)
    }

    private companion object {
        const val INIT_TIMEOUT_MS = 15_000L
    }
}

private class AndroidTtsSession(private val engine: TextToSpeech) : TtsSession {
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private val mutex = Mutex()
    private var applied: VoiceSettings? = null

    override val maxInputChars: Int = TextToSpeech.getMaxSpeechInputLength()

    init {
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) = Unit

            override fun onDone(utteranceId: String) {
                pending.remove(utteranceId)?.complete(Unit)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) = onError(utteranceId, TextToSpeech.ERROR)

            override fun onError(utteranceId: String, errorCode: Int) {
                pending.remove(utteranceId)?.completeExceptionally(failure(errorCode))
            }

            override fun onStop(utteranceId: String, interrupted: Boolean) {
                pending.remove(utteranceId)?.completeExceptionally(NarraException(ErrorKind.SYNTHESIS_FAILED, "stopped"))
            }
        })
    }

    override suspend fun voices(): List<Voice> = withContext(Dispatchers.Default) {
        engine.voices.orEmpty()
            .groupBy { it.locale }
            .flatMap { (locale, voices) ->
                voices.sortedBy { it.name }.mapIndexed { index, voice -> voice.toDomain(locale, index) }
            }
            .sortedWith(compareBy({ it.languageLabel }, { it.regionLabel.orEmpty() }, { it.requiresNetwork }, { it.displayName }))
    }

    override suspend fun synthesize(text: String, voice: VoiceSettings, target: File) = mutex.withLock {
        require(text.length <= maxInputChars) { "Texto de ${text.length} caracteres; el máximo es $maxInputChars" }
        apply(voice)
        target.parentFile?.mkdirs()
        target.delete()
        val id = UUID.randomUUID().toString()
        runUtterance(id, timeoutFor(text)) {
            engine.synthesizeToFile(text, Bundle.EMPTY, target, id)
        }
        if (target.length() <= MIN_WAV_BYTES) throw NarraException(ErrorKind.SYNTHESIS_FAILED, "archivo vacío")
    }

    override suspend fun speak(text: String, voice: VoiceSettings) = mutex.withLock {
        apply(voice)
        val id = UUID.randomUUID().toString()
        runUtterance(id, timeoutFor(text)) {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle.EMPTY, id)
        }
    }

    override fun stop() {
        engine.stop()
        pending.values.forEach { it.cancel() }
        pending.clear()
    }

    override fun close() {
        stop()
        engine.shutdown()
    }

    /** Lanza la petición y espera su final; si la corrutina se cancela, detiene el motor. */
    private suspend fun runUtterance(id: String, timeoutMs: Long, start: () -> Int) {
        val done = CompletableDeferred<Unit>()
        pending[id] = done
        if (start() != TextToSpeech.SUCCESS) {
            pending.remove(id)
            throw NarraException(ErrorKind.SYNTHESIS_FAILED, "rechazado")
        }
        try {
            withTimeout(timeoutMs) { done.await() }
        } catch (timeout: TimeoutCancellationException) {
            engine.stop()
            throw NarraException(ErrorKind.SYNTHESIS_FAILED, "sin respuesta", timeout)
        } finally {
            if (pending.remove(id) != null) engine.stop()
        }
    }

    /** Aplica voz, velocidad y tono solo cuando cambian: el motor tarda en cambiar de voz. */
    private fun apply(voice: VoiceSettings) {
        if (voice == applied) return
        val system = voice.voiceId?.let { id -> engine.voices.orEmpty().firstOrNull { it.name == id } }
        when {
            system != null -> {
                if (system.isNotInstalled) throw NarraException(ErrorKind.VOICE_UNAVAILABLE, system.name)
                if (engine.setVoice(system) != TextToSpeech.SUCCESS) throw NarraException(ErrorKind.VOICE_UNAVAILABLE, system.name)
            }
            voice.voiceId != null -> throw NarraException(ErrorKind.VOICE_UNAVAILABLE, voice.voiceId)
            else -> {
                val locale = voice.languageTag?.let(Locale::forLanguageTag) ?: Locale.getDefault()
                if (engine.setLanguage(locale) < TextToSpeech.LANG_AVAILABLE) {
                    throw NarraException(ErrorKind.VOICE_UNAVAILABLE, locale.toLanguageTag())
                }
                // El idioma puede estar "disponible" aunque su voz no esté descargada todavía.
                if (engine.voice?.isNotInstalled == true) throw NarraException(ErrorKind.VOICE_UNAVAILABLE, locale.toLanguageTag())
            }
        }
        engine.setSpeechRate(voice.speechRate)
        engine.setPitch(voice.pitch)
        applied = voice
    }

    private fun failure(code: Int): NarraException = when (code) {
        TextToSpeech.ERROR_NETWORK, TextToSpeech.ERROR_NETWORK_TIMEOUT -> NarraException(ErrorKind.NETWORK, "tts=$code")
        TextToSpeech.ERROR_NOT_INSTALLED_YET -> NarraException(ErrorKind.VOICE_UNAVAILABLE, "tts=$code")
        TextToSpeech.ERROR_OUTPUT -> NarraException(ErrorKind.STORAGE_FULL, "tts=$code")
        else -> NarraException(ErrorKind.SYNTHESIS_FAILED, "tts=$code")
    }

    private fun timeoutFor(text: String): Long = BASE_TIMEOUT_MS + text.length * TIMEOUT_PER_CHAR_MS

    private val SystemVoice.isNotInstalled: Boolean
        get() = features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)

    private fun SystemVoice.toDomain(locale: Locale, position: Int) = Voice(
        id = name,
        providerId = VoiceSettings.ANDROID_TTS_PROVIDER_ID,
        displayName = "Voz ${position + 1}",
        languageTag = locale.toLanguageTag(),
        languageLabel = locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) },
        regionLabel = locale.getDisplayCountry(locale).takeIf { it.isNotBlank() },
        gender = VoiceGender.UNKNOWN,
        quality = (quality * QUALITY_SCALE / SystemVoice.QUALITY_VERY_HIGH).coerceIn(0, QUALITY_SCALE),
        requiresNetwork = isNetworkConnectionRequired,
        isInstalled = !isNotInstalled,
    )

    private companion object {
        /** Un WAV sin muestras ocupa solo su cabecera. */
        const val MIN_WAV_BYTES = 44L
        const val BASE_TIMEOUT_MS = 30_000L
        const val TIMEOUT_PER_CHAR_MS = 40L
        const val QUALITY_SCALE = 100
    }
}
