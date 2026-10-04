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

/**
 * Motores de voz instalados en Android (Google, Samsung, eSpeak, RHVoice…). Se puede usar
 * cualquiera, no solo el predeterminado del teléfono. Todo ocurre en el dispositivo salvo las
 * voces marcadas como "en línea", que el propio motor resuelve con su servicio.
 */
@Singleton
class AndroidTtsProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : TtsProvider {

    override val info = TtsProviderInfo(
        id = VoiceSettings.ANDROID_TTS_PROVIDER_ID,
        displayName = "Voces del teléfono",
        description = "Usa los motores de voz instalados en Android. Las voces sin conexión no envían el texto a ningún sitio.",
        capabilities = TtsCapabilities(
            supportsPitch = true,
            supportsRate = true,
            requiresNetwork = false,
            sendsTextToThirdParty = false,
            maxInputChars = TextToSpeech.getMaxSpeechInputLength(),
        ),
    )

    override suspend fun open(): TtsSession {
        val session = AndroidTtsSession(context)
        try {
            session.connectDefault()
        } catch (e: NarraException) {
            session.close()
            throw e
        }
        return session
    }
}

private class AndroidTtsSession(private val context: Context) : TtsSession {
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private val mutex = Mutex()

    /** Motores ya conectados, por paquete. Se conectan la primera vez que hacen falta. */
    private val engines = ConcurrentHashMap<String, TextToSpeech>()
    private val applied = HashMap<String, VoiceSettings>()
    private lateinit var defaultEngine: String

    override val maxInputChars: Int = TextToSpeech.getMaxSpeechInputLength()

    private val listener = object : UtteranceProgressListener() {
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
    }

    /** Conecta el motor predeterminado del teléfono; si no hay ninguno, no hay voz posible. */
    suspend fun connectDefault() {
        val tts = connect(null)
        defaultEngine = tts.defaultEngine
        engines[defaultEngine] = tts
    }

    private suspend fun connect(packageName: String?): TextToSpeech {
        val ready = CompletableDeferred<Int>()
        val tts = withContext(Dispatchers.Main) {
            TextToSpeech(context, { status -> ready.complete(status) }, packageName)
        }
        val status = withTimeoutOrNull(INIT_TIMEOUT_MS) { ready.await() }
        if (status != TextToSpeech.SUCCESS) {
            tts.shutdown()
            throw NarraException(ErrorKind.TTS_UNAVAILABLE, "init ${packageName ?: "predeterminado"}=$status")
        }
        tts.setOnUtteranceProgressListener(listener)
        return tts
    }

    private suspend fun engine(packageName: String?): TextToSpeech {
        val name = packageName ?: defaultEngine
        engines[name]?.let { return it }
        // El motor elegido se desinstaló: la voz ya no está disponible.
        if (engines.getValue(defaultEngine).engines.none { it.name == name }) throw NarraException(ErrorKind.VOICE_UNAVAILABLE, name)
        return connect(name).also { engines[name] = it }
    }

    override suspend fun voices(): List<Voice> = mutex.withLock {
        engines.getValue(defaultEngine).engines.flatMap { info ->
            val tts = try {
                engine(info.name)
            } catch (_: NarraException) {
                return@flatMap emptyList()
            }
            tts.voices.orEmpty()
                .groupBy { LocaleCodes.normalize(it.locale) }
                .flatMap { (locale, voices) ->
                    voices.sortedBy { it.name }.mapIndexed { index, voice -> voice.toDomain(info.name, info.label, locale, index) }
                }
        }.sortedWith(
            // Dentro de cada idioma y región: primero lo que se puede usar ya, después lo que hay que descargar.
            compareBy<Voice>({ it.languageLabel }, { it.regionLabel.orEmpty() }, { !it.isInstalled }, { it.engine != defaultEngine }, { it.engineLabel })
                .thenBy { it.requiresNetwork }
                .thenBy { it.displayName },
        )
    }

    override suspend fun synthesize(text: String, voice: VoiceSettings, target: File) = mutex.withLock {
        require(text.length <= maxInputChars) { "Texto de ${text.length} caracteres; el máximo es $maxInputChars" }
        val tts = prepare(voice)
        target.parentFile?.mkdirs()
        target.delete()
        val id = UUID.randomUUID().toString()
        runUtterance(tts, id, timeoutFor(text)) { tts.synthesizeToFile(text, Bundle.EMPTY, target, id) }
        if (target.length() <= MIN_WAV_BYTES) throw NarraException(ErrorKind.SYNTHESIS_FAILED, "archivo vacío")
    }

    override suspend fun speak(text: String, voice: VoiceSettings) = mutex.withLock {
        val tts = prepare(voice)
        val id = UUID.randomUUID().toString()
        runUtterance(tts, id, timeoutFor(text)) { tts.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle.EMPTY, id) }
    }

    override suspend fun requiresNetwork(voice: VoiceSettings): Boolean = mutex.withLock {
        prepare(voice).voice?.isNetworkConnectionRequired == true
    }

    override fun stop() {
        engines.values.forEach { it.stop() }
        pending.values.forEach { it.cancel() }
        pending.clear()
    }

    override fun close() {
        stop()
        engines.values.forEach { it.shutdown() }
        engines.clear()
    }

    /** Lanza la petición y espera su final; si la corrutina se cancela, detiene el motor. */
    private suspend fun runUtterance(tts: TextToSpeech, id: String, timeoutMs: Long, start: () -> Int) {
        val done = CompletableDeferred<Unit>()
        pending[id] = done
        if (start() != TextToSpeech.SUCCESS) {
            pending.remove(id)
            throw NarraException(ErrorKind.SYNTHESIS_FAILED, "rechazado")
        }
        try {
            withTimeout(timeoutMs) { done.await() }
        } catch (timeout: TimeoutCancellationException) {
            tts.stop()
            throw NarraException(ErrorKind.SYNTHESIS_FAILED, "sin respuesta", timeout)
        } finally {
            if (pending.remove(id) != null) tts.stop()
        }
    }

    /** Elige motor, voz, velocidad y tono, solo si cambian: cambiar de voz es lento. */
    private suspend fun prepare(voice: VoiceSettings): TextToSpeech {
        val name = voice.engine ?: defaultEngine
        val tts = engine(name)
        if (applied[name] == voice) return tts
        val system = voice.voiceId?.let { id -> tts.voices.orEmpty().firstOrNull { it.name == id } }
        when {
            system != null -> {
                if (system.isNotInstalled) throw NarraException(ErrorKind.VOICE_UNAVAILABLE, system.name)
                if (tts.setVoice(system) != TextToSpeech.SUCCESS) throw NarraException(ErrorKind.VOICE_UNAVAILABLE, system.name)
            }
            voice.voiceId != null -> throw NarraException(ErrorKind.VOICE_UNAVAILABLE, voice.voiceId)
            else -> {
                val locale = voice.languageTag?.let(Locale::forLanguageTag) ?: Locale.getDefault()
                if (tts.setLanguage(locale) < TextToSpeech.LANG_AVAILABLE) {
                    throw NarraException(ErrorKind.VOICE_UNAVAILABLE, locale.toLanguageTag())
                }
                // El idioma puede estar "disponible" aunque su voz no esté descargada todavía.
                if (tts.voice?.isNotInstalled == true) throw NarraException(ErrorKind.VOICE_UNAVAILABLE, locale.toLanguageTag())
            }
        }
        tts.setSpeechRate(voice.speechRate)
        tts.setPitch(voice.pitch)
        applied[name] = voice
        return tts
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

    private fun SystemVoice.toDomain(engine: String, engineLabel: String, locale: Locale, position: Int) = Voice(
        id = name,
        providerId = VoiceSettings.ANDROID_TTS_PROVIDER_ID,
        engine = engine,
        engineLabel = engineLabel,
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
        const val INIT_TIMEOUT_MS = 15_000L

        /** Un WAV sin muestras ocupa solo su cabecera. */
        const val MIN_WAV_BYTES = 44L
        const val BASE_TIMEOUT_MS = 30_000L
        const val TIMEOUT_PER_CHAR_MS = 40L
        const val QUALITY_SCALE = 100
    }
}
