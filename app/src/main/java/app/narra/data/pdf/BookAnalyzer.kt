package app.narra.data.pdf

import androidx.room.withTransaction
import app.narra.analysis.ChapterDetector
import app.narra.analysis.ChapterMark
import app.narra.analysis.ChapterPlan
import app.narra.analysis.CleaningStats
import app.narra.analysis.DocumentProfile
import app.narra.analysis.MetadataHeuristics
import app.narra.analysis.PageText
import app.narra.analysis.ParagraphBuilder
import app.narra.analysis.Segmenter
import app.narra.data.db.ChapterEntity
import app.narra.data.db.NarraDatabase
import app.narra.data.db.SegmentEntity
import app.narra.data.db.ocrPages
import app.narra.data.db.toJson
import app.narra.data.files.BookStorage
import app.narra.data.ocr.PageRecognizer
import app.narra.data.repository.BookStatsUpdater
import app.narra.domain.model.AnalysisReport
import app.narra.domain.model.BookState
import app.narra.domain.model.ChapterState
import app.narra.domain.model.ErrorKind
import app.narra.domain.model.NarraException
import app.narra.domain.model.Paragraph
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Convierte un PDF importado en capítulos y segmentos listos para generar audio:
 *
 * 1. Lee el PDF una vez y guarda las páginas en disco mientras aprende su perfil.
 * 2. Si se pidió, reconoce el texto de las páginas escaneadas y lo incorpora a esas páginas.
 * 3. Detecta los capítulos recorriendo esas páginas.
 * 4. Limpia el texto capítulo a capítulo y lo guarda segmentado en la base de datos.
 *
 * En ningún momento se tiene el libro entero en memoria.
 */
@Singleton
class BookAnalyzer @Inject constructor(
    private val db: NarraDatabase,
    private val storage: BookStorage,
    private val parser: PdfParser,
    private val coverRenderer: CoverRenderer,
    private val stats: BookStatsUpdater,
    private val recognizer: PageRecognizer,
) {
    private val books = db.bookDao()

    /**
     * Analiza el libro. Con [ocr], reconoce antes el texto de las páginas escaneadas que aún no
     * lo tengan e informa de cada página con [onOcrProgress].
     */
    suspend fun analyze(bookId: String, ocr: Boolean = false, onOcrProgress: suspend (done: Int, total: Int) -> Unit = { _, _ -> }) {
        val book = books.get(bookId) ?: return
        // En un reanálisis se respeta el título que el usuario ya revisó.
        val firstAnalysis = book.analysisJson == null
        try {
            books.setState(bookId, BookState.ANALYZING)
            val source = storage.sourcePdf(bookId)
            val pagesFile = storage.pagesFile(bookId)
            val (parsed, extracted) = extract(bookId, source, pagesFile)

            val scanned = PageStore.read(pagesFile) { pages -> pages.filter { it.looksScanned }.map { it.index }.toList() }
            // El usuario puede limitar el OCR a unas páginas: el resto de las escaneadas se queda sin texto.
            val chosen = book.ocrPages()
            if (ocr) recognizeMissing(bookId, source, chosen?.let { range -> scanned.filter { it in range } } ?: scanned, onOcrProgress)
            val recognized = scanned.filter { storage.ocrPage(bookId, it).exists() }
            val profile = if (recognized.isEmpty()) extracted else mergeRecognized(bookId, pagesFile, recognized.toSet(), extracted.pageCount)

            if (profile.totalChars < MIN_BOOK_CHARS || (profile.isMostlyScanned && chosen == null)) {
                // Sin capa de texto: hace falta reconocimiento óptico (o no sirvió).
                throw NarraException(if (ocr) ErrorKind.OCR_FAILED else ErrorKind.NO_TEXT)
            }
            if (book.coverPath == null && profile.earlyPages.firstOrNull()?.looksLikeCover() == true) {
                val cover = storage.cover(bookId)
                if (coverRenderer.render(source, cover)) books.setCover(bookId, cover.absolutePath)
            }

            books.setState(bookId, BookState.PARSING)
            val metadata = MetadataHeuristics.combine(parsed.metadata, profile)
            val plan = PageStore.read(pagesFile) { pages -> ChapterDetector(profile).detect(parsed.outline, pages) }
            val cleaning = CleaningStats()
            writeChapters(bookId, plan, profile, pagesFile, cleaning)

            val report = AnalysisReport(
                chapterSource = plan.source,
                headings = plan.headingsFound + cleaning.subheadings,
                headersRemoved = cleaning.headersRemoved,
                footersRemoved = cleaning.footersRemoved,
                pageNumbersRemoved = cleaning.pageNumbersRemoved,
                footnotes = cleaning.footnotes,
                images = profile.images,
                tables = cleaning.tables,
                lists = cleaning.lists,
                quotes = cleaning.quotes,
                dialogues = cleaning.dialogues,
                references = plan.marks.count { !it.included },
                scannedPages = profile.scannedPages,
                hyphenationsFixed = cleaning.hyphenationsFixed,
                ocrPages = recognized.size,
            )
            val current = books.get(bookId) ?: return
            books.update(
                current.copy(
                    title = metadata.title?.takeIf { firstAnalysis } ?: current.title,
                    author = current.author ?: metadata.author,
                    description = current.description ?: metadata.description,
                    language = metadata.language ?: current.language,
                    isbn = current.isbn ?: metadata.isbn,
                    keywords = current.keywords ?: metadata.keywords.joinToString("\n").takeIf { it.isNotBlank() },
                    pageCount = profile.pageCount,
                    analysisJson = report.toJson(),
                    // La voz por defecto sigue el idioma del libro si nadie eligió otra.
                    voiceLanguage = current.voiceLanguage ?: metadata.language,
                    state = BookState.READY,
                    errorKind = null,
                    errorDetail = null,
                ),
            )
            stats.refreshBook(bookId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: NarraException) {
            books.setState(bookId, BookState.ERROR, e.kind, e.message)
        } catch (e: IOException) {
            val kind = if (storage.availableBytes() < LOW_SPACE_BYTES) ErrorKind.STORAGE_FULL else ErrorKind.UNKNOWN
            books.setState(bookId, BookState.ERROR, kind, e.message)
        } catch (e: RuntimeException) {
            // PdfBox lanza excepciones no comprobadas ante estructuras inesperadas.
            books.setState(bookId, BookState.ERROR, ErrorKind.PDF_CORRUPT, e.message)
        }
    }

    /** Primera pasada: PDF → páginas en disco + perfil, informando del avance. */
    private suspend fun extract(bookId: String, source: File, pagesFile: File): Pair<ParsedDocument, DocumentProfile> =
        coroutineScope {
            val partial = File(pagesFile.parentFile, "${pagesFile.name}.part")
            val progress = MutableStateFlow(0 to 0)
            var builder: DocumentProfile.Builder? = null
            val reporter = launch {
                var reported = -1
                while (isActive) {
                    delay(PROGRESS_INTERVAL_MS)
                    val (done, total) = progress.value
                    if (done != reported) books.setAnalysisProgress(bookId, done, total, scanned = 0)
                    reported = done
                }
            }
            val parsed = partial.bufferedWriter().use { writer ->
                parser.parse(
                    file = source,
                    onStart = { document ->
                        builder = DocumentProfile.Builder(document.pageCount)
                        progress.value = 0 to document.pageCount
                    },
                    onPage = { page ->
                        PageStore.write(writer, page)
                        builder?.accept(page)
                        progress.value = page.index + 1 to progress.value.second
                    },
                )
            }
            reporter.cancel()
            if (!partial.renameTo(pagesFile)) {
                pagesFile.delete()
                if (!partial.renameTo(pagesFile)) throw IOException("No se pudo guardar el texto extraído")
            }
            val profile = builder?.build() ?: throw NarraException(ErrorKind.PDF_EMPTY)
            books.setAnalysisProgress(bookId, parsed.pageCount, parsed.pageCount, profile.scannedPages)
            parsed to profile
        }

    /** Reconoce las páginas escaneadas que todavía no tienen texto guardado. */
    private suspend fun recognizeMissing(
        bookId: String,
        source: File,
        scanned: List<Int>,
        onProgress: suspend (done: Int, total: Int) -> Unit,
    ) {
        val missing = scanned.filterNot { storage.ocrPage(bookId, it).exists() }
        if (missing.isEmpty()) return
        books.setState(bookId, BookState.OCR)
        var done = 0
        books.setOcrProgress(bookId, done, missing.size)
        onProgress(done, missing.size)
        recognizer.recognize(source, missing) { page ->
            withContext(Dispatchers.IO) { PageStore.saveSingle(storage.ocrPage(bookId, page.index), page) }
            done++
            books.setOcrProgress(bookId, done, missing.size)
            onProgress(done, missing.size)
        }
    }

    /** Sustituye las páginas escaneadas por su texto reconocido y recalcula el perfil. */
    private suspend fun mergeRecognized(bookId: String, pagesFile: File, recognized: Set<Int>, pageCount: Int): DocumentProfile =
        withContext(Dispatchers.IO) {
            val builder = DocumentProfile.Builder(pageCount)
            val partial = File(pagesFile.parentFile, "${pagesFile.name}.part")
            partial.bufferedWriter().use { writer ->
                PageStore.read(pagesFile) { pages ->
                    for (page in pages) {
                        val merged = if (page.index in recognized) PageStore.loadSingle(storage.ocrPage(bookId, page.index)) else page
                        PageStore.write(writer, merged)
                        builder.accept(merged)
                    }
                }
            }
            if (!partial.renameTo(pagesFile)) {
                pagesFile.delete()
                if (!partial.renameTo(pagesFile)) throw IOException("No se pudo guardar el texto reconocido")
            }
            builder.build()
        }

    /** Tercera pasada: texto limpio por capítulo → segmentos en la base de datos. */
    private suspend fun writeChapters(
        bookId: String,
        plan: ChapterPlan,
        profile: DocumentProfile,
        pagesFile: File,
        cleaning: CleaningStats,
    ) {
        val marks = plan.marks
        val chapterIds = db.withTransaction {
            db.chapterDao().deleteForBook(bookId) // reanálisis: se empieza de cero
            db.chapterDao().insertAll(
                marks.mapIndexed { i, mark ->
                    ChapterEntity(
                        bookId = bookId,
                        orderIndex = i,
                        title = mark.title,
                        level = mark.level,
                        startPage = mark.pageIndex,
                        endPage = (marks.getOrNull(i + 1)?.pageIndex ?: profile.pageCount).coerceAtLeast(mark.pageIndex + 1) - 1,
                        included = mark.included,
                        state = if (mark.included) ChapterState.PENDING else ChapterState.SKIPPED,
                    )
                },
            )
        }
        val builder = ParagraphBuilder(profile, cleaning)
        val startsByPage: Map<Int, List<IndexedValue<ChapterMark>>> = marks.withIndex().groupBy { it.value.pageIndex }
        var current = -1

        suspend fun finish() {
            if (current < 0) return
            saveSegments(bookId, chapterIds[current], builder.finishChapter())
        }

        PageStore.read(pagesFile) { pages ->
            for (page in pages) {
                var cursor = 0
                var skip = IntRange.EMPTY
                startsByPage[page.index].orEmpty().sortedBy { it.value.lineIndex }.forEach { (index, mark) ->
                    if (current >= 0) builder.addPage(page, cursor, mark.lineIndex, skip)
                    finish()
                    current = index
                    builder.startChapter(mark.title)
                    cursor = mark.lineIndex
                    skip = mark.lineIndex until mark.lineIndex + mark.titleLines
                }
                if (current >= 0) builder.addPage(page, cursor, page.lines.size, skip)
            }
        }
        finish()
    }

    private suspend fun saveSegments(bookId: String, chapterId: Long, paragraphs: List<Paragraph>) {
        val segments = Segmenter.segment(paragraphs).mapIndexed { index, group ->
            SegmentEntity(
                bookId = bookId,
                chapterId = chapterId,
                orderIndex = index,
                paragraphsJson = group.toJson(),
                charCount = group.sumOf { it.text.length },
            )
        }
        if (segments.isNotEmpty()) db.segmentDao().insertAll(segments)
    }

    /** Una primera página con imagen o con muy poco texto suele ser la cubierta. */
    private fun PageText.looksLikeCover(): Boolean = images > 0 || charCount < COVER_MAX_CHARS

    private companion object {
        const val MIN_BOOK_CHARS = 200
        const val COVER_MAX_CHARS = 400
        const val PROGRESS_INTERVAL_MS = 400L
        const val LOW_SPACE_BYTES = 32L * 1024 * 1024
    }
}
