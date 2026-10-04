package app.narra

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.narra.domain.model.ThemeMode
import app.narra.playback.PlaybackController
import app.narra.presentation.AppViewModel
import app.narra.presentation.navigation.NarraApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var playback: PlaybackController

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // El tema depende de los ajustes guardados: la splash se mantiene hasta leerlos.
        splash.setKeepOnScreenCondition { viewModel.settings.value == null }
        enableEdgeToEdge()
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val current = settings ?: return@setContent
            val dark = when (current.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Los iconos de las barras del sistema siguen el tema de Narra, no solo el del teléfono.
            DisposableEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            NarraApp(viewModel, current)
        }
        // Al recrearse (p. ej. al girar) el intent ya se atendió.
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        playback.connect()
    }

    override fun onStop() {
        playback.disconnect()
        super.onStop()
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_OPEN_PLAYER -> viewModel.openPlayer()
            ACTION_OPEN_BOOK -> intent.getStringExtra(EXTRA_BOOK_ID)?.let(viewModel::showBook)
            Intent.ACTION_VIEW -> intent.data?.let(viewModel::importPdf)
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                ?.let(viewModel::importPdf)
        }
    }

    companion object {
        const val ACTION_OPEN_PLAYER = "app.narra.action.OPEN_PLAYER"
        const val ACTION_OPEN_BOOK = "app.narra.action.OPEN_BOOK"
        const val EXTRA_BOOK_ID = "bookId"
    }
}
