package app.narra.audio

import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** Unir fragmentos de un capítulo en un solo M4A, sin volver a codificar. */
@RunWith(AndroidJUnit4::class)
class M4aConcatenatorTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val concatenator = M4aConcatenator()
    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = File(context.cacheDir, "test-join").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    @Test
    fun uneLosFragmentosConSuDuracion() = runTest {
        val parts = listOf(1_200, 800, 1_500).mapIndexed { index, ms -> tone(File(dir, "$index.m4a"), SAMPLE_RATE, ms) }
        val target = File(dir, "capitulo.m4a")

        val durationMs = concatenator.join(parts, target)

        val expected = 1_200 + 800 + 1_500
        assertTrue("duración $durationMs ≈ $expected", abs(durationMs - expected) <= TOLERANCE_MS)
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(target.path)
            val format = extractor.getTrackFormat(0)
            assertEquals(MediaFormat.MIMETYPE_AUDIO_AAC, format.getString(MediaFormat.KEY_MIME))
            assertEquals(SAMPLE_RATE, format.getInteger(MediaFormat.KEY_SAMPLE_RATE))
            val containerMs = format.getLong(MediaFormat.KEY_DURATION) / US_PER_MS
            assertTrue("contenedor $containerMs ≈ $expected", abs(containerMs - expected) <= TOLERANCE_MS)
        } finally {
            extractor.release()
        }
    }

    @Test
    fun noUneFormatosDistintos() {
        val a = concatenator.formatOf(tone(File(dir, "a.m4a"), SAMPLE_RATE, 500))
        val b = concatenator.formatOf(tone(File(dir, "b.m4a"), OTHER_SAMPLE_RATE, 500))
        val c = concatenator.formatOf(tone(File(dir, "c.m4a"), SAMPLE_RATE, 700))

        assertFalse(concatenator.canJoin(a, b))
        assertTrue(concatenator.canJoin(a, c))
    }

    private fun tone(target: File, sampleRate: Int, durationMs: Int): File {
        val frames = sampleRate * durationMs / MS_PER_SECOND
        val samples = ShortArray(frames) { i -> (sin(2 * PI * TONE_HZ * i / sampleRate) * AMPLITUDE).toInt().toShort() }
        AacEncoder(target, sampleRate, BITRATE).use { encoder ->
            encoder.write(samples, samples.size)
            encoder.finish()
        }
        return target
    }

    private companion object {
        const val SAMPLE_RATE = 22_050
        const val OTHER_SAMPLE_RATE = 16_000
        const val BITRATE = 48_000
        const val TONE_HZ = 440.0
        const val AMPLITUDE = 8_000.0
        const val MS_PER_SECOND = 1_000
        const val US_PER_MS = 1_000L

        /** El AAC añade una trama de relleno por archivo (≈ 46 ms a 22 kHz). */
        const val TOLERANCE_MS = 200L
    }
}
