package app.narra.work

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.narra.data.export.AudiobookExporter
import app.narra.domain.model.ErrorKind
import app.narra.domain.model.NarraException
import app.narra.domain.repository.BookRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Exporta el audio de un libro a la carpeta elegida. Copiar cientos de megas puede tardar:
 * corre en primer plano con su notificación, y avisa al terminar.
 */
@HiltWorker
class ExportWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val exporter: AudiobookExporter,
    private val books: BookRepository,
    private val notifications: GenerationNotifications,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val bookId = inputData.getString(KEY_BOOK_ID) ?: return Result.failure()
        val folder = inputData.getString(KEY_FOLDER)?.toUri() ?: return Result.failure()
        val title = books.getBook(bookId)?.title.orEmpty()
        return try {
            val result = exporter.export(bookId, folder) { done, total ->
                setProgress(workDataOf(KEY_DONE to done, KEY_TOTAL to total))
                promote(notifications.exportInfo(bookId, title, done, total))
            }
            notifications.exported(bookId, title, result.folderName, result.files)
            Result.success(
                workDataOf(
                    KEY_FOLDER_NAME to result.folderName,
                    KEY_FILES to result.files,
                    KEY_MISSING to result.chaptersMissing,
                ),
            )
        } catch (e: NarraException) {
            Log.w(TAG, "Exportación fallida", e)
            notifications.exportFailed(bookId, title, e.kind)
            Result.failure(workDataOf(KEY_ERROR to e.kind.name))
        } finally {
            // El permiso sobre la carpeta solo hacía falta para esta exportación.
            runCatching {
                applicationContext.contentResolver.releasePersistableUriPermission(folder, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        notifications.exportInfo(inputData.getString(KEY_BOOK_ID).orEmpty(), "", 0, 0)

    private suspend fun promote(info: ForegroundInfo) {
        try {
            setForeground(info)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Sin servicio en primer plano para exportar", e)
        }
    }

    companion object {
        const val KEY_BOOK_ID = "bookId"
        const val KEY_FOLDER = "folder"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_FOLDER_NAME = "folderName"
        const val KEY_FILES = "files"
        const val KEY_MISSING = "missing"
        const val KEY_ERROR = "error"
        private const val TAG = "ExportWorker"

        fun uniqueName(bookId: String) = "export-$bookId"

        fun errorOf(name: String?): ErrorKind = ErrorKind.entries.firstOrNull { it.name == name } ?: ErrorKind.UNKNOWN
    }
}
