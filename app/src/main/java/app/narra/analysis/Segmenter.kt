package app.narra.analysis

import app.narra.domain.model.Paragraph
import app.narra.text.SentenceSplitter

/**
 * Agrupa los párrafos de un capítulo en segmentos de unos minutos de audio. Cada segmento es
 * un archivo y la unidad de generación: si falla uno, se reintenta solo ese.
 */
object Segmenter {
    /** ≈ 3 minutos de voz a velocidad normal. */
    const val TARGET_CHARS = 2_500
    const val MAX_CHARS = 3_200

    fun segment(paragraphs: List<Paragraph>, targetChars: Int = TARGET_CHARS, maxChars: Int = MAX_CHARS): List<List<Paragraph>> {
        require(targetChars in 1..maxChars)
        val segments = ArrayList<List<Paragraph>>()
        var current = ArrayList<Paragraph>()
        var size = 0
        for (piece in paragraphs.flatMap { split(it, maxChars) }) {
            if (current.isNotEmpty() && size + piece.text.length > maxChars) {
                segments += current
                current = ArrayList()
                size = 0
            }
            current += piece
            size += piece.text.length
            if (size >= targetChars) {
                segments += current
                current = ArrayList()
                size = 0
            }
        }
        if (current.isNotEmpty()) segments += current
        return segments
    }

    /** Un párrafo larguísimo se parte por frases; una frase larguísima, por palabras. */
    private fun split(paragraph: Paragraph, maxChars: Int): List<Paragraph> {
        val text = paragraph.text
        if (text.length <= maxChars) return listOf(paragraph)
        val pieces = ArrayList<Paragraph>()
        val piece = StringBuilder()
        var start = SentenceSplitter.skipWhitespace(text, 0)
        while (start < text.length) {
            val end = SentenceSplitter.sentenceEnd(text, start, maxChars)
            val sentence = text.substring(start, end).trim()
            if (piece.isNotEmpty() && piece.length + 1 + sentence.length > maxChars) {
                pieces += paragraph.copy(text = piece.toString())
                piece.setLength(0)
            }
            if (piece.isNotEmpty()) piece.append(' ')
            piece.append(sentence)
            start = SentenceSplitter.skipWhitespace(text, end)
        }
        if (piece.isNotEmpty()) pieces += paragraph.copy(text = piece.toString())
        return pieces
    }
}
