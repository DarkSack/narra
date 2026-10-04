package app.narra.analysis

import app.narra.analysis.AnalysisFixtures.filler
import app.narra.analysis.AnalysisFixtures.page
import app.narra.analysis.AnalysisFixtures.profile
import app.narra.domain.model.Paragraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmenterAndMetadataTest {

    @Test
    fun `los segmentos respetan el máximo y no pierden texto`() {
        val paragraphs = (1..40).map { Paragraph("$filler $it.") }
        val segments = Segmenter.segment(paragraphs, targetChars = 500, maxChars = 700)
        assertTrue(segments.all { group -> group.sumOf { it.text.length } <= 700 })
        assertEquals(paragraphs, segments.flatten())
    }

    @Test
    fun `un párrafo enorme se parte por frases`() {
        val sentence = "Esta es una frase de prueba que se repite. "
        val huge = Paragraph(sentence.repeat(200).trim())
        val segments = Segmenter.segment(listOf(huge), targetChars = 1_000, maxChars = 1_200)
        assertTrue(segments.size > 1)
        assertTrue(segments.flatten().all { it.text.length <= 1_200 })
        assertEquals(huge.text.replace(" ", ""), segments.flatten().joinToString("") { it.text.replace(" ", "") })
    }

    @Test
    fun `valida ISBN-10 e ISBN-13`() {
        assertTrue(MetadataHeuristics.isValidIsbn("9788420412146"))
        assertTrue(MetadataHeuristics.isValidIsbn("843760494X"))
        assertFalse(MetadataHeuristics.isValidIsbn("9788420412147"))
        assertEquals("9788420412146", MetadataHeuristics.findIsbn("Depósito legal. ISBN: 978-84-204-1214-6. Impreso en España"))
    }

    @Test
    fun `descarta títulos de metadatos sin sentido`() {
        assertFalse(MetadataHeuristics.isPlausibleTitle("Microsoft Word - borrador_final.docx"))
        assertFalse(MetadataHeuristics.isPlausibleTitle("libro_v2_final"))
        assertTrue(MetadataHeuristics.isPlausibleTitle("El faro de las palabras"))
    }

    @Test
    fun `combina metadatos y deduce el título de la portada`() {
        val pages = listOf(
            page(0) { heading("El faro de las palabras", size = 28f) },
        ) + (1..5).map { i -> page(i) { repeat(20) { line(filler) } } }
        val metadata = MetadataHeuristics.combine(PdfMetadata(title = "Untitled", author = "admin"), profile(pages))
        assertEquals("El faro de las palabras", metadata.title)
        assertNull(metadata.author)
        assertEquals("es", metadata.language)
    }

    @Test
    fun `detecta inglés y español`() {
        assertEquals("en", LanguageDetector.detect("The keeper of the lighthouse was waiting for her at the pier. ".repeat(10)))
        assertEquals("es", LanguageDetector.detect("El farero la esperaba en el muelle con una linterna en la mano. ".repeat(10)))
        assertNull(LanguageDetector.detect("Hola"))
    }
}
