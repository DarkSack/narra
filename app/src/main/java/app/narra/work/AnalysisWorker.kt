package app.narra.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import app.narra.data.pdf.BookAnalyzer
import app.narra.domain.repository.BookRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Analiza un PDF recién importado. Los errores quedan registrados en el libro, con su
 * explicación. El reconocimiento de texto puede durar minutos: corre en primer plano.
 */
@HiltWorker
class AnalysisWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val analyzer: BookAnalyzer,
    private val books: BookRepository,
    private val notifications: GenerationNotifications,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val bookId = inputData.getString(KEY_BOOK_ID) ?: return Result.failure()
        val ocr = inputData.getBoolean(KEY_OCR, false)
        val title = books.getBook(bookId)?.title.orEmpty()
        analyzer.analyze(bookId, ocr) { done, total ->
            promote(notifications.recognitionInfo(bookId, title, done, total))
        }
        return Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        notifications.recognitionInfo(inputData.getString(KEY_BOOK_ID).orEmpty(), "", 0, 0)

    /** Si Android no permite el primer plano ahora, se sigue igual; WorkManager lo retomará si lo para. */
    private suspend fun promote(info: ForegroundInfo) {
        try {
            setForeground(info)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Sin servicio en primer plano para el OCR", e)
        }
    }

    companion object {
        const val KEY_BOOK_ID = "bookId"
        const val KEY_OCR = "ocr"
        private const val TAG = "AnalysisWorker"
        fun uniqueName(bookId: String) = "analysis-$bookId"
    }
}
