package app.narra.analysis

import app.narra.domain.model.Paragraph
import app.narra.domain.model.SegmentKind
import app.narra.text.TextNormalizer

/** Lo que la limpieza encontró y quitó, para contárselo al usuario en la revisión. */
class CleaningStats {
    var headersRemoved = 0
    var footersRemoved = 0
    var pageNumbersRemoved = 0
    var footnotes = 0
    var tables = 0
    var lists = 0
    var quotes = 0
    var dialogues = 0
    var hyphenationsFixed = 0
    var subheadings = 0
}

/**
 * Convierte las líneas de un capítulo en párrafos listos para leer en voz alta: quita
 * encabezados, pies y notas, une palabras cortadas con guion y reconoce diálogos, listas,
 * citas y tablas. Trabaja página a página; solo guarda en memoria el capítulo en curso.
 */
class ParagraphBuilder(private val profile: DocumentProfile, private val stats: CleaningStats) {
    private val paragraphs = ArrayList<Paragraph>()
    private val buffer = StringBuilder()
    private var bufferLines = 0
    private var bufferAllIndented = true
    private var previous: TextLine? = null
    private var previousRightEdge = 1f
    private var inTable = false

    fun startChapter(title: String) {
        paragraphs.clear()
        resetBuffer()
        previous = null
        inTable = false
        // Sin punto añadido: la pausa tras un título la pone el sintetizador según el tipo.
        paragraphs += Paragraph(title.trim(), SegmentKind.HEADING)
    }

    /** Añade las líneas `[from, to)` de la página, saltando las de [skip] (el título del capítulo). */
    fun addPage(page: PageText, from: Int = 0, to: Int = page.lines.size, skip: IntRange = IntRange.EMPTY) {
        val body = ArrayList<TextLine>()
        for (i in from until to.coerceAtMost(page.lines.size)) {
            val line = page.lines[i]
            when {
                i in skip -> Unit
                profile.isPageNumberLine(line) -> stats.pageNumbersRemoved++
                profile.isRunningHeaderOrFooter(line) -> if (line.y < HALF_PAGE) stats.headersRemoved++ else stats.footersRemoved++
                line.text.isBlank() -> Unit
                else -> body += line
            }
        }
        val footnoteStart = footnoteStart(body)
        if (footnoteStart < body.size) {
            stats.footnotes += body.subList(footnoteStart, body.size).count { FOOTNOTE_MARKER.containsMatchIn(it.text) }.coerceAtLeast(1)
        }
        val lines = body.subList(0, footnoteStart)
        if (lines.isEmpty()) return

        val bodyLines = lines.filter { it.fontSize <= profile.bodyFontSize * SUBHEADING_FACTOR }.ifEmpty { lines }
        val leftMargin = percentile(bodyLines.map { it.x }, MARGIN_PERCENTILE)
        val rightEdge = percentile(bodyLines.map { it.right }, 1f - MARGIN_PERCENTILE)

        lines.forEachIndexed { index, line ->
            val prev = previous
            val samePage = index > 0
            when {
                line.columns >= TABLE_MIN_COLUMNS -> {
                    flush()
                    if (!inTable) stats.tables++
                    inTable = true
                    val row = line.text.split(PageText.COLUMN_SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }
                    emit(row.joinToString(", ") + ".", SegmentKind.LIST)
                    previous = line
                    return@forEachIndexed
                }
                line.fontSize >= profile.bodyFontSize * SUBHEADING_FACTOR && line.text.length <= MAX_SUBHEADING_CHARS -> {
                    flush()
                    stats.subheadings++
                    emit(line.text.trim(), SegmentKind.HEADING)
                    previous = null
                    inTable = false
                    return@forEachIndexed
                }
            }
            inTable = false
            val indented = line.x > leftMargin + INDENT
            if (prev != null && startsNewParagraph(prev, line, page, samePage, indented, previousRightEdge)) flush()
            append(line.text)
            bufferLines++
            if (!indented) bufferAllIndented = false
            previous = line
            previousRightEdge = rightEdge
        }
    }

    fun finishChapter(): List<Paragraph> {
        flush()
        return paragraphs.toList()
    }

    private fun startsNewParagraph(
        prev: TextLine,
        line: TextLine,
        page: PageText,
        samePage: Boolean,
        indented: Boolean,
        prevRightEdge: Float,
    ): Boolean {
        val text = line.text.trimStart()
        if (DIALOGUE_START.containsMatchIn(text) || LIST_START.containsMatchIn(text)) return true
        val prevEnds = prev.text.trimEnd().lastOrNull()?.let { it in TERMINATORS || it in CLOSERS } == true
        val prevShort = prev.right < prevRightEdge - SHORT_LINE
        if (samePage) {
            val gap = (line.y - prev.y) * page.heightPt
            if (gap > profile.lineSpacingPt * PARAGRAPH_GAP) return true
            if (gap < 0f) return prevEnds // Columna nueva: sigue el mismo párrafo si la frase no terminó.
        }
        return prevEnds && (indented || prevShort)
    }

    private fun append(raw: String) {
        val text = raw.trim()
        if (buffer.isEmpty()) {
            buffer.append(text)
            return
        }
        val last = buffer.last()
        if (last in HYPHENS && text.firstOrNull()?.isLowerCase() == true && buffer.length >= 2 && buffer[buffer.length - 2].isLetter()) {
            buffer.setLength(buffer.length - 1) // "pala-" + "bra" → "palabra"
            stats.hyphenationsFixed++
            buffer.append(text)
        } else if (last in HYPHENS) {
            buffer.append(text) // "Jean-" + "Paul": se conserva el guion
        } else {
            buffer.append(' ').append(text)
        }
    }

    private fun flush() {
        if (buffer.isNotEmpty()) {
            val text = buffer.toString()
            val kind = when {
                DIALOGUE_START.containsMatchIn(text) -> SegmentKind.DIALOGUE.also { stats.dialogues++ }
                LIST_START.containsMatchIn(text) -> SegmentKind.LIST.also { stats.lists++ }
                bufferAllIndented && bufferLines >= QUOTE_MIN_LINES -> SegmentKind.QUOTE.also { stats.quotes++ }
                else -> SegmentKind.NARRATION
            }
            emit(text, kind)
        }
        resetBuffer()
    }

    private fun emit(text: String, kind: SegmentKind) {
        val clean = TextNormalizer.normalizePage(text)
        if (clean.any { it.isLetterOrDigit() }) paragraphs += Paragraph(clean, kind)
    }

    private fun resetBuffer() {
        buffer.setLength(0)
        bufferLines = 0
        bufferAllIndented = true
    }

    /** Las notas al pie: letra pequeña al final de la página que empieza con un número o símbolo. */
    private fun footnoteStart(lines: List<TextLine>): Int {
        val small = profile.bodyFontSize * FOOTNOTE_FACTOR
        val start = lines.indexOfFirst { it.y > FOOTNOTE_ZONE && it.fontSize <= small && FOOTNOTE_MARKER.containsMatchIn(it.text) }
        if (start < 0) return lines.size
        return if (lines.subList(start, lines.size).all { it.fontSize <= small }) start else lines.size
    }

    private fun percentile(values: List<Float>, fraction: Float): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.sorted()
        return sorted[((sorted.size - 1) * fraction).toInt()]
    }

    private companion object {
        const val HALF_PAGE = 0.5f
        const val TERMINATORS = ".!?…:"
        const val CLOSERS = "\"'”’»)]"
        const val HYPHENS = "-­‐"
        const val INDENT = 0.02f
        const val SHORT_LINE = 0.1f
        const val PARAGRAPH_GAP = 1.5f
        const val MARGIN_PERCENTILE = 0.1f
        const val SUBHEADING_FACTOR = 1.25f
        const val MAX_SUBHEADING_CHARS = 90
        const val FOOTNOTE_FACTOR = 0.86f
        const val FOOTNOTE_ZONE = 0.6f
        const val TABLE_MIN_COLUMNS = 3
        const val QUOTE_MIN_LINES = 2

        val DIALOGUE_START = Regex("^[—–―«“\"]")
        val LIST_START = Regex("^(?:[•●▪◦‣·\\-*]\\s|\\d{1,2}[.)]\\s|[a-z][)]\\s)")
        val FOOTNOTE_MARKER = Regex("^\\s*(?:\\d{1,3}|[*†‡§])")
    }
}
