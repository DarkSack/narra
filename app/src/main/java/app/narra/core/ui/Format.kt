package app.narra.core.ui

import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToLong

private const val MS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L
private const val BYTES_PER_KB = 1_024.0

/** 45 s · 12 min · 5 h 12 min. Para duraciones totales y tiempos restantes. */
fun formatDuration(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0) / MS_PER_SECOND)
    val hours = totalSeconds / SECONDS_PER_HOUR
    val minutes = (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    return when {
        hours > 0 && minutes > 0 -> "$hours h $minutes min"
        hours > 0 -> "$hours h"
        minutes > 0 -> "$minutes min"
        totalSeconds > 0 -> "$totalSeconds s"
        else -> "0 min"
    }
}

/** "Quedan 3 h 20 min". */
fun formatRemaining(ms: Long): String = "Quedan ${formatDuration(ms)}"

/** 1:15 · 1:02:05. Para el reproductor. */
fun formatClock(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0) / MS_PER_SECOND
    val hours = totalSeconds / SECONDS_PER_HOUR
    val minutes = (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}

/** 1×, 1,25×, 0,75×. */
fun formatSpeed(speed: Float): String {
    val text = String.format(Locale.ROOT, "%.2f", speed).trimEnd('0').trimEnd('.').replace('.', ',')
    return "$text×"
}

/** 820 KB · 14,2 MB · 1,3 GB. */
fun formatBytes(bytes: Long): String {
    if (bytes < BYTES_PER_KB) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes / BYTES_PER_KB
    var unit = 0
    while (value >= BYTES_PER_KB && unit < units.lastIndex) {
        value /= BYTES_PER_KB
        unit++
    }
    val pattern = if (value >= 100 || unit == 0) "%.0f" else "%.1f"
    return String.format(Locale.ROOT, pattern, value).replace('.', ',') + " " + units[unit]
}

fun formatPercent(fraction: Float): String = "${(fraction.coerceIn(0f, 1f) * 100).roundToLong()} %"

/** hoy · ayer · hace 4 días · 12 mar 2026. */
fun formatRelativeDay(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    val days = TimeUnit.MILLISECONDS.toDays(startOfDay(now) - startOfDay(timestamp))
    return when {
        days <= 0L -> "hoy"
        days == 1L -> "ayer"
        days < 7L -> "hace $days días"
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestamp))
    }
}

private fun startOfDay(timestamp: Long): Long = Calendar.getInstance().apply {
    timeInMillis = timestamp
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** "1 capítulo" / "19 capítulos". */
fun plural(count: Int, singular: String, plural: String): String = "$count ${if (count == 1) singular else plural}"

/** Nombre del idioma en español a partir de una etiqueta BCP 47 ("es-MX" → "Español (México)"). */
fun languageLabel(tag: String?): String? {
    if (tag.isNullOrBlank()) return null
    val spanish = Locale.forLanguageTag("es")
    return Locale.forLanguageTag(tag).getDisplayName(spanish)
        .replaceFirstChar { it.titlecase(spanish) }
        .takeIf { it.isNotBlank() }
}
