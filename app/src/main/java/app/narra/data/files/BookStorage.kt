package app.narra.data.files

import android.content.Context
import android.os.StatFs
import app.narra.domain.model.AudioFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class StorageUsage(
    val audioBytes: Long,
    val sourceBytes: Long,
    val workingBytes: Long,
    val tempBytes: Long,
    val availableBytes: Long,
) {
    val totalBytes: Long get() = audioBytes + sourceBytes + workingBytes + tempBytes
}

/**
 * Dónde vive cada archivo de un libro. La base de datos solo guarda rutas.
 *
 * ```
 * files/books/<id>/source.pdf        PDF original
 * files/books/<id>/cover.jpg         portada
 * files/books/<id>/pages.jsonl       texto por página con posiciones (análisis y OCR)
 * files/books/<id>/audio/<cap>/<seg>.m4a
 * cache/synthesis/                   WAV temporales del motor de voz
 * ```
 */
@Singleton
class BookStorage @Inject constructor(@ApplicationContext private val context: Context) {
    private val root: File get() = File(context.filesDir, "books")

    fun bookDir(bookId: String): File = File(root, bookId)
    fun sourcePdf(bookId: String): File = File(bookDir(bookId), "source.pdf")
    fun cover(bookId: String): File = File(bookDir(bookId), "cover.jpg")
    fun pagesFile(bookId: String): File = File(bookDir(bookId), "pages.jsonl")
    fun audioDir(bookId: String): File = File(bookDir(bookId), "audio")

    fun audioFile(bookId: String, chapterId: Long, segmentId: Long, format: AudioFormat): File =
        File(File(audioDir(bookId), chapterId.toString()), "$segmentId.${format.extension}")

    fun tempDir(): File = File(context.cacheDir, "synthesis").apply { mkdirs() }

    fun exportDir(): File = File(context.cacheDir, "export").apply { mkdirs() }

    suspend fun ensureBookDir(bookId: String): File = withContext(Dispatchers.IO) {
        bookDir(bookId).apply { mkdirs() }
    }

    suspend fun deleteBook(bookId: String) = withContext(Dispatchers.IO) {
        bookDir(bookId).deleteRecursively()
    }

    suspend fun deleteAudio(bookId: String) = withContext(Dispatchers.IO) {
        audioDir(bookId).deleteRecursively()
    }

    suspend fun clearTemp() = withContext(Dispatchers.IO) {
        File(context.cacheDir, "synthesis").deleteRecursively()
        File(context.cacheDir, "export").deleteRecursively()
    }

    fun availableBytes(): Long = StatFs(context.filesDir.absolutePath).availableBytes

    suspend fun usage(): StorageUsage = withContext(Dispatchers.IO) {
        var audio = 0L
        var source = 0L
        var working = 0L
        root.listFiles().orEmpty().forEach { book ->
            audio += File(book, "audio").sizeRecursive()
            source += File(book, "source.pdf").length()
            working += File(book, "pages.jsonl").length() + File(book, "cover.jpg").length()
        }
        StorageUsage(
            audioBytes = audio,
            sourceBytes = source,
            workingBytes = working,
            tempBytes = File(context.cacheDir, "synthesis").sizeRecursive() +
                File(context.cacheDir, "export").sizeRecursive(),
            availableBytes = availableBytes(),
        )
    }

    private fun File.sizeRecursive(): Long =
        if (!exists()) 0L else walkBottomUp().filter { it.isFile }.sumOf { it.length() }
}
