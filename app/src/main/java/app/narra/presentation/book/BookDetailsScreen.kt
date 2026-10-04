package app.narra.presentation.book

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.narra.core.designsystem.component.CoverImage
import app.narra.core.designsystem.component.EmptyState
import app.narra.core.designsystem.component.ErrorState
import app.narra.core.designsystem.component.NarraProgressBar
import app.narra.core.designsystem.component.SectionHeader
import app.narra.core.designsystem.component.rememberCoverAccent
import app.narra.core.designsystem.motion.sharedCover
import app.narra.core.designsystem.motion.sharedTitle
import app.narra.core.designsystem.theme.NarraTheme
import app.narra.core.designsystem.theme.narraTween
import app.narra.core.ui.RecoveryAction
import app.narra.core.ui.formatBytes
import app.narra.core.ui.formatPercent
import app.narra.core.ui.formatRelativeDay
import app.narra.core.ui.formatRemaining
import app.narra.core.ui.languageLabel
import app.narra.core.ui.toErrorText
import app.narra.domain.model.Book
import app.narra.domain.model.BookState
import app.narra.domain.model.ExportStatus
import app.narra.presentation.common.metaLine
import app.narra.presentation.common.openStorageSettings
import app.narra.presentation.common.openVoiceInstaller
import app.narra.presentation.components.BookStatusPill
import app.narra.presentation.components.ChapterRow
import app.narra.presentation.components.ProcessingCard
import app.narra.presentation.navigation.LocalBottomOverlayPadding
import kotlinx.coroutines.launch

/**
 * Ficha de un libro. Las acciones que dependen de fases todavía no disponibles llegan como
 * `null` y no se muestran: nunca hay botones que no hacen nada.
 */
@Composable
fun BookDetailsScreen(
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenProcessing: ((String) -> Unit)?,
    onReview: (String) -> Unit,
    onEdit: ((String) -> Unit)?,
    onChooseVoice: ((String) -> Unit)?,
    viewModel: BookDetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val export by viewModel.export.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val chooseFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { folder ->
        if (folder != null) viewModel.export(folder)
    }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var confirm by remember { mutableStateOf<DeleteKind?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                BookDetailsEvent.Deleted -> onBack()
                is BookDetailsEvent.Message -> snackbar.showSnackbar(event.text)
            }
        }
    }

    val book = state.book
    val playable = book?.hasPlayableAudio
    LaunchedEffect(playable) { if (playable != null) viewModel.onPlayableChanged(playable) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver") }
                },
                actions = {
                    if (book != null) {
                        IconButton(onClick = viewModel::toggleFavorite) {
                            Icon(
                                if (book.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = if (book.isFavorite) "Quitar de favoritos" else "Añadir a favoritos",
                                tint = if (book.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OverflowMenu(
                            book = book,
                            onEdit = onEdit?.let { { it(book.id) } },
                            onChooseVoice = onChooseVoice?.let { { it(book.id) } },
                            onStopGeneration = viewModel::cancelGeneration.takeIf { state.isGenerating },
                            onExport = { chooseFolder.launch(null) }.takeIf { state.canExport && export !is ExportStatus.Running },
                            onDeleteAudio = { confirm = DeleteKind.AUDIO },
                            onDeleteBook = { confirm = DeleteKind.BOOK },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding))
            state.missing || book == null -> EmptyState(
                icon = Icons.Rounded.AutoStories,
                title = "Este libro ya no está",
                explanation = "Se eliminó de tu biblioteca.",
                primaryLabel = "Volver",
                onPrimary = onBack,
                modifier = Modifier.padding(padding).padding(top = 48.dp),
            )
            else -> BookDetailsContent(
                state = state,
                book = book,
                export = export,
                contentPadding = padding,
                onPlay = { viewModel.play(); if (!state.isPlayingThisBook) onOpenPlayer() },
                onPlayChapter = viewModel::playChapter,
                onOpenProcessing = onOpenProcessing?.let { { it(book.id) } },
                onTogglePause = viewModel::togglePause.takeIf { state.isGenerating },
                onReview = { onReview(book.id) },
                onErrorAction = { action ->
                    when (action) {
                        RecoveryAction.RETRY -> viewModel.retry()
                        RecoveryAction.CHOOSE_VOICE -> onChooseVoice?.invoke(book.id)
                        RecoveryAction.INSTALL_VOICE -> if (!context.openVoiceInstaller()) {
                            scope.launch { snackbar.showSnackbar("Instala un motor de voz desde Google Play, como «Servicios de voz de Google».") }
                        }
                        RecoveryAction.FREE_SPACE -> context.openStorageSettings()
                        RecoveryAction.CHOOSE_ANOTHER_FILE, RecoveryAction.RUN_OCR, RecoveryAction.DELETE -> onReview(book.id)
                    }
                },
            )
        }
    }

    confirm?.let { kind ->
        DeleteDialog(
            kind = kind,
            book = book,
            onConfirm = {
                confirm = null
                if (kind == DeleteKind.AUDIO) viewModel.deleteAudio() else viewModel.deleteBook()
            },
            onDismiss = { confirm = null },
        )
    }
}

private enum class DeleteKind { AUDIO, BOOK }

@Composable
private fun BookDetailsContent(
    state: BookDetailsUiState,
    book: Book,
    export: ExportStatus?,
    contentPadding: PaddingValues,
    onPlay: () -> Unit,
    onPlayChapter: (app.narra.domain.model.Chapter) -> Unit,
    onOpenProcessing: (() -> Unit)?,
    onTogglePause: (() -> Unit)?,
    onReview: () -> Unit,
    onErrorAction: (RecoveryAction) -> Unit,
) {
    val tokens = NarraTheme.tokens
    val accent by animateColorAsState(rememberCoverAccent(book.title, book.coverPath), narraTween(), label = "accent")
    val chapters = state.includedChapters
    LazyColumn(
        contentPadding = PaddingValues(bottom = LocalBottomOverlayPadding.current + tokens.sectionSpacing),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "header") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (tokens.tintedBackdrops) {
                            Modifier.background(Brush.verticalGradient(listOf(accent.copy(alpha = if (NarraTheme.isDark) 0.5f else 0.32f), Color.Transparent)))
                        } else {
                            Modifier
                        },
                    )
                    .padding(top = contentPadding.calculateTopPadding())
                    .padding(horizontal = tokens.screenPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(8.dp))
                CoverImage(
                    book.title,
                    book.author,
                    book.coverPath,
                    Modifier.sharedCover(book.id),
                    width = tokens.heroCoverWidth * if (tokens.editorial) 1.2f else 1.4f,
                    contentDescription = "Portada de ${book.title}",
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    book.title,
                    style = if (tokens.editorial) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.sharedTitle(book.id).semantics { heading() },
                )
                book.author?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(12.dp))
                BookStatusPill(book)
                book.metaLine().takeIf { it.isNotEmpty() }?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(20.dp))
                PlayBlock(book, state.isPlayingThisBook, onPlay)
            }
        }

        if (book.needsReview) {
            item(key = "review") { ReviewCard(book, onReview, Modifier.padding(horizontal = tokens.screenPadding, vertical = 8.dp)) }
        }

        book.error?.takeIf { book.state == BookState.ERROR }?.let { error ->
            item(key = "error") {
                val text = error.kind.toErrorText()
                ErrorState(
                    text,
                    Modifier.padding(horizontal = tokens.screenPadding, vertical = 8.dp),
                    onAction = text.action?.let { action -> { onErrorAction(action) } },
                )
            }
        }

        state.processing?.let { summary ->
            item(key = "processing") {
                ProcessingCard(
                    summary = summary,
                    onClick = { onOpenProcessing?.invoke() },
                    onTogglePause = onTogglePause,
                    modifier = Modifier.padding(horizontal = tokens.screenPadding, vertical = 8.dp),
                )
            }
        }

        (export as? ExportStatus.Running)?.let { running ->
            item(key = "export") { ExportProgress(running, Modifier.padding(horizontal = tokens.screenPadding, vertical = 8.dp)) }
        }

        book.metadata.description?.takeIf { it.isNotBlank() }?.let { description ->
            item(key = "description") { Description(description) }
        }

        if (chapters.isNotEmpty()) {
            item(key = "chapters-header") {
                SectionHeader(
                    "Capítulos",
                    subtitle = "${book.chaptersAvailable} de ${chapters.size} con audio",
                    modifier = Modifier.padding(start = tokens.screenPadding, end = tokens.screenPadding, top = tokens.sectionSpacing, bottom = 4.dp),
                )
            }
            itemsIndexed(chapters, key = { _, chapter -> chapter.id }) { index, chapter ->
                ChapterRow(
                    chapter = chapter,
                    number = index + 1,
                    listenedFraction = state.listenedFraction(chapter),
                    isCurrent = chapter.id == state.currentChapterId,
                    isPlaying = state.isPlayingThisBook,
                    onPlay = { onPlayChapter(chapter) },
                    onPrioritize = { onPlayChapter(chapter) }.takeIf { state.isGenerating },
                    modifier = Modifier.padding(horizontal = tokens.screenPadding - 8.dp),
                )
            }
        }

        item(key = "info") { BookInfo(book, Modifier.padding(horizontal = tokens.screenPadding, vertical = tokens.sectionSpacing)) }
    }
}

/** Aún no hay audio porque el libro se está preparando o espera la revisión. */
private val Book.needsReview: Boolean
    get() = state.isImporting || state == BookState.READY || (state == BookState.ERROR && chapterCount == 0)

@Composable
private fun ReviewCard(book: Book, onReview: () -> Unit, modifier: Modifier = Modifier) {
    val (text, action) = when {
        book.state.isImporting -> "Estamos preparando este libro." to "Ver progreso"
        book.state == BookState.ERROR -> "No pudimos preparar este libro." to "Ver detalles"
        else -> "Revisa los capítulos y crea el audiolibro." to "Revisar"
    }
    androidx.compose.material3.Surface(
        onClick = onReview,
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(NarraTheme.tokens.cardRadius),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Text(action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun PlayBlock(book: Book, isPlaying: Boolean, onPlay: () -> Unit) {
    Column(Modifier.widthIn(max = 420.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (book.isStarted && !book.isCompleted) {
            NarraProgressBar(book.listenedFraction, height = 6.dp)
            Spacer(Modifier.height(6.dp))
            Text(
                "${formatPercent(book.listenedFraction)} escuchado · ${formatRemaining(book.remainingMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
        }
        Button(onClick = onPlay, enabled = book.hasPlayableAudio, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    isPlaying -> "Pausar"
                    !book.hasPlayableAudio -> "Disponible cuando termine el primer capítulo"
                    book.isCompleted -> "Escuchar de nuevo"
                    book.isStarted -> "Continuar"
                    else -> "Empezar a escuchar"
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Description(text: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val tokens = NarraTheme.tokens
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = tokens.screenPadding, vertical = 12.dp)
            .clickable(role = Role.Button, onClickLabel = if (expanded) "Contraer" else "Leer más") { expanded = !expanded }
            .animateContentSize(narraTween<IntSize>()),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded) Int.MAX_VALUE else DESCRIPTION_LINES,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            if (expanded) "Mostrar menos" else "Leer más",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun BookInfo(book: Book, modifier: Modifier = Modifier) {
    val rows = buildList {
        add("Archivo" to book.sourceFileName)
        if (book.pageCount > 0) add("Páginas" to book.pageCount.toString())
        add("Tamaño del PDF" to formatBytes(book.fileSizeBytes))
        if (book.audioSizeBytes > 0) add("Audio generado" to formatBytes(book.audioSizeBytes))
        languageLabel(book.metadata.language)?.let { add("Idioma" to it) }
        book.metadata.publisher?.let { add("Editorial" to it) }
        book.metadata.publishedDate?.let { add("Publicación" to it) }
        book.metadata.isbn?.let { add("ISBN" to it) }
        add("Importado" to formatRelativeDay(book.importedAt))
    }
    Column(modifier) {
        SectionHeader("Información")
        Spacer(Modifier.height(8.dp))
        rows.forEachIndexed { index, (label, value) ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.4f))
                Text(value, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End, modifier = Modifier.weight(0.6f))
            }
        }
    }
}

@Composable
private fun OverflowMenu(
    book: Book,
    onEdit: (() -> Unit)?,
    onChooseVoice: (() -> Unit)?,
    onStopGeneration: (() -> Unit)?,
    onExport: (() -> Unit)?,
    onDeleteAudio: () -> Unit,
    onDeleteBook: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "Más acciones") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            onEdit?.let {
                DropdownMenuItem(
                    text = { Text("Editar libro y capítulos") },
                    leadingIcon = { Icon(Icons.Rounded.Tune, contentDescription = null) },
                    onClick = { expanded = false; it() },
                )
            }
            onChooseVoice?.let {
                DropdownMenuItem(
                    text = { Text("Cambiar voz") },
                    leadingIcon = { Icon(Icons.Rounded.RecordVoiceOver, contentDescription = null) },
                    onClick = { expanded = false; it() },
                )
            }
            onStopGeneration?.let {
                DropdownMenuItem(
                    text = { Text("Detener la creación") },
                    leadingIcon = { Icon(Icons.Rounded.StopCircle, contentDescription = null) },
                    onClick = { expanded = false; it() },
                )
            }
            onExport?.let {
                DropdownMenuItem(
                    text = { Text("Exportar audio") },
                    leadingIcon = { Icon(Icons.Rounded.SaveAlt, contentDescription = null) },
                    onClick = { expanded = false; it() },
                )
            }
            if (book.audioSizeBytes > 0 || book.hasPlayableAudio) {
                DropdownMenuItem(
                    text = { Text("Eliminar solo el audio") },
                    leadingIcon = { Icon(Icons.Rounded.DeleteSweep, contentDescription = null) },
                    onClick = { expanded = false; onDeleteAudio() },
                )
            }
            DropdownMenuItem(
                text = { Text("Eliminar libro", color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Rounded.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                onClick = { expanded = false; onDeleteBook() },
            )
        }
    }
}

/** Avance de la exportación: se ve en la ficha además de en la notificación. */
@Composable
private fun ExportProgress(status: ExportStatus.Running, modifier: Modifier = Modifier) {
    val fraction = if (status.total > 0) status.done.toFloat() / status.total else null
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.SaveAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    if (status.total > 0) "Exportando el audio · ${status.done} de ${status.total} capítulos" else "Preparando la exportación",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (fraction == null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                val animated by animateFloatAsState(fraction, narraTween(), label = "export")
                LinearProgressIndicator(progress = { animated }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun DeleteDialog(kind: DeleteKind, book: Book?, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val title = book?.title.orEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(if (kind == DeleteKind.AUDIO) Icons.Rounded.DeleteSweep else Icons.Rounded.DeleteForever, contentDescription = null) },
        title = { Text(if (kind == DeleteKind.AUDIO) "¿Eliminar el audio?" else "¿Eliminar «$title»?") },
        text = {
            Text(
                if (kind == DeleteKind.AUDIO) {
                    "Liberarás ${formatBytes(book?.audioSizeBytes ?: 0)}. El libro, sus capítulos y tu progreso se conservan, y podrás volver a generar el audio."
                } else {
                    "Se borrarán el PDF importado, el audio y tu progreso. Esta acción no se puede deshacer."
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Eliminar", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

private const val DESCRIPTION_LINES = 4
