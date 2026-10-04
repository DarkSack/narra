package app.narra.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.narra.R

/** Serif editorial para títulos y portadas. Fuente variable: el peso se aplica por eje `wght`. */
val Literata = FontFamily(
    Font(R.font.literata, FontWeight.Normal),
    Font(R.font.literata, FontWeight.Medium),
    Font(R.font.literata, FontWeight.SemiBold),
    Font(R.font.literata, FontWeight.Bold),
)

/** Sans de interfaz. */
val Inter = FontFamily(
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter, FontWeight.Medium),
    Font(R.font.inter, FontWeight.SemiBold),
    Font(R.font.inter, FontWeight.Bold),
)

/**
 * En estilo Lectura los titulares usan Literata; en Android Modern todo es Inter.
 * El cuerpo siempre es Inter: en pantallas pequeñas se lee mejor.
 */
internal fun narraTypography(editorialHeadlines: Boolean): Typography {
    val headline = if (editorialHeadlines) Literata else Inter
    val headlineTracking = if (editorialHeadlines) 0.em else (-0.01).em
    return Typography(
        displayLarge = TextStyle(fontFamily = headline, fontWeight = FontWeight.SemiBold, fontSize = 52.sp, lineHeight = 60.sp, letterSpacing = (-0.02).em),
        displayMedium = TextStyle(fontFamily = headline, fontWeight = FontWeight.SemiBold, fontSize = 42.sp, lineHeight = 50.sp, letterSpacing = (-0.02).em),
        displaySmall = TextStyle(fontFamily = headline, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 42.sp, letterSpacing = (-0.01).em),
        headlineLarge = TextStyle(fontFamily = headline, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 38.sp, letterSpacing = headlineTracking),
        headlineMedium = TextStyle(fontFamily = headline, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 34.sp, letterSpacing = headlineTracking),
        headlineSmall = TextStyle(fontFamily = headline, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 30.sp, letterSpacing = headlineTracking),
        titleLarge = TextStyle(fontFamily = headline, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
        titleMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.005.em),
        titleSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.005.em),
        bodyLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.01.em),
        labelLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.01.em),
        labelMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.02.em),
        labelSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.03.em),
    )
}

/** Texto de portadas generadas. */
val CoverTitleStyle = TextStyle(fontFamily = Literata, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.01).em)
