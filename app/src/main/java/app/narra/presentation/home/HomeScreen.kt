package app.narra.presentation.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.narra.core.designsystem.component.BookRowSkeleton
import app.narra.core.designsystem.component.CoverImage
import app.narra.core.designsystem.component.EmptyState
import app.narra.core.designsystem.component.NarraProgressBar
import app.narra.core.designsystem.component.SectionHeader
import app.narra.core.designsystem.component.rememberCoverAccent
import app.narra.core.designsystem.motion.sharedCover
import app.narra.core.designsystem.theme.NarraTheme
import app.narra.core.designsystem.theme.narraTween
import app.narra.core.ui.formatPercent
import app.narra.core.ui.formatRemaining
import app.narra.core.ui.toErrorText
import app.narra.domain.model.Book
import app.narra.domain.model.BookState
import app.narra.presentation.components.BookCard
import app.narra.presentation.components.BookListItem
import app.narra.presentation.components.ProcessingCard
import app.narra.presentation.navigation.LocalBottomOverlayPadding

@Composable
fun HomeScreen(
    onOpenBook: (String) -> Unit,
    onOpenProcessing: (Book) -> Unit,
    onReview: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenPlayer: () -> Unit,
    onImport: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tokens = NarraTheme.tokens
    val bottom = LocalBottomOverlayPadding.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottom + tokens.sectionSpacing),
        verticalArrangement = Arrangement.spacedBy(tokens.sectionSpacing),
    ) {
        item(key = "header") {
            HomeHeader(state, onSearch = onOpenLibrary, onImport = onImport)
        }

        when {
            state.loading -> item(key = "loading") {
                Column(Modifier.padding(horizontal = tokens.screenPadding), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    repeat(3) { BookRowSkeleton(tokens.listCoverWidth) }
                }
            }
            state.isEmpty -> item(key = "empty") { EmptyHome(onImport) }
            else -> {
                state.continueListening?.let { current ->
                    item(key = "continue") {
                        if (tokens.editorial) {
                            ContinueHero(current, onPlay = { viewModel.play(current.book); onOpenPlayer() }, onOpen = { onOpenBook(current.book.id) })
                        } else {
                            ContinueCard(current, onPlay = { viewModel.play(current.book); onOpenPlayer() }, onOpen = { onOpenBook(current.book.id) })
                        }
                    }
                }
                if (state.needsAttention.isNotEmpty()) {
                    item(key = "attention") { AttentionSection(state.needsAttention, onOpenBook) }
                }
                if (state.toReview.isNotEmpty()) {
                    item(key = "review") { ReviewSection(state.toReview, onReview) }
                }
                if (state.processing.isNotEmpty()) {
                    item(key = "processing") {
                        Column(Modifier.padding(horizontal = tokens.screenPadding), verticalArrangement = Arrangement.spacedBy(tokens.itemSpacing)) {
                            SectionHeader("Procesando", subtitle = "Puedes escuchar lo que ya esté listo mientras terminamos el resto.")
                            state.processing.forEach { summary ->
                                ProcessingCard(
                                    summary = summary,
                                    onClick = { onOpenProcessing(summary.book) },
                                    // El análisis no se puede pausar; la creación del audio sí.
                                    onTogglePause = { viewModel.togglePause(summary.book.id, summary.isPaused) }
                                        .takeIf { summary.book.state == BookState.GENERATING || summary.book.state == BookState.PAUSED },
                                )
                            }
                        }
                    }
                }
                if (state.recentlyListened.isNotEmpty()) {
                    item(key = "listened") { Shelf("Escuchados recientemente", state.recentlyListened, onOpenBook, shownAbove = shownBefore(state, Section.LISTENED)) }
                }
                if (state.recentlyAdded.isNotEmpty()) {
                    item(key = "added") { Shelf("Añadidos recientemente", state.recentlyAdded, onOpenBook, shownAbove = shownBefore(state, Section.ADDED)) }
                }
                item(key = "library") {
                    OutlinedButton(
                        onClick = onOpenLibrary,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = tokens.screenPadding),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.LibraryBooks, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Ver toda la biblioteca · ${state.totalBooks}")
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(state: HomeUiState, onSearch: () -> Unit, onImport: () -> Unit) {
    val tokens = NarraTheme.tokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = tokens.screenPadding, end = tokens.screenPadding / 2, top = 16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(state.greeting, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                state.headline,
                style = if (tokens.editorial) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
        }
        IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, contentDescription = "Buscar en la biblioteca") }
        IconButton(onClick = onImport) { Icon(Icons.Rounded.Add, contentDescription = "Importar un PDF") }
    }
}

/** Estilo Lectura: portada protagonista sobre un fondo teñido con su color. */
@Composable
private fun ContinueHero(current: ContinueListening, onPlay: () -> Unit, onOpen: () -> Unit) {
    val book = current.book
    val tokens = NarraTheme.tokens
    val accent by animateColorAsState(rememberCoverAccent(book.title, book.coverPath), narraTween(), label = "accent")
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = tokens.screenPadding)
            .background(
                Brush.verticalGradient(listOf(accent.copy(alpha = if (NarraTheme.isDark) 0.45f else 0.28f), Color.Transparent)),
                RoundedCornerShape(tokens.cardRadius),
            ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Surface(onClick = onOpen, color = Color.Transparent) {
                    CoverImage(book.title, book.author, book.coverPath, Modifier.sharedCover(book.id), width = tokens.heroCoverWidth)
                }
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(book.title, style = MaterialTheme.typography.headlineSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    book.author?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    current.chapterTitle?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            NarraProgressBar(book.listenedFraction, height = 6.dp)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${formatPercent(book.listenedFraction)} · ${formatRemaining(book.remainingMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = onPlay, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (book.isStarted) "Continuar" else "Empezar")
                }
            }
        }
    }
}

/** Estilo Android Modern: tarjeta compacta y funcional. */
@Composable
private fun ContinueCard(current: ContinueListening, onPlay: () -> Unit, onOpen: () -> Unit) {
    val book = current.book
    val tokens = NarraTheme.tokens
    Surface(
        onClick = onOpen,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(tokens.cardRadius),
        modifier = Modifier.fillMaxWidth().padding(horizontal = tokens.screenPadding),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverImage(book.title, book.author, book.coverPath, Modifier.sharedCover(book.id), width = tokens.heroCoverWidth * 0.75f)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                book.author?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                current.chapterTitle?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(10.dp))
                NarraProgressBar(book.listenedFraction)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${formatPercent(book.listenedFraction)} · ${formatRemaining(book.remainingMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            androidx.compose.material3.FilledIconButton(onClick = onPlay, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = if (book.isStarted) "Continuar ${book.title}" else "Empezar ${book.title}")
            }
        }
    }
}

private enum class Section { LISTENED, ADDED }

/** Libros cuya portada ya se dibujó más arriba: solo la primera aparición anima la transición. */
private fun shownBefore(state: HomeUiState, section: Section): Set<String> = buildSet {
    state.continueListening?.let { add(it.book.id) }
    state.toReview.forEach { add(it.id) }
    if (section == Section.ADDED) state.recentlyListened.forEach { add(it.id) }
}

@Composable
private fun AttentionSection(books: List<Book>, onOpenBook: (String) -> Unit) {
    val tokens = NarraTheme.tokens
    Column(Modifier.padding(horizontal = tokens.screenPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Necesita tu atención")
        books.forEach { book ->
            val error = book.error?.kind?.toErrorText()
            Surface(
                onClick = { onOpenBook(book.id) },
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = RoundedCornerShape(tokens.cardRadius),
            ) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.WarningAmber, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(error?.title ?: "Algo salió mal", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(error?.actionLabel ?: "Ver", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun ReviewSection(books: List<Book>, onReview: (String) -> Unit) {
    val tokens = NarraTheme.tokens
    Column(Modifier.padding(horizontal = tokens.screenPadding), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionHeader("Listos para revisar", subtitle = "Confirma capítulos y voz para crear el audiolibro.")
        books.forEach { book ->
            BookListItem(book, onClick = { onReview(book.id) }) {
                FilledTonalButton(onClick = { onReview(book.id) }) { Text("Revisar") }
            }
        }
    }
}

@Composable
private fun Shelf(title: String, books: List<Book>, onOpenBook: (String) -> Unit, shownAbove: Set<String>) {
    val tokens = NarraTheme.tokens
    Column {
        SectionHeader(title, Modifier.padding(horizontal = tokens.screenPadding))
        Spacer(Modifier.height(tokens.itemSpacing))
        LazyRow(
            contentPadding = PaddingValues(horizontal = tokens.screenPadding),
            horizontalArrangement = Arrangement.spacedBy(tokens.itemSpacing),
        ) {
            items(books, key = { it.id }) { book ->
                BookCard(book, onClick = { onOpenBook(book.id) }, coverWidth = tokens.shelfCoverWidth, sharedCover = book.id !in shownAbove)
            }
        }
    }
}

@Composable
private fun EmptyHome(onImport: () -> Unit) {
    val tokens = NarraTheme.tokens
    Column(Modifier.padding(horizontal = tokens.screenPadding)) {
        EmptyState(
            icon = Icons.Rounded.AutoStories,
            title = "Convierte tu primer libro",
            explanation = "Elige un PDF y Narra detectará su título, autor y capítulos para crear un audiolibro " +
                "en tu teléfono, sin subirlo a ningún servidor.",
            primaryLabel = "Importar un PDF",
            onPrimary = onImport,
        )
        Spacer(Modifier.height(8.dp))
        HowItWorks()
    }
}

@Composable
private fun HowItWorks() {
    val steps = listOf(
        "Analizamos el PDF" to "Portada, autor, índice y capítulos, todo automático.",
        "Generamos el audio por capítulos" to "En segundo plano, aunque bloquees el teléfono.",
        "Escuchas desde el primer capítulo" to "No esperas al libro entero: empieza cuando el capítulo 1 esté listo.",
    )
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(NarraTheme.tokens.cardRadius)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            steps.forEachIndexed { index, (title, body) ->
                Row {
                    Box(
                        Modifier.size(28.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(50)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${index + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(title, style = MaterialTheme.typography.titleSmall)
                        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
