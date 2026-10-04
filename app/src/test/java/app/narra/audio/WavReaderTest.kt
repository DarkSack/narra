package app.narra.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavReaderTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun `lee PCM de 16 bits mono`() {
        val samples = shortArrayOf(0, 1000, -1000, Short.MAX_VALUE, Short.MIN_VALUE)
        val file = wav(sampleRate = 22_050, channels = 1, bits = 16, data = pcm16(samples))

        val format = WavReader.readFormat(file)

        assertEquals(22_050, format.sampleRate)
        assertEquals(samples.size.toLong(), format.frames)
        assertEquals(samples.toList(), readAll(file, format))
    }

    @Test
    fun `mezcla el estéreo a mono`() {
        val file = wav(sampleRate = 16_000, channels = 2, bits = 16, data = pcm16(shortArrayOf(1000, 3000, -2000, 0)))

        assertEquals(listOf<Short>(2000, -1000), readAll(file, WavReader.readFormat(file)))
    }

    @Test
    fun `convierte 8 bits sin signo y coma flotante`() {
        val eight = wav(sampleRate = 8_000, channels = 1, bits = 8, data = byteArrayOf(128.toByte(), 255.toByte(), 0))
        assertEquals(listOf<Short>(0, 32512, -32768), readAll(eight, WavReader.readFormat(eight)))

        val floats = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putFloat(0.5f).putFloat(-1f).array()
        val float = wav(sampleRate = 24_000, channels = 1, bits = 32, data = floats, encoding = 3)
        assertEquals(listOf<Short>(16384, -32767), readAll(float, WavReader.readFormat(float)))
    }

    @Test
    fun `admite tamaño de datos sin rellenar y bloques extra con relleno`() {
        val samples = shortArrayOf(5, 6, 7)
        val file = wav(sampleRate = 22_050, channels = 1, bits = 16, data = pcm16(samples), declaredDataSize = 0, extraChunk = true)

        val format = WavReader.readFormat(file)

        assertEquals(3L, format.frames)
        assertEquals(samples.toList(), readAll(file, format))
    }

    @Test(expected = IOException::class)
    fun `rechaza lo que no es WAV`() {
        val file = folder.newFile("no.wav").apply { writeText("esto no es audio") }
        WavReader.readFormat(file)
    }

    @Test
    fun `el remuestreo conserva la duración y la forma`() {
        val input = ShortArray(2_205) { (it * 10).toShort() }
        val output = ArrayList<Short>()
        val resampler = LinearResampler(22_050, 16_000)
        // En dos bloques, para comprobar la continuidad entre ellos.
        resampler.process(input.copyOfRange(0, 1_000), 1_000) { samples, count -> repeat(count) { output += samples[it] } }
        resampler.process(input.copyOfRange(1_000, input.size), input.size - 1_000) { samples, count -> repeat(count) { output += samples[it] } }

        val expected = input.size * 16_000 / 22_050
        assertTrue("${output.size} ≈ $expected", kotlin.math.abs(output.size - expected) <= 1)
        // Una rampa sigue siendo una rampa creciente.
        assertTrue(output.zipWithNext().all { (a, b) -> b >= a })
        assertEquals(0, output.first().toInt())
    }

    private fun readAll(file: File, format: WavFormat): List<Short> {
        val result = ArrayList<Short>()
        WavReader.readMono16(file, format) { samples, count -> repeat(count) { result += samples[it] } }
        return result
    }

    private fun pcm16(samples: ShortArray): ByteArray =
        ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN).apply { samples.forEach { putShort(it) } }.array()

    private fun wav(
        sampleRate: Int,
        channels: Int,
        bits: Int,
        data: ByteArray,
        encoding: Int = 1,
        declaredDataSize: Int = data.size,
        extraChunk: Boolean = false,
    ): File {
        val out = ByteArrayOutputStream()
        fun int(value: Int) = out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array())
        fun short(value: Int) = out.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort()).array())
        out.write("RIFF".toByteArray())
        int(0)
        out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray())
        int(16)
        short(encoding)
        short(channels)
        int(sampleRate)
        int(sampleRate * channels * bits / 8)
        short(channels * bits / 8)
        short(bits)
        if (extraChunk) {
            // Bloque de tamaño impar: el lector debe saltar también el byte de relleno.
            out.write("LIST".toByteArray())
            int(3)
            out.write(byteArrayOf(1, 2, 3, 0))
        }
        out.write("data".toByteArray())
        int(declaredDataSize)
        out.write(data)
        return folder.newFile().apply { writeBytes(out.toByteArray()) }
    }
}
