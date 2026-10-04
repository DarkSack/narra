package app.narra.domain.model

/** Calidad del AAC generado. Voz mono: por encima de 64 kbps no se nota la diferencia. */
enum class AudioQuality(val bitrate: Int) {
    COMPACT(32_000),
    STANDARD(48_000),
    HIGH(64_000),
    ;

    /** Bytes aproximados por segundo de audio, para estimar espacio. */
    val bytesPerSecond: Long get() = bitrate / 8L
}

enum class AudioFormat(val extension: String, val mimeType: String) {
    M4A("m4a", "audio/mp4"),
}

data class VoiceSettings(
    val providerId: String = ANDROID_TTS_PROVIDER_ID,
    val voiceId: String? = null,
    val languageTag: String? = null,
    val speechRate: Float = 1f,
    val pitch: Float = 1f,
    val quality: AudioQuality = AudioQuality.STANDARD,
    val format: AudioFormat = AudioFormat.M4A,
    val paragraphPauseMs: Int = DEFAULT_PARAGRAPH_PAUSE_MS,
    val chapterPauseMs: Int = DEFAULT_CHAPTER_PAUSE_MS,
) {
    companion object {
        const val ANDROID_TTS_PROVIDER_ID = "android"
        const val DEFAULT_PARAGRAPH_PAUSE_MS = 450
        const val DEFAULT_CHAPTER_PAUSE_MS = 1_500
    }
}

enum class VoiceGender { FEMALE, MALE, NEUTRAL, UNKNOWN }

data class Voice(
    val id: String,
    val providerId: String,
    val displayName: String,
    val languageTag: String,
    val languageLabel: String,
    val regionLabel: String?,
    val gender: VoiceGender,
    /** 0–100, según lo que informe el proveedor. */
    val quality: Int,
    val requiresNetwork: Boolean,
    val isInstalled: Boolean,
)

/** Lo que un proveedor de voz sabe hacer: la UI se adapta en lugar de ocultar funciones. */
data class TtsCapabilities(
    val supportsPitch: Boolean,
    val supportsRate: Boolean,
    val requiresNetwork: Boolean,
    /** true si el texto sale del teléfono: la UI debe pedir consentimiento. */
    val sendsTextToThirdParty: Boolean,
    val maxInputChars: Int,
)

data class TtsProviderInfo(
    val id: String,
    val displayName: String,
    val description: String,
    val capabilities: TtsCapabilities,
)
