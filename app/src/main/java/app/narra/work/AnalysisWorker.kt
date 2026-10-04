package app.narra.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.narra.data.pdf.BookAnalyzer
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Analiza un PDF recién importado. Los errores quedan registrados en el libro, con su explicación. */
@HiltWorker
class AnalysisWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val analyzer: BookAnalyzer,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val bookId = inputData.getString(KEY_BOOK_ID) ?: return Result.failure()
        analyzer.analyze(bookId)
        return Result.success()
    }

    companion object {
        const val KEY_BOOK_ID = "bookId"
        fun uniqueName(bookId: String) = "analysis-$bookId"
    }
}
