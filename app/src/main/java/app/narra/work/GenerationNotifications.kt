package app.narra.work

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ForegroundInfo
import app.narra.MainActivity
import app.narra.R
import app.narra.core.ui.toErrorText
import app.narra.data.generation.GenerationEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Notificaciones de la generación de audio: progreso en curso y aviso al terminar o fallar. */
@Singleton
class GenerationNotifications @Inject constructor(@ApplicationContext private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    fun createChannels() {
        manager.createNotificationChannelsCompat(
            listOf(
                NotificationChannelCompat.Builder(CHANNEL_PROGRESS, NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName("Creación de audiolibros")
                    .setDescription("Progreso mientras Narra convierte tus libros en audio.")
                    .setShowBadge(false)
                    .build(),
                NotificationChannelCompat.Builder(CHANNEL_RESULTS, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName("Audiolibros listos")
                    .setDescription("Aviso cuando un libro termina de convertirse o necesita tu atención.")
                    .build(),
            ),
        )
    }

    /** Notificación del servicio en primer plano. Sin [event], la del arranque. */
    fun foregroundInfo(event: GenerationEvent.Progress?): ForegroundInfo {
        val builder = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (event == null) {
            builder.setContentTitle("Preparando el audio").setProgress(0, 0, true)
        } else {
            val available = when (event.chaptersAvailable) {
                0 -> null
                1 -> "1 capítulo listo para escuchar"
                else -> "${event.chaptersAvailable} capítulos listos para escuchar"
            }
            builder
                .setContentTitle("Creando «${event.title}»")
                .setContentText(listOfNotNull("Capítulo ${event.chapterNumber} de ${event.chapterCount}", available).joinToString(" · "))
                .setProgress(event.segmentCount, event.segmentsAvailable, event.segmentCount == 0)
                .setContentIntent(openBook(event.bookId))
                .addAction(0, "Pausar", pause(event.bookId))
        }
        return ForegroundInfo(NOTIFICATION_PROGRESS, builder.build(), foregroundType())
    }

    /** Notificación en primer plano mientras se reconoce el texto de un libro escaneado. */
    fun recognitionInfo(bookId: String, title: String, done: Int, total: Int): ForegroundInfo {
        val builder = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentTitle(if (title.isBlank()) "Reconociendo el texto" else "Reconociendo el texto de «$title»")
            .setProgress(total, done, total == 0)
        if (total > 0) builder.setContentText("$done de $total páginas")
        if (bookId.isNotEmpty()) builder.setContentIntent(openBook(bookId))
        return ForegroundInfo(NOTIFICATION_RECOGNITION, builder.build(), foregroundType())
    }

    fun finished(event: GenerationEvent.Finished) = post(
        event.bookId,
        NotificationCompat.Builder(context, CHANNEL_RESULTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("«${event.title}» está listo")
            .setContentText("Ya puedes escucharlo completo, también sin conexión.")
            .setAutoCancel(true)
            .setContentIntent(openBook(event.bookId))
            .build(),
    )

    fun failed(event: GenerationEvent.Failed) {
        val error = event.kind.toErrorText()
        post(
            event.bookId,
            NotificationCompat.Builder(context, CHANNEL_RESULTS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("«${event.title}»: ${error.title}")
                .setContentText(error.explanation)
                .setStyle(NotificationCompat.BigTextStyle().bigText(error.explanation))
                .setAutoCancel(true)
                .setContentIntent(openBook(event.bookId))
                .build(),
        )
    }

    /** Quita el aviso de un libro (por ejemplo, al volver a generarlo). */
    fun dismiss(bookId: String) = manager.cancel(bookId, NOTIFICATION_RESULT)

    private fun post(bookId: String, notification: Notification) {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (granted) manager.notify(bookId, NOTIFICATION_RESULT, notification)
    }

    private fun openBook(bookId: String): PendingIntent = PendingIntent.getActivity(
        context,
        bookId.hashCode(),
        Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_BOOK)
            .putExtra(MainActivity.EXTRA_BOOK_ID, bookId)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun pause(bookId: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        bookId.hashCode(),
        Intent(context, GenerationActionReceiver::class.java)
            .setAction(GenerationActionReceiver.ACTION_PAUSE)
            .putExtra(GenerationActionReceiver.EXTRA_BOOK_ID, bookId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * Android 15 tiene un tipo propio para procesar contenido; de Android 10 a 14 se usa
     * sincronización de datos. Antes de Android 10 los servicios no declaran tipo.
     */
    private fun foregroundType(): Int = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        else -> NO_FOREGROUND_TYPE
    }

    private companion object {
        const val CHANNEL_PROGRESS = "generation"
        const val CHANNEL_RESULTS = "generation-results"
        const val NOTIFICATION_PROGRESS = 2001
        const val NOTIFICATION_RESULT = 2002
        const val NOTIFICATION_RECOGNITION = 2003
        const val NO_FOREGROUND_TYPE = 0
    }
}
