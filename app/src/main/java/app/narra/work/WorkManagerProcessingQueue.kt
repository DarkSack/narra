package app.narra.work

import android.content.Context
import androidx.room.withTransaction
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.narra.data.db.NarraDatabase
import app.narra.data.db.ProcessingJobEntity
import app.narra.data.db.toDomain
import app.narra.data.repository.BookStatsUpdater
import app.narra.domain.model.BookState
import app.narra.domain.model.JobState
import app.narra.domain.model.ProcessingJob
import app.narra.domain.repository.ProcessingQueue
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cola de trabajos sobre WorkManager: sobrevive a cierres de la app y reinicios del teléfono.
 * El estado de cada libro en la cola vive en Room; WorkManager solo despierta al procesador.
 */
@Singleton
class WorkManagerProcessingQueue @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: NarraDatabase,
    private val stats: BookStatsUpdater,
    private val notifications: GenerationNotifications,
) : ProcessingQueue {
    private val workManager get() = WorkManager.getInstance(context)
    private val jobs get() = db.jobDao()
    private val books get() = db.bookDao()

    override fun analyze(bookId: String, restart: Boolean, ocr: Boolean) {
        val request = OneTimeWorkRequestBuilder<AnalysisWorker>()
            .setInputData(workDataOf(AnalysisWorker.KEY_BOOK_ID to bookId, AnalysisWorker.KEY_OCR to ocr))
            .addTag(TAG_ANALYSIS)
            .build()
        workManager.enqueueUniqueWork(
            AnalysisWorker.uniqueName(bookId),
            // Pedir OCR sustituye a un análisis sin OCR que estuviera en marcha.
            if (restart || ocr) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request,
        )
    }

    override fun cancelAnalysis(bookId: String) {
        workManager.cancelUniqueWork(AnalysisWorker.uniqueName(bookId))
    }

    override fun observeQueue(): Flow<List<ProcessingJob>> =
        jobs.observeQueue().map { list -> list.map { it.toDomain() } }.distinctUntilChanged()

    override suspend fun generate(bookId: String) {
        db.withTransaction {
            val existing = jobs.get(bookId)
            jobs.upsert(
                ProcessingJobEntity(
                    bookId = bookId,
                    state = JobState.QUEUED,
                    enqueuedAt = existing?.enqueuedAt?.takeIf { existing.state != JobState.DONE } ?: System.currentTimeMillis(),
                    attempts = (existing?.attempts ?: 0) + 1,
                    priorityChapterId = existing?.priorityChapterId,
                    currentChapterId = existing?.currentChapterId,
                    currentSegmentIndex = existing?.currentSegmentIndex ?: 0,
                    throughputCharsPerSecond = existing?.throughputCharsPerSecond ?: 0f,
                ),
            )
            books.setState(bookId, BookState.GENERATING)
        }
        // El aviso de un intento anterior ("necesita tu atención") ya no vale.
        notifications.dismiss(bookId)
        startProcessor()
    }

    override suspend fun pause(bookId: String) {
        db.withTransaction {
            val job = jobs.get(bookId) ?: return@withTransaction
            if (job.state != JobState.QUEUED && job.state != JobState.RUNNING) return@withTransaction
            jobs.setState(bookId, JobState.PAUSED)
            books.setState(bookId, BookState.PAUSED)
        }
    }

    override suspend fun resume(bookId: String) {
        val resumed = db.withTransaction {
            val job = jobs.get(bookId) ?: return@withTransaction false
            if (job.state != JobState.PAUSED) return@withTransaction false
            jobs.setState(bookId, JobState.QUEUED)
            books.setState(bookId, BookState.GENERATING)
            true
        }
        if (resumed) startProcessor()
    }

    override suspend fun cancelGeneration(bookId: String) {
        jobs.delete(bookId)
        stats.refreshBook(bookId)
        val book = books.get(bookId) ?: return
        val complete = book.segmentCount > 0 && book.segmentsAvailable == book.segmentCount
        books.setState(bookId, if (complete) BookState.COMPLETED else BookState.READY)
    }

    override suspend fun retry(bookId: String) {
        db.withTransaction {
            db.segmentDao().resetErrors(bookId)
            db.chapterDao().resetErrors(bookId)
        }
        stats.refreshBook(bookId)
        generate(bookId)
    }

    override suspend fun prioritizeChapter(bookId: String, chapterId: Long) {
        if (jobs.get(bookId) == null) return
        jobs.setPriority(bookId, chapterId)
        moveToFront(bookId)
    }

    override suspend fun moveToFront(bookId: String) {
        db.withTransaction {
            val job = jobs.get(bookId) ?: return@withTransaction
            val earliest = jobs.earliestEnqueuedAt() ?: return@withTransaction
            if (job.enqueuedAt > earliest || (job.state != JobState.QUEUED && job.state != JobState.RUNNING)) {
                jobs.setEnqueuedAt(bookId, earliest - 1)
            }
        }
    }

    override suspend fun resumePending() {
        if (jobs.activeCount() > 0) startProcessor()
    }

    /**
     * Despierta al procesador. Si ya hay uno trabajando, el nuevo espera detrás y solo arranca
     * si al terminar aquel queda algo en la cola; así no se pierde un libro añadido justo
     * cuando el anterior acababa.
     */
    private fun startProcessor() {
        val request = OneTimeWorkRequestBuilder<GenerationWorker>()
            .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).build())
            .addTag(TAG_GENERATION)
            .build()
        workManager.enqueueUniqueWork(GenerationWorker.UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    private companion object {
        const val TAG_ANALYSIS = "analysis"
        const val TAG_GENERATION = "generation"
    }
}
