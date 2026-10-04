package app.narra

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import app.narra.work.GenerationNotifications
import app.narra.work.ProcessingRecovery
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NarraApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var recovery: ProcessingRecovery

    @Inject lateinit var notifications: GenerationNotifications

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(this)
        notifications.createChannels()
        recovery.resume()
    }
}
