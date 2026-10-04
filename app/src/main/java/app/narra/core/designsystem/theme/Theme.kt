package app.narra.core.designsystem.theme

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import app.narra.domain.model.AppSettings
import app.narra.domain.model.ThemeMode
import app.narra.domain.model.UiDensity
import app.narra.domain.model.UiStyle

private val LocalNarraTokens = staticCompositionLocalOf { NarraTokens.of(UiStyle.READING, UiDensity.COMFORTABLE) }
private val LocalNarraColors = staticCompositionLocalOf { narraColorsFor(LightColors, dark = false) }
private val LocalReduceMotion = staticCompositionLocalOf { false }
private val LocalIsDark = staticCompositionLocalOf { false }

/** Acceso a los tokens propios de Narra, junto a `MaterialTheme`. */
object NarraTheme {
    val tokens: NarraTokens
        @Composable @ReadOnlyComposable get() = LocalNarraTokens.current

    val colors: NarraColors
        @Composable @ReadOnlyComposable get() = LocalNarraColors.current

    val reduceMotion: Boolean
        @Composable @ReadOnlyComposable get() = LocalReduceMotion.current

    val isDark: Boolean
        @Composable @ReadOnlyComposable get() = LocalIsDark.current
}

@Composable
fun NarraTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val scheme = (if (dark) DarkColors else LightColors).let { if (settings.highContrast) it.withHighContrast() else it }
    val tokens = remember(settings.uiStyle, settings.density) { NarraTokens.of(settings.uiStyle, settings.density) }
    val typography = remember(tokens.editorial) { narraTypography(tokens.editorial) }
    val reduceMotion = settings.reduceMotion || systemAnimationsDisabled()

    val baseDensity = LocalDensity.current
    val scaledDensity = remember(baseDensity, settings.textScale) {
        Density(baseDensity.density, baseDensity.fontScale * settings.textScale)
    }

    CompositionLocalProvider(
        LocalNarraTokens provides tokens,
        LocalNarraColors provides narraColorsFor(scheme, dark),
        LocalReduceMotion provides reduceMotion,
        LocalIsDark provides dark,
        LocalDensity provides scaledDensity,
    ) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}

/** Respeta "Quitar animaciones" de las opciones de accesibilidad del sistema. */
@Composable
private fun systemAnimationsDisabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
