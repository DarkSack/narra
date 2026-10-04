package app.narra.core.designsystem.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.narra.domain.model.UiDensity
import app.narra.domain.model.UiStyle

/**
 * Medidas que cambian entre los dos estilos de interfaz. Las pantallas leen estos tokens en
 * lugar de usar números sueltos, así el cambio de estilo es coherente en toda la app.
 */
@Immutable
data class NarraTokens(
    val style: UiStyle,
    val screenPadding: Dp,
    val sectionSpacing: Dp,
    val itemSpacing: Dp,
    val cardRadius: Dp,
    val coverRadius: Dp,
    /** Portada del bloque "Continúa escuchando". */
    val heroCoverWidth: Dp,
    /** Portadas en carruseles horizontales. */
    val shelfCoverWidth: Dp,
    /** Portadas en listas. */
    val listCoverWidth: Dp,
    /** Ancho mínimo de celda en la cuadrícula de la biblioteca. */
    val gridMinCellWidth: Dp,
    /** Portada de "Reproduciendo". */
    val playerCoverMaxWidth: Dp,
    /** Titulares en serif y metadatos con tono editorial. */
    val editorial: Boolean,
    /** Fondos teñidos con el color de la portada. */
    val tintedBackdrops: Boolean,
    /** Sombras de libro físico en las portadas. */
    val coverShadows: Boolean,
    /** Duración base de las transiciones entre pantallas. */
    val transitionMillis: Int,
) {
    /** Separación mínima recomendada para objetivos táctiles. */
    val minTouchTarget: Dp get() = 48.dp

    companion object {
        fun of(style: UiStyle, density: UiDensity): NarraTokens {
            val base = when (style) {
                UiStyle.MODERN -> NarraTokens(
                    style = style,
                    screenPadding = 16.dp,
                    sectionSpacing = 24.dp,
                    itemSpacing = 12.dp,
                    cardRadius = 16.dp,
                    coverRadius = 10.dp,
                    heroCoverWidth = 96.dp,
                    shelfCoverWidth = 108.dp,
                    listCoverWidth = 52.dp,
                    gridMinCellWidth = 140.dp,
                    playerCoverMaxWidth = 280.dp,
                    editorial = false,
                    tintedBackdrops = false,
                    coverShadows = false,
                    transitionMillis = 280,
                )
                UiStyle.READING -> NarraTokens(
                    style = style,
                    screenPadding = 20.dp,
                    sectionSpacing = 36.dp,
                    itemSpacing = 16.dp,
                    cardRadius = 24.dp,
                    coverRadius = 6.dp,
                    heroCoverWidth = 150.dp,
                    shelfCoverWidth = 128.dp,
                    listCoverWidth = 64.dp,
                    gridMinCellWidth = 152.dp,
                    playerCoverMaxWidth = 340.dp,
                    editorial = true,
                    tintedBackdrops = true,
                    coverShadows = true,
                    transitionMillis = 420,
                )
            }
            return if (density == UiDensity.COMPACT) base.compact() else base
        }

        private fun NarraTokens.compact() = copy(
            screenPadding = screenPadding * 0.8f,
            sectionSpacing = sectionSpacing * 0.7f,
            itemSpacing = itemSpacing * 0.66f,
            heroCoverWidth = heroCoverWidth * 0.85f,
            shelfCoverWidth = shelfCoverWidth * 0.85f,
            gridMinCellWidth = gridMinCellWidth * 0.82f,
        )
    }
}

/** Relación de aspecto de una portada (2:3, la de un libro de bolsillo). */
const val COVER_ASPECT_RATIO = 2f / 3f

/** Curva estándar de Narra. Con movimiento reducido, los cambios son instantáneos. */
@Composable
@ReadOnlyComposable
fun <T> narraTween(durationMillis: Int = NarraTheme.tokens.transitionMillis): FiniteAnimationSpec<T> =
    if (NarraTheme.reduceMotion) snap() else tween(durationMillis, easing = FastOutSlowInEasing)
