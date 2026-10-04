package app.narra.analysis

/** Entrada del índice impreso: "Capítulo 3. La última luz ........ 47". */
data class TocEntry(val title: String, val printedPage: Int)

data class TocResult(val entries: List<TocEntry>, val lastPage: Int)

/** Reconoce el índice impreso en las primeras páginas del libro. */
object TocParser {
    private val entryLine = Regex("^(.{2,100}?)(?:\\s*[.·…_]{2,}\\s*|\\s+)(\\d{1,4})$")
    private val tocHeading = Regex("^(índice|indice|contenido|contenidos|contents|table of contents|sumario)$", RegexOption.IGNORE_CASE)
    private const val MIN_ENTRIES_PER_PAGE = 3
    private const val MIN_ENTRIES = 3

    fun parse(pages: List<PageText>): TocResult? {
        val entries = ArrayList<TocEntry>()
        var lastPage = -1
        for (page in pages) {
            val pageEntries = page.lines.mapNotNull { line ->
                val text = line.text.trim()
                if (DocumentProfile.isPageNumber(text) || tocHeading.matches(text)) return@mapNotNull null
                entryLine.matchEntire(text)?.let { match ->
                    val title = match.groupValues[1].trim().trimEnd('.', '·', '…', '_', ' ')
                    val printed = match.groupValues[2].toInt()
                    if (title.any { it.isLetter() }) TocEntry(title, printed) else null
                }
            }
            val headed = page.lines.any { tocHeading.matches(it.text.trim()) }
            if (pageEntries.size >= MIN_ENTRIES_PER_PAGE || (headed && pageEntries.isNotEmpty())) {
                entries += pageEntries
                lastPage = page.index
            } else if (entries.isNotEmpty()) {
                break // El índice terminó.
            }
        }
        val ordered = increasingRun(entries)
        return if (ordered.size >= MIN_ENTRIES) TocResult(ordered, lastPage) else null
    }

    /** Descarta líneas sueltas que rompen el orden creciente de páginas (falsos positivos). */
    private fun increasingRun(entries: List<TocEntry>): List<TocEntry> {
        val result = ArrayList<TocEntry>()
        entries.forEach { entry -> if (result.isEmpty() || entry.printedPage >= result.last().printedPage) result += entry }
        return result
    }
}

/**
 * Localiza cada entrada del índice en el cuerpo del libro, en orden. Las que no aparecen se
 * sitúan con el desfase medio entre página impresa y página real.
 */
internal class TocResolver(private val entries: List<TocEntry>, private val firstSearchPage: Int) {
    private val found = arrayOfNulls<ChapterMark>(entries.size)
    private val keys = entries.map { ChapterDetector.comparable(it.title) }
    private var next = 0

    fun accept(page: PageText, body: List<IndexedValue<TextLine>>) {
        if (page.index < firstSearchPage) return
        var i = 0
        while (i < body.size && next < entries.size) {
            val line = ChapterDetector.comparable(body[i].value.text)
            val hit = (next until minOf(next + LOOKAHEAD, entries.size)).firstOrNull { k -> matches(line, keys[k]) }
            if (hit == null) {
                i++
                continue
            }
            val located = ChapterDetector.findTitle(entries[hit].title, body.subList(i, body.size))
            found[hit] = ChapterMark(
                title = entries[hit].title,
                pageIndex = page.index,
                lineIndex = body[i].index,
                titleLines = located?.second ?: 1,
            )
            next = hit + 1
            i += located?.second ?: 1
        }
    }

    private fun matches(line: String, key: String): Boolean =
        line.length >= MIN_MATCH && key.isNotEmpty() && (line == key || key.startsWith(line) || line.startsWith(key))

    fun isReliable(): Boolean {
        val resolved = found.count { it != null }
        return resolved >= ChapterDetector.MIN_CHAPTERS && resolved >= entries.size * RELIABLE_FRACTION
    }

    fun marks(): List<ChapterMark> {
        val offsets = entries.indices.mapNotNull { i -> found[i]?.let { it.pageIndex - (entries[i].printedPage - 1) } }.sorted()
        val offset = offsets.getOrNull(offsets.size / 2) ?: 0
        return entries.indices.map { i ->
            found[i] ?: ChapterMark(entries[i].title, (entries[i].printedPage - 1 + offset).coerceAtLeast(0), 0)
        }
    }

    private companion object {
        const val LOOKAHEAD = 3
        const val MIN_MATCH = 4
        const val RELIABLE_FRACTION = 0.6f
    }
}
