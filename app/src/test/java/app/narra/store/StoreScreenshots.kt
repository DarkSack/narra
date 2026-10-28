package app.narra.store

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import app.narra.core.designsystem.theme.NarraTheme
import app.narra.domain.model.AnalysisProgress
import app.narra.domain.model.AnalysisReport
import app.narra.domain.model.AppSettings
import app.narra.domain.model.Book
import app.narra.domain.model.BookMetadata
import app.narra.domain.model.BookState
import app.narra.domain.model.Chapter
import app.narra.domain.model.ChapterState
import app.narra.domain.model.ExportStatus
import app.narra.domain.model.JobState
import app.narra.domain.model.ListeningProgress
import app.narra.domain.model.Paragraph
import app.narra.domain.model.PlayableTrack
import app.narra.domain.model.ProcessingJob
import app.narra.domain.model.ThemeMode
import app.narra.domain.model.UiStyle
import app.narra.domain.model.VoiceSettings
import app.narra.domain.repository.AudiobookExports
import app.narra.domain.repository.BookRepository
import app.narra.domain.repository.PlaybackRepository
import app.narra.domain.repository.ProcessingQueue
import app.narra.domain.repository.SettingsRepository
import app.narra.playback.PlaybackController
import app.narra.playback.PlaybackSnapshot
import app.narra.presentation.book.BookDetailsScreen
import app.narra.presentation.book.BookDetailsViewModel
import app.narra.presentation.home.HomeScreen
import app.narra.presentation.home.HomeViewModel
import app.narra.presentation.library.LibraryScreen
import app.narra.presentation.library.LibraryViewModel
import app.narra.presentation.player.PlayerScreen
import app.narra.presentation.player.PlayerViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

/**
 * Capturas para la ficha de Google Play: pantallas reales con una biblioteca inventada de clásicos de
 * dominio público, a 1080 × 2160 (el máximo 2:1 de Play). No corre con las pruebas normales:
 * `./gradlew :app:testPlayDebugUnitTest --tests "app.narra.store.StoreScreenshots" -Pcapturas=E:/Dev/pdf-audiobooks/play/capturas --rerun`
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h720dp-xxhdpi")
class StoreScreenshots {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val out = System.getProperty("narra.capturas").orEmpty()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val now = System.currentTimeMillis()
    private val hour = 3_600_000L
    private val minute = 60_000L

    private lateinit var books: List<Book>
    private lateinit var chapters: Map<String, List<Chapter>>
    private lateinit var tracks: Map<String, List<PlayableTrack>>
    private lateinit var jobs: List<ProcessingJob>
    // -Ptema=PRESS, -Ptema=LAMP:DARK…: estilo y, opcional, modo (por defecto Lámpara oscura, la cara de Narra).
    private val tema = System.getProperty("narra.tema").orEmpty().split(':')
    private val settings = MutableStateFlow(
        AppSettings(
            onboardingCompleted = true,
            uiStyle = UiStyle.entries.firstOrNull { it.name == tema[0] } ?: UiStyle.LAMP,
            themeMode = ThemeMode.entries.firstOrNull { it.name == tema.getOrNull(1) } ?: ThemeMode.DARK,
        ),
    )

    @Before
    fun setUp() {
        assumeTrue("Solo con -Pcapturas=<carpeta>", out.isNotEmpty())
        seed()
    }

    // ------------------------------------------------------------------ Datos inventados

    private fun roman(n: Int): String {
        val values = listOf(50 to "L", 40 to "XL", 10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I")
        var rest = n
        return buildString { values.forEach { (v, s) -> while (rest >= v) { append(s); rest -= v } } }
    }

    /** Capítulos de 40 minutos en 8 partes de 5; los primeros [available] ya tienen audio. */
    private fun chaptersOf(bookId: String, count: Int, available: Int, base: Long) = (1..count).map { i ->
        val ready = i <= available
        Chapter(
            id = base + i, bookId = bookId, index = i - 1, title = "Capítulo ${roman(i)}", level = 0,
            startPage = i * 9, endPage = i * 9 + 8, included = true,
            state = when {
                ready -> ChapterState.AVAILABLE
                i == available + 1 -> ChapterState.PROCESSING
                else -> ChapterState.PENDING
            },
            charCount = 36_000, segmentCount = 8, segmentsAvailable = if (ready) 8 else if (i == available + 1) 3 else 0,
            durationMs = 40 * minute, error = null,
        )
    }

    private fun tracksOf(list: List<Chapter>) = list.filter { it.segmentsAvailable > 0 }.flatMap { c ->
        (0 until c.segmentsAvailable).map { k ->
            PlayableTrack(c.id * 100 + k, c.id, c.index, c.title, k, "audio/${c.id}-$k.m4a", 5 * minute)
        }
    }

    private fun book(
        id: String, title: String, author: String, chapters: List<Chapter>, state: BookState,
        importedDaysAgo: Int, playedHoursAgo: Int? = null, listenedMs: Long = 0, completed: Boolean = false,
        description: String? = null,
    ): Book {
        val current = chapters.getOrNull((listenedMs / (40 * minute)).toInt())
        val available = chapters.count { it.state == ChapterState.AVAILABLE }
        return Book(
            id = id,
            metadata = BookMetadata(title, author, description = description, language = "es"),
            // Sin imagen: Narra dibuja su portada generada, la misma que ve quien importa un PDF sin portada.
            coverPath = null,
            sourceFileName = "$id.pdf", fileSizeBytes = 2_400_000, pageCount = chapters.size * 9 + 12,
            importedAt = now - importedDaysAgo * 24 * hour,
            lastPlayedAt = playedHoursAgo?.let { now - it * hour },
            isFavorite = false, state = state, error = null, voice = VoiceSettings(),
            chapterCount = chapters.size, chaptersAvailable = available,
            segmentCount = chapters.sumOf { it.segmentCount }, segmentsAvailable = chapters.sumOf { it.segmentsAvailable },
            totalChars = chapters.sumOf { it.charCount },
            durationMs = chapters.sumOf { it.durationMs }, generatedDurationMs = available * 40 * minute,
            audioSizeBytes = available * 14_000_000L,
            analysis = AnalysisProgress(pagesDone = 1, pagesTotal = 1),
            progress = if (listenedMs > 0 || completed) {
                ListeningProgress(id, current?.id?.times(100)?.plus(3), current?.id, current?.title, 15 * minute, listenedMs, completed, now)
            } else {
                null
            },
        )
    }

    private fun seed() {
        val quijote = chaptersOf("quijote", 52, 52, 1_000)
        val orgullo = chaptersOf("orgullo", 61, 14, 2_000)
        val frank = chaptersOf("frankenstein", 24, 24, 3_000)
        val odisea = chaptersOf("odisea", 24, 24, 4_000)
        val meditaciones = chaptersOf("meditaciones", 12, 12, 5_000)
        val guerra = chaptersOf("guerra", 13, 13, 6_000)
        chapters = mapOf("quijote" to quijote, "orgullo" to orgullo, "frankenstein" to frank, "odisea" to odisea, "meditaciones" to meditaciones, "guerra" to guerra)
        tracks = chapters.mapValues { (_, list) -> tracksOf(list) }
        books = listOf(
            book(
                "quijote", "Don Quijote de la Mancha", "Miguel de Cervantes", quijote, BookState.COMPLETED,
                importedDaysAgo = 20, playedHoursAgo = 2, listenedMs = 7 * 40 * minute + 15 * minute,
                description = "Un hidalgo de La Mancha lee tantos libros de caballerías que decide hacerse caballero andante.",
            ),
            book("orgullo", "Orgullo y prejuicio", "Jane Austen", orgullo, BookState.GENERATING, importedDaysAgo = 0),
            book("frankenstein", "Frankenstein", "Mary Shelley", frank, BookState.COMPLETED, importedDaysAgo = 9, playedHoursAgo = 30, listenedMs = 9 * 40 * minute),
            book("odisea", "La Odisea", "Homero", odisea, BookState.COMPLETED, importedDaysAgo = 4),
            book("meditaciones", "Meditaciones", "Marco Aurelio", meditaciones, BookState.COMPLETED, importedDaysAgo = 14, playedHoursAgo = 80, completed = true),
            book("guerra", "El arte de la guerra", "Sun Tzu", guerra, BookState.COMPLETED, importedDaysAgo = 30, playedHoursAgo = 200, completed = true),
        )
        jobs = listOf(ProcessingJob("orgullo", JobState.RUNNING, now, 1, null, 2_015, 3, 160f, null))
    }

    // ------------------------------------------------------------------ Repositorios de mentira

    private val bookRepository = object : BookRepository {
        override fun observeBooks(): Flow<List<Book>> = flowOf(books)
        override fun observeBook(id: String): Flow<Book?> = flowOf(books.firstOrNull { it.id == id })
        override fun observeChapters(bookId: String): Flow<List<Chapter>> = flowOf(chapters[bookId].orEmpty())
        override fun observeJob(bookId: String): Flow<ProcessingJob?> = flowOf(jobs.firstOrNull { it.bookId == bookId })
        override suspend fun getBook(id: String): Book? = books.firstOrNull { it.id == id }
        override suspend fun getChapters(bookId: String): List<Chapter> = chapters[bookId].orEmpty()
        override suspend fun getAnalysisReport(bookId: String) = AnalysisReport()
        override suspend fun getChapterText(chapterId: Long): List<Paragraph> = emptyList()
        override suspend fun bookIdsWithChapterMatching(query: String): Set<String> = emptySet()
        override suspend fun setFavorite(id: String, favorite: Boolean) = Unit
        override suspend fun setOcrPages(id: String, pages: IntRange?) = Unit
        override suspend fun updateMetadata(id: String, metadata: BookMetadata) = Unit
        override suspend fun updateVoice(id: String, voice: VoiceSettings) = Unit
        override suspend fun setCover(id: String, image: Uri) = Unit
        override suspend fun renameChapter(chapterId: Long, title: String) = Unit
        override suspend fun setChapterIncluded(bookId: String, chapterId: Long, included: Boolean) = Unit
        override suspend fun moveChapter(bookId: String, chapterId: Long, offset: Int) = Unit
        override suspend fun deleteAudio(id: String) = Unit
        override suspend fun deleteBook(id: String) = Unit
    }

    private val playbackRepository = object : PlaybackRepository {
        override fun observePlayableTracks(bookId: String): Flow<List<PlayableTrack>> = flowOf(tracks[bookId].orEmpty())
        override suspend fun playableTracks(bookId: String): List<PlayableTrack> = tracks[bookId].orEmpty()
        override fun observeLastPlayedBookId(): Flow<String?> = flowOf("quijote")
        override suspend fun getProgress(bookId: String): ListeningProgress? = books.firstOrNull { it.id == bookId }?.progress
        override suspend fun saveProgress(progress: ListeningProgress) = Unit
    }

    private val settingsRepository = object : SettingsRepository {
        override val settings: Flow<AppSettings> = this@StoreScreenshots.settings
        override suspend fun update(transform: (AppSettings) -> AppSettings) {
            this@StoreScreenshots.settings.value = transform(this@StoreScreenshots.settings.value)
        }
    }

    private val queue = object : ProcessingQueue {
        override fun analyze(bookId: String, restart: Boolean, ocr: Boolean) = Unit
        override fun cancelAnalysis(bookId: String) = Unit
        override fun observeQueue(): Flow<List<ProcessingJob>> = flowOf(jobs)
        override suspend fun generate(bookId: String) = Unit
        override suspend fun pause(bookId: String) = Unit
        override suspend fun resume(bookId: String) = Unit
        override suspend fun cancelGeneration(bookId: String) = Unit
        override suspend fun retry(bookId: String) = Unit
        override suspend fun prioritizeChapter(bookId: String, chapterId: Long) = Unit
        override suspend fun moveToFront(bookId: String) = Unit
        override suspend fun resumePending() = Unit
    }

    private val exports = object : AudiobookExports {
        override fun export(bookId: String, folder: Uri) = Unit
        override fun observe(bookId: String): Flow<ExportStatus?> = flowOf(null)
    }

    /** Reproductor «conectado» y sonando el capítulo VIII del Quijote, sin servicio de medios. */
    private fun playing(): PlaybackController {
        val controller = PlaybackController(context)
        val field = PlaybackController::class.java.getDeclaredField("_snapshot").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        (field.get(controller) as MutableStateFlow<PlaybackSnapshot>).value = PlaybackSnapshot(
            connected = true, bookId = "quijote", segmentId = 1_008L * 100 + 3, chapterId = 1_008, chapterTitle = "Capítulo VIII",
            isPlaying = true, playWhenReady = true,
        )
        return controller
    }

    // ------------------------------------------------------------------ Capturas

    private fun shoot(name: String, waitFor: String, scrollBy: Float = 0f, content: @Composable () -> Unit) {
        val theme = settings.value
        compose.setContent { NarraTheme(theme) { content() } }
        // Hay animaciones infinitas (progreso, reproducción) y esperas con el reloj de Android (la
        // búsqueda de la biblioteca): los dos relojes se avanzan a mano. Mientras, Coil carga las portadas.
        compose.mainClock.autoAdvance = false
        val target = hasText(waitFor, substring = true) or hasContentDescription(waitFor, substring = true)
        var ticks = 0
        while (compose.onAllNodes(target, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) {
            check(ticks++ < 150) { "No apareció «$waitFor»" }
            tick()
        }
        if (scrollBy > 0f) compose.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, scrollBy) }
        repeat(12) { tick() }
        // captureToImage espera un redibujado que con el reloj detenido no llega: se dibuja la ventana.
        lateinit var bitmap: Bitmap
        compose.runOnUiThread {
            val view = compose.activity.window.decorView
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
        }
        File(out).mkdirs()
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("Captura $name: ${bitmap.width}x${bitmap.height}")
    }

    private fun tick() {
        Thread.sleep(40)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        compose.mainClock.advanceTimeBy(100)
    }

    /** La barra inferior de la app (en NarraApp es privada). */
    @Composable
    private fun Tab(selected: String, content: @Composable () -> Unit) {
        val items = listOf(
            Triple("Inicio", Icons.Outlined.Home, Icons.Rounded.Home),
            Triple("Biblioteca", Icons.AutoMirrored.Outlined.LibraryBooks, Icons.AutoMirrored.Rounded.LibraryBooks),
            Triple("Ajustes", Icons.Outlined.Settings, Icons.Rounded.Settings),
        )
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar {
                    val rubric = NarraTheme.colors.rubric
                    items.forEach { (label, icon, selectedIcon) ->
                        NavigationBarItem(
                            selected = label == selected,
                            onClick = {},
                            icon = { Icon(if (label == selected) selectedIcon else icon, contentDescription = null) },
                            label = { Text(label) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent, selectedIconColor = rubric, selectedTextColor = rubric),
                        )
                    }
                }
            },
        ) { padding -> Box(Modifier.padding(padding)) { content() } }
    }

    @Test
    fun inicio() {
        val vm = HomeViewModel(bookRepository, PlaybackController(context), queue)
        shoot("1-inicio", waitFor = "Don Quijote") {
            Tab("Inicio") { HomeScreen({}, {}, {}, {}, {}, {}, viewModel = vm) }
        }
    }

    @Test
    fun biblioteca() {
        val vm = LibraryViewModel(bookRepository, settingsRepository)
        shoot("2-biblioteca", waitFor = "Orgullo") { Tab("Biblioteca") { LibraryScreen({}, {}, viewModel = vm) } }
    }

    @Test
    fun libro() {
        val vm = BookDetailsViewModel(
            SavedStateHandle(mapOf("bookId" to "quijote")), bookRepository, playbackRepository, PlaybackController(context), queue, exports, settingsRepository,
        )
        shoot("3-libro", waitFor = "Don Quijote") {
            BookDetailsScreen(onBack = {}, onOpenPlayer = {}, onOpenProcessing = {}, onReview = {}, onEdit = {}, onChooseVoice = {}, viewModel = vm)
        }
    }

    @Test
    fun reproductor() {
        val vm = PlayerViewModel(bookRepository, playbackRepository, settingsRepository, playing(), queue)
        // Baja un poco para que el botón de pausa entre completo.
        shoot("4-reproductor", waitFor = "Capítulo VIII", scrollBy = 120f) { PlayerScreen(onClose = {}, onOpenLibrary = {}, viewModel = vm) }
    }
}
