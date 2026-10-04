package app.narra.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.graphics.scale
import androidx.room.withTransaction
import app.narra.data.db.NarraDatabase
import app.narra.data.db.toAnalysisReport
import app.narra.data.db.toDomain
import app.narra.data.db.toParagraphs
import app.narra.data.files.BookStorage
import app.narra.domain.model.AnalysisReport
import app.narra.domain.model.Book
import app.narra.domain.model.BookMetadata
import app.narra.domain.model.BookState
import app.narra.domain.model.Chapter
import app.narra.domain.model.ErrorKind
import app.narra.domain.model.NarraException
import app.narra.domain.model.Paragraph
import app.narra.domain.model.ProcessingJob
import app.narra.domain.model.VoiceSettings
import app.narra.domain.repository.BookRepository
import app.narra.domain.repository.ProcessingQueue
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: NarraDatabase,
    private val storage: BookStorage,
    private val stats: BookStatsUpdater,
    private val queue: ProcessingQueue,
) : BookRepository {
    private val books = db.bookDao()
    private val chapters = db.chapterDao()
    private val segments = db.segmentDao()

    override fun observeBooks(): Flow<List<Book>> =
        books.observeAll().map { rows -> rows.map { it.toDomain() } }.distinctUntilChanged()

    override fun observeBook(id: String): Flow<Book?> =
        books.observe(id).map { it?.toDomain() }.distinctUntilChanged()

    override fun observeChapters(bookId: String): Flow<List<Chapter>> =
        chapters.observe(bookId).map { rows -> rows.map { it.toDomain() } }.distinctUntilChanged()

    override fun observeJob(bookId: String): Flow<ProcessingJob?> =
        db.jobDao().observe(bookId).map { it?.toDomain() }.distinctUntilChanged()

    override suspend fun getBook(id: String): Book? = books.getWithProgress(id)?.toDomain()

    override suspend fun getChapters(bookId: String): List<Chapter> = chapters.getAll(bookId).map { it.toDomain() }

    override suspend fun getAnalysisReport(bookId: String): AnalysisReport =
        books.get(bookId)?.analysisJson?.toAnalysisReport() ?: AnalysisReport()

    override suspend fun getChapterText(chapterId: Long): List<Paragraph> =
        segments.forChapter(chapterId).flatMap { it.paragraphsJson.toParagraphs() }

    override suspend fun bookIdsWithChapterMatching(query: String): Set<String> {
        if (query.isBlank()) return emptySet()
        return chapters.bookIdsWithTitleLike("%${query.trim()}%").toSet()
    }

    override suspend fun setFavorite(id: String, favorite: Boolean) = books.setFavorite(id, favorite)

    override suspend fun updateMetadata(id: String, metadata: BookMetadata) {
        val current = books.get(id) ?: return
        books.update(
            current.copy(
                title = metadata.title.trim().ifEmpty { current.title },
                author = metadata.author?.trim()?.takeIf { it.isNotEmpty() },
                description = metadata.description?.trim()?.takeIf { it.isNotEmpty() },
                language = metadata.language,
                publisher = metadata.publisher?.trim()?.takeIf { it.isNotEmpty() },
                publishedDate = metadata.publishedDate?.trim()?.takeIf { it.isNotEmpty() },
                isbn = metadata.isbn?.trim()?.takeIf { it.isNotEmpty() },
                keywords = metadata.keywords.joinToString("\n").takeIf { it.isNotBlank() },
            ),
        )
    }

    override suspend fun updateVoice(id: String, voice: VoiceSettings) {
        val current = books.get(id) ?: return
        books.update(
            current.copy(
                voiceProviderId = voice.providerId,
                voiceEngine = voice.engine,
                voiceId = voice.voiceId,
                voiceLanguage = voice.languageTag,
                speechRate = voice.speechRate,
                pitch = voice.pitch,
                quality = voice.quality,
                format = voice.format,
                paragraphPauseMs = voice.paragraphPauseMs,
                chapterPauseMs = voice.chapterPauseMs,
            ),
        )
        stats.refreshBook(id)
    }

    override suspend fun setCover(id: String, image: Uri) {
        val target = storage.cover(id)
        withContext(Dispatchers.IO) {
            val bitmap = context.contentResolver.openInputStream(image)?.use { input ->
                BitmapFactory.decodeStream(input)
            } ?: throw NarraException(ErrorKind.FILE_MISSING, "No se pudo leer la imagen")
            val scaled = bitmap.scaledToWidth(COVER_WIDTH_PX)
            val partial = File(target.parentFile, "${target.name}.part")
            partial.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, COVER_JPEG_QUALITY, it) }
            if (!partial.renameTo(target)) throw NarraException(ErrorKind.STORAGE_FULL)
        }
        // La ruta no cambia: se fuerza un valor nuevo para que la UI recargue la imagen.
        books.setCover(id, null)
        books.setCover(id, target.absolutePath)
    }

    override suspend fun renameChapter(chapterId: Long, title: String) {
        val clean = title.trim()
        if (clean.isNotEmpty()) chapters.rename(chapterId, clean)
    }

    override suspend fun setChapterIncluded(bookId: String, chapterId: Long, included: Boolean) {
        chapters.setIncluded(chapterId, included)
        stats.refreshBook(bookId)
    }

    override suspend fun moveChapter(bookId: String, chapterId: Long, offset: Int) {
        db.withTransaction {
            val list = chapters.getAll(bookId).toMutableList()
            val from = list.indexOfFirst { it.id == chapterId }
            if (from < 0) return@withTransaction
            val to = (from + offset).coerceIn(0, list.lastIndex)
            if (from == to) return@withTransaction
            list.add(to, list.removeAt(from))
            chapters.updateAll(list.mapIndexed { index, chapter -> chapter.copy(orderIndex = index) })
        }
    }

    override suspend fun deleteAudio(id: String) {
        queue.cancelGeneration(id)
        db.withTransaction {
            db.audioTrackDao().deleteForBook(id)
            segments.resetAll(id)
            db.progressDao().delete(id)
        }
        storage.deleteAudio(id)
        stats.refreshBook(id)
        books.setState(id, BookState.READY)
    }

    override suspend fun deleteBook(id: String) {
        queue.cancelAnalysis(id)
        // Al borrar el libro desaparece su trabajo y el generador interrumpe el segmento en curso.
        books.delete(id) // en cascada: capítulos, segmentos, pistas, progreso y trabajos
        storage.deleteBook(id)
    }

    private fun Bitmap.scaledToWidth(width: Int): Bitmap {
        if (this.width <= width) return this
        val height = (this.height * width.toFloat() / this.width).toInt().coerceAtLeast(1)
        return scale(width, height)
    }

    companion object {
        const val COVER_WIDTH_PX = 720
        const val COVER_JPEG_QUALITY = 88
    }
}
