package app.narra.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import app.narra.domain.model.ErrorKind
import app.narra.domain.model.NarraException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer

/**
 * Une varios M4A con AAC en uno solo sin volver a codificar: copia las muestras tal cual y
 * desplaza sus tiempos. Solo sirve para archivos con el mismo formato (ver [canJoin]); el audio
 * de un capítulo creado con la misma voz siempre lo cumple.
 */
class M4aConcatenator {

    /** Formato de la pista de audio de [file]. */
    fun formatOf(file: File): MediaFormat = withExtractor(file) { extractor, track -> extractor.getTrackFormat(track) }

    /** Dos archivos se pueden unir si comparten códec, frecuencia, canales y configuración del códec. */
    fun canJoin(a: MediaFormat, b: MediaFormat): Boolean =
        a.getString(MediaFormat.KEY_MIME) == b.getString(MediaFormat.KEY_MIME) &&
            a.getInteger(MediaFormat.KEY_SAMPLE_RATE) == b.getInteger(MediaFormat.KEY_SAMPLE_RATE) &&
            a.getInteger(MediaFormat.KEY_CHANNEL_COUNT) == b.getInteger(MediaFormat.KEY_CHANNEL_COUNT) &&
            a.codecConfig() == b.codecConfig()

    /** Escribe en [target] el audio de [sources], en orden. Devuelve la duración total en ms. */
    suspend fun join(sources: List<File>, target: File): Long {
        require(sources.isNotEmpty()) { "Nada que unir" }
        val format = formatOf(sources.first())
        val muxer = try {
            MediaMuxer(target.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        } catch (e: IOException) {
            throw NarraException(ErrorKind.STORAGE_FULL, "no se pudo crear ${target.name}", e)
        }
        var started = false
        try {
            val track = muxer.addTrack(format)
            muxer.start()
            started = true
            val buffer = ByteBuffer.allocate(maxSampleSize(format))
            val info = MediaCodec.BufferInfo()
            val frameUs = AAC_FRAME_SAMPLES * US_PER_SECOND / format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var offsetUs = 0L
            for (source in sources) {
                currentCoroutineContext().ensureActive()
                offsetUs = withExtractor(source) { extractor, index ->
                    if (!canJoin(format, extractor.getTrackFormat(index))) {
                        throw NarraException(ErrorKind.ENCODING_FAILED, "${source.name} tiene otro formato")
                    }
                    extractor.selectTrack(index)
                    var endUs = offsetUs
                    while (true) {
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) break
                        val timeUs = offsetUs + extractor.sampleTime
                        val flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                        info.set(0, size, timeUs, flags)
                        muxer.writeSampleData(track, buffer, info)
                        endUs = timeUs + frameUs
                        extractor.advance()
                    }
                    endUs
                }
            }
            muxer.stop()
            started = false
            return offsetUs / US_PER_MS
        } catch (e: IllegalStateException) {
            throw NarraException(ErrorKind.ENCODING_FAILED, "no se pudo unir el audio", e)
        } finally {
            if (started) runCatching { muxer.stop() }
            muxer.release()
        }
    }

    private inline fun <T> withExtractor(file: File, block: (MediaExtractor, Int) -> T): T {
        val extractor = MediaExtractor()
        try {
            try {
                extractor.setDataSource(file.path)
            } catch (e: IOException) {
                throw NarraException(ErrorKind.FILE_MISSING, file.name, e)
            }
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty().startsWith(AUDIO_MIME_PREFIX)
            } ?: throw NarraException(ErrorKind.ENCODING_FAILED, "${file.name} no tiene audio")
            return block(extractor, track)
        } finally {
            extractor.release()
        }
    }

    private fun MediaFormat.codecConfig(): ByteBuffer? =
        if (containsKey(CODEC_CONFIG_KEY)) getByteBuffer(CODEC_CONFIG_KEY) else null

    private fun maxSampleSize(format: MediaFormat): Int =
        if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) maxOf(format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE), MIN_BUFFER_BYTES) else MIN_BUFFER_BYTES

    private companion object {
        const val AUDIO_MIME_PREFIX = "audio/"

        /** AudioSpecificConfig del AAC: si difiere, el reproductor no podría decodificar el resto. */
        const val CODEC_CONFIG_KEY = "csd-0"

        /** Muestras por trama AAC-LC: la duración de la última trama de cada archivo. */
        const val AAC_FRAME_SAMPLES = 1_024L
        const val US_PER_SECOND = 1_000_000L
        const val US_PER_MS = 1_000L

        /** Una trama AAC mono a 64 kbps ocupa unos cientos de bytes; con esto sobra. */
        const val MIN_BUFFER_BYTES = 64 * 1024
    }
}
