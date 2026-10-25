package app.narra.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.OptIn
import androidx.core.os.bundleOf
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.narra.MainActivity
import app.narra.R
import app.narra.core.ui.formatPercent
import app.narra.core.ui.formatRemaining
import app.narra.domain.model.AppSettings
import app.narra.domain.model.Book
import app.narra.domain.repository.BookRepository
import app.narra.domain.repository.SettingsRepository
import app.narra.playback.PlaybackContract
import app.narra.playback.PlaybackService
import app.narra.playback.togglePlayPause
import app.narra.presentation.common.statusInfo
import app.narra.presentation.common.toContinue
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Widgets de Inicio sobre el libro de «Continúa escuchando» (el mismo que muestra Inicio):
 * - [ContinueWidget]: portada, título, capítulo, cómo vas y un botón para seguir o pausar.
 * - [PlayerWidget]: reproductor compacto con barra de avance y botones para atrasar, reproducir y adelantar.
 * Los botones hablan con [PlaybackService] como cualquier mando (auriculares, pantalla de bloqueo):
 * no abren la app. Si no hay nada cargado, cargan ese libro donde se quedó.
 */
object NarraWidgets {
    /** Si suena algo ahora. Lo publica [PlaybackService], que vive en el mismo proceso. */
    val playing = MutableStateFlow(false)

    internal const val ACTION_PLAY_PAUSE = "app.narra.widget.PLAY_PAUSE"
    internal const val ACTION_BACK = "app.narra.widget.BACK"
    internal const val ACTION_FORWARD = "app.narra.widget.FORWARD"
    internal val COMMANDS = setOf(ACTION_PLAY_PAUSE, ACTION_BACK, ACTION_FORWARD)

    private const val PROGRESS_MAX = 1_000
    private const val COVER_MAX_PX = 320

    /** Desde NarraApplication: repinta los widgets cuando cambian la biblioteca, el progreso, los ajustes o el audio. */
    fun observe(context: Context, books: BookRepository, settings: SettingsRepository, scope: CoroutineScope) {
        scope.launch {
            combine(books.observeBooks().map { it.toContinue() }, playing, settings.settings) { book, isPlaying, s ->
                Triple(book, isPlaying, s)
            }.distinctUntilChanged().collect { (book, isPlaying, s) -> render(context, book, isPlaying, s) }
        }
    }

    internal suspend fun refresh(context: Context) {
        val entry = entry(context)
        render(context, entry.books().observeBooks().first().toContinue(), playing.value, entry.settings().settings.first())
    }

    private fun render(context: Context, book: Book?, isPlaying: Boolean, settings: AppSettings) {
        val manager = AppWidgetManager.getInstance(context)
        val continueIds = manager.getAppWidgetIds(ComponentName(context, ContinueWidget::class.java))
        val playerIds = manager.getAppWidgetIds(ComponentName(context, PlayerWidget::class.java))
        if (continueIds.isEmpty() && playerIds.isEmpty()) return
        if (continueIds.isNotEmpty()) manager.updateAppWidget(continueIds, continueViews(context, book, isPlaying))
        if (playerIds.isNotEmpty()) manager.updateAppWidget(playerIds, playerViews(context, book, isPlaying, settings))
    }

    private fun continueViews(context: Context, book: Book?, isPlaying: Boolean) =
        RemoteViews(context.packageName, R.layout.widget_continue).apply {
            if (book == null) {
                setTextViewText(R.id.widget_title, context.getString(R.string.widget_empty_title))
                setTextViewText(R.id.widget_status, context.getString(R.string.widget_empty_body))
                setViewVisibility(R.id.widget_chapter, View.GONE)
                setViewVisibility(R.id.widget_play, View.GONE)
                setOnClickPendingIntent(R.id.widget_root, openApp(context, null))
                return@apply
            }
            val cover = book.coverPath?.let(::loadCover)
            if (cover != null) setImageViewBitmap(R.id.widget_cover, cover) else setImageViewResource(R.id.widget_cover, R.drawable.widget_cover_placeholder)
            setTextViewText(R.id.widget_title, book.title)
            val chapter = book.progress?.chapterTitle
            setTextViewText(R.id.widget_chapter, chapter.orEmpty())
            setViewVisibility(R.id.widget_chapter, if (chapter.isNullOrBlank()) View.GONE else View.VISIBLE)
            setTextViewText(R.id.widget_status, book.widgetStatus())
            playButton(context, R.id.widget_play, isPlaying, ContinueWidget::class.java, idleLabel = R.string.widget_continue_action)
            setOnClickPendingIntent(R.id.widget_root, openApp(context, book.id))
        }

    private fun playerViews(context: Context, book: Book?, isPlaying: Boolean, settings: AppSettings) =
        RemoteViews(context.packageName, R.layout.widget_player).apply {
            setTextViewText(R.id.widget_title, book?.title ?: context.getString(R.string.widget_empty_title))
            setTextViewText(
                R.id.widget_chapter,
                book?.progress?.chapterTitle ?: book?.author ?: context.getString(R.string.widget_empty_body),
            )
            setProgressBar(R.id.widget_progress, PROGRESS_MAX, ((book?.listenedFraction ?: 0f) * PROGRESS_MAX).toInt(), false)
            setTextViewText(R.id.widget_back_seconds, settings.skipBackSeconds.toString())
            setTextViewText(R.id.widget_forward_seconds, settings.skipForwardSeconds.toString())
            setContentDescription(R.id.widget_back, "Retroceder ${settings.skipBackSeconds} segundos")
            setContentDescription(R.id.widget_forward, "Avanzar ${settings.skipForwardSeconds} segundos")
            val controls = if (book == null) View.GONE else View.VISIBLE
            listOf(R.id.widget_back, R.id.widget_play, R.id.widget_forward).forEach { setViewVisibility(it, controls) }
            if (book != null) {
                playButton(context, R.id.widget_play, isPlaying, PlayerWidget::class.java, idleLabel = R.string.widget_play)
                setOnClickPendingIntent(R.id.widget_back, command(context, PlayerWidget::class.java, ACTION_BACK))
                setOnClickPendingIntent(R.id.widget_forward, command(context, PlayerWidget::class.java, ACTION_FORWARD))
            }
            setOnClickPendingIntent(R.id.widget_root, if (book == null) openApp(context, null) else openPlayer(context))
        }

    private fun RemoteViews.playButton(context: Context, id: Int, isPlaying: Boolean, target: Class<*>, idleLabel: Int) {
        setViewVisibility(id, View.VISIBLE)
        setImageViewResource(id, if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play)
        setContentDescription(id, context.getString(if (isPlaying) R.string.widget_pause else idleLabel))
        setOnClickPendingIntent(id, command(context, target, ACTION_PLAY_PAUSE))
    }

    private fun command(context: Context, target: Class<*>, action: String): PendingIntent = PendingIntent.getBroadcast(
        context, action.hashCode(), Intent(context, target).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun openApp(context: Context, bookId: String?): PendingIntent = PendingIntent.getActivity(
        context, 1,
        Intent(context, MainActivity::class.java).apply {
            if (bookId != null) setAction(MainActivity.ACTION_OPEN_BOOK).putExtra(MainActivity.EXTRA_BOOK_ID, bookId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun openPlayer(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 2,
        Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_PLAYER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Portada reducida: los widgets tienen un límite de memoria para imágenes. */
    private fun loadCover(path: String): Bitmap? = runCatching {
        val file = File(path)
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outHeight / (sample * 2) >= COVER_MAX_PX) sample *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    /** Lo que hace cada botón, con un mando de Media3 de vida corta. */
    @OptIn(UnstableApi::class)
    internal suspend fun control(context: Context, action: String) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        try {
            val controller = future.await()
            val playing = controller.playWhenReady && controller.playbackState != Player.STATE_ENDED
            val loaded = controller.sessionExtras.getString(PlaybackContract.EXTRA_BOOK_ID).takeIf { controller.mediaItemCount > 0 }
            // Lo que suena se pausa; si no, el libro que enseña el widget, aunque el reproductor tenga otro (o nada) cargado.
            val book = if (playing) null else entry(context).books().observeBooks().first().toContinue()
            if (book != null && book.id != loaded) {
                val play = action == ACTION_PLAY_PAUSE
                controller.sendCustomCommand(
                    PlaybackContract.loadBook,
                    bundleOf(PlaybackContract.ARG_BOOK_ID to book.id, PlaybackContract.ARG_PLAY to play),
                ).await()
                if (play) return
            }
            when (action) {
                ACTION_PLAY_PAUSE -> controller.togglePlayPause(
                    controller.sessionExtras.getBoolean(PlaybackContract.EXTRA_WAITING_FOR_AUDIO, false),
                )
                ACTION_BACK -> controller.seekBack()
                ACTION_FORWARD -> controller.seekForward()
            }
        } finally {
            delay(COMMAND_SETTLE_MS) // que la orden llegue al servicio antes de soltar el mando
            MediaController.releaseFuture(future)
        }
    }

    private const val COMMAND_SETTLE_MS = 300L

    private fun entry(context: Context) = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
}

/** "35 % escuchado · Quedan 2 h 10 min", "Terminado" o el estado de creación del audio. */
internal fun Book.widgetStatus(): String = when {
    isCompleted -> "Terminado"
    isStarted -> "${formatPercent(listenedFraction)} escuchado · ${formatRemaining(remainingMs)}"
    else -> statusInfo().label
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun books(): BookRepository
    fun settings(): SettingsRepository
}

/** Base de los dos widgets: repintar al añadirlos y atender sus botones sin bloquear el hilo principal. */
abstract class NarraWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = async { NarraWidgets.refresh(context) }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        // El contexto de un receptor no puede enlazarse a servicios; el de la aplicación sí.
        if (action in NarraWidgets.COMMANDS) async { NarraWidgets.control(context.applicationContext, action) }
    }

    private fun async(block: suspend () -> Unit) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Main).launch {
            try {
                withTimeoutOrNull(RECEIVER_BUDGET_MS) { block() }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w("NarraWidgets", "El botón del widget no pudo llegar al reproductor", e) // un widget nunca debe cerrar la app
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        /** Un receptor tiene unos 10 s; se deja margen. */
        const val RECEIVER_BUDGET_MS = 8_000L
    }
}

class ContinueWidget : NarraWidgetProvider()

class PlayerWidget : NarraWidgetProvider()
