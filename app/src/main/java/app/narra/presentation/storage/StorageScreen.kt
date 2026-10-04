package app.narra.presentation.storage

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.narra.core.designsystem.component.CoverImage
import app.narra.core.designsystem.component.SectionHeader
import app.narra.core.designsystem.theme.NarraTheme
import app.narra.core.designsystem.theme.narraTween
import app.narra.core.ui.formatBytes
import app.narra.domain.model.StorageUsage
import app.narra.presentation.navigation.LocalBottomOverlayPadding

/** Qué ocupa Narra, qué se puede liberar y dónde están los archivos. */
@Composable
fun StorageScreen(onBack: () -> Unit, onOpenBook: (String) -> Unit, viewModel: StorageViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val tokens = NarraTheme.tokens

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Almacenamiento") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val usage = state.usage ?: return@Scaffold
        LazyColumn(
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + LocalBottomOverlayPadding.current + tokens.sectionSpacing,
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "summary") { Summary(usage, Modifier.padding(horizontal = tokens.screenPadding, vertical = 8.dp)) }
            item(key = "temporary") {
                TemporaryFilesCard(
                    bytes = usage.tempBytes,
                    clearing = state.clearing,
                    onClear = viewModel::clearTemporaryFiles,
                    modifier = Modifier.padding(horizontal = tokens.screenPadding, vertical = 8.dp),
                )
            }
            item(key = "location") { Location(Modifier.padding(horizontal = tokens.screenPadding, vertical = 8.dp)) }
            if (state.books.isNotEmpty()) {
                item(key = "books-header") {
                    SectionHeader(
                        "Por libro",
                        subtitle = "Para liberar espacio, abre un libro y elimina su audio: podrás volver a crearlo.",
                        modifier = Modifier.padding(start = tokens.screenPadding, end = tokens.screenPadding, top = tokens.sectionSpacing, bottom = 4.dp),
                    )
                }
                items(state.books, key = { it.book.id }) { usage ->
                    BookUsageRow(usage, onClick = { onOpenBook(usage.book.id) })
                }
            }
        }
    }
}

private data class Slice(val label: String, val bytes: Long, val color: Color)

@Composable
private fun Summary(usage: StorageUsage, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val slices = listOf(
        Slice("Audio", usage.audioBytes, colors.primary),
        Slice("PDF importados", usage.sourceBytes, NarraTheme.colors.available),
        Slice("Texto analizado y portadas", usage.workingBytes, colors.secondary),
        Slice("Archivos temporales", usage.tempBytes, colors.outline),
    )
    Column(modifier) {
        Text(formatBytes(usage.totalBytes), style = MaterialTheme.typography.displaySmall, modifier = Modifier.semantics { heading() })
        Text(
            "ocupa Narra · ${formatBytes(usage.availableBytes)} libres en el teléfono",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        UsageBar(slices, usage.totalBytes)
        Spacer(Modifier.height(12.dp))
        slices.forEach { slice ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(LEGEND_DOT).clip(CircleShape).background(slice.color))
                Spacer(Modifier.size(12.dp))
                Text(slice.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(formatBytes(slice.bytes), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
        }
    }
}

/** Barra apilada; la leyenda de debajo da los mismos datos a los lectores de pantalla. */
@Composable
private fun UsageBar(slices: List<Slice>, total: Long) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(BAR_HEIGHT)
            .clip(RoundedCornerShape(BAR_HEIGHT / 2))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clearAndSetSemantics {},
    ) {
        if (total > 0) {
            slices.filter { it.bytes > 0 }.forEach { slice ->
                key(slice.label) {
                    val weight by animateFloatAsState(slice.bytes.toFloat() / total, narraTween(), label = slice.label)
                    if (weight > 0f) Box(Modifier.weight(weight).fillMaxHeight().background(slice.color))
                }
            }
        }
    }
}

@Composable
private fun TemporaryFilesCard(bytes: Long, clearing: Boolean, onClear: () -> Unit, modifier: Modifier = Modifier) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.CleaningServices, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Archivos temporales · ${formatBytes(bytes)}", style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Restos de la creación y la exportación de audio. Borrarlos no afecta a tus libros; lo que esté en uso ahora se conserva.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = onClear, enabled = !clearing && bytes > 0) {
                Text(if (clearing) "Borrando…" else "Borrar temporales")
            }
        }
    }
}

@Composable
private fun Location(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Text(
            "Tus libros se guardan en el almacenamiento interno de Narra: otras apps no pueden leerlos y no salen del teléfono. " +
                "Para copiar un audiolibro a otra carpeta, ábrelo y elige «Exportar audio».",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BookUsageRow(usage: BookUsage, onClick: () -> Unit) {
    val book = usage.book
    val parts = listOfNotNull(
        book.audioSizeBytes.takeIf { it > 0 }?.let { "Audio ${formatBytes(it)}" },
        book.fileSizeBytes.takeIf { it > 0 }?.let { "PDF ${formatBytes(it)}" },
    )
    ListItem(
        leadingContent = { CoverImage(book.title, book.author, book.coverPath, width = ROW_COVER_WIDTH, elevated = false) },
        headlineContent = { Text(book.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(parts.joinToString(" · ")) },
        trailingContent = { Text(formatBytes(usage.bytes), style = MaterialTheme.typography.labelLarge) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = NarraTheme.tokens.screenPadding - 16.dp)
            .semantics(mergeDescendants = true) { contentDescription = "${book.title}, ${formatBytes(usage.bytes)}. ${parts.joinToString(", ")}" },
    )
}

private val BAR_HEIGHT = 14.dp
private val LEGEND_DOT = 10.dp
private val ROW_COVER_WIDTH = 40.dp
