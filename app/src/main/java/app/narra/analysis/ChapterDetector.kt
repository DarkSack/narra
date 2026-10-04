package app.narra.analysis

import app.narra.domain.model.ChapterSource
import java.text.Normalizer
import kotlin.math.roundToInt

/** Dónde empieza un capítulo: página y línea, y cuántas líneas ocupa su título. */
data class ChapterMark(
    val title: String,
    val pageIndex: Int,
    val lineIndex: Int,
    val level: Int = 0,
    /** Líneas del título al principio del capítulo, que no se leen dos veces. */
    val titleLines: Int = 0,
    val included: Boolean = true,
)

data class ChapterPlan(val marks: List<ChapterMark>, val source: ChapterSource, val headingsFound: Int)

/**
 * Detecta los capítulos con varias estrategias, de la más fiable a la menos:
 * marcadores del PDF → índice impreso → títulos tipográficos → bloques de páginas.
 * Recorre las páginas una sola vez ([pages] se lee en streaming).
 */
class ChapterDetector(private val profile: DocumentProfile) {

    fun detect(outline: List<OutlineEntry>, pages: Sequence<PageText>): ChapterPlan {
        val outlineEntries = pickOutlineLevel(outline)
        val toc = TocParser.parse(profile.earlyPages)
        val tocResolver = toc?.let { TocResolver(it.entries, firstSearchPage = it.lastPage + 1) }
        val outlineLocator = OutlineLocator(outlineEntries)
        val headings = ArrayList<HeadingCandidate>()

        for (page in pages) {
            val body = page.lines.withIndex().filterNot { profile.isRunningHeaderOrFooter(it.value) }
            outlineLocator.accept(page, body)
            tocResolver?.accept(page, body)
            headings += headingCandidates(page, body)
        }

        val chosenHeadings = chooseHeadings(headings)
        val (marks, source) = when {
            outlineEntries.size >= MIN_CHAPTERS -> outlineLocator.marks() to ChapterSource.OUTLINE
            tocResolver != null && tocResolver.isReliable() -> tocResolver.marks() to ChapterSource.TABLE_OF_CONTENTS
            chosenHeadings.size >= MIN_CHAPTERS -> chosenHeadings.map { it.toMark() } to ChapterSource.HEADINGS
            else -> pageChunks() to ChapterSource.PAGES
        }
        return ChapterPlan(withFrontMatter(normalize(marks)), source, chosenHeadings.size)
    }

    // ------------------------------------------------------------------ Marcadores

    private fun pickOutlineLevel(outline: List<OutlineEntry>): List<OutlineEntry> {
        val valid = outline.filter { it.pageIndex in 0 until profile.pageCount && it.title.isNotBlank() }
        val topLevel = valid.filter { it.level == 0 }
        // Un único marcador raíz (el título del libro) con capítulos dentro: se usan los hijos.
        return if (topLevel.size >= MIN_CHAPTERS) topLevel else valid.filter { it.level == 1 }
    }

    /** Busca en la página la línea donde aparece cada título de los marcadores. */
    private class OutlineLocator(private val entries: List<OutlineEntry>) {
        private val located = arrayOfNulls<ChapterMark>(entries.size)

        fun accept(page: PageText, body: List<IndexedValue<TextLine>>) {
            entries.forEachIndexed { i, entry ->
                if (entry.pageIndex != page.index || located[i] != null) return@forEachIndexed
                val match = findTitle(entry.title, body)
                located[i] = ChapterMark(
                    title = entry.title.trim(),
                    pageIndex = page.index,
                    lineIndex = match?.first ?: body.firstOrNull()?.index ?: 0,
                    level = 0,
                    titleLines = match?.second ?: 0,
                )
            }
        }

        fun marks(): List<ChapterMark> = entries.mapIndexed { i, entry ->
            located[i] ?: ChapterMark(entry.title.trim(), entry.pageIndex, 0)
        }
    }

    // ------------------------------------------------------------------ Títulos tipográficos

    private data class HeadingCandidate(
        val title: String,
        val pageIndex: Int,
        val lineIndex: Int,
        val lineCount: Int,
        val fontSize: Float,
        val strong: Boolean,
    ) {
        fun toMark() = ChapterMark(title, pageIndex, lineIndex, titleLines = lineCount)
    }

    private fun headingCandidates(page: PageText, body: List<IndexedValue<TextLine>>): List<HeadingCandidate> {
        val result = ArrayList<HeadingCandidate>()
        var i = 0
        while (i < body.size) {
            val (index, line) = body[i]
            val previous = body.getOrNull(i - 1)?.value
            if (!isHeadingLike(line, previous, page.heightPt)) {
                i++
                continue
            }
            // Títulos de varias líneas ("Capítulo 3" + "La última luz").
            val parts = mutableListOf(line)
            var j = i + 1
            while (j < body.size && parts.size < MAX_HEADING_LINES) {
                val next = body[j].value
                val gap = (next.y - parts.last().y) * page.heightPt
                if (gap > profile.lineSpacingPt * HEADING_JOIN_GAP || !isHeadingLike(next, null, page.heightPt, continuation = true)) break
                parts += next
                j++
            }
            val title = parts.joinToString(" ") { it.text.trim() }
            result += HeadingCandidate(
                title = title,
                pageIndex = page.index,
                lineIndex = index,
                lineCount = parts.size,
                fontSize = parts.maxOf { it.fontSize },
                strong = parts.any { CHAPTER_KEYWORD.containsMatchIn(it.text.trim()) } ||
                    (STANDALONE_NUMBER.matches(line.text.trim()) && line.fontSize >= profile.bodyFontSize * LARGE_FACTOR),
            )
            i = j
        }
        return result
    }

    private fun isHeadingLike(line: TextLine, previous: TextLine?, pageHeight: Float, continuation: Boolean = false): Boolean {
        val text = line.text.trim()
        if (text.isEmpty() || text.length > MAX_HEADING_CHARS || text.last() in ",;") return false
        if (!text.any { it.isLetterOrDigit() }) return false
        val large = line.fontSize >= profile.bodyFontSize * LARGE_FACTOR
        if (continuation) return large || line.bold
        val keyword = CHAPTER_KEYWORD.containsMatchIn(text)
        val isolated = previous == null || (line.y - previous.y) * pageHeight > profile.lineSpacingPt * ISOLATION_GAP
        val emphasized = line.fontSize >= profile.bodyFontSize * EMPHASIS_FACTOR || line.bold
        return when {
            keyword -> isolated && (emphasized || text.length <= SHORT_KEYWORD_HEADING)
            large -> line.y < UPPER_PAGE || isolated
            else -> false
        }
    }

    private fun chooseHeadings(candidates: List<HeadingCandidate>): List<HeadingCandidate> {
        val strong = candidates.filter { it.strong }
        if (strong.size >= MIN_CHAPTERS) return strong
        val maxChapters = profile.pageCount / 2 + 1
        // El grupo de tamaño de letra más grande que aparece en varias páginas distintas.
        return candidates
            .groupBy { it.fontSize.roundToInt() }
            .entries
            .sortedByDescending { it.key }
            .map { it.value }
            .firstOrNull { group ->
                group.size in MIN_CHAPTERS..maxChapters &&
                    group.map { it.pageIndex }.distinct().size >= group.size * DISTINCT_PAGES_FRACTION
            }
            .orEmpty()
    }

    // ------------------------------------------------------------------ Bloques de páginas

    private fun pageChunks(): List<ChapterMark> {
        val marks = ArrayList<ChapterMark>()
        var start = 0
        var chars = 0
        profile.pageChars.forEachIndexed { index, count ->
            chars += count
            val last = index == profile.pageChars.lastIndex
            if (chars >= TARGET_CHUNK_CHARS || last) {
                marks += ChapterMark(chunkTitle(marks.size + 1, start, index), start, 0)
                start = index + 1
                chars = 0
            }
        }
        return marks.ifEmpty { listOf(ChapterMark(chunkTitle(1, 0, (profile.pageCount - 1).coerceAtLeast(0)), 0, 0)) }
    }

    private fun chunkTitle(number: Int, firstPage: Int, lastPage: Int) =
        if (firstPage == lastPage) "Parte $number (pág. ${firstPage + 1})" else "Parte $number (págs. ${firstPage + 1}–${lastPage + 1})"

    // ------------------------------------------------------------------ Ajustes finales

    private fun normalize(marks: List<ChapterMark>): List<ChapterMark> = marks
        .filter { it.pageIndex in 0 until profile.pageCount }
        .sortedWith(compareBy({ it.pageIndex }, { it.lineIndex }))
        .distinctBy { it.pageIndex to it.lineIndex }
        .map { mark ->
            val title = cleanTitle(mark.title)
            mark.copy(title = title, included = mark.included && !EXCLUDED_SECTION.matches(title))
        }

    /** El texto anterior al primer capítulo (créditos, dedicatoria, índice) se ofrece aparte. */
    private fun withFrontMatter(marks: List<ChapterMark>): List<ChapterMark> {
        val first = marks.firstOrNull() ?: return marks
        if (first.pageIndex == 0 && first.lineIndex == 0) return marks
        val charsBefore = profile.pageChars.take(first.pageIndex).sum()
        if (charsBefore < MIN_FRONT_MATTER_CHARS) return marks
        val front = if (charsBefore >= SUBSTANTIAL_FRONT_MATTER_CHARS) {
            ChapterMark("Inicio", 0, 0)
        } else {
            ChapterMark("Preliminares", 0, 0, included = false)
        }
        return listOf(front) + marks
    }

    private fun cleanTitle(raw: String): String {
        // "La isla ....... 3": solo con puntos guía el número final es una página ("Capítulo 1" no).
        val withoutLeaders = if (LEADERS.containsMatchIn(raw)) TRAILING_PAGE_NUMBER.replace(raw.replace(LEADERS, " ").trim(), "") else raw
        val title = withoutLeaders.replace(SPACES, " ").trim()
        if (title.isEmpty()) return raw.trim()
        // TÍTULOS EN MAYÚSCULAS → "Títulos en mayúsculas", más naturales en la lista.
        val letters = title.filter { it.isLetter() }
        return if (letters.length > 3 && letters.all { it.isUpperCase() }) {
            title.lowercase().replaceFirstChar { it.titlecase() }
        } else {
            title
        }
    }

    companion object {
        const val MIN_CHAPTERS = 2
        private const val MAX_HEADING_CHARS = 90
        private const val MAX_HEADING_LINES = 3
        private const val SHORT_KEYWORD_HEADING = 40
        private const val LARGE_FACTOR = 1.3f
        private const val EMPHASIS_FACTOR = 1.05f
        private const val ISOLATION_GAP = 1.8f
        private const val HEADING_JOIN_GAP = 2.6f
        private const val UPPER_PAGE = 0.45f
        private const val DISTINCT_PAGES_FRACTION = 0.8f
        /** ≈ 30 minutos de escucha por bloque cuando el libro no trae ninguna estructura. */
        const val TARGET_CHUNK_CHARS = 27_000
        private const val MIN_FRONT_MATTER_CHARS = 200
        private const val SUBSTANTIAL_FRONT_MATTER_CHARS = 3_000

        val CHAPTER_KEYWORD = Regex(
            "^(cap[ií]tulo|chapter|parte|part|libro|book|pr[oó]logo|prologue|ep[ií]logo|epilogue|introducci[oó]n|introduction|" +
                "prefacio|preface|pre[aá]mbulo|ap[eé]ndice|appendix|conclusi[oó]n|conclusion|interludio|interlude|" +
                "agradecimientos|acknowledg(e)?ments|nota del autor|author'?s note)\\b",
            RegexOption.IGNORE_CASE,
        )
        private val STANDALONE_NUMBER = Regex("^(\\d{1,3}|[IVXLC]{1,7})\\.?$")
        private val EXCLUDED_SECTION = Regex(
            "^(índice|indice|contenido|contenidos|contents|table of contents|copyright|créditos|bibliograf[ií]a|bibliography|" +
                "referencias|references|índice alfabético|index|sumario)\\.?$",
            RegexOption.IGNORE_CASE,
        )
        private val LEADERS = Regex("[.·…]{2,}")
        private val TRAILING_PAGE_NUMBER = Regex("\\s+\\d{1,4}$")
        private val SPACES = Regex("\\s+")
        private val NON_ALNUM = Regex("[^\\p{L}\\p{N}]+")
        private val DIACRITICS = Regex("\\p{M}+")

        /** Forma comparable de un título: sin acentos, signos ni mayúsculas. */
        fun comparable(text: String): String =
            NON_ALNUM.replace(
                DIACRITICS.replace(Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD), ""),
                "",
            )

        /** Línea donde empieza [title] y cuántas líneas ocupa, o null si no aparece. */
        internal fun findTitle(title: String, body: List<IndexedValue<TextLine>>): Pair<Int, Int>? {
            val target = comparable(title)
            if (target.isEmpty()) return null
            body.forEachIndexed { i, (index, line) ->
                val text = comparable(line.text)
                if (text.length < MIN_TITLE_MATCH && text != target) return@forEachIndexed
                if (text == target || target.startsWith(text) || text.startsWith(target)) {
                    // El título puede seguir en las líneas siguientes.
                    var covered = text
                    var count = 1
                    while (covered.length < target.length && i + count < body.size) {
                        val next = comparable(body[i + count].value.text)
                        if (next.isEmpty() || !target.startsWith(covered + next)) break
                        covered += next
                        count++
                    }
                    return index to count
                }
            }
            return null
        }

        private const val MIN_TITLE_MATCH = 4
    }
}
