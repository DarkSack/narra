package app.narra.core.designsystem.motion

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import app.narra.core.designsystem.theme.NarraTheme

val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Expone el ámbito de animación de un destino de navegación a sus componentes. */
@Composable
fun ProvideNavAnimatedScope(scope: AnimatedVisibilityScope, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNavAnimatedScope provides scope, content = content)
}

/**
 * La portada de un libro "viaja" entre pantallas (biblioteca → detalle → reproductor).
 * Comunica continuidad: es el mismo libro. Sin efecto si se reduce el movimiento.
 */
@Composable
fun Modifier.sharedCover(bookId: String): Modifier = sharedElementOrNothing("cover-$bookId")

@Composable
fun Modifier.sharedTitle(bookId: String): Modifier = sharedElementOrNothing("title-$bookId")

@Composable
private fun Modifier.sharedElementOrNothing(key: String): Modifier {
    val transitionScope = LocalSharedTransitionScope.current ?: return this
    val animatedScope = LocalNavAnimatedScope.current ?: return this
    if (NarraTheme.reduceMotion) return this
    return with(transitionScope) {
        this@sharedElementOrNothing.sharedElement(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = animatedScope,
        )
    }
}
