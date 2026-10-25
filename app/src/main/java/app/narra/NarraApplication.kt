package app.narra

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import app.narra.di.ApplicationScope
import app.narra.domain.repository.BookRepository
import app.narra.domain.repository.SettingsRepository
import app.narra.widget.NarraWidgets
import app.narra.work.BackgroundPolicy
import app.narra.work.GenerationNotifications
import app.narra.work.ProcessingRecovery
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

@HiltAndroidApp
class NarraApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var recovery: ProcessingRecovery

    @Inject lateinit var notifications: GenerationNotifications

    @Inject lateinit var backgroundPolicy: BackgroundPolicy

    @Inject lateinit var books: BookRepository

    @Inject lateinit var settings: SettingsRepository

    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(this)
        notifications.createChannels()
        recovery.resume()
        backgroundPolicy.register()
        NarraWidgets.observe(this, books, settings, appScope)
    }
}
