package app.narra.data.pdf

import app.narra.analysis.OutlineEntry
import app.narra.analysis.PageText
import app.narra.analysis.PdfMetadata
import app.narra.analysis.TextLine
import app.narra.domain.model.ErrorKind
import app.narra.domain.model.NarraException
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.Writer
import javax.inject.Inject

/** Estructura del documento que no depende del texto de las páginas. */
data class ParsedDocument(val pageCount: Int, val metadata: PdfMetadata, val outline: List<OutlineEntry>)

/** Lectura del PDF. La implementación por defecto usa PdfBox; se puede sustituir en pruebas. */
interface PdfParser {
    /**
     * Recorre el documento una vez, entregando cada página a [onPage] en orden. No conserva las
     * páginas: quien las recibe decide si las guarda en disco.
     */
    suspend fun parse(file: File, onStart: (ParsedDocument) -> Unit, onPage: (PageText) -> Unit): ParsedDocument
}

class PdfBoxParser @Inject constructor() : PdfParser {

    override suspend fun parse(file: File, onStart: (ParsedDocument) -> Unit, onPage: (PageText) -> Unit): ParsedDocument =
        withContext(Dispatchers.IO) {
            if (!file.exists()) throw NarraException(ErrorKind.FILE_MISSING)
            val document = try {
                // Las estructuras del PDF van a un archivo temporal, no a la memoria.
                PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly())
            } catch (e: InvalidPasswordException) {
                throw NarraException(ErrorKind.PDF_ENCRYPTED, cause = e)
            } catch (e: IOException) {
                throw NarraException(ErrorKind.PDF_CORRUPT, e.message, e)
            }
            document.use { doc ->
                val pageCount = doc.numberOfPages
                if (pageCount == 0) throw NarraException(ErrorKind.PDF_EMPTY)
                val parsed = ParsedDocument(pageCount, metadata(doc), outline(doc))
                onStart(parsed)
                val job = coroutineContext
                val stripper = LineCollector { page ->
                    job.ensureActive() // cancela entre páginas si el usuario lo pide
                    onPage(page)
                }
                try {
                    stripper.writeText(doc, NullWriter)
                } catch (e: IOException) {
                    throw NarraException(ErrorKind.PDF_CORRUPT, e.message, e)
                }
                parsed
            }
        }

    private fun metadata(doc: PDDocument): PdfMetadata {
        val info = runCatching { doc.documentInformation }.getOrNull()
        return PdfMetadata(
            title = info?.title,
            author = info?.author,
            subject = info?.subject,
            keywords = info?.keywords,
            language = runCatching { doc.documentCatalog.language }.getOrNull(),
        )
    }

    private fun outline(doc: PDDocument): List<OutlineEntry> {
        val root = runCatching { doc.documentCatalog.documentOutline }.getOrNull() ?: return emptyList()
        val entries = ArrayList<OutlineEntry>()
        fun walk(node: PDOutlineNode, level: Int) {
            if (level > MAX_OUTLINE_DEPTH) return
            for (item in node.children()) {
                val page = runCatching { item.findDestinationPage(doc) }.getOrNull()
                val index = page?.let { doc.pages.indexOf(it) } ?: -1
                val title = item.title?.trim().orEmpty()
                if (index >= 0 && title.isNotEmpty()) entries += OutlineEntry(title, index, level)
                walk(item, level + 1)
                if (entries.size > MAX_OUTLINE_ENTRIES) return
            }
        }
        runCatching { walk(root, 0) }
        return entries
    }

    /**
     * Reconstruye las líneas con su posición y tamaño de letra. PdfBox entrega palabra a
     * palabra; aquí se agrupan en líneas y se descartan las llamadas de nota en superíndice.
     */
    private class LineCollector(private val onPage: (PageText) -> Unit) : PDFTextStripper() {
        private val lines = ArrayList<TextLine>()
        private val words = ArrayList<List<TextPosition>>()
        private var pageWidth = 1f
        private var pageHeight = 1f
        private var pageImages = 0

        init {
            sortByPosition = true
            setSuppressDuplicateOverlappingText(true)
        }

        override fun startPage(page: PDPage) {
            lines.clear()
            words.clear()
            val box = page.cropBox ?: page.mediaBox
            pageWidth = box.width.coerceAtLeast(1f)
            pageHeight = box.height.coerceAtLeast(1f)
            pageImages = runCatching {
                val resources = page.resources
                resources?.xObjectNames?.count { resources.isImageXObject(it) } ?: 0
            }.getOrDefault(0)
            super.startPage(page)
        }

        override fun writeString(text: String, textPositions: List<TextPosition>) {
            if (textPositions.isNotEmpty()) words += textPositions.toList()
        }

        override fun writeWordSeparator() = Unit

        override fun writeLineSeparator() = flushLine()

        override fun endPage(page: PDPage) {
            flushLine()
            super.endPage(page)
            onPage(PageText(currentPageNo - 1, pageHeight, lines.sortedBy { it.y }.toList(), pageImages))
        }

        private fun flushLine() {
            if (words.isEmpty()) return
            val all = words.flatten()
            val size = all.groupingBy { (it.fontSizeInPt * 2).toInt() }.eachCount().maxByOrNull { it.value }?.key?.div(2f) ?: return
            val baseline = all.filter { it.fontSizeInPt >= size * SUPERSCRIPT_FACTOR }.maxOfOrNull { it.yDirAdj } ?: all.maxOf { it.yDirAdj }
            val text = StringBuilder()
            var columns = 1
            var previousEnd: Float? = null
            for (word in words) {
                val kept = word.filterNot { it.isSuperscriptMarker(size, baseline) }
                if (kept.isEmpty()) continue
                val start = kept.first().xDirAdj
                previousEnd?.let { end ->
                    val gap = start - end
                    if (gap > size * COLUMN_GAP_EMS) {
                        text.append(PageText.COLUMN_SEPARATOR)
                        columns++
                    } else {
                        text.append(' ')
                    }
                }
                kept.forEach { text.append(it.unicode) }
                previousEnd = kept.last().let { it.xDirAdj + it.widthDirAdj }
            }
            words.clear()
            val content = text.toString().trim()
            if (content.isEmpty()) return
            val first = all.minOf { it.xDirAdj }
            val last = all.maxOf { it.xDirAdj + it.widthDirAdj }
            val boldChars = all.count { it.isBold() }
            lines += TextLine(
                text = content,
                fontSize = size,
                x = first / pageWidth,
                y = baseline / pageHeight,
                width = (last - first) / pageWidth,
                bold = boldChars * 2 > all.size,
                columns = columns,
            )
        }

        private fun TextPosition.isSuperscriptMarker(lineSize: Float, baseline: Float): Boolean =
            fontSizeInPt < lineSize * SUPERSCRIPT_FACTOR &&
                yDirAdj < baseline - lineSize * SUPERSCRIPT_RISE &&
                unicode.all { it.isDigit() || it in FOOTNOTE_SYMBOLS }

        private fun TextPosition.isBold(): Boolean {
            val font = font ?: return false
            val name = runCatching { font.name }.getOrNull().orEmpty()
            val descriptor = font.fontDescriptor
            return BOLD_NAME.containsMatchIn(name) ||
                descriptor?.isForceBold == true ||
                (descriptor?.fontWeight ?: 0f) >= BOLD_WEIGHT
        }
    }

    /** El texto se recoge por las líneas, no por el flujo de salida. */
    private object NullWriter : Writer() {
        override fun write(cbuf: CharArray, off: Int, len: Int) = Unit
        override fun flush() = Unit
        override fun close() = Unit
    }

    private companion object {
        const val MAX_OUTLINE_DEPTH = 2
        const val MAX_OUTLINE_ENTRIES = 2_000
        const val SUPERSCRIPT_FACTOR = 0.75f
        const val SUPERSCRIPT_RISE = 0.25f
        const val COLUMN_GAP_EMS = 2.5f
        const val BOLD_WEIGHT = 600f
        const val FOOTNOTE_SYMBOLS = "*†‡§"
        val BOLD_NAME = Regex("bold|black|heavy|semibold|demibold", RegexOption.IGNORE_CASE)
    }
}
