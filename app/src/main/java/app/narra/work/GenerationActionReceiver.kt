package app.narra.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.narra.di.ApplicationScope
import app.narra.domain.repository.ProcessingQueue
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Acciones de la notificación de generación. Solo la propia app puede enviarlas. */
@AndroidEntryPoint
class GenerationActionReceiver : BroadcastReceiver() {
    @Inject lateinit var queue: ProcessingQueue
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val bookId = intent.getStringExtra(EXTRA_BOOK_ID) ?: return
        if (intent.action != ACTION_PAUSE) return
        val pending = goAsync()
        scope.launch {
            try {
                queue.pause(bookId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_PAUSE = "app.narra.action.PAUSE_GENERATION"
        const val EXTRA_BOOK_ID = "bookId"
    }
}
