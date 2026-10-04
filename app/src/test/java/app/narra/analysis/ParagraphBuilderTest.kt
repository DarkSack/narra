package app.narra.analysis

import app.narra.analysis.AnalysisFixtures.filler
import app.narra.analysis.AnalysisFixtures.page
import app.narra.analysis.AnalysisFixtures.profile
import app.narra.domain.model.Paragraph
import app.narra.domain.model.SegmentKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParagraphBuilderTest {

    private fun build(vararg pages: PageText, stats: CleaningStats = CleaningStats()): List<Paragraph> {
        // Páginas de relleno para que encabezados y pies cuenten como repetidos.
        val padding = (pages.size until pages.size + 4).map { i ->
            page(i) {
                header("El faro de las palabras")
                repeat(20) { line(filler) }
                footer("${i + 1}")
            }
        }
        val builder = ParagraphBuilder(profile(pages.toList() + padding), stats)
        builder.startChapter("La isla")
        pages.forEach { builder.addPage(it) }
        return builder.finishChapter()
    }

    @Test
    fun `el capítulo empieza con su título como encabezado`() {
        val result = build(page(0) { line("Texto.", short = true) })
        assertEquals(Paragraph("La isla", SegmentKind.HEADING), result.first())
    }

    @Test
    fun `une las palabras cortadas con guion al final de línea`() {
        val stats = CleaningStats()
        val result = build(
            page(0) {
                line("Nadie recordaba cuándo se había encen-")
                line("dido el faro por primera vez.", short = true)
            },
            stats = stats,
        )
        assertEquals("Nadie recordaba cuándo se había encendido el faro por primera vez.", result[1].text)
        assertEquals(1, stats.hyphenationsFixed)
    }

    @Test
    fun `separa párrafos por sangría tras punto y aparte`() {
        val result = build(
            page(0) {
                line("Primera frase del primer párrafo que llega hasta el margen")
                line("y termina aquí.", short = true)
                line("Segundo párrafo con sangría.", indent = true, short = true)
            },
        )
        assertEquals(3, result.size)
        assertEquals("Segundo párrafo con sangría.", result[2].text)
    }

    @Test
    fun `quita encabezados, pies y números de página`() {
        val stats = CleaningStats()
        val result = build(
            page(0) {
                header("El faro de las palabras")
                line("Contenido real.", short = true)
                footer("1")
            },
            stats = stats,
        )
        assertEquals(listOf("La isla", "Contenido real."), result.map { it.text })
        assertEquals(1, stats.headersRemoved)
        assertEquals(1, stats.pageNumbersRemoved)
    }

    @Test
    fun `reconoce diálogos y aparta las notas al pie`() {
        val stats = CleaningStats()
        val result = build(
            page(0) {
                line("Llegó al muelle.", short = true)
                line("—Aquí las palabras pesan más —le dijo.", indent = true, short = true)
                footnote("1 Nota del traductor sobre el faro.")
            },
            stats = stats,
        )
        assertEquals(SegmentKind.DIALOGUE, result.last().kind)
        assertFalse(result.any { "traductor" in it.text })
        assertEquals(1, stats.footnotes)
        assertEquals(1, stats.dialogues)
    }

    @Test
    fun `las filas de una tabla se leen como enumeraciones`() {
        val stats = CleaningStats()
        val result = build(
            page(0) {
                row("Año", "Faro", "Altura")
                row("1890", "Isla Norte", "32 m")
            },
            stats = stats,
        )
        assertEquals("1890, Isla Norte, 32 m.", result.last().text)
        assertEquals(1, stats.tables)
    }

    @Test
    fun `un párrafo continúa en la página siguiente`() {
        val result = build(
            page(0) { line("El farero la esperaba en el muelle con una linterna") },
            page(1) { line("encendida en la mano.", short = true) },
        )
        assertTrue(result.any { it.text == "El farero la esperaba en el muelle con una linterna encendida en la mano." })
    }
}
