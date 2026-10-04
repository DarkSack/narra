package app.narra.data.files

import android.content.Context
import android.os.StatFs
import app.narra.domain.model.AudioFormat
import app.narra.domain.model.StorageUsage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dónde vive cada archivo de un libro. La base de datos solo guarda rutas.
 *
 * ```
 * files/books/<id>/source.pdf        PDF original
 * files/books/<id>/cover.jpg         portada
 * files/books/<id>/pages.jsonl       texto por página con posiciones (análisis y OCR)
 * files/books/<id>/ocr/<página>.json  texto reconocido de cada página escaneada
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

    /** Resultado del OCR de una página. Se conserva para no repetirlo en un nuevo análisis. */
    fun ocrPage(bookId: String, pageIndex: Int): File = File(File(bookDir(bookId), "ocr"), "$pageIndex.json")

    fun audioFile(bookId: String, chapterId: Long, segmentId: Long, format: AudioFormat): File =
        File(File(audioDir(bookId), chapterId.toString()), "$segmentId.${format.extension}")

    fun tempDir(): File = File(context.cacheDir, SYNTHESIS_DIR).apply { mkdirs() }

    fun exportDir(): File = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }

    suspend fun ensureBookDir(bookId: String): File = withContext(Dispatchers.IO) {
        bookDir(bookId).apply { mkdirs() }
    }

    suspend fun deleteBook(bookId: String) = withContext(Dispatchers.IO) {
        bookDir(bookId).deleteRecursively()
    }

    suspend fun deleteAudio(bookId: String) = withContext(Dispatchers.IO) {
        audioDir(bookId).deleteRecursively()
    }

    /**
     * Borra los temporales de Narra con más de [STALE_AFTER_MS]: los de un fragmento o una
     * exportación en curso son recientes y se respetan. Solo toca sus propias carpetas: en la
     * caché también viven los cerrojos de las bases de datos.
     */
    suspend fun clearTemporaryFiles(): Long = withContext(Dispatchers.IO) {
        val limit = System.currentTimeMillis() - STALE_AFTER_MS
        var freed = 0L
        temporaryDirs().forEach { dir ->
            dir.walkBottomUp().filter { it != dir }.forEach { file ->
                when {
                    file.isFile && file.lastModified() < limit -> {
                        val size = file.length()
                        if (file.delete()) freed += size
                    }
                    // Las carpetas solo se quitan si quedaron vacías.
                    file.isDirectory -> file.delete()
                }
            }
        }
        freed
    }

    private fun temporaryDirs(): List<File> = listOf(File(context.cacheDir, SYNTHESIS_DIR), File(context.cacheDir, EXPORT_DIR))

    fun availableBytes(): Long = StatFs(context.filesDir.absolutePath).availableBytes

    suspend fun usage(): StorageUsage = withContext(Dispatchers.IO) {
        var audio = 0L
        var source = 0L
        var working = 0L
        root.listFiles().orEmpty().forEach { book ->
            audio += File(book, "audio").sizeRecursive()
            source += File(book, "source.pdf").length()
            working += File(book, "pages.jsonl").length() + File(book, "cover.jpg").length() + File(book, "ocr").sizeRecursive()
        }
        StorageUsage(
            audioBytes = audio,
            sourceBytes = source,
            workingBytes = working,
            tempBytes = temporaryDirs().sumOf { it.sizeRecursive() },
            availableBytes = availableBytes(),
        )
    }

    private fun File.sizeRecursive(): Long =
        if (!exists()) 0L else walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    private companion object {
        /** Nada en curso conserva un temporal tanto tiempo: un fragmento tarda segundos. */
        const val STALE_AFTER_MS = 60 * 60 * 1_000L
        const val SYNTHESIS_DIR = "synthesis"
        const val EXPORT_DIR = "export"
    }
}
