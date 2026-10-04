package app.narra.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SentenceSplitterTest {

    private fun sentences(text: String, from: Int = 0, maxChars: Int = Int.MAX_VALUE, maxSentence: Int = 600) =
        SentenceSplitter.batch(text, from, maxChars, maxSentence).map { text.substring(it.start, it.end) }

    @Test
    fun splitsOnTerminalPunctuation() {
        assertEquals(
            listOf("Hola mundo.", "¿Cómo estás?", "¡Bien!", "Fin"),
            sentences("Hola mundo. ¿Cómo estás? ¡Bien! Fin"),
        )
    }

    @Test
    fun keepsClosingQuotesAndEllipsisWithTheSentence() {
        assertEquals(
            listOf("Dijo: «ya voy…»", "Y se fue.", "\"¿Seguro?\"", "Sí."),
            sentences("Dijo: «ya voy…» Y se fue. \"¿Seguro?\" Sí."),
        )
    }

    @Test
    fun doesNotSplitDecimalsOrUrls() {
        assertEquals(
            listOf("Pi vale 3.14 más o menos.", "Visita www.ejemplo.com hoy."),
            sentences("Pi vale 3.14 más o menos. Visita www.ejemplo.com hoy."),
        )
    }

    @Test
    fun longSentenceIsCutAtWordBoundary() {
        val text = "uno dos tres cuatro cinco seis siete ocho"
        val parts = sentences(text, maxSentence = 12)
        assertTrue(parts.all { it.length <= 12 })
        assertEquals(text, parts.joinToString(" "))
    }

    @Test
    fun giantWordIsHardCut() {
        val text = "a".repeat(25)
        val parts = sentences(text, maxSentence = 10)
        assertEquals(listOf(10, 10, 5), parts.map { it.length })
    }

    @Test
    fun batchStopsAfterReachingMaxCharsButReturnsAtLeastOne() {
        val text = "Primera frase larga. Segunda. Tercera."
        assertEquals(listOf("Primera frase larga."), sentences(text, maxChars = 5))
        assertEquals(listOf("Primera frase larga.", "Segunda."), sentences(text, maxChars = 21))
    }

    @Test
    fun batchFromMiddleOfTextAndPastTheEnd() {
        val text = "Uno. Dos. Tres."
        assertEquals(listOf("Dos.", "Tres."), sentences(text, from = 4))
        assertEquals(emptyList<String>(), sentences(text, from = text.length))
        assertEquals(emptyList<String>(), sentences(text, from = text.length + 50))
    }

    @Test
    fun spansCoverTextWithoutGapsOtherThanWhitespace() {
        val text = "Esto es. Una prueba!   Con espacios… y \"comillas\". Final sin punto"
        val spans = SentenceSplitter.batch(text, 0, Int.MAX_VALUE)
        var cursor = 0
        for (span in spans) {
            assertTrue(text.substring(cursor, span.start).isBlank())
            cursor = span.end
        }
        assertEquals(text.length, cursor)
    }

    @Test
    fun wordStartMovesBackToBeginningOfWord() {
        val text = "hola mundo cruel"
        assertEquals(5, SentenceSplitter.wordStart(text, 7))
        assertEquals(5, SentenceSplitter.wordStart(text, 5))
        assertEquals(0, SentenceSplitter.wordStart(text, 2))
        assertEquals(11, SentenceSplitter.wordStart(text, 999)) // fuera de rango: se acota al final
        assertEquals(0, SentenceSplitter.wordStart(text, -3))
    }
}
