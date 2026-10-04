package app.narra.audio

import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import kotlin.math.roundToInt

/** Formato de las muestras de un WAV. */
data class WavFormat(
    val sampleRate: Int,
    val channels: Int,
    val bitsPerSample: Int,
    val isFloat: Boolean,
    val dataOffset: Long,
    val dataBytes: Long,
) {
    val bytesPerFrame: Int get() = channels * bitsPerSample / Byte.SIZE_BITS
    val frames: Long get() = if (bytesPerFrame > 0) dataBytes / bytesPerFrame else 0
}

/**
 * Lee los WAV que escriben los motores de voz y entrega las muestras en mono de 16 bits, por
 * bloques, sin cargar el archivo entero en memoria.
 */
object WavReader {

    fun readFormat(file: File): WavFormat = RandomAccessFile(file, "r").use { raf ->
        val header = ByteArray(RIFF_HEADER_BYTES)
        raf.readFully(header)
        if (header.ascii(0) != "RIFF" || header.ascii(WAVE_OFFSET) != "WAVE") throw IOException("No es un WAV")

        var format: WavFormat? = null
        var position = RIFF_HEADER_BYTES.toLong()
        val chunkHeader = ByteArray(CHUNK_HEADER_BYTES)
        while (position + CHUNK_HEADER_BYTES <= raf.length()) {
            raf.seek(position)
            raf.readFully(chunkHeader)
            val id = chunkHeader.ascii(0)
            val size = chunkHeader.uint32(ID_BYTES)
            val body = position + CHUNK_HEADER_BYTES
            when (id) {
                "fmt " -> {
                    val fmt = ByteArray(minOf(size, MAX_FMT_BYTES.toLong()).toInt())
                    raf.readFully(fmt)
                    format = parseFmt(fmt)
                }
                "data" -> {
                    val parsed = format ?: throw IOException("Falta el bloque fmt")
                    val available = raf.length() - body
                    // Algunos motores escriben el WAV en streaming y dejan el tamaño a 0 o al máximo.
                    val bytes = if (size == 0L || size > available) available else size
                    return parsed.copy(dataOffset = body, dataBytes = bytes - bytes % parsed.bytesPerFrame)
                }
            }
            position = body + size + (size and 1L)
        }
        throw IOException("El WAV no tiene muestras")
    }

    /** Llama a [block] con bloques de muestras mono de 16 bits; `count` es cuántas son válidas. */
    fun readMono16(file: File, format: WavFormat, block: (samples: ShortArray, count: Int) -> Unit) {
        val frameBytes = format.bytesPerFrame
        val input = ByteArray(FRAMES_PER_BLOCK * frameBytes)
        val output = ShortArray(FRAMES_PER_BLOCK)
        file.inputStream().buffered().use { stream ->
            stream.skipFully(format.dataOffset)
            var remaining = format.dataBytes
            while (remaining > 0) {
                val toRead = minOf(input.size.toLong(), remaining).toInt()
                val read = stream.readAtMost(input, toRead)
                if (read <= 0) break
                val frames = read / frameBytes
                for (frame in 0 until frames) output[frame] = mixFrame(input, frame * frameBytes, format)
                if (frames > 0) block(output, frames)
                remaining -= read
            }
        }
    }

    private fun parseFmt(fmt: ByteArray): WavFormat {
        var encoding = fmt.uint16(0)
        if (encoding == WAVE_FORMAT_EXTENSIBLE && fmt.size >= EXTENSIBLE_SUBFORMAT_OFFSET + 2) {
            encoding = fmt.uint16(EXTENSIBLE_SUBFORMAT_OFFSET)
        }
        val channels = fmt.uint16(CHANNELS_OFFSET)
        val sampleRate = fmt.uint32(SAMPLE_RATE_OFFSET).toInt()
        val bits = fmt.uint16(BITS_OFFSET)
        val isFloat = encoding == WAVE_FORMAT_FLOAT
        if (encoding != WAVE_FORMAT_PCM && !isFloat) throw IOException("Codificación WAV no admitida: $encoding")
        if (isFloat && bits != FLOAT_BITS) throw IOException("WAV en coma flotante de $bits bits")
        if (bits !in SUPPORTED_BITS || channels <= 0 || sampleRate <= 0) throw IOException("WAV no admitido: $bits bits, $channels canales")
        return WavFormat(sampleRate, channels, bits, isFloat, dataOffset = 0, dataBytes = 0)
    }

    /** Media de los canales de un fotograma, convertida a 16 bits con signo. */
    private fun mixFrame(bytes: ByteArray, offset: Int, format: WavFormat): Short {
        val sampleBytes = format.bitsPerSample / Byte.SIZE_BITS
        var sum = 0
        for (channel in 0 until format.channels) {
            sum += sampleAt(bytes, offset + channel * sampleBytes, format)
        }
        return (sum / format.channels).toShort()
    }

    private fun sampleAt(bytes: ByteArray, at: Int, format: WavFormat): Int = when {
        format.isFloat -> {
            val bits = (bytes[at].toInt() and 0xFF) or ((bytes[at + 1].toInt() and 0xFF) shl 8) or
                ((bytes[at + 2].toInt() and 0xFF) shl 16) or (bytes[at + 3].toInt() shl 24)
            (Float.fromBits(bits).coerceIn(-1f, 1f) * Short.MAX_VALUE).roundToInt()
        }
        // 8 bits va sin signo, centrado en 128.
        format.bitsPerSample == 8 -> ((bytes[at].toInt() and 0xFF) - UNSIGNED_8_BIT_CENTER) shl 8
        // En 16, 24 y 32 bits los dos bytes más significativos son los últimos.
        else -> {
            val high = at + format.bitsPerSample / Byte.SIZE_BITS - 1
            (bytes[high].toInt() shl 8) or (bytes[high - 1].toInt() and 0xFF)
        }
    }

    private fun ByteArray.ascii(at: Int) = String(this, at, ID_BYTES, Charsets.US_ASCII)
    private fun ByteArray.uint16(at: Int) = (this[at].toInt() and 0xFF) or ((this[at + 1].toInt() and 0xFF) shl 8)
    private fun ByteArray.uint32(at: Int): Long = uint16(at).toLong() or (uint16(at + 2).toLong() shl 16)

    private fun InputStream.skipFully(bytes: Long) {
        var left = bytes
        while (left > 0) {
            val skipped = skip(left)
            if (skipped <= 0) {
                if (read() < 0) throw EOFException()
                left--
            } else {
                left -= skipped
            }
        }
    }

    private fun InputStream.readAtMost(buffer: ByteArray, length: Int): Int {
        var total = 0
        while (total < length) {
            val read = read(buffer, total, length - total)
            if (read < 0) break
            total += read
        }
        return total
    }

    private const val RIFF_HEADER_BYTES = 12
    private const val WAVE_OFFSET = 8
    private const val CHUNK_HEADER_BYTES = 8
    private const val ID_BYTES = 4
    private const val MAX_FMT_BYTES = 64
    private const val CHANNELS_OFFSET = 2
    private const val SAMPLE_RATE_OFFSET = 4
    private const val BITS_OFFSET = 14
    private const val EXTENSIBLE_SUBFORMAT_OFFSET = 24
    private const val WAVE_FORMAT_PCM = 1
    private const val WAVE_FORMAT_FLOAT = 3
    private const val WAVE_FORMAT_EXTENSIBLE = 0xFFFE
    private const val FLOAT_BITS = 32
    private val SUPPORTED_BITS = setOf(8, 16, 24, 32)
    private const val UNSIGNED_8_BIT_CENTER = 128
    private const val FRAMES_PER_BLOCK = 4_096
}
