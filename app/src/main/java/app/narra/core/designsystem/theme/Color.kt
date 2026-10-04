package app.narra.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Paleta de Narra: "tinta" (índigo) y "lámpara" (ámbar), sobre papel cálido de día y
 * noche de lectura de noche. El tema oscuro está diseñado por separado, no invertido:
 * superficies que se aclaran con la elevación, texto sin blanco puro y acentos menos saturados.
 */
internal object NarraPalette {
    // Tinta
    val Ink10 = Color(0xFF150F5C)
    val Ink20 = Color(0xFF241C8F)
    val Ink30 = Color(0xFF3B34AE)
    val Ink40 = Color(0xFF4B45C6)
    val Ink80 = Color(0xFFC4C1FF)
    val Ink90 = Color(0xFFE3E0FF)

    // Lámpara
    val Lamp10 = Color(0xFF2C1800)
    val Lamp20 = Color(0xFF452B00)
    val Lamp30 = Color(0xFF633F00)
    val Lamp40 = Color(0xFF8F5800)
    val Lamp80 = Color(0xFFF2B45A)
    val Lamp90 = Color(0xFFFFDDB0)

    // Salvia
    val Sage10 = Color(0xFF002117)
    val Sage20 = Color(0xFF00382A)
    val Sage30 = Color(0xFF1F5140)
    val Sage40 = Color(0xFF3F7A63)
    val Sage80 = Color(0xFF8FD1B5)
    val Sage90 = Color(0xFFC2EFD9)

    // Error
    val Red10 = Color(0xFF410004)
    val Red20 = Color(0xFF690005)
    val Red30 = Color(0xFF93000A)
    val Red40 = Color(0xFFC2363B)
    val Red80 = Color(0xFFFF9E9A)
    val Red90 = Color(0xFFFFDAD7)

    // Papel (claro)
    val Paper = Color(0xFFFBF8F2)
    val PaperLowest = Color(0xFFFFFFFF)
    val PaperLow = Color(0xFFF6F2EB)
    val PaperMid = Color(0xFFF0ECE5)
    val PaperHigh = Color(0xFFEAE6DF)
    val PaperHighest = Color(0xFFE4E0D9)
    val PaperDim = Color(0xFFDCD8D1)
    val InkText = Color(0xFF1C1B20)
    val InkTextVariant = Color(0xFF4A4655)

    // Noche (oscuro)
    val Night = Color(0xFF111016)
    val NightLowest = Color(0xFF0C0B10)
    val NightLow = Color(0xFF17161D)
    val NightMid = Color(0xFF1D1C24)
    val NightHigh = Color(0xFF24232C)
    val NightHighest = Color(0xFF2D2B36)
    val NightBright = Color(0xFF37353F)
    val MoonText = Color(0xFFECEAF2)
    val MoonTextVariant = Color(0xFFC9C4D3)
}

internal val LightColors: ColorScheme = with(NarraPalette) {
    lightColorScheme(
        primary = Ink40,
        onPrimary = Color.White,
        primaryContainer = Ink90,
        onPrimaryContainer = Ink10,
        inversePrimary = Ink80,
        secondary = Lamp40,
        onSecondary = Color.White,
        secondaryContainer = Lamp90,
        onSecondaryContainer = Lamp10,
        tertiary = Sage40,
        onTertiary = Color.White,
        tertiaryContainer = Sage90,
        onTertiaryContainer = Sage10,
        error = Red40,
        onError = Color.White,
        errorContainer = Red90,
        onErrorContainer = Red10,
        background = Paper,
        onBackground = InkText,
        surface = Paper,
        onSurface = InkText,
        surfaceVariant = Color(0xFFE7E2EC),
        onSurfaceVariant = InkTextVariant,
        surfaceTint = Ink40,
        inverseSurface = Color(0xFF312F35),
        inverseOnSurface = Color(0xFFF3EFF5),
        outline = Color(0xFF7A7586),
        outlineVariant = Color(0xFFCBC5D3),
        scrim = Color.Black,
        surfaceBright = Paper,
        surfaceDim = PaperDim,
        surfaceContainerLowest = PaperLowest,
        surfaceContainerLow = PaperLow,
        surfaceContainer = PaperMid,
        surfaceContainerHigh = PaperHigh,
        surfaceContainerHighest = PaperHighest,
    )
}

internal val DarkColors: ColorScheme = with(NarraPalette) {
    darkColorScheme(
        primary = Ink80,
        onPrimary = Ink20,
        primaryContainer = Ink30,
        onPrimaryContainer = Ink90,
        inversePrimary = Ink40,
        secondary = Lamp80,
        onSecondary = Lamp20,
        secondaryContainer = Lamp30,
        onSecondaryContainer = Lamp90,
        tertiary = Sage80,
        onTertiary = Sage20,
        tertiaryContainer = Sage30,
        onTertiaryContainer = Sage90,
        error = Red80,
        onError = Red20,
        errorContainer = Red30,
        onErrorContainer = Red90,
        background = Night,
        onBackground = MoonText,
        surface = Night,
        onSurface = MoonText,
        surfaceVariant = Color(0xFF47444F),
        onSurfaceVariant = MoonTextVariant,
        surfaceTint = Ink80,
        inverseSurface = MoonText,
        inverseOnSurface = Color(0xFF312F35),
        outline = Color(0xFF938F9C),
        outlineVariant = Color(0xFF47444F),
        scrim = Color.Black,
        surfaceBright = NightBright,
        surfaceDim = Night,
        surfaceContainerLowest = NightLowest,
        surfaceContainerLow = NightLow,
        surfaceContainer = NightMid,
        surfaceContainerHigh = NightHigh,
        surfaceContainerHighest = NightHighest,
    )
}

/** Contraste alto: el texto secundario y los contornos pasan a tener el contraste del primario. */
internal fun ColorScheme.withHighContrast(): ColorScheme = copy(
    onSurfaceVariant = onSurface,
    outline = onSurface.copy(alpha = 0.72f),
    outlineVariant = onSurface.copy(alpha = 0.45f),
)

/** Colores semánticos propios de Narra que Material 3 no cubre. */
@Immutable
data class NarraColors(
    /** ✓ Disponible para escuchar. */
    val available: Color,
    val availableContainer: Color,
    /** ⟳ Procesando. */
    val processing: Color,
    val processingContainer: Color,
    /** ○ Pendiente. */
    val pending: Color,
    /** ⚠ Error. */
    val attention: Color,
    val attentionContainer: Color,
    /** Velo sobre portadas para que el texto encima sea legible. */
    val coverScrim: Color,
    /** Sombra de las portadas, más sutil en oscuro. */
    val coverShadow: Color,
    /** Atenuación de imágenes en tema oscuro para que no deslumbren. */
    val imageDim: Float,
)

internal fun narraColorsFor(scheme: ColorScheme, dark: Boolean) = NarraColors(
    available = scheme.tertiary,
    availableContainer = scheme.tertiaryContainer,
    processing = scheme.secondary,
    processingContainer = scheme.secondaryContainer,
    pending = scheme.outline,
    attention = scheme.error,
    attentionContainer = scheme.errorContainer,
    coverScrim = if (dark) Color.Black.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.45f),
    coverShadow = if (dark) Color.Black.copy(alpha = 0.6f) else Color(0xFF2A2340).copy(alpha = 0.28f),
    imageDim = if (dark) 0.92f else 1f,
)

/** Degradados para portadas generadas cuando el PDF no trae una utilizable. */
internal val GeneratedCoverGradients: List<Pair<Color, Color>> = listOf(
    Color(0xFF3B34AE) to Color(0xFF7C5CE0),
    Color(0xFF8F5800) to Color(0xFFE59A2E),
    Color(0xFF1F5140) to Color(0xFF4F9A7E),
    Color(0xFF6B2340) to Color(0xFFC2566F),
    Color(0xFF213B5C) to Color(0xFF4C7DB8),
    Color(0xFF3A3540) to Color(0xFF7A7286),
)
