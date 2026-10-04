package app.narra.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.narra.core.designsystem.theme.NarraTheme
import app.narra.core.designsystem.theme.narraTween
import app.narra.domain.model.ChapterState

/** Barra de progreso fina y redondeada, con cambio animado (instantáneo si se reduce el movimiento). */
@Composable
fun NarraProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    height: Dp = 4.dp,
    /** Segunda capa: lo generado, por debajo de lo escuchado. */
    secondaryProgress: Float? = null,
    secondaryColor: Color = color.copy(alpha = 0.3f),
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), narraTween(), label = "progress")
    val animatedSecondary by animateFloatAsState((secondaryProgress ?: 0f).coerceIn(0f, 1f), narraTween(), label = "secondary")
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(trackColor)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f) },
    ) {
        if (secondaryProgress != null) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(animatedSecondary).clip(CircleShape).background(secondaryColor))
        }
        Box(Modifier.fillMaxHeight().fillMaxWidth(animated).clip(CircleShape).background(color))
    }
}

/** Icono del estado de un capítulo: ✓ disponible, ⟳ procesando, ○ pendiente, ⚠ error. */
@Composable
fun ChapterStateIcon(state: ChapterState, modifier: Modifier = Modifier, size: Dp = 20.dp) {
    val colors = NarraTheme.colors
    val (icon, tint, label) = when (state) {
        ChapterState.AVAILABLE -> Triple(Icons.Rounded.CheckCircle, colors.available, "Disponible")
        ChapterState.PROCESSING -> Triple(Icons.Rounded.Sync, colors.processing, "Procesando")
        ChapterState.PENDING -> Triple(Icons.Rounded.RadioButtonUnchecked, colors.pending, "Pendiente")
        ChapterState.ERROR -> Triple(Icons.Rounded.ErrorOutline, colors.attention, "Error")
        ChapterState.SKIPPED -> Triple(Icons.Rounded.RemoveCircleOutline, colors.pending, "Omitido")
    }
    if (state == ChapterState.PROCESSING) {
        SpinningIcon(icon, label, tint, modifier.size(size))
    } else {
        Icon(icon, contentDescription = label, tint = tint, modifier = modifier.size(size))
    }
}

/** Icono que gira mientras hay trabajo en curso (quieto si se reduce el movimiento). */
@Composable
fun SpinningIcon(icon: ImageVector, contentDescription: String?, tint: Color, modifier: Modifier = Modifier) {
    if (NarraTheme.reduceMotion) {
        Icon(icon, contentDescription, tint = tint, modifier = modifier)
        return
    }
    val transition = rememberInfiniteTransition(label = "spin")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = -360f,
        animationSpec = infiniteRepeatable(tween(SPIN_MILLIS, easing = LinearEasing), RepeatMode.Restart),
        label = "angle",
    )
    Icon(icon, contentDescription, tint = tint, modifier = modifier.rotate(angle))
}

enum class StatusTone { NEUTRAL, AVAILABLE, PROCESSING, ATTENTION }

/** Etiqueta pequeña de estado ("Procesando", "3 de 19 listos", "Error"). */
@Composable
fun StatusPill(text: String, tone: StatusTone, modifier: Modifier = Modifier, leading: (@Composable () -> Unit)? = null) {
    val colors = NarraTheme.colors
    val (container, content) = when (tone) {
        StatusTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
        StatusTone.AVAILABLE -> colors.availableContainer to MaterialTheme.colorScheme.onTertiaryContainer
        StatusTone.PROCESSING -> colors.processingContainer to MaterialTheme.colorScheme.onSecondaryContainer
        StatusTone.ATTENTION -> colors.attentionContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(color = container, contentColor = content, shape = CircleShape, modifier = modifier) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) { leading() }
                Spacer(Modifier.width(6.dp))
            }
            Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

private const val SPIN_MILLIS = 1_400
