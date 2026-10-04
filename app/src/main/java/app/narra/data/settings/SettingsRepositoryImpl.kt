package app.narra.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.narra.domain.model.AppSettings
import app.narra.domain.model.AudioFormat
import app.narra.domain.model.AudioQuality
import app.narra.domain.model.ChapterEndBehavior
import app.narra.domain.model.LibraryFilter
import app.narra.domain.model.LibrarySort
import app.narra.domain.model.LibraryView
import app.narra.domain.model.NetworkPolicy
import app.narra.domain.model.ThemeMode
import app.narra.domain.model.UiDensity
import app.narra.domain.model.UiStyle
import app.narra.domain.model.VoiceSettings
import app.narra.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
) : SettingsRepository {
    private val store = context.settingsStore

    override val settings: Flow<AppSettings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.toSettings() }
        .distinctUntilChanged()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs ->
            val updated = transform(prefs.toSettings())
            prefs.write(updated)
        }
    }

    private object Keys {
        val onboarding = booleanPreferencesKey("onboarding_completed")
        val theme = stringPreferencesKey("theme_mode")
        val style = stringPreferencesKey("ui_style")
        val textScale = floatPreferencesKey("text_scale")
        val reduceMotion = booleanPreferencesKey("reduce_motion")
        val highContrast = booleanPreferencesKey("high_contrast")
        val density = stringPreferencesKey("density")
        val speed = floatPreferencesKey("default_speed")
        val skipBack = intPreferencesKey("skip_back")
        val skipForward = intPreferencesKey("skip_forward")
        val chapterEnd = stringPreferencesKey("chapter_end")
        val autoPlay = booleanPreferencesKey("auto_play")
        val provider = stringPreferencesKey("tts_provider")
        val voice = stringPreferencesKey("tts_voice")
        val voiceLanguage = stringPreferencesKey("tts_language")
        val ttsRate = floatPreferencesKey("tts_rate")
        val ttsPitch = floatPreferencesKey("tts_pitch")
        val quality = stringPreferencesKey("audio_quality")
        val format = stringPreferencesKey("audio_format")
        val paragraphPause = intPreferencesKey("paragraph_pause")
        val chapterPause = intPreferencesKey("chapter_pause")
        val network = stringPreferencesKey("network_policy")
        val background = booleanPreferencesKey("background_processing")
        val libraryView = stringPreferencesKey("library_view")
        val librarySort = stringPreferencesKey("library_sort")
        val libraryFilter = stringPreferencesKey("library_filter")
    }

    private fun Preferences.toSettings(): AppSettings {
        val defaults = AppSettings()
        val voiceDefaults = defaults.defaultVoice
        return AppSettings(
            onboardingCompleted = this[Keys.onboarding] ?: defaults.onboardingCompleted,
            themeMode = enumOr(this[Keys.theme], defaults.themeMode),
            uiStyle = enumOr(this[Keys.style], defaults.uiStyle),
            textScale = (this[Keys.textScale] ?: defaults.textScale)
                .coerceIn(AppSettings.MIN_TEXT_SCALE, AppSettings.MAX_TEXT_SCALE),
            reduceMotion = this[Keys.reduceMotion] ?: defaults.reduceMotion,
            highContrast = this[Keys.highContrast] ?: defaults.highContrast,
            density = enumOr(this[Keys.density], defaults.density),
            defaultSpeed = (this[Keys.speed] ?: defaults.defaultSpeed)
                .coerceIn(AppSettings.MIN_SPEED, AppSettings.MAX_SPEED),
            skipBackSeconds = this[Keys.skipBack] ?: defaults.skipBackSeconds,
            skipForwardSeconds = this[Keys.skipForward] ?: defaults.skipForwardSeconds,
            chapterEndBehavior = enumOr(this[Keys.chapterEnd], defaults.chapterEndBehavior),
            autoPlayWhenReady = this[Keys.autoPlay] ?: defaults.autoPlayWhenReady,
            defaultVoice = VoiceSettings(
                providerId = this[Keys.provider] ?: voiceDefaults.providerId,
                voiceId = this[Keys.voice],
                languageTag = this[Keys.voiceLanguage],
                speechRate = this[Keys.ttsRate] ?: voiceDefaults.speechRate,
                pitch = this[Keys.ttsPitch] ?: voiceDefaults.pitch,
                quality = enumOr(this[Keys.quality], voiceDefaults.quality),
                format = enumOr(this[Keys.format], voiceDefaults.format),
                paragraphPauseMs = this[Keys.paragraphPause] ?: voiceDefaults.paragraphPauseMs,
                chapterPauseMs = this[Keys.chapterPause] ?: voiceDefaults.chapterPauseMs,
            ),
            networkPolicy = enumOr(this[Keys.network], defaults.networkPolicy),
            backgroundProcessing = this[Keys.background] ?: defaults.backgroundProcessing,
            libraryView = enumOr(this[Keys.libraryView], defaults.libraryView),
            librarySort = enumOr(this[Keys.librarySort], defaults.librarySort),
            libraryFilter = enumOr(this[Keys.libraryFilter], defaults.libraryFilter),
        )
    }

    private fun androidx.datastore.preferences.core.MutablePreferences.write(s: AppSettings) {
        this[Keys.onboarding] = s.onboardingCompleted
        this[Keys.theme] = s.themeMode.name
        this[Keys.style] = s.uiStyle.name
        this[Keys.textScale] = s.textScale
        this[Keys.reduceMotion] = s.reduceMotion
        this[Keys.highContrast] = s.highContrast
        this[Keys.density] = s.density.name
        this[Keys.speed] = s.defaultSpeed
        this[Keys.skipBack] = s.skipBackSeconds
        this[Keys.skipForward] = s.skipForwardSeconds
        this[Keys.chapterEnd] = s.chapterEndBehavior.name
        this[Keys.autoPlay] = s.autoPlayWhenReady
        this[Keys.provider] = s.defaultVoice.providerId
        s.defaultVoice.voiceId?.let { this[Keys.voice] = it } ?: remove(Keys.voice)
        s.defaultVoice.languageTag?.let { this[Keys.voiceLanguage] = it } ?: remove(Keys.voiceLanguage)
        this[Keys.ttsRate] = s.defaultVoice.speechRate
        this[Keys.ttsPitch] = s.defaultVoice.pitch
        this[Keys.quality] = s.defaultVoice.quality.name
        this[Keys.format] = s.defaultVoice.format.name
        this[Keys.paragraphPause] = s.defaultVoice.paragraphPauseMs
        this[Keys.chapterPause] = s.defaultVoice.chapterPauseMs
        this[Keys.network] = s.networkPolicy.name
        this[Keys.background] = s.backgroundProcessing
        this[Keys.libraryView] = s.libraryView.name
        this[Keys.librarySort] = s.librarySort.name
        this[Keys.libraryFilter] = s.libraryFilter.name
    }

    private inline fun <reified E : Enum<E>> enumOr(value: String?, default: E): E =
        value?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default
}
