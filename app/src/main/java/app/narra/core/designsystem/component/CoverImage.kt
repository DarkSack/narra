package app.narra.core.designsystem.component

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import app.narra.core.designsystem.theme.COVER_ASPECT_RATIO
import app.narra.core.designsystem.theme.CoverTitleStyle
import app.narra.core.designsystem.theme.GeneratedCoverGradients
import app.narra.core.designsystem.theme.NarraTheme
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
    elevated: Boolean = NarraTheme.tokens.coverShadows,
    contentDescription: String? = null,
) {
    val radius = NarraTheme.tokens.coverRadius
    val shape = RoundedCornerShape(radius)
    val colors = NarraTheme.colors
    Box(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .aspectRatio(COVER_ASPECT_RATIO)
            .then(
                if (elevated) {
                    Modifier.shadow(10.dp, shape, ambientColor = colors.coverShadow, spotColor = colors.coverShadow)
                } else {
                    Modifier
                },
            )
            .clip(shape)
            // El título dibujado en la portada generada es decoración: la portada se describe entera
            // con [contentDescription], o nada si el texto de al lado ya dice de qué libro se trata.
            .clearAndSetSemantics { if (contentDescription != null) this.contentDescription = contentDescription },
    ) {
        GeneratedCover(title = title, author = author)
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
        // Lomo: una franja de luz que hace que se lea como libro y no como tarjeta.
        Box(
            Modifier
                .fillMaxHeight()
                .width(radius.coerceAtLeast(4.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.White.copy(alpha = 0.18f), Color.Transparent),
                    ),
                ),
        )
    }
}

@Composable
private fun GeneratedCover(title: String, author: String?) {
    val (start, end) = remember(title) { gradientFor(title) }
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(start, end))),
    ) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
            val unit = maxWidth
            val titleSize = with(density) { (unit * 0.13f).toSp() }
            val authorSize = with(density) { (unit * 0.075f).toSp() }
            Column(Modifier.fillMaxSize().padding(unit * 0.1f)) {
                Text(
                    text = title,
                    style = CoverTitleStyle.copy(fontSize = titleSize, lineHeight = titleSize * 1.15f),
                    color = Color.White,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                if (author != null) {
                    Text(
                        text = author.uppercase(),
                        style = CoverTitleStyle.copy(fontSize = authorSize),
                        // Blanco pleno: es letra pequeña sobre un degradado y necesita todo el contraste.
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.Start),
                    )
                }
            }
        }
    }
}

private fun gradientFor(title: String): Pair<Color, Color> =
    GeneratedCoverGradients[abs(title.hashCode()) % GeneratedCoverGradients.size]

private val accentCache = LruCache<String, Int>(64)

/**
 * Color dominante de la portada, para teñir fondos en el estilo Lectura.
 * Se calcula en segundo plano y se cachea.
 */
@Composable
fun rememberCoverAccent(title: String, coverPath: String?): Color {
    val fallback = remember(title) { gradientFor(title).first }
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
