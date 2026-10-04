package app.narra.presentation.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.speech.tts.TextToSpeech

/** Pide al motor de voz que instale datos de voz. Devuelve false si el teléfono no tiene a quién pedírselo. */
fun Context.openVoiceInstaller(): Boolean = tryStart(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))

/** Abre los ajustes de almacenamiento del sistema. */
fun Context.openStorageSettings(): Boolean = tryStart(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))

private fun Context.tryStart(intent: Intent): Boolean = try {
    startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
}
