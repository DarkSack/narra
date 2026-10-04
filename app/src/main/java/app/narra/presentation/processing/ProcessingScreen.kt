package app.narra.presentation.processing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.narra.core.designsystem.component.ChapterStateIcon
import app.narra.core.designsystem.component.CoverImage
import app.narra.core.designsystem.component.ErrorState
import app.narra.core.designsystem.component.NarraProgressBar
import app.narra.core.designsystem.component.SectionHeader
import app.narra.core.designsystem.theme.NarraTheme
import app.narra.core.ui.RecoveryAction
import app.narra.core.ui.formatDuration
import app.narra.core.ui.formatPercent
import app.narra.core.ui.toErrorText
import app.narra.domain.model.BookState
import app.narra.domain.model.Chapter
import app.narra.domain.model.ChapterState
import app.narra.domain.model.ErrorKind
import app.narra.domain.model.JobState
import app.narra.domain.model.ProcessingJob
import app.narra.presentation.common.chapterLabel
import app.narra.presentation.common.openStorageSettings
import app.narra.presentation.common.openVoiceInstaller
import app.narra.presentation.navigation.LocalBottomOverlayPadding
import kotlinx.coroutines.launch

/**
 * Qué está pasando con la creación de un audiolibro: cuánto falta, qué se puede escuchar ya y
 * qué hacer (pausar, adelantar en la cola, generar antes un capítulo, reintentar).
 */
@Composable
fun ProcessingScreen(
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenProcessing: (String) -> Unit,
    onChooseVoice: (String) -> Unit,
    viewModel: ProcessingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var confirmCancel by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ProcessingEvent.Message -> snackbar.showSnackbar(event.text)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Creación del audiolibro") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val book = state.book
        if (state.loading || book == null) {
            Box(Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        val tokens = NarraTheme.tokens
        LazyColumn(
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = LocalBottomOverlayPadding.current + tokens.sectionSpacing),
        ) {
            item(key = "header") {
                Row(Modifier.padding(horizontal = tokens.screenPadding, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CoverImage(book.title, book.author, book.coverPath, width = 72.dp, elevated = false)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(book.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        book.author?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }

            item(key = "status") {
                StatusBlock(
                    state = state,
                    onTogglePause = viewModel::togglePause,
                    onListen = { viewModel.listen(); onOpenPlayer() },
                    onMoveToFront = viewModel::moveToFront,
                    onCancel = { confirmCancel = true },
                    modifier = Modifier.padding(horizontal = tokens.screenPadding, vertical = 8.dp),
                )
            }

            book.error?.takeIf { book.state == BookState.ERROR }?.let { error ->
                item(key = "error") {
                    val text = error.kind.toErrorText()
                    ErrorState(
                        text,
                        Modifier.padding(horizontal = tokens.screenPadding, vertical = 8.dp),
                        onAction = text.action?.let { action ->
                            {
                                when (action) {
                                    RecoveryAction.CHOOSE_VOICE -> onChooseVoice(book.id)
                                    RecoveryAction.INSTALL_VOICE -> if (!context.openVoiceInstaller()) {
                                        scope.launch { snackbar.showSnackbar("Instala un motor de voz desde Google Play.") }
                                    }
                                    RecoveryAction.FREE_SPACE -> context.openStorageSettings()
                                    else -> viewModel.retry()
                                }
                            }
                        },
                    )
                }
            }

            val chapters = state.includedChapters
            if (chapters.isNotEmpty()) {
                item(key = "chapters-header") {
                    SectionHeader(
                        "Capítulos",
                        subtitle = if (state.job != null) "Toca uno pendiente para generarlo antes." else null,
                        modifier = Modifier.padding(start = tokens.screenPadding, end = tokens.screenPadding, top = tokens.sectionSpacing, bottom = 4.dp),
                    )
                }
                itemsIndexed(chapters, key = { _, chapter -> chapter.id }) { index, chapter ->
                    ChapterProgressRow(
                        chapter = chapter,
                        number = index + 1,
                        isCurrent = chapter.id == state.job?.currentChapterId && state.job?.state == JobState.RUNNING,
                        onPrioritize = { viewModel.prioritize(chapter) }.takeIf {
                            state.job != null && chapter.state != ChapterState.AVAILABLE && chapter.state != ChapterState.ERROR
                        },
                    )
                }
            }

            val others = state.queue.filter { it.book.id != book.id }
            if (others.isNotEmpty()) {
                item(key = "queue-header") {
                    SectionHeader(
                        "Cola de creación",
                        subtitle = "Los libros se crean de uno en uno, en este orden.",
                        modifier = Modifier.padding(start = tokens.screenPadding, end = tokens.screenPadding, top = tokens.sectionSpacing, bottom = 4.dp),
                    )
                }
                itemsIndexed(state.queue, key = { _, entry -> "queue-${entry.book.id}" }) { index, entry ->
                    QueueRow(
                        position = index + 1,
                        title = entry.book.title,
                        status = entry.job.label(),
                        isThis = entry.book.id == book.id,
                        onClick = { if (entry.book.id != book.id) onOpenProcessing(entry.book.id) },
                    )
                }
            }
        }
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("¿Detener la creación?") },
            text = { Text("El audio ya creado se conserva y podrás escucharlo. Para seguir más tarde, vuelve a crear el audiolibro desde su ficha.") },
            confirmButton = { TextButton(onClick = { confirmCancel = false; viewModel.cancel() }) { Text("Detener") } },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("Seguir creando") } },
        )
    }
}

@Composable
private fun StatusBlock(
    state: ProcessingUiState,
    onTogglePause: () -> Unit,
    onListen: () -> Unit,
    onMoveToFront: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val book = state.book ?: return
    val summary = state.summary
    val completed = book.state == BookState.COMPLETED
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(NarraTheme.tokens.cardRadius), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                when {
                    completed -> "Audiolibro terminado"
                    state.job == null -> "Creación detenida"
                    else -> summary?.headline.orEmpty()
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (completed) NarraTheme.colors.available else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            val details = listOfNotNull(
                summary?.detail?.takeIf { state.isActive },
                summary?.etaMs?.takeIf { state.isActive }?.let { "faltan ≈ ${formatDuration(it)}" },
            )
            if (details.isNotEmpty()) {
                Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            NarraProgressBar(book.generationFraction, color = if (completed) NarraTheme.colors.available else NarraTheme.colors.processing, height = 8.dp)
            Spacer(Modifier.height(6.dp))
            Text(
                "${formatPercent(book.generationFraction)} · ${book.chaptersAvailable} de ${book.chapterCount} capítulos con audio",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.booksAhead > 0) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (state.booksAhead == 1) "Hay 1 libro antes en la cola." else "Hay ${state.booksAhead} libros antes en la cola.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onMoveToFront) { Text("Adelantar") }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (book.hasPlayableAudio) {
                    Button(onClick = onListen, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Headphones, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (completed) "Escuchar" else "Escuchar ya")
                    }
                }
                if (state.job != null && book.state != BookState.ERROR) {
                    FilledTonalButton(onClick = onTogglePause, modifier = Modifier.weight(1f)) {
                        Icon(if (state.isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.isPaused) "Reanudar" else "Pausar")
                    }
                }
            }
            if (state.job != null && book.state != BookState.ERROR) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Detener la creación") }
            }
        }
    }
}

@Composable
private fun ChapterProgressRow(chapter: Chapter, number: Int, isCurrent: Boolean, onPrioritize: (() -> Unit)?) {
    val tokens = NarraTheme.tokens
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (onPrioritize != null) {
                    Modifier.clickable(onClickLabel = "Generar este capítulo primero", role = Role.Button, onClick = onPrioritize)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = tokens.screenPadding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChapterStateIcon(chapter.state, size = 20.dp, decorative = true)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                chapterLabel(number, chapter.title),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                when (chapter.state) {
                    ChapterState.AVAILABLE -> "Listo para escuchar"
                    ChapterState.PROCESSING -> "Generando · ${chapter.segmentsAvailable} de ${chapter.segmentCount} partes"
                    ChapterState.PENDING -> "Pendiente"
                    ChapterState.ERROR -> "Falló una parte"
                    ChapterState.SKIPPED -> "No se incluye"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (chapter.state == ChapterState.PROCESSING) {
                Spacer(Modifier.height(6.dp))
                NarraProgressBar(chapter.generationFraction, color = NarraTheme.colors.processing, height = 3.dp)
            }
        }
        if (onPrioritize != null) {
            TextButton(onClick = onPrioritize) { Text("Ahora") }
        }
    }
}

@Composable
private fun QueueRow(position: Int, title: String, status: String, isThis: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = !isThis, onClickLabel = "Ver su progreso", role = Role.Button, onClick = onClick)
            .padding(horizontal = NarraTheme.tokens.screenPadding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$position", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(28.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (isThis) "$title (este libro)" else title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isThis) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun ProcessingJob.label(): String = if (state == JobState.QUEUED && error?.kind == ErrorKind.NETWORK) "Esperando conexión" else state.label()

private fun JobState.label(): String = when (this) {
    JobState.RUNNING -> "Creándose ahora"
    JobState.QUEUED -> "Esperando su turno"
    JobState.PAUSED -> "En pausa"
    JobState.FAILED -> "Necesita atención"
    JobState.DONE -> "Terminado"
    JobState.CANCELLED -> "Detenido"
}
