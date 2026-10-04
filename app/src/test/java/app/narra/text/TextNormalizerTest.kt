package app.narra.text

import org.junit.Assert.assertEquals
import org.junit.Test

class TextNormalizerTest {

    @Test
    fun collapsesWhitespaceAndLineBreaks() {
        assertEquals("Hola mundo cruel", TextNormalizer.normalizePage("  Hola\n\n mundo\r\n\tcruel  "))
    }

    @Test
    fun joinsWordsHyphenatedAcrossLines() {
        assertEquals("una palabra partida", TextNormalizer.normalizePage("una pala-\nbra partida"))
        assertEquals("una palabra partida", TextNormalizer.normalizePage("una pala\u00AD\nbra partida"))
    }

    @Test
    fun keepsRealHyphensBeforeCapitalsOrSameLine() {
        assertEquals("Norte- Sur", TextNormalizer.normalizePage("Norte-\nSur"))
        assertEquals("franco-alemán", TextNormalizer.normalizePage("franco-alemán"))
    }

    @Test
    fun expandsLigaturesAndRemovesInvisibleCharacters() {
        assertEquals("fiel oficina", TextNormalizer.normalizePage("\uFB01el o\uFB01cina"))
        assertEquals("ab", TextNormalizer.normalizePage("a\u200Bb"))
        assertEquals("a b", TextNormalizer.normalizePage("a\u0000b"))
    }

    @Test
    fun dropsLinesThatAreOnlyPageNumbers() {
        assertEquals("Capítulo uno Texto del capítulo.", TextNormalizer.normalizePage("Capítulo uno\n12\nTexto del capítulo."))
        assertEquals("Hay 12 sillas.", TextNormalizer.normalizePage("Hay 12 sillas."))
    }

    @Test
    fun blankPageBecomesEmpty() {
        assertEquals("", TextNormalizer.normalizePage(" \n\t "))
        assertEquals("", TextNormalizer.normalizePage("\n 7 \n"))
    }
}
