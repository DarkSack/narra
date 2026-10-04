package app.narra.presentation.voices

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.narra.domain.model.AudioQuality
import app.narra.domain.model.Book
import app.narra.domain.model.BookState
import app.narra.domain.model.ErrorKind
import app.narra.domain.model.NarraException
import app.narra.domain.model.Voice
import app.narra.domain.model.VoiceSettings
import app.narra.domain.repository.BookRepository
import app.narra.domain.repository.ProcessingQueue
import app.narra.domain.repository.SettingsRepository
import app.narra.domain.tts.TtsProviders
import app.narra.domain.tts.TtsSession
import app.narra.presentation.navigation.VoicesRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/** Errores con los que la creación se detiene hasta que se elija o se instale una voz. */
private val VOICE_ERRORS = setOf(ErrorKind.VOICE_UNAVAILABLE, ErrorKind.TTS_UNAVAILABLE, ErrorKind.NETWORK)

data class VoicesUiState(
    val loading: Boolean = true,
    /** El motor no arrancó: no hay voces que mostrar. */
    val engineError: ErrorKind? = null,
    val book: Book? = null,
    val voices: List<Voice> = emptyList(),
    /** Idioma con el que se filtra la lista; null = todos. */
    val languageFilter: String? = null,
    val draft: VoiceSettings = VoiceSettings(),
    /** [Voice.key] de la voz que suena en la muestra. */
    val previewingKey: String? = null,
    val previewing: Boolean = false,
    /** Hay cambios sin guardar. */
    val dirty: Boolean = false,
) {
    /** Idioma principal del libro (o de la voz por defecto), sin región. */
    val baseLanguage: String? get() = draft.languageTag?.let { Locale.forLanguageTag(it).language }

    val visibleVoices: List<Voice>
        get() = languageFilter?.let { language -> voices.filter { Locale.forLanguageTag(it.languageTag).language == language } } ?: voices

    val selectedVoice: Voice? get() = voices.firstOrNull { it.id == draft.voiceId && (draft.engine == null || it.engine == draft.engine) }

    /** Clave de la voz elegida, comparable con [Voice.key]. */
    val draftKey: String get() = selectedVoice?.key ?: "${draft.engine}/${draft.voiceId}"

    /** El libro ya tiene audio: cambiar la voz plantea si regenerarlo. */
    val hasAudio: Boolean get() = (book?.segmentsAvailable ?: 0) > 0

    /**
     * La creación del audio se detuvo por la voz. Guardar la reanuda aunque no haya cambios: la
     * voz puede haberse descargado después o resolverse con otro motor del teléfono.
     */
    val stoppedByVoice: Boolean
        get() = book?.let { it.state == BookState.ERROR && it.error?.kind in VOICE_ERRORS && it.chapterCount > 0 } == true

    val canSave: Boolean get() = dirty || stoppedByVoice

    /** Ninguna voz del idioma del libro está descargada: hay que avisar antes de que falle la creación. */
    val missingLanguageVoices: Boolean
        get() {
            val language = baseLanguage ?: return false
            return voices.filter { Locale.forLanguageTag(it.languageTag).language == language }.none { it.isInstalled }
        }
}

sealed interface VoicesEvent {
    data object Saved : VoicesEvent
    data class Message(val text: String) : VoicesEvent
}

/**
 * Elegir voz para un libro (con [VoicesRoute.bookId]) o la voz por defecto de los libros nuevos.
 * Abre su propia sesión con el motor para las vistas previas y la cierra al salir.
 */
@HiltViewModel
class VoicesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val books: BookRepository,
    private val settings: SettingsRepository,
    private val providers: TtsProviders,
    private val queue: ProcessingQueue,
) : ViewModel() {
    private val bookId: String? = savedStateHandle.toRoute<VoicesRoute>().bookId

    private val _state = MutableStateFlow(VoicesUiState())
    val state: StateFlow<VoicesUiState> = _state.asStateFlow()

    private val _events = Channel<VoicesEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var session: TtsSession? = null
    private var previewJob: Job? = null
    private var previewToken = 0
    private var sample: String = DEFAULT_SAMPLE

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val book = bookId?.let { books.getBook(it) }
        val voice = book?.voice ?: settings.settings.first().defaultVoice
        val draft = if (voice.languageTag == null) voice.copy(languageTag = Locale.getDefault().toLanguageTag()) else voice
        sample = book?.let { sampleFrom(it) } ?: DEFAULT_SAMPLE
        val provider = providers.get(draft.providerId) ?: providers.all.firstOrNull()
        val opened = try {
            provider?.open()
        } catch (e: NarraException) {
            _state.update { it.copy(loading = false, engineError = e.kind, book = book, draft = draft) }
            return
        }
        session = opened
        val voices = opened?.voices().orEmpty()
        val language = draft.baseLanguageOf()
        _state.update {
            it.copy(
                loading = false,
                engineError = if (opened == null) ErrorKind.TTS_UNAVAILABLE else null,
                book = book,
                voices = voices,
                languageFilter = language?.takeIf { lang -> voices.any { v -> Locale.forLanguageTag(v.languageTag).language == lang } },
                draft = draft,
            )
        }
    }

    /** Primeras frases del primer capítulo: oír la voz con el propio libro ayuda a decidir. */
    private suspend fun sampleFrom(book: Book): String {
        val chapter = books.getChapters(book.id).firstOrNull { it.included } ?: return DEFAULT_SAMPLE
        val text = books.getChapterText(chapter.id).asSequence().map { it.text }.filter { it.length > MIN_SAMPLE_PARAGRAPH }.firstOrNull()
            ?: return DEFAULT_SAMPLE
        if (text.length <= MAX_SAMPLE_CHARS) return text
        val cut = text.lastIndexOfAny(charArrayOf('.', '!', '?', '…'), MAX_SAMPLE_CHARS)
        return if (cut > MIN_SAMPLE_PARAGRAPH) text.substring(0, cut + 1) else text.substring(0, MAX_SAMPLE_CHARS)
    }

    fun setLanguageFilter(language: String?) = _state.update { it.copy(languageFilter = language) }

    fun select(voice: Voice) {
        _state.update {
            it.copy(
                draft = it.draft.copy(providerId = voice.providerId, engine = voice.engine, voiceId = voice.id, languageTag = voice.languageTag),
                dirty = true,
            )
        }
        startPreview(voice)
    }

    fun setRate(rate: Float) = edit { it.copy(speechRate = rate) }
    fun setPitch(pitch: Float) = edit { it.copy(pitch = pitch) }
    fun setParagraphPause(ms: Int) = edit { it.copy(paragraphPauseMs = ms) }
    fun setQuality(quality: AudioQuality) = edit { it.copy(quality = quality) }

    private fun edit(transform: (VoiceSettings) -> VoiceSettings) = _state.update { it.copy(draft = transform(it.draft), dirty = true) }

    /** Dice la muestra con la voz indicada (o la elegida, con null). Si ya suena, la detiene. */
    fun togglePreview(voice: Voice? = null) {
        val current = _state.value
        if (current.previewing && current.previewingKey == (voice?.key ?: current.draftKey)) stopPreview() else startPreview(voice)
    }

    private fun startPreview(voice: Voice?) {
        val current = _state.value
        val target = voice?.key ?: current.draftKey
        val active = session ?: return
        previewJob?.cancel()
        active.stop()
        val settings = if (voice == null) {
            current.draft
        } else {
            current.draft.copy(engine = voice.engine, voiceId = voice.id, languageTag = voice.languageTag)
        }
        val token = ++previewToken
        previewJob = viewModelScope.launch {
            _state.update { it.copy(previewing = true, previewingKey = target) }
            try {
                active.speak(sample, settings)
            } catch (e: NarraException) {
                _events.send(VoicesEvent.Message(previewError(e.kind)))
            } finally {
                // Una muestra interrumpida por otra no debe borrar el estado de la nueva.
                if (token == previewToken) _state.update { it.copy(previewing = false, previewingKey = null) }
            }
        }
    }

    fun stopPreview() {
        previewJob?.cancel()
        session?.stop()
        _state.update { it.copy(previewing = false, previewingKey = null) }
    }

    /** Guarda la voz. Con [regenerate], borra el audio creado y lo vuelve a crear con la nueva voz. */
    fun save(regenerate: Boolean = false) {
        val draft = _state.value.draft
        stopPreview()
        viewModelScope.launch {
            val book = _state.value.book
            if (book == null) {
                settings.update { it.copy(defaultVoice = draft) }
            } else {
                books.updateVoice(book.id, draft)
                when {
                    regenerate -> {
                        books.deleteAudio(book.id)
                        queue.generate(book.id)
                    }
                    // La creación se había detenido por la voz: con la nueva, sigue sola.
                    _state.value.stoppedByVoice -> queue.retry(book.id)
                }
            }
            _state.update { it.copy(dirty = false) }
            _events.send(VoicesEvent.Saved)
        }
    }

    private fun previewError(kind: ErrorKind): String = when (kind) {
        ErrorKind.VOICE_UNAVAILABLE -> "Esta voz aún no está descargada en el teléfono."
        ErrorKind.NETWORK -> "Esta voz necesita conexión a internet."
        else -> "No se pudo reproducir la muestra."
    }

    private fun VoiceSettings.baseLanguageOf(): String? = languageTag?.let { Locale.forLanguageTag(it).language }

    override fun onCleared() {
        session?.close()
        session = null
    }

    private companion object {
        const val DEFAULT_SAMPLE = "Hola. Esta es la voz que leerá tus libros. ¿Te gusta cómo suena?"
        const val MAX_SAMPLE_CHARS = 280
        const val MIN_SAMPLE_PARAGRAPH = 60
    }
}
