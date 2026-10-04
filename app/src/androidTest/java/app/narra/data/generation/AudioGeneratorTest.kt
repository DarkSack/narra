package app.narra.data.generation

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.narra.audio.SegmentAudioRenderer
import app.narra.data.db.BookEntity
import app.narra.data.db.ChapterEntity
import app.narra.data.db.NarraDatabase
import app.narra.data.db.ProcessingJobEntity
import app.narra.data.db.SegmentEntity
import app.narra.data.db.toJson
import app.narra.data.files.BookStorage
import app.narra.data.repository.BookStatsUpdater
import app.narra.domain.model.BookState
import app.narra.domain.model.ErrorKind
import app.narra.domain.model.JobState
import app.narra.domain.model.NarraException
import app.narra.domain.model.Paragraph
import app.narra.domain.model.SegmentState
import app.narra.domain.model.TtsCapabilities
import app.narra.domain.model.TtsProviderInfo
import app.narra.domain.model.Voice
import app.narra.domain.model.VoiceSettings
import app.narra.domain.tts.TtsProvider
import app.narra.domain.tts.TtsProviders
import app.narra.domain.tts.TtsSession
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** La cola de generación de punta a punta, con Room en memoria y un motor de voz falso. */
@RunWith(AndroidJUnit4::class)
class AudioGeneratorTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: NarraDatabase
    private lateinit var storage: BookStorage
    private val engine = FakeProvider()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, NarraDatabase::class.java).build()
        storage = BookStorage(context)
    }

    @After
    fun tearDown() {
        db.close()
        File(context.filesDir, "books/$BOOK").deleteRecursively()
    }

    private fun generator() = AudioGenerator(db, storage, BookStatsUpdater(db), engine, SegmentAudioRenderer(storage))

    @Test
    fun generaTodoElLibroYLoMarcaComoTerminado() = runTest {
        seed(chapters = 2, segmentsPerChapter = 2)
        val events = mutableListOf<GenerationEvent>()

        generator().runQueue { events += it }

        val book = db.bookDao().get(BOOK)!!
        assertEquals(BookState.COMPLETED, book.state)
        assertEquals(4, book.segmentsAvailable)
        assertEquals(2, book.chaptersAvailable)
        assertTrue(book.generatedDurationMs > 0)
        assertNull("el trabajo terminado sale de la cola", db.jobDao().get(BOOK))
        assertTrue(events.last() is GenerationEvent.Finished)
        assertEquals(4, events.count { it is GenerationEvent.Progress })
        db.audioTrackDao().forBook(BOOK).forEach { assertTrue(File(it.path).length() > 0) }
    }

    @Test
    fun elCapituloPrioritarioSeGeneraPrimero() = runTest {
        val chapterIds = seed(chapters = 3, segmentsPerChapter = 1)
        db.jobDao().setPriority(BOOK, chapterIds[2])
        val order = mutableListOf<Int>()

        generator().runQueue { if (it is GenerationEvent.Progress) order += it.chapterNumber }

        assertEquals(listOf(3, 1, 2), order)
    }

    @Test
    fun unaVozNoDisponibleDetieneElLibroConSuCausa() = runTest {
        seed(chapters = 1, segmentsPerChapter = 2)
        engine.failure = NarraException(ErrorKind.VOICE_UNAVAILABLE, "voz")
        val events = mutableListOf<GenerationEvent>()

        generator().runQueue { events += it }

        val book = db.bookDao().get(BOOK)!!
        assertEquals(BookState.ERROR, book.state)
        assertEquals(ErrorKind.VOICE_UNAVAILABLE, book.errorKind)
        assertEquals(JobState.FAILED, db.jobDao().get(BOOK)?.state)
        // El segmento interrumpido vuelve a la cola sin gastar un intento.
        val segments = db.segmentDao().forChapter(db.chapterDao().getAll(BOOK).first().id)
        assertTrue(segments.all { it.state == SegmentState.PENDING && it.attempts == 0 })
        assertEquals(ErrorKind.VOICE_UNAVAILABLE, (events.last() as GenerationEvent.Failed).kind)
    }

    @Test
    fun unLibroEnPausaNoSeProcesa() = runTest {
        seed(chapters = 1, segmentsPerChapter = 1)
        db.jobDao().setState(BOOK, JobState.PAUSED)

        generator().runQueue { }

        assertEquals(0, db.bookDao().get(BOOK)!!.segmentsAvailable)
        assertEquals(0, engine.calls)
    }

    private suspend fun seed(chapters: Int, segmentsPerChapter: Int): List<Long> {
        db.bookDao().insert(
            BookEntity(
                id = BOOK,
                title = "Libro de prueba",
                sourceFileName = "prueba.pdf",
                fileSizeBytes = 1,
                importedAt = 0,
                state = BookState.GENERATING,
                voiceProviderId = FakeProvider.ID,
                paragraphPauseMs = 100,
                chapterPauseMs = 200,
            ),
        )
        val ids = db.chapterDao().insertAll(
            (0 until chapters).map { ChapterEntity(bookId = BOOK, orderIndex = it, title = "Capítulo ${it + 1}", startPage = it, endPage = it) },
        )
        db.segmentDao().insertAll(
            ids.flatMap { chapterId ->
                (0 until segmentsPerChapter).map { index ->
                    val paragraphs = listOf(Paragraph("Texto del segmento $index."))
                    SegmentEntity(bookId = BOOK, chapterId = chapterId, orderIndex = index, paragraphsJson = paragraphs.toJson(), charCount = 20)
                }
            },
        )
        db.jobDao().upsert(ProcessingJobEntity(bookId = BOOK, state = JobState.QUEUED, enqueuedAt = 0))
        BookStatsUpdater(db).refreshBook(BOOK)
        return ids
    }

    private class FakeProvider : TtsProviders, TtsProvider, TtsSession {
        var failure: NarraException? = null
        var calls = 0

        override val all: List<TtsProvider> get() = listOf(this)
        override fun get(id: String): TtsProvider? = this.takeIf { id == ID }

        override val info = TtsProviderInfo(ID, "Falso", "Para pruebas", TtsCapabilities(true, true, false, false, MAX_INPUT))
        override suspend fun open(): TtsSession = this

        override val maxInputChars = MAX_INPUT
        override suspend fun voices(): List<Voice> = emptyList()
        override suspend fun speak(text: String, voice: VoiceSettings) = Unit
        override fun stop() = Unit
        override fun close() = Unit

        override suspend fun synthesize(text: String, voice: VoiceSettings, target: File) {
            calls++
            failure?.let { throw it }
            val frames = RATE / 10
            val data = ByteBuffer.allocate(44 + frames * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray()); putInt(36 + frames * 2); put("WAVE".toByteArray())
                put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(RATE); putInt(RATE * 2); putShort(2); putShort(16)
                put("data".toByteArray()); putInt(frames * 2)
                repeat(frames) { putShort((it % 100 * 50).toShort()) }
            }
            target.parentFile?.mkdirs()
            target.writeBytes(data.array())
        }

        companion object {
            const val ID = "fake"
            const val MAX_INPUT = 4_000
            const val RATE = 22_050
        }
    }

    private companion object {
        const val BOOK = "book-test"
    }
}
