package app.narra.presentation.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.narra.core.designsystem.component.CoverImage
import app.narra.core.designsystem.component.EmptyState
import app.narra.core.designsystem.component.rememberCoverAccent
import app.narra.core.designsystem.motion.sharedCover
import app.narra.core.designsystem.theme.NarraTheme
import app.narra.core.designsystem.theme.narraTween
import app.narra.core.ui.formatClock
import app.narra.core.ui.formatPercent
import app.narra.core.ui.formatSpeed
import app.narra.domain.model.BookState
import app.narra.playback.SleepTimerState
import app.narra.presentation.common.chapterHeading
import app.narra.presentation.components.ChapterRow
import app.narra.presentation.components.CustomSleepMinutes
import app.narra.presentation.components.SleepTimerSelector
import app.narra.presentation.components.SpeedSelector

private enum class PlayerSheet { SPEED, SLEEP, SLEEP_CUSTOM, CHAPTERS, VOLUME }

@Composable
fun PlayerScreen(
    onClose: () -> Unit,
    onOpenLibrary: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf<PlayerSheet?>(null) }
    val book = state.book

    val accent by animateColorAsState(
        if (book != null) rememberCoverAccent(book.title, book.coverPath) else MaterialTheme.colorScheme.primary,
        narraTween(),
        label = "accent",
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .background(
                Brush.verticalGradient(
                    listOf(accent.copy(alpha = if (NarraTheme.isDark) 0.55f else 0.35f), MaterialTheme.colorScheme.background),
                ),
            ),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Minimizar reproductor") }
                Text(
                    "Reproduciendo",
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { sheet = PlayerSheet.CHAPTERS }, enabled = state.chapters.isNotEmpty()) {
                    Icon(Icons.AutoMirrored.Rounded.FormatListBulleted, contentDescription = "Capítulos")
                }
            }
            if (book == null) {
                if (!state.loading) {
                    EmptyState(
                        icon = Icons.Rounded.Headphones,
                        title = "No hay nada sonando",
                        explanation = "Elige un libro de tu biblioteca para empezar a escuchar.",
                        primaryLabel = "Ir a la biblioteca",
                        onPrimary = onOpenLibrary,
                        modifier = Modifier.padding(top = 64.dp),
                    )
                }
            } else {
                PlayerBody(state, viewModel, onSheet = { sheet = it })
            }
        }
    }

    sheet?.let { current ->
        PlayerSheetHost(current, state, viewModel, onChange = { sheet = it })
    }
}

@Composable
private fun PlayerBody(state: PlayerUiState, viewModel: PlayerViewModel, onSheet: (PlayerSheet) -> Unit) {
    val book = state.book ?: return
    val tokens = NarraTheme.tokens
    val snapshot = state.snapshot
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // En pantallas bajas la portada cede espacio a los controles.
        val coverWidth: Dp = minOf(tokens.playerCoverMaxWidth, maxWidth - tokens.screenPadding * 4, maxHeight * 0.38f)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = tokens.screenPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))
            val coverScale by animateFloatAsState(if (snapshot.playWhenReady) 1f else COVER_PAUSED_SCALE, narraTween(), label = "coverScale")
            CoverImage(
                book.title,
                book.author,
                book.coverPath,
                Modifier.sharedCover(book.id).scale(coverScale),
                width = coverWidth,
                contentDescription = "Portada de ${book.title}",
            )
            Spacer(Modifier.height(28.dp))
            Text(
                book.title,
                style = if (tokens.editorial) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            book.author?.let {
                Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Spacer(Modifier.height(6.dp))
            val timeline = state.timeline
            Text(
                timeline?.let { chapterHeading(it.chapterNumber, it.chapterTitle) } ?: snapshot.chapterTitle.orEmpty(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(20.dp))
            ChapterSeekBar(state, onSeek = viewModel::seekInChapter)

            AnimatedVisibility(snapshot.waitingForAudio || snapshot.errorMessage != null) {
                Text(
                    snapshot.errorMessage ?: "Generando el siguiente fragmento. La reproducción seguirá sola en cuanto esté listo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (snapshot.errorMessage != null) MaterialTheme.colorScheme.error else NarraTheme.colors.processing,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Spacer(Modifier.height(12.dp))
            TransportControls(state, viewModel)
            Spacer(Modifier.height(24.dp))
            SecondaryControls(state, onSheet)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ChapterSeekBar(state: PlayerUiState, onSeek: (Float) -> Unit) {
    val timeline = state.timeline
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val value = if (dragging) dragValue else timeline?.chapterFraction ?: 0f
    val available = timeline?.chapterAvailableFraction ?: 0f
    val duration = timeline?.chapterDurationMs ?: 0L
    Column(Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
        Slider(
            value = value,
            onValueChange = {
                dragging = true
                // No se puede saltar más allá de lo que ya está generado.
                dragValue = it.coerceAtMost(available)
            },
            onValueChangeFinished = {
                onSeek(dragValue)
                dragging = false
            },
            enabled = timeline != null,
            modifier = Modifier.semantics {
                contentDescription = "Posición en el capítulo"
                stateDescription = "${formatClock((duration * value).toLong())} de ${formatClock(duration)}"
            },
        )
        Row(Modifier.fillMaxWidth()) {
            Text(
                formatClock((duration * value).toLong()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            if (available in 0f..AVAILABLE_HINT_THRESHOLD && timeline != null) {
                Text(
                    "Audio listo: ${formatPercent(available)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = NarraTheme.colors.processing,
                )
                Spacer(Modifier.weight(1f))
            }
            Text(
                "-${formatClock(timeline?.chapterRemainingMs ?: 0L)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TransportControls(state: PlayerUiState, viewModel: PlayerViewModel) {
    val snapshot = state.snapshot
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = viewModel::previousChapter, enabled = state.hasPrevious) {
            Icon(Icons.Rounded.SkipPrevious, contentDescription = "Capítulo anterior", modifier = Modifier.size(30.dp))
        }
        SkipButton(seconds = state.skipBackSeconds, forward = false, onClick = viewModel::seekBack)
        FilledIconButton(
            onClick = viewModel::togglePlayPause,
            modifier = Modifier.size(PLAY_BUTTON_SIZE),
            colors = IconButtonDefaults.filledIconButtonColors(),
        ) {
            when {
                snapshot.playWhenReady && (snapshot.isBuffering || snapshot.waitingForAudio) ->
                    CircularProgressIndicator(Modifier.size(32.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 3.dp)
                snapshot.playWhenReady -> Icon(Icons.Rounded.Pause, contentDescription = "Pausar", modifier = Modifier.size(40.dp))
                else -> Icon(Icons.Rounded.PlayArrow, contentDescription = "Reproducir", modifier = Modifier.size(40.dp))
            }
        }
        SkipButton(seconds = state.skipForwardSeconds, forward = true, onClick = viewModel::seekForward)
        IconButton(onClick = viewModel::nextChapter, enabled = state.hasNext) {
            Icon(Icons.Rounded.SkipNext, contentDescription = "Capítulo siguiente", modifier = Modifier.size(30.dp))
        }
    }
}

/** Botón de salto con los segundos configurados dentro de la flecha circular. */
@Composable
private fun SkipButton(seconds: Int, forward: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(56.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Rounded.Replay,
                contentDescription = if (forward) "Avanzar $seconds segundos" else "Retroceder $seconds segundos",
                modifier = Modifier.size(40.dp).graphicsLayer { scaleX = if (forward) -1f else 1f },
            )
            Text(
                seconds.toString(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

@Composable
private fun SecondaryControls(state: PlayerUiState, onSheet: (PlayerSheet) -> Unit) {
    val snapshot = state.snapshot
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        PillAction(Icons.Rounded.Speed, formatSpeed(snapshot.speed), "Velocidad de reproducción") { onSheet(PlayerSheet.SPEED) }
        PillAction(
            Icons.Rounded.Bedtime,
            when (val timer = snapshot.sleepTimer) {
                SleepTimerState.Off -> "Temporizador"
                SleepTimerState.EndOfChapter -> "Fin de cap."
                is SleepTimerState.Running -> formatClock(timer.remainingMs)
            },
            "Temporizador de apagado",
            highlighted = snapshot.sleepTimer != SleepTimerState.Off,
        ) { onSheet(PlayerSheet.SLEEP) }
        PillAction(Icons.AutoMirrored.Rounded.VolumeUp, formatPercent(snapshot.volume), "Volumen") { onSheet(PlayerSheet.VOLUME) }
    }
}

@Composable
private fun PillAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    description: String,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = "$description: $label" }) {
        Icon(icon, contentDescription = null, tint = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Text(label, color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun PlayerSheetHost(sheet: PlayerSheet, state: PlayerUiState, viewModel: PlayerViewModel, onChange: (PlayerSheet?) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = sheet == PlayerSheet.CHAPTERS)
    ModalBottomSheet(onDismissRequest = { onChange(null) }, sheetState = sheetState) {
        when (sheet) {
            PlayerSheet.SPEED -> SpeedSelector(state.snapshot.speed, viewModel::setSpeed)
            PlayerSheet.SLEEP -> SleepTimerSelector(
                state = state.snapshot.sleepTimer,
                onSelect = { minutes ->
                    viewModel.setSleepTimer(minutes)
                    onChange(null)
                },
                onCustom = { onChange(PlayerSheet.SLEEP_CUSTOM) },
            )
            PlayerSheet.SLEEP_CUSTOM -> CustomSleepMinutes(onConfirm = { minutes ->
                viewModel.setSleepTimer(minutes)
                onChange(null)
            })
            PlayerSheet.VOLUME -> VolumeSheet(state.snapshot.volume, viewModel::setVolume)
            PlayerSheet.CHAPTERS -> ChaptersSheet(
                state,
                onPlay = { chapter ->
                    if (chapter.id == state.timeline?.chapterId) viewModel.togglePlayPause() else viewModel.playChapter(chapter)
                    onChange(null)
                },
                onPrioritize = viewModel::prioritize,
            )
        }
    }
}

@Composable
private fun VolumeSheet(volume: Float, onChange: (Float) -> Unit) {
    Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
        Text("Volumen de Narra", style = MaterialTheme.typography.titleLarge)
        Text(
            "Independiente del volumen del sistema: útil para equilibrar con otras apps.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Slider(
                value = volume,
                onValueChange = onChange,
                modifier = Modifier.weight(1f).semantics { contentDescription = "Volumen" },
            )
            Spacer(Modifier.width(12.dp))
            Text(formatPercent(volume), style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(48.dp))
        }
    }
}

@Composable
private fun ChaptersSheet(
    state: PlayerUiState,
    onPlay: (app.narra.domain.model.Chapter) -> Unit,
    onPrioritize: (app.narra.domain.model.Chapter) -> Unit,
) {
    val chapters = state.includedChapters
    val currentIndex = chapters.indexOfFirst { it.id == state.timeline?.chapterId }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (currentIndex - 1).coerceAtLeast(0))
    LaunchedEffect(Unit) { listState.scrollToItem((currentIndex - 1).coerceAtLeast(0)) }
    Column {
        Text("Capítulos", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
            itemsIndexed(chapters, key = { _, chapter -> chapter.id }) { index, chapter ->
                val isCurrent = chapter.id == state.timeline?.chapterId
                ChapterRow(
                    chapter = chapter,
                    number = index + 1,
                    listenedFraction = when {
                        index < currentIndex -> 1f
                        isCurrent -> state.timeline?.chapterFraction ?: 0f
                        else -> 0f
                    },
                    isCurrent = isCurrent,
                    isPlaying = state.snapshot.playWhenReady,
                    onPlay = { onPlay(chapter) },
                    onPrioritize = { onPrioritize(chapter) }.takeIf {
                        state.book?.state == BookState.GENERATING || state.book?.state == BookState.PAUSED
                    },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

private val PLAY_BUTTON_SIZE = 76.dp
private const val COVER_PAUSED_SCALE = 0.92f

/** Por encima de esta fracción no vale la pena avisar de que falta audio. */
private const val AVAILABLE_HINT_THRESHOLD = 0.999f
