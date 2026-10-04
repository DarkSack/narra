package app.narra.audio

import app.narra.data.files.BookStorage
import app.narra.domain.model.ErrorKind
import app.narra.domain.model.NarraException
import app.narra.domain.model.Paragraph
import app.narra.domain.model.SegmentKind
import app.narra.domain.model.VoiceSettings
import app.narra.domain.tts.TtsSession
import app.narra.text.SentenceSplitter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject

/** Resultado de convertir un segmento en audio. */
data class RenderedAudio(val durationMs: Long, val sizeBytes: Long)

/**
 * Convierte los párrafos de un segmento en un M4A: cada párrafo se sintetiza a un WAV temporal,
 * se codifica en cuanto existe y se borra. Entre párrafos se inserta silencio según su tipo, así
 * las pausas no dependen de la puntuación ni del motor de voz.
 */
class SegmentAudioRenderer @Inject constructor(private val storage: BookStorage) {

    suspend fun render(
        session: TtsSession,
        paragraphs: List<Paragraph>,
        voice: VoiceSettings,
        endsChapter: Boolean,
        target: File,
    ): RenderedAudio {
        val workDir = File(storage.tempDir(), UUID.randomUUID().toString())
        val partial = File(target.parentFile, "${target.name}.part")
        var encoder: AacEncoder? = null
        try {
            withContext(Dispatchers.IO) {
                workDir.mkdirs()
                target.parentFile?.mkdirs()
            }
            val spoken = paragraphs.filter { it.text.isNotBlank() }
            spoken.forEachIndexed { paragraphIndex, paragraph ->
                pieces(paragraph.text, session.maxInputChars).forEachIndexed { pieceIndex, piece ->
                    val wav = File(workDir, "$paragraphIndex-$pieceIndex.wav")
                    session.synthesize(piece, voice, wav)
                    withContext(Dispatchers.IO) {
                        val format = readFormat(wav)
                        val output = encoder ?: AacEncoder(partial, format.sampleRate, voice.quality.bitrate).also { encoder = it }
                        // El codificador usa la frecuencia del primer WAV; si el motor cambia, se adapta.
                        val converter = if (format.sampleRate == output.sampleRate) null else LinearResampler(format.sampleRate, output.sampleRate)
                        WavReader.readMono16(wav, format) { samples, count ->
                            ensureActive()
                            if (converter == null) output.write(samples, count) else converter.process(samples, count, output::write)
                        }
                        wav.delete()
                    }
                }
                val isLast = paragraphIndex == spoken.lastIndex
                val pause = when {
                    isLast && endsChapter -> voice.chapterPauseMs
                    paragraph.kind == SegmentKind.HEADING -> voice.paragraphPauseMs * HEADING_PAUSE_FACTOR
                    else -> voice.paragraphPauseMs
                }
                encoder?.writeSilence(pause)
            }
            val output = encoder ?: throw NarraException(ErrorKind.SYNTHESIS_FAILED, "segmento sin texto")
            return withContext(Dispatchers.IO) {
                val durationMs = output.finish()
                output.close()
                encoder = null
                if (!partial.renameTo(target)) throw NarraException(ErrorKind.STORAGE_FULL, "no se pudo guardar ${target.name}")
                RenderedAudio(durationMs, target.length())
            }
        } finally {
            withContext(Dispatchers.IO + NonCancellable) {
                encoder?.close()
                partial.delete()
                workDir.deleteRecursively()
            }
        }
    }

    private fun readFormat(wav: File): WavFormat = try {
        WavReader.readFormat(wav)
    } catch (e: IOException) {
        throw NarraException(ErrorKind.SYNTHESIS_FAILED, "WAV ilegible: ${e.message}", e)
    }

    /** Parte un párrafo que no cabe en una sola llamada al motor, por frases. */
    private fun pieces(text: String, maxChars: Int): List<String> {
        if (text.length <= maxChars) return listOf(text)
        val result = ArrayList<String>()
        var start = SentenceSplitter.skipWhitespace(text, 0)
        while (start < text.length) {
            val end = SentenceSplitter.sentenceEnd(text, start, maxChars)
            text.substring(start, end).trim().takeIf { it.isNotEmpty() }?.let(result::add)
            start = SentenceSplitter.skipWhitespace(text, end)
        }
        return result
    }

    private companion object {
        /** Tras un título, el doble de pausa que entre párrafos. */
        const val HEADING_PAUSE_FACTOR = 2
    }
}
