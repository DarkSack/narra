package app.narra.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import app.narra.domain.model.ErrorKind
import app.narra.domain.model.NarraException
import java.io.File
import java.io.IOException
import java.nio.ByteOrder

/**
 * Codifica PCM mono de 16 bits a AAC-LC dentro de un M4A, a medida que llegan las muestras.
 * No es seguro entre hilos: un codificador por segmento.
 */
class AacEncoder(target: File, val sampleRate: Int, bitrate: Int) : AutoCloseable {
    private val codec: MediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
    private val muxer: MediaMuxer = try {
        MediaMuxer(target.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    } catch (e: IOException) {
        codec.release()
        throw NarraException(ErrorKind.ENCODING_FAILED, "no se pudo crear ${target.name}", e)
    }
    private val info = MediaCodec.BufferInfo()
    private var track = -1
    private var muxerStarted = false
    private var finished = false
    private var framesQueued = 0L
    private val silence = ShortArray(SILENCE_BLOCK_FRAMES)

    init {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_INPUT_BYTES)
        }
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
        } catch (e: RuntimeException) {
            close()
            throw NarraException(ErrorKind.ENCODING_FAILED, "configure ${sampleRate}Hz ${bitrate}bps", e)
        }
    }

    /** Duración de lo escrito hasta ahora. */
    val durationMs: Long get() = framesQueued * MS_PER_SECOND / sampleRate

    fun write(samples: ShortArray, count: Int) {
        check(!finished) { "El codificador ya terminó" }
        var offset = 0
        var waits = 0
        while (offset < count) {
            val index = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
            if (index < 0) {
                if (++waits > MAX_DRAIN_ATTEMPTS) throw NarraException(ErrorKind.ENCODING_FAILED, "el codificador no acepta datos")
                drain(endOfStream = false)
                continue
            }
            waits = 0
            val buffer = codec.getInputBuffer(index) ?: throw NarraException(ErrorKind.ENCODING_FAILED, "sin búfer de entrada")
            buffer.clear()
            val frames = minOf(count - offset, buffer.remaining() / BYTES_PER_SAMPLE)
            buffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(samples, offset, frames)
            codec.queueInputBuffer(index, 0, frames * BYTES_PER_SAMPLE, presentationTimeUs(), 0)
            framesQueued += frames
            offset += frames
            drain(endOfStream = false)
        }
    }

    fun writeSilence(durationMs: Int) {
        var remaining = durationMs.toLong() * sampleRate / MS_PER_SECOND
        while (remaining > 0) {
            val frames = minOf(remaining, silence.size.toLong()).toInt()
            write(silence, frames)
            remaining -= frames
        }
    }

    /** Cierra el flujo y el contenedor. Devuelve la duración total. */
    fun finish(): Long {
        check(!finished) { "El codificador ya terminó" }
        var index: Int
        var attempts = 0
        do {
            index = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
            if (index < 0) drain(endOfStream = false)
            if (++attempts > MAX_DRAIN_ATTEMPTS) throw NarraException(ErrorKind.ENCODING_FAILED, "sin búfer para el final")
        } while (index < 0)
        codec.queueInputBuffer(index, 0, 0, presentationTimeUs(), MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        drain(endOfStream = true)
        finished = true
        if (!muxerStarted) throw NarraException(ErrorKind.ENCODING_FAILED, "sin datos")
        muxer.stop()
        muxerStarted = false
        return durationMs
    }

    private fun presentationTimeUs(): Long = framesQueued * US_PER_SECOND / sampleRate

    /** Pasa al contenedor lo que el codificador tenga listo; con [endOfStream], hasta el final. */
    private fun drain(endOfStream: Boolean) {
        var idleAttempts = 0
        while (true) {
            val index = codec.dequeueOutputBuffer(info, if (endOfStream) DEQUEUE_TIMEOUT_US else 0)
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) return
                    if (++idleAttempts > MAX_DRAIN_ATTEMPTS) throw NarraException(ErrorKind.ENCODING_FAILED, "el codificador no termina")
                }
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    check(!muxerStarted) { "El formato cambió dos veces" }
                    track = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    muxerStarted = true
                }
                index >= 0 -> {
                    val buffer = codec.getOutputBuffer(index) ?: throw NarraException(ErrorKind.ENCODING_FAILED, "sin búfer de salida")
                    val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (!isConfig && info.size > 0 && muxerStarted) {
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        muxer.writeSampleData(track, buffer, info)
                    }
                    codec.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    override fun close() {
        runCatching { codec.stop() }
        runCatching { codec.release() }
        if (muxerStarted) runCatching { muxer.stop() }
        runCatching { muxer.release() }
    }

    private companion object {
        const val BYTES_PER_SAMPLE = 2
        const val MAX_INPUT_BYTES = 16_384
        const val SILENCE_BLOCK_FRAMES = 4_096
        const val DEQUEUE_TIMEOUT_US = 10_000L
        /** Con [DEQUEUE_TIMEOUT_US], unos 10 s sin respuesta del codificador. */
        const val MAX_DRAIN_ATTEMPTS = 1_000
        const val MS_PER_SECOND = 1_000L
        const val US_PER_SECOND = 1_000_000L
    }
}
