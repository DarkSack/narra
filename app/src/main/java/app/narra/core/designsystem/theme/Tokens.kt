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
    /** Esquinas de los botones: píldora en Lámpara, casi rectas en Imprenta. */
    val buttonRadius: Dp,
    /** Duración base de las transiciones entre pantallas. */
    val transitionMillis: Int,
) {
    /** Separación mínima recomendada para objetivos táctiles. */
    val minTouchTarget: Dp get() = 48.dp

    val isPress: Boolean get() = style == UiStyle.PRESS

    companion object {
        fun of(style: UiStyle, density: UiDensity): NarraTokens {
            val lamp = NarraTokens(
                style = style,
                screenPadding = 20.dp,
                sectionSpacing = 36.dp,
                itemSpacing = 16.dp,
                cardRadius = 24.dp,
                coverRadius = 4.dp,
                heroCoverWidth = 140.dp,
                shelfCoverWidth = 120.dp,
                listCoverWidth = 64.dp,
                gridMinCellWidth = 152.dp,
                playerCoverMaxWidth = 300.dp,
                buttonRadius = 50.dp,
                transitionMillis = 420,
            )
            val base = when (style) {
                UiStyle.LAMP -> lamp
                UiStyle.PRESS -> lamp.copy(cardRadius = 2.dp, coverRadius = 0.dp, buttonRadius = 3.dp, transitionMillis = 320)
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
