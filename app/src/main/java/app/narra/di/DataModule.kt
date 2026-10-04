package app.narra.di

import android.content.Context
import androidx.room.Room
import app.narra.data.db.NarraDatabase
import app.narra.data.pdf.PdfBoxParser
import app.narra.data.pdf.PdfParser
import app.narra.data.repository.BookRepositoryImpl
import app.narra.data.repository.ImportRepositoryImpl
import app.narra.data.repository.PlaybackRepositoryImpl
import app.narra.data.settings.SettingsRepositoryImpl
import app.narra.data.tts.AndroidTtsProvider
import app.narra.data.tts.TtsProvidersImpl
import app.narra.domain.model.VoiceSettings
import app.narra.domain.repository.BookRepository
import app.narra.domain.repository.ImportRepository
import app.narra.domain.repository.PlaybackRepository
import app.narra.domain.repository.ProcessingQueue
import app.narra.domain.repository.SettingsRepository
import app.narra.domain.tts.TtsProvider
import app.narra.domain.tts.TtsProviders
import app.narra.work.WorkManagerProcessingQueue
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/** Ámbito de la aplicación: para escrituras que deben terminar aunque se cierre una pantalla. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object DataProvidersModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): NarraDatabase =
        Room.databaseBuilder(context, NarraDatabase::class.java, NarraDatabase.NAME).build()

    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindingsModule {
    @Binds
    abstract fun bookRepository(impl: BookRepositoryImpl): BookRepository

    @Binds
    abstract fun playbackRepository(impl: PlaybackRepositoryImpl): PlaybackRepository

    @Binds
    abstract fun importRepository(impl: ImportRepositoryImpl): ImportRepository

    @Binds
    abstract fun processingQueue(impl: WorkManagerProcessingQueue): ProcessingQueue

    @Binds
    abstract fun pdfParser(impl: PdfBoxParser): PdfParser

    @Binds
    abstract fun settingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    abstract fun ttsProviders(impl: TtsProvidersImpl): TtsProviders

    @Binds
    @IntoMap
    @StringKey(VoiceSettings.ANDROID_TTS_PROVIDER_ID)
    abstract fun androidTts(impl: AndroidTtsProvider): TtsProvider
}
