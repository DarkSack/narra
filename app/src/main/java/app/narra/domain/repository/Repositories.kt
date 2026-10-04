package app.narra.domain.repository

import android.net.Uri
import app.narra.domain.model.AnalysisReport
import app.narra.domain.model.AppSettings
import app.narra.domain.model.Book
import app.narra.domain.model.BookMetadata
import app.narra.domain.model.Chapter
import app.narra.domain.model.ErrorKind
import app.narra.domain.model.ListeningProgress
import app.narra.domain.model.Paragraph
import app.narra.domain.model.PlayableTrack
import app.narra.domain.model.ProcessingJob
import app.narra.domain.model.VoiceSettings
import kotlinx.coroutines.flow.Flow

interface BookRepository {
    fun observeBooks(): Flow<List<Book>>
    fun observeBook(id: String): Flow<Book?>
    fun observeChapters(bookId: String): Flow<List<Chapter>>
    fun observeJob(bookId: String): Flow<ProcessingJob?>

    suspend fun getBook(id: String): Book?
    suspend fun getChapters(bookId: String): List<Chapter>
    suspend fun getAnalysisReport(bookId: String): AnalysisReport

    /** Texto limpio de un capítulo, para revisarlo antes de generar el audio. */
    suspend fun getChapterText(chapterId: Long): List<Paragraph>

    /** Ids de libros con algún capítulo cuyo título contiene [query]. */
    suspend fun bookIdsWithChapterMatching(query: String): Set<String>

    suspend fun setFavorite(id: String, favorite: Boolean)
    suspend fun updateMetadata(id: String, metadata: BookMetadata)
    suspend fun updateVoice(id: String, voice: VoiceSettings)
    suspend fun setCover(id: String, image: Uri)

    suspend fun renameChapter(chapterId: Long, title: String)
    suspend fun setChapterIncluded(bookId: String, chapterId: Long, included: Boolean)
    suspend fun moveChapter(bookId: String, chapterId: Long, offset: Int)

    /** Borra el audio generado (se puede volver a generar). */
    suspend fun deleteAudio(id: String)

    /** Borra libro, capítulos, audio y archivos. */
    suspend fun deleteBook(id: String)
}

interface PlaybackRepository {
    fun observePlayableTracks(bookId: String): Flow<List<PlayableTrack>>
    suspend fun playableTracks(bookId: String): List<PlayableTrack>
    fun observeLastPlayedBookId(): Flow<String?>
    suspend fun getProgress(bookId: String): ListeningProgress?
    suspend fun saveProgress(progress: ListeningProgress)
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun update(transform: (AppSettings) -> AppSettings)
}

sealed interface ImportResult {
    data class Imported(val bookId: String) : ImportResult

    /** Ya hay un libro con el mismo archivo (mismo nombre y tamaño). */
    data class AlreadyInLibrary(val bookId: String, val title: String) : ImportResult

    data class Failed(val kind: ErrorKind) : ImportResult
}

interface ImportRepository {
    /**
     * Copia el PDF al almacenamiento privado de la app y crea el libro. El análisis empieza
     * después, en segundo plano: el documento nunca sale del teléfono.
     */
    suspend fun importPdf(uri: Uri, allowDuplicate: Boolean = false): ImportResult
}

/**
 * Trabajo en segundo plano sobre los libros. El análisis corre por libro; la generación de audio
 * es una cola única (un libro tras otro) para no saturar el motor de voz ni la batería.
 */
interface ProcessingQueue {
    fun analyze(bookId: String, restart: Boolean = false)
    fun cancelAnalysis(bookId: String)

    /** La cola de generación en el orden en que se procesará; los libros detenidos van al final. */
    fun observeQueue(): Flow<List<ProcessingJob>>

    /** Pone el libro en la cola de generación, o lo continúa donde se quedó. */
    suspend fun generate(bookId: String)
    suspend fun pause(bookId: String)
    suspend fun resume(bookId: String)

    /** Saca el libro de la cola. El audio ya creado se conserva y se puede seguir más tarde. */
    suspend fun cancelGeneration(bookId: String)

    /** Vuelve a intentar las partes que fallaron. */
    suspend fun retry(bookId: String)

    /** El usuario quiere escuchar este capítulo: se genera antes que los demás. */
    suspend fun prioritizeChapter(bookId: String, chapterId: Long)

    /** Adelanta el libro al principio de la cola. */
    suspend fun moveToFront(bookId: String)

    /** Arranca el procesador si quedaron trabajos pendientes (por ejemplo, tras reiniciar el teléfono). */
    suspend fun resumePending()
}
