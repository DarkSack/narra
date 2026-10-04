package app.narra.analysis

import app.narra.analysis.AnalysisFixtures.filler
import app.narra.analysis.AnalysisFixtures.page
import app.narra.analysis.AnalysisFixtures.profile
import app.narra.domain.model.ChapterSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterDetectorTest {

    private fun bookWithHeadings(): List<PageText> = (0 until 9).map { i ->
        page(i) {
            header("El faro de las palabras")
            if (i % 3 == 0) heading("Capítulo ${i / 3 + 1}")
            repeat(20) { line(filler) }
            footer("${i + 1}")
        }
    }

    @Test
    fun `usa los marcadores del PDF cuando existen`() {
        val pages = bookWithHeadings()
        val outline = listOf(
            OutlineEntry("Capítulo 1", 0, 0),
            OutlineEntry("Capítulo 2", 3, 0),
            OutlineEntry("Capítulo 3", 6, 0),
        )
        val plan = ChapterDetector(profile(pages)).detect(outline, pages.asSequence())
        assertEquals(ChapterSource.OUTLINE, plan.source)
        assertEquals(listOf(0, 3, 6), plan.marks.map { it.pageIndex })
        // El título se localiza en la página para no leerlo dos veces.
        assertTrue(plan.marks.all { it.titleLines == 1 })
    }

    @Test
    fun `sin marcadores reconoce los títulos tipográficos`() {
        val pages = bookWithHeadings()
        val plan = ChapterDetector(profile(pages)).detect(emptyList(), pages.asSequence())
        assertEquals(ChapterSource.HEADINGS, plan.source)
        assertEquals(listOf("Capítulo 1", "Capítulo 2", "Capítulo 3"), plan.marks.map { it.title })
    }

    @Test
    fun `un único marcador raíz usa sus hijos`() {
        val pages = bookWithHeadings()
        val outline = listOf(
            OutlineEntry("El faro de las palabras", 0, 0),
            OutlineEntry("Capítulo 1", 0, 1),
            OutlineEntry("Capítulo 2", 3, 1),
        )
        val plan = ChapterDetector(profile(pages)).detect(outline, pages.asSequence())
        assertEquals(listOf("Capítulo 1", "Capítulo 2"), plan.marks.map { it.title })
    }

    @Test
    fun `resuelve el índice impreso contra el cuerpo del libro`() {
        val toc = page(0) {
            heading("Índice")
            line("La isla ........ 3", short = true)
            line("El cuaderno ........ 5", short = true)
            line("La última luz ........ 7", short = true)
        }
        val body = (1 until 8).map { i ->
            page(i) {
                when (i) {
                    2 -> heading("La isla", size = 12f)
                    4 -> heading("El cuaderno", size = 12f)
                    6 -> heading("La última luz", size = 12f)
                }
                repeat(20) { line(filler) }
            }
        }
        val pages = listOf(toc) + body
        val plan = ChapterDetector(profile(pages)).detect(emptyList(), pages.asSequence())
        assertEquals(ChapterSource.TABLE_OF_CONTENTS, plan.source)
        val chapters = plan.marks.filter { it.included }
        assertEquals(listOf(2, 4, 6), chapters.map { it.pageIndex }.takeLast(3))
    }

    @Test
    fun `sin ninguna estructura reparte el libro en bloques de páginas`() {
        val pages = (0 until 40).map { i -> page(i) { repeat(30) { line(filler) } } }
        val plan = ChapterDetector(profile(pages)).detect(emptyList(), pages.asSequence())
        assertEquals(ChapterSource.PAGES, plan.source)
        assertTrue(plan.marks.size > 1)
        assertTrue(plan.marks.first().title.startsWith("Parte 1"))
    }

    @Test
    fun `el índice y la bibliografía se ofrecen pero no se incluyen`() {
        val pages = (0 until 6).map { i ->
            page(i) {
                when (i) {
                    0 -> heading("Capítulo 1")
                    2 -> heading("Capítulo 2")
                    4 -> heading("Bibliografía", size = 18f)
                }
                repeat(20) { line(filler) }
            }
        }
        val outline = listOf(OutlineEntry("Capítulo 1", 0, 0), OutlineEntry("Capítulo 2", 2, 0), OutlineEntry("Bibliografía", 4, 0))
        val plan = ChapterDetector(profile(pages)).detect(outline, pages.asSequence())
        assertFalse(plan.marks.last().included)
    }

    @Test
    fun `los títulos en mayúsculas se escriben en tipo oración`() {
        val pages = (0 until 4).map { i -> page(i) { repeat(10) { line(filler) } } }
        val outline = listOf(OutlineEntry("LA ISLA", 0, 0), OutlineEntry("EL CUADERNO", 2, 0))
        val plan = ChapterDetector(profile(pages)).detect(outline, pages.asSequence())
        assertEquals(listOf("La isla", "El cuaderno"), plan.marks.map { it.title })
    }
}
