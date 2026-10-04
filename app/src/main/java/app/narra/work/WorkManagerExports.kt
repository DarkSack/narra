package app.narra.work

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.narra.domain.model.ExportStatus
import app.narra.domain.repository.AudiobookExports
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkManagerExports @Inject constructor(@ApplicationContext private val context: Context) : AudiobookExports {
    private val workManager get() = WorkManager.getInstance(context)

    override fun export(bookId: String, folder: Uri) {
        // El permiso del selector caduca con el proceso; el trabajo puede seguir después. Si el
        // proveedor no permite conservarlo, la exportación sigue valiendo mientras viva el proceso.
        try {
            context.contentResolver.takePersistableUriPermission(folder, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (e: SecurityException) {
            Log.w(TAG, "Permiso no persistente para $folder", e)
        }
        val request = OneTimeWorkRequestBuilder<ExportWorker>()
            .setInputData(workDataOf(ExportWorker.KEY_BOOK_ID to bookId, ExportWorker.KEY_FOLDER to folder.toString()))
            .build()
        workManager.enqueueUniqueWork(ExportWorker.uniqueName(bookId), ExistingWorkPolicy.REPLACE, request)
    }

    override fun observe(bookId: String): Flow<ExportStatus?> =
        workManager.getWorkInfosForUniqueWorkFlow(ExportWorker.uniqueName(bookId))
            .map { infos -> infos.firstOrNull()?.toStatus() }
            .distinctUntilChanged()

    private fun WorkInfo.toStatus(): ExportStatus? = when (state) {
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> ExportStatus.Running(0, 0)
        WorkInfo.State.RUNNING -> ExportStatus.Running(
            done = progress.getInt(ExportWorker.KEY_DONE, 0),
            total = progress.getInt(ExportWorker.KEY_TOTAL, 0),
        )
        WorkInfo.State.SUCCEEDED -> ExportStatus.Done(
            folderName = outputData.getString(ExportWorker.KEY_FOLDER_NAME).orEmpty(),
            files = outputData.getInt(ExportWorker.KEY_FILES, 0),
            chaptersMissing = outputData.getInt(ExportWorker.KEY_MISSING, 0),
        )
        WorkInfo.State.FAILED -> ExportStatus.Failed(ExportWorker.errorOf(outputData.getString(ExportWorker.KEY_ERROR)))
        WorkInfo.State.CANCELLED -> null
    }

    private companion object {
        const val TAG = "WorkManagerExports"
    }
}
