package app.narra.core.designsystem.component

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.palette.graphics.Palette
import app.narra.core.designsystem.theme.COVER_ASPECT_RATIO
import app.narra.core.designsystem.theme.CoverTitleStyle
import app.narra.core.designsystem.theme.Inter
import app.narra.core.designsystem.theme.LampCoverCloths
import app.narra.core.designsystem.theme.NarraTheme
import app.narra.core.designsystem.theme.PressCoverBands
import app.narra.core.designsystem.theme.PressPalette
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

/**
 * Portada de un libro en proporción 2:3. Si el PDF no trajo portada (o aún no se extrajo)
 * se dibuja una generada con el título, siempre la misma para el mismo libro.
 */
@Composable
fun CoverImage(
    title: String,
    author: String?,
    coverPath: String?,
    modifier: Modifier = Modifier,
    width: Dp? = null,
    elevated: Boolean = true,
    contentDescription: String? = null,
) {
    val radius = NarraTheme.tokens.coverRadius
    val press = NarraTheme.tokens.isPress
    // Libro de tela: el lomo (izquierda) tiene menos curva que el canto.
    val shape = RoundedCornerShape(topStart = radius / 2, bottomStart = radius / 2, topEnd = radius, bottomEnd = radius)
    val colors = NarraTheme.colors
    Box(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .aspectRatio(COVER_ASPECT_RATIO)
            .then(
                when {
                    !elevated -> Modifier
                    // Imprenta: sombra plana de tinta desplazada, como un grabado.
                    press -> Modifier.drawBehind {
                        val offset = (size.width * 0.04f).coerceIn(2.dp.toPx(), 6.dp.toPx())
                        drawRect(colors.coverShadow, topLeft = Offset(offset, offset), size = size)
                    }
                    else -> Modifier.shadow(14.dp, shape, ambientColor = colors.coverShadow, spotColor = colors.coverShadow)
                },
            )
            .clip(shape)
            .then(if (press) Modifier.border(1.dp, MaterialTheme.colorScheme.onSurface, shape) else Modifier)
            // El título dibujado en la portada generada es decoración: la portada se describe entera
            // con [contentDescription], o nada si el texto de al lado ya dice de qué libro se trata.
            .clearAndSetSemantics { if (contentDescription != null) this.contentDescription = contentDescription },
    ) {
        if (press) PressCover(title, author) else LampCover(title, author)
        if (coverPath != null) {
            AsyncImage(
                model = File(coverPath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (colors.imageDim < 1f) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 1f - colors.imageDim)))
        }
        if (!press) {
            // Lomo: sombra del pliegue y un brillo, para que se lea como libro y no como tarjeta.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to Color.Black.copy(alpha = 0.4f),
                            0.05f to Color.White.copy(alpha = 0.08f),
                            0.09f to Color.Transparent,
                        ),
                    ),
            )
        }
    }
}

/** Lámpara: libro de tela con filete dorado, título en Literata y el autor en versalitas. */
@Composable
private fun LampCover(title: String, author: String?) {
    val cloth = remember(title) { pick(LampCoverCloths, title) }
    val density = LocalDensity.current
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(cloth)
            .background(Brush.radialGradient(listOf(Color(0x38FFDCA0), Color.Transparent), Offset(0f, 0f))),
    ) {
        val unit = maxWidth
        val titleSize = with(density) { (unit * 0.13f).toSp() }
        val authorSize = with(density) { (unit * 0.064f).toSp() }
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = unit * 0.12f, end = unit * 0.075f, top = unit * 0.075f, bottom = unit * 0.075f)
                .border((unit * 0.008f).coerceAtLeast(0.5.dp), CoverGold.copy(alpha = 0.6f))
                .padding(horizontal = unit * 0.06f, vertical = unit * 0.09f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = CoverTitleStyle.copy(fontSize = titleSize, lineHeight = titleSize * 1.15f, textAlign = TextAlign.Center),
                color = CoverGoldLight,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            Text("✦", color = CoverGold, fontSize = titleSize * 0.8f)
            Spacer(Modifier.weight(1f))
            if (author != null) {
                Text(
                    text = author.uppercase(),
                    style = TextStyle(fontFamily = Inter, fontSize = authorSize, letterSpacing = 0.16.em, textAlign = TextAlign.Center),
                    color = CoverGoldLight,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Imprenta: papel con una franja de color, el título en cursiva y un ornamento de rúbrica. */
@Composable
private fun PressCover(title: String, author: String?) {
    val band = remember(title) { pick(PressCoverBands, title) }
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize().background(PressPalette.PaperLow)) {
        val unit = maxWidth
        val titleSize = with(density) { (unit * 0.12f).toSp() }
        val authorSize = with(density) { (unit * 0.055f).toSp() }
        if (author != null) {
            Text(
                text = author.uppercase(),
                style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = authorSize, letterSpacing = 0.2.em, textAlign = TextAlign.Center),
                color = PressPalette.Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(top = unit * 0.09f, start = unit * 0.06f, end = unit * 0.06f),
            )
        }
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .heightIn(min = unit * 0.5f)
                .background(band)
                .padding(horizontal = unit * 0.07f, vertical = unit * 0.08f),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title,
                style = CoverTitleStyle.copy(fontSize = titleSize, lineHeight = titleSize * 1.12f, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Normal, textAlign = TextAlign.Center),
                color = PressPalette.Paper,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            "❦",
            color = if (band == PressPalette.Ink) PressPalette.Rubric else band,
            fontSize = titleSize,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = unit * 0.09f),
        )
    }
}

private fun pick(colors: List<Color>, title: String): Color = colors[abs(title.hashCode()) % colors.size]

private val CoverGold = Color(0xFFE2B25E)
private val CoverGoldLight = Color(0xFFF6E7C8)

private val accentCache = LruCache<String, Int>(64)

/**
 * Color dominante de la portada, para teñir fondos en el estilo Lectura.
 * Se calcula en segundo plano y se cachea.
 */
@Composable
fun rememberCoverAccent(title: String, coverPath: String?): Color {
    val fallback = remember(title) { pick(LampCoverCloths, title) }
    val accent by produceState(initialValue = fallback, coverPath) {
        if (coverPath == null) return@produceState
        accentCache.get(coverPath)?.let {
            value = Color(it)
            return@produceState
        }
        val color = withContext(Dispatchers.IO) {
            val options = BitmapFactory.Options().apply { inSampleSize = ACCENT_SAMPLE_SIZE }
            val bitmap = BitmapFactory.decodeFile(coverPath, options) ?: return@withContext null
            val palette = Palette.from(bitmap).maximumColorCount(ACCENT_PALETTE_COLORS).generate()
            (palette.darkVibrantSwatch ?: palette.vibrantSwatch ?: palette.dominantSwatch)?.rgb
        }
        if (color != null) {
            accentCache.put(coverPath, color)
            value = Color(color)
        }
    }
    return accent
}

private const val ACCENT_SAMPLE_SIZE = 8
private const val ACCENT_PALETTE_COLORS = 12
