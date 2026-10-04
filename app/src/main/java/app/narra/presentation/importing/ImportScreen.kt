package app.narra.presentation.importing

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.narra.core.designsystem.component.CoverImage
import app.narra.core.designsystem.component.ErrorState
import app.narra.core.designsystem.component.NarraProgressBar
import app.narra.core.designsystem.component.SectionHeader
import app.narra.core.designsystem.motion.sharedCover
import app.narra.core.designsystem.theme.NarraTheme
import app.narra.core.ui.RecoveryAction
import app.narra.core.ui.formatDuration
import app.narra.core.ui.languageLabel
import app.narra.core.ui.plural
import app.narra.core.ui.toErrorText
import app.narra.domain.model.Book
import app.narra.domain.model.BookMetadata
import app.narra.domain.model.BookState
import app.narra.domain.model.Chapter
import app.narra.presentation.common.chapterLabel
import app.narra.presentation.common.explanation
import app.narra.presentation.common.highlights
import app.narra.presentation.common.summary

/**
 * Del PDF recién elegido al libro listo para convertir: muestra el análisis en curso y, al
 * terminar, permite revisar datos, portada y capítulos antes de generar el audio.
 */
@Composable
fun ImportScreen(
    onBack: () -> Unit,
    onReadChapter: (Chapter) -> Unit,
    onChooseVoice: (String) -> Unit,
    onAudiobookStarted: (String) -> Unit,
    viewModel: ImportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }
    // El aviso de "tu libro está listo" necesita permiso de notificaciones (Android 13+). Se pide
    // al crear el audiolibro, que es cuando se entiende para qué sirve; la creación sigue igual si se niega.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.create() }
    val context = LocalContext.current
    val create: () -> Unit = {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission && state.isFirstReview) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else viewModel.create()
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ImportEvent.Deleted, ImportEvent.Done -> onBack()
                ImportEvent.Started -> state.book?.let { onAudiobookStarted(it.id) }
                is ImportEvent.Message -> snackbar.showSnackbar(event.text)
            }
        }
    }

    val book = state.book
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isReady) "Revisar libro" else "Importando", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver") }
                },
                actions = {
                    // Descartar solo tiene sentido antes de crear el audio; después se borra desde la ficha.
                    if (book != null && (book.state.isImporting || book.state == BookState.ERROR || state.isFirstReview)) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Descartar este libro")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        bottomBar = {
            if (state.isReady && book != null) {
                CreateBar(
                    book = book,
                    includedCount = state.includedCount,
                    firstReview = state.isFirstReview,
                    onChooseVoice = { onChooseVoice(book.id) },
                    onCreate = create,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        AnimatedContent(
            targetState = when {
                state.loading -> Phase.LOADING
                book == null -> Phase.GONE
                book.state == BookState.ERROR -> Phase.ERROR
                state.isAnalyzing -> Phase.ANALYZING
                else -> Phase.READY
            },
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "importPhase",
            modifier = Modifier.padding(padding),
        ) { phase ->
            when (phase) {
                Phase.LOADING, Phase.GONE -> Box(Modifier.fillMaxSize())
                Phase.ANALYZING -> book?.let { AnalyzingContent(it) }
                Phase.ERROR -> book?.let {
                    ErrorContent(it, onRetry = viewModel::retry, onDiscard = { confirmDelete = true })
                }
                Phase.READY -> book?.let { ReadyContent(state, it, viewModel, onReadChapter) }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Descartar este libro?") },
            text = { Text("Se borrará la copia del PDF que hizo Narra. El archivo original no se toca.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }) { Text("Descartar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

private enum class Phase { LOADING, GONE, ANALYZING, ERROR, READY }

// ---------------------------------------------------------------------- Analizando

@Composable
private fun AnalyzingContent(book: Book) {
    val tokens = NarraTheme.tokens
    val pulse = if (NarraTheme.reduceMotion) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "pulse")
        transition.animateFloat(
            initialValue = PULSE_MIN_ALPHA,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(PULSE_MILLIS), RepeatMode.Reverse),
            label = "alpha",
        ).value
    }
    val analysis = book.analysis
    val steps = listOf(
        Step("PDF copiado en tu teléfono", done = true, active = false),
        Step(
            if (analysis.pagesTotal > 0) "Leyendo páginas · ${analysis.pagesDone} de ${analysis.pagesTotal}" else "Abriendo el PDF",
            done = book.state == BookState.PARSING,
            active = book.state == BookState.IDLE || book.state == BookState.ANALYZING,
            progress = analysis.fraction.takeIf { analysis.pagesTotal > 0 && book.state == BookState.ANALYZING },
        ),
        Step("Buscando capítulos y limpiando el texto", done = false, active = book.state == BookState.PARSING),
    )
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = tokens.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        CoverImage(book.title, book.author, book.coverPath, Modifier.sharedCover(book.id).alpha(pulse), width = tokens.heroCoverWidth)
        Spacer(Modifier.height(24.dp))
        Text(book.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(
            "Preparamos el libro en tu teléfono. Puedes salir: seguiremos en segundo plano.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp),
        )
        Spacer(Modifier.height(32.dp))
        Column(Modifier.widthIn(max = 480.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            steps.forEach { StepRow(it) }
        }
    }
}

private data class Step(val label: String, val done: Boolean, val active: Boolean, val progress: Float? = null)

@Composable
private fun StepRow(step: Step) {
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            when {
                step.done -> Icon(Icons.Rounded.Check, contentDescription = "Hecho", tint = NarraTheme.colors.available)
                step.active -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = NarraTheme.colors.processing)
                else -> Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.outlineVariant, CircleShape))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                step.label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (step.done || step.active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            step.progress?.let {
                Spacer(Modifier.height(8.dp))
                NarraProgressBar(it, color = NarraTheme.colors.processing)
            }
        }
    }
}

// ---------------------------------------------------------------------- Error

@Composable
private fun ErrorContent(book: Book, onRetry: () -> Unit, onDiscard: () -> Unit) {
    val error = (book.error?.kind)?.toErrorText()
    Column(Modifier.fillMaxSize().padding(NarraTheme.tokens.screenPadding), horizontalAlignment = Alignment.CenterHorizontally) {
        CoverImage(book.title, book.author, book.coverPath, width = NarraTheme.tokens.heroCoverWidth * 0.7f, elevated = false)
        Spacer(Modifier.height(24.dp))
        if (error != null) {
            // Solo se ofrecen las acciones que ya se pueden ejecutar desde aquí.
            val action: (() -> Unit)? = when (error.action) {
                RecoveryAction.RETRY -> onRetry
                RecoveryAction.CHOOSE_ANOTHER_FILE, RecoveryAction.DELETE -> onDiscard
                else -> null
            }
            ErrorState(error, onAction = action)
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onRetry) { Text("Analizar de nuevo") }
            TextButton(onClick = onDiscard) { Text("Descartar") }
        }
    }
}

// ---------------------------------------------------------------------- Revisión

@Composable
private fun ReadyContent(state: ImportUiState, book: Book, viewModel: ImportViewModel, onReadChapter: (Chapter) -> Unit) {
    val tokens = NarraTheme.tokens
    var editing by rememberSaveable { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Chapter?>(null) }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.setCover(uri)
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.fillMaxSize()) {
        item(key = "header") {
            Column(Modifier.fillMaxWidth().padding(horizontal = tokens.screenPadding), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Tu libro está listo",
                    style = if (tokens.editorial) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Revisa los datos y los capítulos. Todo se puede cambiar después.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                Box {
                    CoverImage(book.title, book.author, book.coverPath, Modifier.sharedCover(book.id), width = tokens.heroCoverWidth)
                    Surface(
                        onClick = { coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.align(Alignment.BottomEnd).offset(x = 12.dp, y = 12.dp).size(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.PhotoLibrary, contentDescription = "Cambiar portada", modifier = Modifier.size(20.dp))
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(book.title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                Text(
                    book.author ?: "Autor desconocido",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    listOfNotNull(
                        plural(book.pageCount, "página", "páginas"),
                        plural(state.includedCount, "capítulo", "capítulos"),
                        book.durationMs.takeIf { it > 0 }?.let { "≈ ${formatDuration(it)} de audio" },
                        languageLabel(book.metadata.language),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { editing = true }) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Editar título, autor y más")
                }
            }
        }

        state.report?.let { report ->
            item(key = "report") {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(tokens.cardRadius),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = tokens.screenPadding, vertical = tokens.sectionSpacing / 2),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Text("Qué preparamos", style = MaterialTheme.typography.titleSmall)
                        }
                        Text(report.chapterSource.explanation(), style = MaterialTheme.typography.bodyMedium)
                        report.highlights().forEach { line ->
                            Row {
                                Text("•", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(16.dp))
                                Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        item(key = "chapters-header") {
            val allIncluded = state.chapters.all { it.included }
            SectionHeader(
                "Capítulos",
                subtitle = "${state.includedCount} de ${state.chapters.size} se convertirán en audio",
                actionLabel = if (allIncluded) "Quitar todos" else "Incluir todos",
                onAction = { viewModel.includeAll(!allIncluded) },
                modifier = Modifier.padding(horizontal = tokens.screenPadding, vertical = 8.dp),
            )
        }
        itemsIndexed(state.chapters, key = { _, chapter -> chapter.id }) { index, chapter ->
            ChapterEditorRow(
                chapter = chapter,
                number = index + 1,
                isFirst = index == 0,
                isLast = index == state.chapters.lastIndex,
                onToggle = { viewModel.setIncluded(chapter, !chapter.included) },
                onRename = { renaming = chapter },
                onMove = { viewModel.move(chapter, it) },
                onRead = { onReadChapter(chapter) },
                modifier = Modifier.animateItem().padding(horizontal = tokens.screenPadding - 8.dp),
            )
            if (index < state.chapters.lastIndex) {
                HorizontalDivider(Modifier.padding(start = tokens.screenPadding + 48.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
        }
    }

    if (editing) {
        MetadataDialog(book, onDismiss = { editing = false }, onSave = {
            editing = false
            viewModel.saveMetadata(it)
        })
    }
    renaming?.let { chapter ->
        RenameDialog(chapter, onDismiss = { renaming = null }, onSave = {
            renaming = null
            viewModel.rename(chapter, it)
        })
    }
}

@Composable
private fun ChapterEditorRow(
    chapter: Chapter,
    number: Int,
    isFirst: Boolean,
    isLast: Boolean,
    onToggle: () -> Unit,
    onRename: () -> Unit,
    onMove: (Int) -> Unit,
    onRead: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Ver el texto", onClick = onRead).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = chapter.included, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f).alpha(if (chapter.included) 1f else DISABLED_ALPHA)) {
            Text(
                chapterLabel(number, chapter.title),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(
                    chapter.durationMs.takeIf { it > 0 }?.let { "≈ ${formatDuration(it)}" },
                    if (chapter.endPage > chapter.startPage) "págs. ${chapter.startPage + 1}–${chapter.endPage + 1}" else "pág. ${chapter.startPage + 1}",
                    if (!chapter.included) "no se incluye" else null,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "Opciones de ${chapter.title}") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Ver el texto") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.MenuBook, contentDescription = null) },
                    onClick = { menu = false; onRead() },
                )
                DropdownMenuItem(
                    text = { Text("Renombrar") },
                    leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                    onClick = { menu = false; onRename() },
                )
                if (!isFirst) {
                    DropdownMenuItem(
                        text = { Text("Subir") },
                        leadingIcon = { Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = null) },
                        onClick = { menu = false; onMove(-1) },
                    )
                }
                if (!isLast) {
                    DropdownMenuItem(
                        text = { Text("Bajar") },
                        leadingIcon = { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null) },
                        onClick = { menu = false; onMove(1) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateBar(book: Book, includedCount: Int, firstReview: Boolean, onChooseVoice: () -> Unit, onCreate: () -> Unit) {
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Column(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = NarraTheme.tokens.screenPadding, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.RecordVoiceOver, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    book.voice.summary(),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onChooseVoice) { Text("Cambiar voz") }
            }
            Button(onClick = onCreate, enabled = includedCount > 0, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (firstReview) "Crear audiolibro" else "Hecho")
            }
            Text(
                when {
                    includedCount == 0 -> "Incluye al menos un capítulo."
                    firstReview -> "Podrás escuchar el primer capítulo en cuanto esté listo."
                    else -> "Los cambios ya están guardados."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun RenameDialog(chapter: Chapter, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var title by rememberSaveable { mutableStateOf(chapter.title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renombrar capítulo") },
        text = { OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { onSave(title) }, enabled = title.isNotBlank()) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun MetadataDialog(book: Book, onDismiss: () -> Unit, onSave: (BookMetadata) -> Unit) {
    val meta = book.metadata
    var title by rememberSaveable { mutableStateOf(meta.title) }
    var author by rememberSaveable { mutableStateOf(meta.author.orEmpty()) }
    var description by rememberSaveable { mutableStateOf(meta.description.orEmpty()) }
    var publisher by rememberSaveable { mutableStateOf(meta.publisher.orEmpty()) }
    var date by rememberSaveable { mutableStateOf(meta.publishedDate.orEmpty()) }
    var isbn by rememberSaveable { mutableStateOf(meta.isbn.orEmpty()) }
    var language by rememberSaveable { mutableStateOf(meta.language.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Datos del libro") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Título") }, singleLine = true, isError = title.isBlank())
                OutlinedTextField(author, { author = it }, label = { Text("Autor") }, singleLine = true)
                OutlinedTextField(description, { description = it }, label = { Text("Descripción") }, minLines = 2, maxLines = 5)
                LanguageField(language) { language = it }
                OutlinedTextField(publisher, { publisher = it }, label = { Text("Editorial") }, singleLine = true)
                OutlinedTextField(date, { date = it }, label = { Text("Fecha de publicación") }, singleLine = true)
                OutlinedTextField(isbn, { isbn = it }, label = { Text("ISBN") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        meta.copy(
                            title = title.trim(),
                            author = author.trim().ifEmpty { null },
                            description = description.trim().ifEmpty { null },
                            language = language.ifEmpty { null },
                            publisher = publisher.trim().ifEmpty { null },
                            publishedDate = date.trim().ifEmpty { null },
                            isbn = isbn.trim().ifEmpty { null },
                        ),
                    )
                },
                enabled = title.isNotBlank(),
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun LanguageField(tag: String, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = languageLabel(tag) ?: "Sin especificar",
            onValueChange = {},
            readOnly = true,
            label = { Text("Idioma") },
            trailingIcon = { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null) },
            singleLine = true,
        )
        // Capa transparente encima del campo para abrir el menú al tocar.
        Box(Modifier.matchParentSize().clickable(role = Role.DropdownList, onClickLabel = "Elegir idioma") { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            LANGUAGES.forEach { option ->
                DropdownMenuItem(text = { Text(languageLabel(option) ?: option) }, onClick = { open = false; onChange(option) })
            }
        }
    }
}

private val LANGUAGES = listOf("es", "en", "pt", "fr", "it", "de", "ca")
private const val PULSE_MILLIS = 900
private const val PULSE_MIN_ALPHA = 0.55f
private const val DISABLED_ALPHA = 0.55f
