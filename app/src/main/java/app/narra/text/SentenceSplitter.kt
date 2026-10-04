package app.narra.text

import kotlin.math.min

/** Rango `[start, end)` dentro del texto completo del libro. */
data class Span(val start: Int, val end: Int) {
    val length: Int get() = end - start
}

/**
 * Corta el texto en frases para enviarlas una a una al motor de voz.
 * Frases cortas = menos latencia al empezar y reanudar casi exacto tras una pausa.
 */
object SentenceSplitter {
    /** Muy por debajo de `TextToSpeech.getMaxSpeechInputLength()` (4000). */
    const val MAX_SENTENCE_CHARS = 600

    private const val TERMINATORS = ".!?…"
    private const val CLOSERS = "\"'”’»)]}"

    /**
     * Frases consecutivas a partir de [from] hasta acumular unos [maxChars] caracteres
     * (siempre devuelve al menos una si queda texto).
     */
    fun batch(
        text: CharSequence,
        from: Int,
        maxChars: Int,
        maxSentenceChars: Int = MAX_SENTENCE_CHARS,
    ): List<Span> {
        val spans = mutableListOf<Span>()
        var start = skipWhitespace(text, from.coerceIn(0, text.length))
        var total = 0
        while (start < text.length && (spans.isEmpty() || total < maxChars)) {
            val end = sentenceEnd(text, start, maxSentenceChars)
            spans += Span(start, end)
            total += end - start
            start = skipWhitespace(text, end)
        }
        return spans
    }

    /** Fin (exclusivo) de la frase que empieza en [start]. */
    fun sentenceEnd(text: CharSequence, start: Int, maxSentenceChars: Int = MAX_SENTENCE_CHARS): Int {
        require(maxSentenceChars > 0)
        val limit = min(text.length, start + maxSentenceChars)
        var i = start
        while (i < limit) {
            if (text[i] in TERMINATORS) {
                var end = i + 1
                while (end < text.length && (text[end] in TERMINATORS || text[end] in CLOSERS)) end++
                // "3.14" o "www.ejemplo.com" no terminan frase: hace falta un espacio después.
                if (end >= text.length || text[end].isWhitespace()) return end
                i = end
            } else {
                i++
            }
        }
        if (limit >= text.length) return text.length

        // Frase demasiado larga: cortar en el último espacio antes del límite.
        for (cut in limit - 1 downTo start + 1) {
            if (text[cut].isWhitespace()) return cut
        }
        // Una "palabra" gigante sin espacios: corte duro, sin partir un par sustituto.
        return if (text[limit - 1].isHighSurrogate() && limit - 1 > start) limit - 1 else limit
    }

    /** Inicio de la palabra que contiene [offset], para no reanudar a media palabra. */
    fun wordStart(text: CharSequence, offset: Int): Int {
        var i = offset.coerceIn(0, text.length)
        while (i > 0 && !text[i - 1].isWhitespace()) i--
        return i
    }

    fun skipWhitespace(text: CharSequence, from: Int): Int {
        var i = from
        while (i < text.length && text[i].isWhitespace()) i++
        return i
    }
}
