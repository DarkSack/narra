package app.narra.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import app.narra.domain.model.UiStyle

/** Colores base de Narra: tinta (índigo), lámpara (ámbar), salvia y error, más papel y noche. */
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
    val Lamp40 = Color(0xFF8A5500)
    val Lamp80 = Color(0xFFF2B45A)
    val Lamp90 = Color(0xFFFFDDB0)

    // Salvia
    val Sage10 = Color(0xFF002117)
    val Sage20 = Color(0xFF00382A)
    val Sage30 = Color(0xFF1F5140)
    val Sage40 = Color(0xFF356854)
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


/** Imprenta: papel, tinta negra y rojo de rúbrica. */
internal object PressPalette {
    val Paper = Color(0xFFF3EEE3)
    val PaperLowest = Color(0xFFFAF7F0)
    val PaperLow = Color(0xFFEFE9DD)
    val PaperMid = Color(0xFFEAE3D6)
    val PaperHigh = Color(0xFFE4DCCE)
    val PaperHighest = Color(0xFFDDD4C5)
    val PaperDim = Color(0xFFD6CDBD)
    val Ink = Color(0xFF1B1814)
    val InkVariant = Color(0xFF5B544B)
    val Rubric = Color(0xFFB53A25)
    val RubricLight = Color(0xFFF08A6E)

    // Noche de imprenta: la tinta pasa a ser el papel.
    val InkNight = Color(0xFF17140F)
    val InkNightLowest = Color(0xFF110F0B)
    val InkNightLow = Color(0xFF1F1B16)
    val InkNightMid = Color(0xFF25211B)
    val InkNightHigh = Color(0xFF2C2720)
    val InkNightHighest = Color(0xFF342E26)
    val InkNightBright = Color(0xFF3D362D)
    val PaperText = Color(0xFFEDE6D8)
    val PaperTextVariant = Color(0xFFBDB4A4)
}

/** Lámpara de noche: tinta oscura, texto cálido y el ámbar como única luz. */
private val LampDark: ColorScheme = with(NarraPalette) {
    darkColorScheme(
        primary = Lamp80,
        onPrimary = Color(0xFF2A1A03),
        primaryContainer = Lamp30,
        onPrimaryContainer = Lamp90,
        inversePrimary = Lamp40,
        secondary = Ink80,
        onSecondary = Ink20,
        secondaryContainer = Color(0xFF3A2A12),
        onSecondaryContainer = Lamp90,
        tertiary = Sage80,
        onTertiary = Sage20,
        tertiaryContainer = Sage30,
        onTertiaryContainer = Sage90,
        error = Red80,
        onError = Red20,
        errorContainer = Red30,
        onErrorContainer = Red90,
        background = Color(0xFF0F0E14),
        onBackground = Color(0xFFEEE7DA),
        surface = Color(0xFF0F0E14),
        onSurface = Color(0xFFEEE7DA),
        surfaceVariant = Color(0xFF3D3A46),
        onSurfaceVariant = Color(0xFFADA6B8),
        surfaceTint = Lamp80,
        inverseSurface = Color(0xFFEEE7DA),
        inverseOnSurface = Color(0xFF2D2B36),
        outline = Color(0xFF8E889C),
        outlineVariant = Color(0xFF3D3A46),
        scrim = Color.Black,
        surfaceBright = NightBright,
        surfaceDim = Color(0xFF0F0E14),
        surfaceContainerLowest = NightLowest,
        surfaceContainerLow = Color(0xFF15141B),
        surfaceContainer = NightLow,
        surfaceContainerHigh = NightMid,
        surfaceContainerHighest = NightHigh,
    )
}

/** Lámpara de día: el mismo ámbar sobre papel crema. */
private val LampLight: ColorScheme = with(NarraPalette) {
    lightColorScheme(
        primary = Lamp40,
        onPrimary = Color.White,
        primaryContainer = Lamp90,
        onPrimaryContainer = Lamp10,
        inversePrimary = Lamp80,
        secondary = Ink40,
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
        onBackground = Color(0xFF1F1A14),
        surface = Paper,
        onSurface = Color(0xFF1F1A14),
        surfaceVariant = Color(0xFFEDE4D6),
        onSurfaceVariant = Color(0xFF51493F),
        surfaceTint = Lamp40,
        inverseSurface = Color(0xFF34302A),
        inverseOnSurface = Color(0xFFF7F1E6),
        outline = Color(0xFF857B6E),
        outlineVariant = Color(0xFFD6CCBC),
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

private val PressLight: ColorScheme = with(PressPalette) {
    lightColorScheme(
        primary = Ink,
        onPrimary = Paper,
        primaryContainer = PaperHighest,
        onPrimaryContainer = Ink,
        inversePrimary = PaperText,
        secondary = Rubric,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFF6D9CF),
        onSecondaryContainer = Color(0xFF4A1206),
        tertiary = NarraPalette.Sage40,
        onTertiary = Color.White,
        tertiaryContainer = NarraPalette.Sage90,
        onTertiaryContainer = NarraPalette.Sage10,
        error = NarraPalette.Red40,
        onError = Color.White,
        errorContainer = NarraPalette.Red90,
        onErrorContainer = NarraPalette.Red10,
        background = Paper,
        onBackground = Ink,
        surface = Paper,
        onSurface = Ink,
        surfaceVariant = PaperHigh,
        onSurfaceVariant = InkVariant,
        surfaceTint = Color.Transparent,
        inverseSurface = Ink,
        inverseOnSurface = Paper,
        outline = Color(0xFF8A8277),
        outlineVariant = Color(0xFFCFC6B6),
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

private val PressDark: ColorScheme = with(PressPalette) {
    darkColorScheme(
        primary = PaperText,
        onPrimary = Ink,
        primaryContainer = InkNightHighest,
        onPrimaryContainer = PaperText,
        inversePrimary = Ink,
        secondary = RubricLight,
        onSecondary = Color(0xFF4A1206),
        secondaryContainer = Color(0xFF6B2414),
        onSecondaryContainer = Color(0xFFFFDAD0),
        tertiary = NarraPalette.Sage80,
        onTertiary = NarraPalette.Sage20,
        tertiaryContainer = NarraPalette.Sage30,
        onTertiaryContainer = NarraPalette.Sage90,
        error = NarraPalette.Red80,
        onError = NarraPalette.Red20,
        errorContainer = NarraPalette.Red30,
        onErrorContainer = NarraPalette.Red90,
        background = InkNight,
        onBackground = PaperText,
        surface = InkNight,
        onSurface = PaperText,
        surfaceVariant = InkNightHighest,
        onSurfaceVariant = PaperTextVariant,
        surfaceTint = Color.Transparent,
        inverseSurface = PaperText,
        inverseOnSurface = Ink,
        outline = Color(0xFF948B7C),
        outlineVariant = Color(0xFF4A4238),
        scrim = Color.Black,
        surfaceBright = InkNightBright,
        surfaceDim = InkNight,
        surfaceContainerLowest = InkNightLowest,
        surfaceContainerLow = InkNightLow,
        surfaceContainer = InkNightMid,
        surfaceContainerHigh = InkNightHigh,
        surfaceContainerHighest = InkNightHighest,
    )
}

internal fun colorSchemeFor(style: UiStyle, dark: Boolean): ColorScheme = when (style) {
    UiStyle.LAMP -> if (dark) LampDark else LampLight
    UiStyle.PRESS -> if (dark) PressDark else PressLight
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
    /** Sombra de las portadas: difusa en Lámpara, plana y de tinta en Imprenta. */
    val coverShadow: Color,
    /** Atenuación de imágenes en tema oscuro para que no deslumbren. */
    val imageDim: Float,
    /** Luz de la lámpara detrás del libro actual; transparente en Imprenta. */
    val glow: Color,
    /** Detalles de rúbrica: el capítulo actual, la pestaña activa, los ornamentos. */
    val rubric: Color,
)

internal fun narraColorsFor(scheme: ColorScheme, style: UiStyle, dark: Boolean) = NarraColors(
    available = scheme.tertiary,
    availableContainer = scheme.tertiaryContainer,
    processing = scheme.secondary,
    processingContainer = scheme.secondaryContainer,
    pending = scheme.outline,
    attention = scheme.error,
    attentionContainer = scheme.errorContainer,
    coverScrim = if (dark) Color.Black.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.45f),
    coverShadow = when {
        style == UiStyle.PRESS -> scheme.onSurface
        dark -> Color.Black.copy(alpha = 0.7f)
        else -> Color(0xFF2A1E10).copy(alpha = 0.3f)
    },
    imageDim = if (dark) 0.92f else 1f,
    glow = if (style == UiStyle.LAMP) NarraPalette.Lamp80.copy(alpha = if (dark) 0.26f else 0.32f) else Color.Transparent,
    rubric = if (style == UiStyle.LAMP) scheme.primary else scheme.secondary,
)

/** Telas de las portadas generadas en Lámpara (con filete y letras doradas). */
internal val LampCoverCloths: List<Color> = listOf(
    Color(0xFF5B2333),
    Color(0xFF1F4A44),
    Color(0xFF203A63),
    Color(0xFF3A3540),
    Color(0xFF5A3D12),
    Color(0xFF23404A),
)

/** Franjas de las portadas generadas en Imprenta, sobre papel. */
internal val PressCoverBands: List<Color> = listOf(
    Color(0xFFB53A25),
    Color(0xFF2F5D50),
    Color(0xFF24467A),
    Color(0xFF1B1814),
    Color(0xFF8C5A12),
    Color(0xFF6B3A5B),
)
