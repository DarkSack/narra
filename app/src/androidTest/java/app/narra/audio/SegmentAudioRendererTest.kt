package app.narra.audio

import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.narra.data.files.BookStorage
import app.narra.domain.model.Paragraph
import app.narra.domain.model.SegmentKind
import app.narra.domain.model.Voice
import app.narra.domain.model.VoiceSettings
import app.narra.domain.tts.TtsSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Comprueba en un dispositivo real la parte de audio de la generación: WAV del motor → AAC en
 * M4A con las pausas correctas. El motor de voz se sustituye por uno que genera un tono, para no
 * depender de las voces instaladas.
 */
@RunWith(AndroidJUnit4::class)
class SegmentAudioRendererTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val renderer = SegmentAudioRenderer(BookStorage(context))
    private lateinit var output: File

    @Before
    fun setUp() {
        output = File(context.cacheDir, "test-audio/segment.m4a").apply {
            parentFile?.deleteRecursively()
            parentFile?.mkdirs()
        }
    }

    @Test
    fun codificaLosParrafosConSusPausas() = runTest {
        val session = ToneSession(sampleRate = 22_050)
        val voice = VoiceSettings(paragraphPauseMs = 400, chapterPauseMs = 1_000)
        val paragraphs = listOf(
            Paragraph("Capítulo 1", SegmentKind.HEADING),
            Paragraph("Nadie recordaba cuándo se había encendido el faro.", SegmentKind.NARRATION),
            Paragraph("—Aquí las palabras pesan más —le dijo.", SegmentKind.DIALOGUE),
        )

        val result = renderer.render(session, paragraphs, voice, endsChapter = true, target = output)

        // Título + pausa doble, párrafo + pausa, último párrafo + pausa de fin de capítulo.
        val expected = session.totalMs + 2 * 400 + 400 + 1_000
        assertTrue("duración ${result.durationMs} ≈ $expected", abs(result.durationMs - expected) <= TOLERANCE_MS)
        assertEquals(output.length(), result.sizeBytes)
        assertM4a(output, expected)
        assertFalse(File(output.parentFile, "segment.m4a.part").exists())
    }

    @Test
    fun adaptaLaFrecuenciaSiElMotorCambia() = runTest {
        val session = ToneSession(sampleRate = 22_050, secondRate = 16_000)
        val voice = VoiceSettings(paragraphPauseMs = 0, chapterPauseMs = 0)
        val paragraphs = listOf(Paragraph("Primera frase de prueba."), Paragraph("Segunda frase, a otra frecuencia."))

        val result = renderer.render(session, paragraphs, voice, endsChapter = false, target = output)

        assertTrue("duración ${result.durationMs} ≈ ${session.totalMs}", abs(result.durationMs - session.totalMs) <= TOLERANCE_MS)
        assertM4a(output, session.totalMs)
    }

    @Test
    fun alCancelarNoDejaArchivosAMedias() = runTest {
        val session = ToneSession(sampleRate = 22_050, hangOnCall = 2)
        val work = async {
            renderer.render(session, listOf(Paragraph("Uno."), Paragraph("Dos.")), VoiceSettings(), endsChapter = false, target = output)
        }
        session.reachedHang.await()
        work.cancel()
        val cancelled = runCatching { work.await() }.exceptionOrNull()

        assertTrue(cancelled is CancellationException)
        assertFalse(output.exists())
        assertFalse(File(output.parentFile, "segment.m4a.part").exists())
    }

    private fun assertM4a(file: File, expectedMs: Long) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            assertEquals(1, extractor.trackCount)
            val format = extractor.getTrackFormat(0)
            assertEquals(MediaFormat.MIMETYPE_AUDIO_AAC, format.getString(MediaFormat.KEY_MIME))
            val durationMs = format.getLong(MediaFormat.KEY_DURATION) / US_PER_MS
            assertTrue("contenedor $durationMs ≈ $expectedMs", abs(durationMs - expectedMs) <= TOLERANCE_MS)
        } finally {
            extractor.release()
        }
    }

    /** Motor falso: escribe un tono de 50 ms por carácter. */
    private class ToneSession(
        private val sampleRate: Int,
        private val secondRate: Int = sampleRate,
        private val hangOnCall: Int = -1,
    ) : TtsSession {
        override val maxInputChars = MAX_INPUT
        var totalMs = 0L
        private var calls = 0
        val reachedHang = CompletableDeferred<Unit>()

        override suspend fun voices(): List<Voice> = emptyList()

        override suspend fun synthesize(text: String, voice: VoiceSettings, target: File) {
            calls++
            if (calls == hangOnCall) {
                reachedHang.complete(Unit)
                awaitCancellation()
            }
            val rate = if (calls == 1) sampleRate else secondRate
            val durationMs = text.length * MS_PER_CHAR
            totalMs += durationMs
            writeTone(target, rate, durationMs)
        }

        override suspend fun speak(text: String, voice: VoiceSettings) = Unit
        override fun stop() = Unit
        override fun close() = Unit

        private fun writeTone(file: File, rate: Int, durationMs: Long) {
            val frames = (rate * durationMs / MS_PER_SECOND).toInt()
            val data = ByteBuffer.allocate(frames * 2).order(ByteOrder.LITTLE_ENDIAN)
            repeat(frames) { data.putShort((sin(2 * PI * TONE_HZ * it / rate) * AMPLITUDE).toInt().toShort()) }
            val header = ByteBuffer.allocate(WAV_HEADER).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray()); putInt(WAV_HEADER - 8 + frames * 2); put("WAVE".toByteArray())
                put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(rate); putInt(rate * 2); putShort(2); putShort(16)
                put("data".toByteArray()); putInt(frames * 2)
            }
            file.outputStream().use {
                it.write(header.array())
                it.write(data.array())
            }
        }

        private companion object {
            const val MAX_INPUT = 4_000
            const val MS_PER_CHAR = 50L
            const val MS_PER_SECOND = 1_000L
            const val TONE_HZ = 220.0
            const val AMPLITUDE = 8_000.0
            const val WAV_HEADER = 44
        }
    }

    private companion object {
        /** El AAC añade unos milisegundos de relleno al principio y al final. */
        const val TOLERANCE_MS = 120L
        const val US_PER_MS = 1_000L
    }
}
