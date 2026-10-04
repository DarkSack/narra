package app.narra.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.narra.core.designsystem.component.CoverImage
import app.narra.core.designsystem.component.NarraProgressBar
import app.narra.core.designsystem.theme.NarraTheme
import app.narra.core.ui.formatClock
import app.narra.core.ui.formatSpeed
import app.narra.domain.model.AppSettings
import app.narra.playback.PlaybackContract
import app.narra.playback.SleepTimerState
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

data class MiniPlayerState(
    val bookId: String,
    val title: String,
    val author: String?,
    val coverPath: String?,
    val chapterTitle: String?,
    val progress: Float,
    val isPlaying: Boolean,
    val isBuffering: Boolean,
    val waitingForAudio: Boolean,
)

/** Reproductor minimizado y persistente sobre la navegación. Tocarlo abre "Reproduciendo". */
@Composable
fun MiniPlayer(state: MiniPlayerState, onOpen: () -> Unit, onPlayPause: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(NarraTheme.tokens.cardRadius.coerceAtMost(20.dp)),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Reproductor: ${state.title}. Toca para abrir" },
    ) {
        Column {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                CoverImage(state.title, state.author, state.coverPath, width = 36.dp, elevated = false)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(state.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        when {
                            state.waitingForAudio -> "Esperando el siguiente fragmento…"
                            else -> state.chapterTitle.orEmpty()
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                PlayPauseButton(state.isPlaying, state.isBuffering || state.waitingForAudio, onPlayPause)
            }
            NarraProgressBar(state.progress, height = 2.dp, modifier = Modifier.padding(horizontal = 12.dp))
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun PlayPauseButton(isPlaying: Boolean, busy: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(NarraTheme.tokens.minTouchTarget)) {
        AnimatedContent(
            targetState = when {
                busy && isPlaying -> 2
                isPlaying -> 1
                else -> 0
            },
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "playPause",
        ) { state ->
            when (state) {
                2 -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                1 -> Icon(Icons.Rounded.Pause, contentDescription = "Pausar")
                else -> Icon(Icons.Rounded.PlayArrow, contentDescription = "Reproducir")
            }
        }
    }
}

/** Selector de velocidad: valores habituales y un ajuste fino de 0,5× a 3×. */
@Composable
fun SpeedSelector(speed: Float, onSpeedChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    var custom by remember(speed) { mutableFloatStateOf(speed) }
    Column(modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text("Velocidad", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            formatSpeed(custom),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppSettings.SPEEDS.forEach { value ->
                FilterChip(
                    selected = (custom * 100).roundToInt() == (value * 100).roundToInt(),
                    onClick = {
                        custom = value
                        onSpeedChange(value)
                    },
                    label = { Text(formatSpeed(value)) },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Ajuste fino", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = custom,
            onValueChange = { custom = (it * 20).roundToInt() / 20f },
            onValueChangeFinished = { onSpeedChange(custom) },
            valueRange = AppSettings.MIN_SPEED..AppSettings.MAX_SPEED,
            modifier = Modifier.semantics { contentDescription = "Velocidad de reproducción" },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatSpeed(AppSettings.MIN_SPEED), style = MaterialTheme.typography.labelSmall)
            Text(formatSpeed(AppSettings.MAX_SPEED), style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(24.dp))
    }
}

private val SLEEP_OPTIONS = listOf(5, 10, 15, 30, 45, 60)

/** Temporizador de apagado, con el tiempo restante en vivo. */
@Composable
fun SleepTimerSelector(
    state: SleepTimerState,
    onSelect: (minutes: Int) -> Unit,
    onCustom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(bottom = 24.dp)) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text("Temporizador", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            SleepTimerStatus(state)
        }
        SLEEP_OPTIONS.forEach { minutes ->
            SleepOption("$minutes minutos", selected = false) { onSelect(minutes) }
        }
        SleepOption("Al terminar el capítulo", selected = state is SleepTimerState.EndOfChapter) {
            onSelect(PlaybackContract.SLEEP_END_OF_CHAPTER)
        }
        SleepOption("Personalizado…", selected = false, onClick = onCustom)
        if (state !is SleepTimerState.Off) {
            TextButton(onClick = { onSelect(PlaybackContract.SLEEP_OFF) }, modifier = Modifier.padding(horizontal = 12.dp)) {
                Text("Desactivar temporizador")
            }
        }
    }
}

@Composable
fun SleepTimerStatus(state: SleepTimerState) {
    when (state) {
        SleepTimerState.Off -> Text(
            "La reproducción se detendrá sola, con el volumen bajando poco a poco.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SleepTimerState.EndOfChapter -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Bedtime, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("Se detendrá al terminar el capítulo", style = MaterialTheme.typography.bodyLarge)
        }
        is SleepTimerState.Running -> {
            var remaining by remember(state) { mutableLongStateOf(state.remainingMs) }
            LaunchedEffect(state) {
                while (remaining > 0) {
                    delay(TICK_MS)
                    remaining = state.remainingMs
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.HourglassTop, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Se detendrá en ${formatClock(remaining)}", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun SleepOption(label: String, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = if (selected) {
            { Icon(Icons.Rounded.Check, contentDescription = "Seleccionado", tint = MaterialTheme.colorScheme.primary) }
        } else {
            null
        },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clickable(role = Role.Button, onClick = onClick),
    )
}

/** Diálogo-contenido para elegir minutos a mano. */
@Composable
fun CustomSleepMinutes(onConfirm: (Int) -> Unit, modifier: Modifier = Modifier) {
    var minutes by remember { mutableFloatStateOf(DEFAULT_CUSTOM_MINUTES) }
    Column(modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text("${minutes.roundToInt()} minutos", style = MaterialTheme.typography.headlineSmall)
        Slider(
            value = minutes,
            onValueChange = { minutes = it },
            valueRange = MIN_CUSTOM_MINUTES..MAX_CUSTOM_MINUTES,
            steps = (MAX_CUSTOM_MINUTES - MIN_CUSTOM_MINUTES).toInt() / CUSTOM_STEP_MINUTES - 1,
        )
        OutlinedButton(onClick = { onConfirm(minutes.roundToInt()) }, modifier = Modifier.align(Alignment.End)) {
            Text("Iniciar")
        }
    }
}

private const val TICK_MS = 1_000L
private const val DEFAULT_CUSTOM_MINUTES = 20f
private const val MIN_CUSTOM_MINUTES = 5f
private const val MAX_CUSTOM_MINUTES = 180f
private const val CUSTOM_STEP_MINUTES = 5
