package app.narra.presentation.common

import app.narra.core.ui.formatSpeed
import app.narra.core.ui.languageLabel
import app.narra.domain.model.VoiceSettings

/** "Voz en español (España) · 1,2×": lo justo para saber con qué voz se creará el audio. */
fun VoiceSettings.summary(): String = buildList {
    add("Voz en ${languageLabel(languageTag)?.replaceFirstChar { it.lowercase() } ?: "el idioma del teléfono"}")
    if (speechRate != 1f) add(formatSpeed(speechRate))
}.joinToString(" · ")
