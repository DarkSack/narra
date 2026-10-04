package app.narra.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.narra.core.designsystem.component.ChapterStateIcon
import app.narra.core.designsystem.component.CoverImage
import app.narra.core.designsystem.component.NarraProgressBar
import app.narra.core.designsystem.component.StatusPill
import app.narra.core.designsystem.component.StatusTone
import app.narra.core.designsystem.motion.sharedCover
import app.narra.core.designsystem.theme.NarraTheme
import app.narra.core.ui.formatDuration
import app.narra.domain.model.Book
import app.narra.domain.model.BookState
import app.narra.domain.model.Chapter
import app.narra.domain.model.ChapterState
import app.narra.presentation.common.metaLine
import app.narra.presentation.common.statusInfo

/** Descripción completa para TalkBack: un solo anuncio por libro en lugar de varios fragmentos. */
private fun Book.accessibilityLabel(): String = buildString {
    append(title)
    author?.let { append(", de ").append(it) }
    append(". ").append(statusInfo().label)
    metaLine().takeIf { it.isNotEmpty() }?.let { append(". ").append(it) }
}

/** Progreso a mostrar: lo escuchado, o lo generado si todavía se está procesando. */
private fun Book.visibleProgress(): Pair<Float, Boolean>? = when {
    state.isWorking || state == BookState.PAUSED -> generationFraction to true
    isStarted -> listenedFraction to false
    else -> null
}

/**
 * Celda de la cuadrícula de la biblioteca. [sharedCover] debe ser false si la misma portada ya
 * aparece en la pantalla: dos elementos compartidos con la misma clave se ocultan entre sí.
 */
@Composable
fun BookCard(book: Book, onClick: () -> Unit, modifier: Modifier = Modifier, coverWidth: Dp? = null, sharedCover: Boolean = true) {
    val tokens = NarraTheme.tokens
    Column(
        modifier = modifier
            .then(if (coverWidth != null) Modifier.width(coverWidth) else Modifier)
            .clip(RoundedCornerShape(tokens.coverRadius + 4.dp))
            .clickable(onClickLabel = "Abrir", role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = book.accessibilityLabel() }
            .padding(bottom = tokens.coverRadius),
    ) {
        Box {
            CoverImage(book.title, book.author, book.coverPath, if (sharedCover) Modifier.sharedCover(book.id) else Modifier)
            if (book.isFavorite) {
                Icon(
                    Icons.Rounded.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(18.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            book.title,
            style = if (tokens.editorial) MaterialTheme.typography.titleMedium.copy(fontFamily = MaterialTheme.typography.titleLarge.fontFamily) else MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        book.author?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        book.visibleProgress()?.let { (progress, generating) ->
            Spacer(Modifier.height(8.dp))
            NarraProgressBar(
                progress,
                color = if (generating) NarraTheme.colors.processing else MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(6.dp))
        val status = book.statusInfo()
        Text(
            status.label,
            style = MaterialTheme.typography.labelSmall,
            color = status.tone.contentColor(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Fila de la vista de lista. */
@Composable
fun BookListItem(
    book: Book,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val tokens = NarraTheme.tokens
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.cardRadius))
            .clickable(onClickLabel = "Abrir", role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = book.accessibilityLabel() }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(book.title, book.author, book.coverPath, Modifier.sharedCover(book.id), width = tokens.listCoverWidth, elevated = false)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            book.author?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Spacer(Modifier.height(4.dp))
            val status = book.statusInfo()
            val meta = book.metaLine()
            Text(
                listOf(status.label, meta).filter { it.isNotEmpty() }.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = status.tone.contentColor(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            book.visibleProgress()?.let { (progress, generating) ->
                Spacer(Modifier.height(6.dp))
                NarraProgressBar(
                    progress,
                    modifier = Modifier.widthIn(max = 280.dp),
                    color = if (generating) NarraTheme.colors.processing else MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
private fun StatusTone.contentColor() = when (this) {
    StatusTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    StatusTone.AVAILABLE -> NarraTheme.colors.available
    StatusTone.PROCESSING -> NarraTheme.colors.processing
    StatusTone.ATTENTION -> NarraTheme.colors.attention
}

/** Resumen de lo que está pasando con un libro en proceso, ya redactado para la UI. */
data class ProcessingSummary(
    val book: Book,
    /** "Generando capítulo 4". */
    val headline: String,
    /** "Segmento 12 de 27". */
    val detail: String?,
    /** "3 capítulos disponibles para escuchar". */
    val availability: String?,
    val fraction: Float,
    val etaMs: Long?,
    val isPaused: Boolean,
)

/** Tarjeta de un libro en proceso, con acceso al detalle y a pausar/reanudar. */
@Composable
fun ProcessingCard(
    summary: ProcessingSummary,
    onClick: () -> Unit,
    onTogglePause: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val book = summary.book
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(NarraTheme.tokens.cardRadius),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp).animateContentSize(), verticalAlignment = Alignment.CenterVertically) {
            CoverImage(book.title, book.author, book.coverPath, width = 56.dp, elevated = false)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    summary.headline,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (summary.isPaused) MaterialTheme.colorScheme.onSurfaceVariant else NarraTheme.colors.processing,
                    fontWeight = FontWeight.Medium,
                )
                val details = listOfNotNull(summary.detail, summary.etaMs?.let { "faltan ≈ ${formatDuration(it)}" })
                if (details.isNotEmpty()) {
                    Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                NarraProgressBar(summary.fraction, color = NarraTheme.colors.processing)
                AnimatedVisibility(summary.availability != null) {
                    Text(
                        summary.availability.orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        color = NarraTheme.colors.available,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            if (onTogglePause != null) {
                Spacer(Modifier.width(8.dp))
                FilledTonalIconButton(onClick = onTogglePause, modifier = Modifier.size(NarraTheme.tokens.minTouchTarget)) {
                    Icon(
                        if (summary.isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                        contentDescription = if (summary.isPaused) "Reanudar procesamiento" else "Pausar procesamiento",
                    )
                }
            }
        }
    }
}

/** Fila del índice de capítulos. */
@Composable
fun ChapterRow(
    chapter: Chapter,
    number: Int,
    listenedFraction: Float,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    /** Si no es null, tocar un capítulo sin audio pide generarlo antes que los demás. */
    onPrioritize: (() -> Unit)? = null,
) {
    val playable = chapter.isPlayable
    val canPrioritize = !playable && onPrioritize != null && chapter.included && chapter.state != ChapterState.AVAILABLE
    val stateText = when (chapter.state) {
        ChapterState.AVAILABLE -> "Disponible"
        ChapterState.PROCESSING -> "Procesando · ${chapter.segmentsAvailable} de ${chapter.segmentCount}"
        ChapterState.PENDING -> "Pendiente"
        ChapterState.ERROR -> "Error"
        ChapterState.SKIPPED -> "Omitido"
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                enabled = playable || canPrioritize,
                onClickLabel = if (playable) "Reproducir capítulo" else "Generar este capítulo primero",
                role = Role.Button,
                onClick = { if (playable) onPlay() else onPrioritize?.invoke() },
            )
            .semantics(mergeDescendants = true) {
                stateDescription = if (isCurrent) "Capítulo actual. $stateText" else stateText
            }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(36.dp), contentAlignment = Alignment.Center) {
            if (isCurrent) {
                Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            } else {
                Text(
                    number.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(8.dp + (chapter.level * 12).dp))
        Column(Modifier.weight(1f)) {
            Text(
                chapter.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                color = if (chapter.included) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChapterStateIcon(chapter.state, size = 14.dp, decorative = true)
                Spacer(Modifier.width(6.dp))
                Text(
                    listOfNotNull(
                        stateText,
                        chapter.durationMs.takeIf { it > 0 }?.let {
                            if (chapter.state == ChapterState.AVAILABLE) formatDuration(it) else "≈ ${formatDuration(it)}"
                        },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (listenedFraction > 0f) {
                Spacer(Modifier.height(6.dp))
                NarraProgressBar(listenedFraction, height = 3.dp)
            }
        }
        IconButton(onClick = onPlay, enabled = playable) {
            Icon(
                if (isCurrent && isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isCurrent && isPlaying) "Pausar" else "Reproducir ${chapter.title}",
            )
        }
    }
}

/** Píldora de estado de un libro. */
@Composable
fun BookStatusPill(book: Book, modifier: Modifier = Modifier) {
    val status = book.statusInfo()
    StatusPill(status.label, status.tone, modifier)
}
