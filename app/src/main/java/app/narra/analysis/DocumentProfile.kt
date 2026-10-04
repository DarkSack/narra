package app.narra.analysis

import kotlin.math.roundToInt

/**
 * Lo que se aprende del documento en la primera lectura, sin guardar el libro en memoria:
 * tamaño de letra del cuerpo, interlineado, encabezados y pies repetidos, páginas escaneadas
 * y una muestra de texto para detectar idioma e ISBN.
 */
class DocumentProfile private constructor(
    val pageCount: Int,
    /** Tamaño de letra más frecuente (ponderado por caracteres): el del cuerpo del texto. */
    val bodyFontSize: Float,
    /** Distancia habitual entre líneas consecutivas del cuerpo, en puntos. */
    val lineSpacingPt: Float,
    private val repeatedZoneSignatures: Set<String>,
    val scannedPages: Int,
    val textSample: String,
    val frontText: String,
    val backText: String,
    /** Primeras páginas completas: título probable e índice impreso. */
    val earlyPages: List<PageText>,
    val totalChars: Long,
    val images: Int,
    /** Caracteres de cada página, para repartir capítulos cuando no hay otra pista. */
    val pageChars: IntArray,
) {
    /** Encabezado o pie corrido (título del libro, nombre del capítulo, número de página). */
    fun isRunningHeaderOrFooter(line: TextLine): Boolean {
        if (!inMarginZone(line)) return false
        return isPageNumber(line.text) || zoneSignature(line.text) in repeatedZoneSignatures
    }

    fun isPageNumberLine(line: TextLine): Boolean = inMarginZone(line) && isPageNumber(line.text)

    val isMostlyScanned: Boolean get() = pageCount > 0 && scannedPages * 2 > pageCount

    class Builder(private val pageCount: Int) {
        private val fontChars = HashMap<Int, Long>()
        private val spacingCounts = HashMap<Int, Int>()
        private val zoneCounts = HashMap<String, Int>()
        private var scanned = 0
        private var totalChars = 0L
        private var images = 0
        private val sample = StringBuilder()
        private val front = StringBuilder()
        private val back = ArrayDeque<String>()
        private val early = ArrayList<PageText>()
        private val pageChars = IntArray(pageCount)
        private val earlyLimit = earlyPageLimit(pageCount)

        fun accept(page: PageText) {
            images += page.images
            if (page.looksScanned) scanned++
            totalChars += page.charCount
            if (page.index in pageChars.indices) pageChars[page.index] = page.charCount
            page.lines.forEach { line ->
                val key = fontKey(line.fontSize)
                fontChars[key] = (fontChars[key] ?: 0L) + line.text.length
            }
            page.lines.zipWithNext().forEach { (a, b) ->
                if (fontKey(a.fontSize) == fontKey(b.fontSize) && b.y > a.y) {
                    val spacing = ((b.y - a.y) * page.heightPt * SPACING_PRECISION).roundToInt()
                    if (spacing > 0) spacingCounts[spacing] = (spacingCounts[spacing] ?: 0) + 1
                }
            }
            // Cada firma cuenta una vez por página.
            page.lines.filter(::inMarginZone).map { zoneSignature(it.text) }.toSet().forEach {
                zoneCounts[it] = (zoneCounts[it] ?: 0) + 1
            }
            val text = page.lines.joinToString(" ") { it.text }
            if (sample.length < SAMPLE_CHARS && page.index >= pageCount / SAMPLE_SKIP_FRACTION) sample.append(text).append(' ')
            if (page.index < ISBN_FRONT_PAGES) front.append(text).append('\n')
            back.addLast(text)
            if (back.size > ISBN_BACK_PAGES) back.removeFirst()
            if (page.index < earlyLimit) early += page
        }

        fun build(): DocumentProfile {
            val body = fontChars.maxByOrNull { it.value }?.key?.let { it / FONT_PRECISION } ?: DEFAULT_BODY_SIZE
            val spacing = spacingCounts.maxByOrNull { it.value }?.key?.let { it / SPACING_PRECISION.toFloat() }
                ?: body * DEFAULT_LINE_SPACING_FACTOR
            val threshold = maxOf(MIN_REPEATS, (pageCount * REPEAT_FRACTION).roundToInt())
            return DocumentProfile(
                pageCount = pageCount,
                bodyFontSize = body,
                lineSpacingPt = spacing,
                repeatedZoneSignatures = if (pageCount >= MIN_REPEATS) zoneCounts.filterValues { it >= threshold }.keys else emptySet(),
                scannedPages = scanned,
                textSample = sample.toString(),
                frontText = front.toString(),
                backText = back.joinToString("\n"),
                earlyPages = early,
                totalChars = totalChars,
                images = images,
                pageChars = pageChars,
            )
        }
    }

    companion object {
        private const val FONT_PRECISION = 2f
        private const val SPACING_PRECISION = 2
        private const val DEFAULT_BODY_SIZE = 11f
        private const val DEFAULT_LINE_SPACING_FACTOR = 1.2f

        /** Franja superior e inferior donde viven encabezados y pies. */
        private const val MARGIN_ZONE = 0.09f
        private const val MIN_REPEATS = 3
        private const val REPEAT_FRACTION = 0.25f

        private const val SAMPLE_CHARS = 20_000
        /** La muestra de idioma salta las primeras páginas (créditos, legales). */
        private const val SAMPLE_SKIP_FRACTION = 10
        private const val ISBN_FRONT_PAGES = 8
        private const val ISBN_BACK_PAGES = 4
        private const val EARLY_PAGES_MIN = 12
        private const val EARLY_PAGES_MAX = 30
        private const val EARLY_PAGES_FRACTION = 0.15f

        private val digits = Regex("\\d+")
        private val spaces = Regex("\\s+")
        private val pageNumber = Regex("^[\\s\\-–—·|]*(?:p[áa]g(?:ina)?\\.?\\s*)?(\\d{1,4}|[ivxlcdm]{1,7})(?:\\s*(?:/|de|of)\\s*\\d{1,4})?[\\s\\-–—·|]*$", RegexOption.IGNORE_CASE)

        fun earlyPageLimit(pageCount: Int): Int =
            (pageCount * EARLY_PAGES_FRACTION).roundToInt().coerceIn(EARLY_PAGES_MIN, EARLY_PAGES_MAX)

        private fun fontKey(size: Float): Int = (size * FONT_PRECISION).roundToInt()

        fun inMarginZone(line: TextLine): Boolean = line.y < MARGIN_ZONE || line.y > 1f - MARGIN_ZONE

        fun isPageNumber(text: String): Boolean = pageNumber.matches(text.trim())

        /** "Capítulo 3 · El faro   47" y "Capítulo 3 · El faro   48" comparten firma. */
        fun zoneSignature(text: String): String =
            spaces.replace(digits.replace(text.lowercase(), "#"), " ").trim()
    }
}
