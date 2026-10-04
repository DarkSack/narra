package app.narra.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import app.narra.data.db.BookEntity
import app.narra.data.db.NarraDatabase
import app.narra.data.files.BookStorage
import app.narra.domain.model.BookState
import app.narra.domain.model.ErrorKind
import app.narra.domain.repository.ImportRepository
import app.narra.domain.repository.ImportResult
import app.narra.domain.repository.ProcessingQueue
import app.narra.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImportRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: NarraDatabase,
    private val storage: BookStorage,
    private val settings: SettingsRepository,
    private val queue: ProcessingQueue,
) : ImportRepository {

    override suspend fun importPdf(uri: Uri, allowDuplicate: Boolean): ImportResult = withContext(Dispatchers.IO) {
        val (displayName, declaredSize) = describe(uri)
        val fileName = displayName ?: uri.lastPathSegment?.substringAfterLast('/') ?: DEFAULT_FILE_NAME

        if (!allowDuplicate && declaredSize != null) {
            db.bookDao().findBySource(fileName, declaredSize)?.let {
                return@withContext ImportResult.AlreadyInLibrary(it.id, it.title)
            }
        }
        // Hace falta espacio para el PDF y un margen para su trabajo intermedio.
        if (declaredSize != null && storage.availableBytes() < declaredSize * SPACE_FACTOR) {
            return@withContext ImportResult.Failed(ErrorKind.STORAGE_FULL)
        }

        val id = UUID.randomUUID().toString()
        val target = storage.sourcePdf(id)
        val copied = try {
            storage.ensureBookDir(id)
            copy(uri, target)
        } catch (e: FileNotFoundException) {
            storage.deleteBook(id)
            return@withContext ImportResult.Failed(ErrorKind.FILE_MISSING)
        } catch (e: IOException) {
            storage.deleteBook(id)
            val full = storage.availableBytes() < MIN_FREE_BYTES
            return@withContext ImportResult.Failed(if (full) ErrorKind.STORAGE_FULL else ErrorKind.UNKNOWN)
        }
        if (copied == 0L || !looksLikePdf(target)) {
            storage.deleteBook(id)
            return@withContext ImportResult.Failed(if (copied == 0L) ErrorKind.PDF_EMPTY else ErrorKind.PDF_CORRUPT)
        }

        val voice = settings.settings.first().defaultVoice
        db.bookDao().insert(
            BookEntity(
                id = id,
                title = titleFromFileName(fileName),
                sourceFileName = fileName,
                fileSizeBytes = copied,
                importedAt = System.currentTimeMillis(),
                state = BookState.IDLE,
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
        queue.analyze(id)
        ImportResult.Imported(id)
    }

    private fun describe(uri: Uri): Pair<String?, Long?> {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null to null
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                val name = if (nameIndex >= 0 && !cursor.isNull(nameIndex)) cursor.getString(nameIndex) else null
                val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else null
                name to size
            }
        }.getOrNull() ?: (null to null)
    }

    /** Copia a un temporal y renombra: nunca queda un PDF a medias con el nombre definitivo. */
    private fun copy(uri: Uri, target: File): Long {
        val partial = File(target.parentFile, "${target.name}.part")
        val input = context.contentResolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString())
        val bytes = input.use { source -> partial.outputStream().use { source.copyTo(it, BUFFER_SIZE) } }
        if (!partial.renameTo(target)) {
            partial.delete()
            throw IOException("No se pudo mover el PDF a su sitio")
        }
        return bytes
    }

    private fun looksLikePdf(file: File): Boolean = file.inputStream().use { stream ->
        val header = ByteArray(PDF_SIGNATURE_SEARCH)
        val read = stream.read(header)
        read > 0 && String(header, 0, read, Charsets.ISO_8859_1).contains(PDF_SIGNATURE)
    }

    /** "el_nombre-del.viento.pdf" → "El nombre del viento". Provisional: el análisis busca el título real. */
    private fun titleFromFileName(fileName: String): String {
        val base = fileName.substringBeforeLast('.', fileName)
            .replace(SEPARATORS, " ")
            .replace(SPACES, " ")
            .trim()
        return base.ifEmpty { DEFAULT_TITLE }.replaceFirstChar { it.titlecase() }
    }

    private companion object {
        const val DEFAULT_FILE_NAME = "documento.pdf"
        const val DEFAULT_TITLE = "Libro sin título"
        const val PDF_SIGNATURE = "%PDF-"

        /** La especificación de PDF tolera bytes antes de la cabecera, dentro del primer KB. */
        const val PDF_SIGNATURE_SEARCH = 1024
        const val BUFFER_SIZE = 64 * 1024
        const val SPACE_FACTOR = 2
        const val MIN_FREE_BYTES = 16L * 1024 * 1024
        val SEPARATORS = Regex("[_.]+|(?<=\\S)-(?=\\S)")
        val SPACES = Regex("\\s+")
    }
}
