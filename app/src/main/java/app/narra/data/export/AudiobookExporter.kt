package app.narra.data.export

import android.content.ContentResolver
import android.content.Context
import android.media.MediaFormat
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.webkit.MimeTypeMap
import app.narra.audio.M4aConcatenator
import app.narra.data.db.NarraDatabase
import app.narra.data.files.BookStorage
import app.narra.domain.model.AudioFormat
import app.narra.domain.model.ChapterState
import app.narra.domain.model.ErrorKind
import app.narra.domain.model.NarraException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class ExportResult(val folderName: String, val files: Int, val chaptersMissing: Int)

/**
 * Guarda el audio de un libro en una carpeta elegida por el usuario: una subcarpeta con el
 * título, un M4A por capítulo («01 Capítulo 1.m4a») y la portada, que es lo que esperan los
 * reproductores de audiolibros. Solo se exportan los capítulos con todo su audio creado.
 */
@Singleton
class AudiobookExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: NarraDatabase,
    private val storage: BookStorage,
) {
    private val concatenator = M4aConcatenator()
    private val resolver: ContentResolver get() = context.contentResolver

    suspend fun export(bookId: String, tree: Uri, onProgress: suspend (done: Int, total: Int) -> Unit): ExportResult =
        withContext(Dispatchers.IO) {
            val book = db.bookDao().get(bookId) ?: throw NarraException(ErrorKind.FILE_MISSING, bookId)
            val chapters = db.chapterDao().getAll(bookId).filter { it.included }.sortedBy { it.orderIndex }
            val tracks = db.segmentDao().playable(bookId).groupBy { it.chapterId }
            val ready = chapters.filter { it.state == ChapterState.AVAILABLE && !tracks[it.id].isNullOrEmpty() }
            if (ready.isEmpty()) throw NarraException(ErrorKind.FILE_MISSING, "ningún capítulo terminado")
            onProgress(0, ready.size)

            val folder = saf { create(rootOf(tree), Document.MIME_TYPE_DIR, safeName(book.title, FALLBACK_FOLDER)) }
            var completed = false
            try {
                storage.cover(bookId).takeIf { it.isFile }?.let { cover ->
                    saf { copy(cover, create(folder, mimeFor(COVER_EXTENSION, COVER_MIME), COVER_NAME)) }
                }
                val digits = maxOf(MIN_NUMBER_DIGITS, ready.size.toString().length)
                val audioMime = mimeFor(AudioFormat.M4A.extension, AudioFormat.M4A.mimeType)
                val temp = File(storage.exportDir(), "$bookId.${AudioFormat.M4A.extension}")
                var files = 0
                ready.forEachIndexed { index, chapter ->
                    val parts = splitByFormat(tracks.getValue(chapter.id).map { File(it.path) })
                    parts.forEachIndexed { part, sources ->
                        val name = buildString {
                            append((index + 1).toString().padStart(digits, '0'))
                            append(' ')
                            append(safeName(chapter.title, "Capítulo ${index + 1}"))
                            if (parts.size > 1) append(" (parte ${part + 1})")
                            append('.').append(AudioFormat.M4A.extension)
                        }
                        try {
                            concatenator.join(sources, temp)
                            saf { copy(temp, create(folder, audioMime, name)) }
                        } finally {
                            temp.delete()
                        }
                        files++
                    }
                    onProgress(index + 1, ready.size)
                }
                completed = true
                ExportResult(displayName(folder) ?: safeName(book.title, FALLBACK_FOLDER), files, chapters.size - ready.size)
            } finally {
                // Una exportación a medias confunde más que ninguna: se retira la carpeta creada.
                if (!completed) withContext(NonCancellable) { runCatching { DocumentsContract.deleteDocument(resolver, folder) } }
            }
        }

    /** Agrupa los fragmentos consecutivos que se pueden unir (misma voz y calidad). */
    private fun splitByFormat(files: List<File>): List<List<File>> {
        val groups = mutableListOf<MutableList<File>>()
        var groupFormat: MediaFormat? = null
        files.forEach { file ->
            val format = concatenator.formatOf(file)
            val current = groupFormat
            if (current != null && concatenator.canJoin(current, format)) {
                groups.last() += file
            } else {
                groups += mutableListOf(file)
                groupFormat = format
            }
        }
        return groups
    }

    private fun rootOf(tree: Uri): Uri = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

    private fun create(parent: Uri, mimeType: String, name: String): Uri =
        DocumentsContract.createDocument(resolver, parent, mimeType, name) ?: throw IOException("no se creó $name")

    private fun copy(source: File, target: Uri) {
        val output = resolver.openOutputStream(target, WRITE_TRUNCATE) ?: throw IOException("no se abrió el destino")
        output.use { out -> source.inputStream().use { it.copyTo(out) } }
    }

    private fun displayName(document: Uri): String? =
        resolver.query(document, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    /**
     * El proveedor añade la extensión que corresponde al tipo si no coincide con la del nombre;
     * pedir el tipo que el sistema asocia a la extensión evita nombres como «01.m4a.mp4».
     */
    private fun mimeFor(extension: String, fallback: String): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: fallback

    /** Errores del proveedor de documentos (carpeta borrada, sin permiso, sin espacio). */
    private inline fun <T> saf(block: () -> T): T = try {
        block()
    } catch (e: IOException) {
        throw NarraException(ErrorKind.EXPORT_FAILED, e.message, e)
    } catch (e: SecurityException) {
        throw NarraException(ErrorKind.EXPORT_FAILED, e.message, e)
    } catch (e: IllegalArgumentException) {
        throw NarraException(ErrorKind.EXPORT_FAILED, e.message, e)
    } catch (e: UnsupportedOperationException) {
        throw NarraException(ErrorKind.EXPORT_FAILED, e.message, e)
    }

    companion object {
        private const val COVER_NAME = "cover.jpg"
        private const val COVER_EXTENSION = "jpg"
        private const val COVER_MIME = "image/jpeg"
        private const val FALLBACK_FOLDER = "Audiolibro"
        private const val WRITE_TRUNCATE = "wt"
        private const val MIN_NUMBER_DIGITS = 2
        private const val MAX_NAME_CHARS = 100
        private val FORBIDDEN = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]+")
        private val SPACES = Regex("\\s+")

        /** Nombre válido en cualquier sistema de archivos (también FAT de tarjetas SD). */
        internal fun safeName(raw: String, fallback: String): String =
            raw.replace(FORBIDDEN, " ").replace(SPACES, " ").trim().trimEnd('.').take(MAX_NAME_CHARS).trim()
                .ifEmpty { fallback }
    }
}
