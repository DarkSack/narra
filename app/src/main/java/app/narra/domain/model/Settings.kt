package app.narra.domain.model

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Estilo de interfaz: cambia composición y densidad, nunca la funcionalidad. */
enum class UiStyle { MODERN, READING }

enum class UiDensity { COMFORTABLE, COMPACT }

enum class LibraryView { GRID, LIST }

enum class LibrarySort { RECENT, TITLE, AUTHOR, PROGRESS, DURATION, IMPORTED }

enum class LibraryFilter { ALL, PROCESSING, AVAILABLE, COMPLETED, FAVORITES, ERRORS }

enum class ChapterEndBehavior { CONTINUE, PAUSE }

enum class NetworkPolicy { WIFI_ONLY, ANY }

data class AppSettings(
    val onboardingCompleted: Boolean = false,
    // Apariencia
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val uiStyle: UiStyle = UiStyle.READING,
    val textScale: Float = 1f,
    val reduceMotion: Boolean = false,
    val highContrast: Boolean = false,
    val density: UiDensity = UiDensity.COMFORTABLE,
    // Reproducción
    val defaultSpeed: Float = 1f,
    val skipBackSeconds: Int = 15,
    val skipForwardSeconds: Int = 30,
    val chapterEndBehavior: ChapterEndBehavior = ChapterEndBehavior.CONTINUE,
    val autoPlayWhenReady: Boolean = false,
    // Audio (valores por defecto para libros nuevos)
    val defaultVoice: VoiceSettings = VoiceSettings(),
    // Procesamiento
    val networkPolicy: NetworkPolicy = NetworkPolicy.WIFI_ONLY,
    val backgroundProcessing: Boolean = true,
    // Biblioteca
    val libraryView: LibraryView = LibraryView.GRID,
    val librarySort: LibrarySort = LibrarySort.RECENT,
    val libraryFilter: LibraryFilter = LibraryFilter.ALL,
) {
    companion object {
        val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)
        val SKIP_OPTIONS = listOf(5, 10, 15, 30, 60)
        const val MIN_SPEED = 0.5f
        const val MAX_SPEED = 3f
        const val MIN_TEXT_SCALE = 0.85f
        const val MAX_TEXT_SCALE = 1.4f
    }
}
