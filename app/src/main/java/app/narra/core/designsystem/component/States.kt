package app.narra.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.narra.core.designsystem.theme.NarraTheme
import app.narra.core.ui.ErrorText

/**
 * Estado vacío que nunca es solo "no hay nada": explica qué pasó, por qué importa y qué hacer.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    explanation: String,
    modifier: Modifier = Modifier,
    primaryLabel: String? = null,
    onPrimary: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(tint.copy(alpha = 0.22f), tint.copy(alpha = 0.06f)))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            explanation,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp),
        )
        if (primaryLabel != null && onPrimary != null) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = onPrimary) { Text(primaryLabel) }
        }
        if (secondaryLabel != null && onSecondary != null) {
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onSecondary) { Text(secondaryLabel) }
        }
    }
}

/** Error recuperable dentro de una pantalla o tarjeta. */
@Composable
fun ErrorState(
    error: ErrorText,
    modifier: Modifier = Modifier,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    /** Un aviso que propone algo (como reconocer un escaneo) no se pinta como un fallo. */
    notice: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val container = if (notice) colors.surfaceContainerHigh else colors.errorContainer
    val content = if (notice) colors.onSurface else colors.onErrorContainer
    Surface(
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(NarraTheme.tokens.cardRadius),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // El icono repite lo que dice el color, para quien no distingue el rojo.
            Icon(
                if (notice) Icons.Rounded.Info else Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = if (notice) colors.primary else content,
            )
            Column(Modifier.weight(1f)) {
                Text(error.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(4.dp))
                Text(error.explanation, style = MaterialTheme.typography.bodyMedium)
                if ((error.actionLabel != null && onAction != null) || onDismiss != null) {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (error.actionLabel != null && onAction != null) {
                            if (notice) {
                                Button(onClick = onAction) { Text(error.actionLabel) }
                            } else {
                                OutlinedButton(
                                    onClick = onAction,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = content),
                                    border = BorderStroke(1.dp, content.copy(alpha = OUTLINE_ALPHA)),
                                ) { Text(error.actionLabel) }
                            }
                        }
                        if (onDismiss != null) {
                            TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = content)) { Text("Cerrar") }
                        }
                    }
                }
            }
        }
    }
}

private const val OUTLINE_ALPHA = 0.6f

/** Bloque gris con brillo que recorre, para esqueletos de carga. */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(8.dp)) {
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlight = MaterialTheme.colorScheme.surfaceContainerHighest
    if (NarraTheme.reduceMotion) {
        Box(modifier.clip(shape).background(base))
        return
    }
    val transition = rememberInfiniteTransition(label = "skeleton")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SHIMMER_MILLIS, easing = LinearEasing), RepeatMode.Restart),
        label = "shift",
    )
    Box(
        modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(base, highlight, base),
                    start = androidx.compose.ui.geometry.Offset(SHIMMER_SPAN * (shift - 1f), 0f),
                    end = androidx.compose.ui.geometry.Offset(SHIMMER_SPAN * shift, 0f),
                ),
            ),
    )
}

/** Esqueleto de una fila de libro mientras carga la biblioteca. */
@Composable
fun BookRowSkeleton(coverWidth: Dp, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SkeletonBlock(Modifier.size(coverWidth, coverWidth * 1.5f), RoundedCornerShape(NarraTheme.tokens.coverRadius))
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonBlock(Modifier.fillMaxWidth(0.7f).height(16.dp))
            SkeletonBlock(Modifier.fillMaxWidth(0.4f).height(12.dp))
            SkeletonBlock(Modifier.fillMaxWidth().height(4.dp))
        }
    }
}

/** Encabezado de sección con acción opcional ("Ver todo"). */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = if (NarraTheme.tokens.editorial) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

private const val SHIMMER_MILLIS = 1_300
private const val SHIMMER_SPAN = 1_200f
