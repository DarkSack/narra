package app.narra.presentation.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.FilterAltOff
import androidx.compose.material.icons.rounded.LocalLibrary
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.narra.core.designsystem.component.BookRowSkeleton
import app.narra.core.designsystem.component.EmptyState
import app.narra.core.designsystem.theme.NarraTheme
import app.narra.domain.model.LibraryFilter
import app.narra.domain.model.LibraryView
import app.narra.presentation.components.BookCard
import app.narra.presentation.components.BookListItem
import app.narra.presentation.components.LibraryControls
import app.narra.presentation.components.NarraSearchField
import app.narra.presentation.components.label
import app.narra.presentation.navigation.LocalBottomOverlayPadding

@Composable
fun LibraryScreen(
    onOpenBook: (String) -> Unit,
    onImport: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val tokens = NarraTheme.tokens
    val bottom = LocalBottomOverlayPadding.current + tokens.sectionSpacing

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        Row(
            Modifier.fillMaxWidth().padding(start = tokens.screenPadding, end = tokens.screenPadding / 2, top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Biblioteca",
                style = if (tokens.editorial) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (state.totalBooks > 0) {
                val grid = state.view == LibraryView.GRID
                IconButton(onClick = { viewModel.setView(if (grid) LibraryView.LIST else LibraryView.GRID) }) {
                    Icon(
                        if (grid) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                        contentDescription = if (grid) "Ver como lista" else "Ver como cuadrícula",
                    )
                }
            }
            IconButton(onClick = onImport) { Icon(Icons.Rounded.Add, contentDescription = "Importar un PDF") }
        }
        if (state.totalBooks > 0) {
            NarraSearchField(
                query = query,
                onQueryChange = viewModel::setQuery,
                modifier = Modifier.padding(horizontal = tokens.screenPadding, vertical = 12.dp),
            )
            LibraryControls(
                filter = state.filter,
                sort = state.sort,
                counts = state.counts,
                onFilterChange = viewModel::setFilter,
                onSortChange = viewModel::setSort,
                contentPadding = PaddingValues(horizontal = tokens.screenPadding),
            )
        }

        when {
            state.loading -> Column(
                Modifier.padding(tokens.screenPadding),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) { repeat(SKELETON_ROWS) { BookRowSkeleton(tokens.listCoverWidth) } }

            state.totalBooks == 0 -> EmptyState(
                icon = Icons.Rounded.LocalLibrary,
                title = "Tu biblioteca está vacía",
                explanation = "Importa un PDF y aparecerá aquí mientras lo preparamos. Podrás escucharlo en cuanto esté listo el primer capítulo.",
                primaryLabel = "Importar un PDF",
                onPrimary = onImport,
                modifier = Modifier.padding(top = 48.dp),
            )

            state.books.isEmpty() && state.isSearching -> EmptyState(
                icon = Icons.Rounded.SearchOff,
                title = "Nada coincide con «${state.query.trim()}»",
                explanation = "Buscamos en títulos, autores, palabras clave y nombres de capítulo. Prueba con otra palabra.",
                secondaryLabel = "Borrar búsqueda",
                onSecondary = { viewModel.setQuery("") },
                modifier = Modifier.padding(top = 32.dp),
            )

            state.books.isEmpty() -> EmptyState(
                icon = Icons.Rounded.FilterAltOff,
                title = "Ningún libro en «${state.filter.label}»",
                explanation = emptyFilterExplanation(state.filter),
                secondaryLabel = "Ver todos",
                onSecondary = { viewModel.setFilter(LibraryFilter.ALL) },
                modifier = Modifier.padding(top = 32.dp),
            )

            state.view == LibraryView.GRID -> LazyVerticalGrid(
                columns = GridCells.Adaptive(tokens.gridMinCellWidth),
                contentPadding = PaddingValues(
                    start = tokens.screenPadding,
                    end = tokens.screenPadding,
                    top = 16.dp,
                    bottom = bottom,
                ),
                horizontalArrangement = Arrangement.spacedBy(tokens.itemSpacing),
                verticalArrangement = Arrangement.spacedBy(tokens.sectionSpacing * 0.66f),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) { ResultCount(state.books.size, state.totalBooks) }
                items(state.books, key = { it.id }) { book ->
                    BookCard(book, onClick = { onOpenBook(book.id) }, modifier = Modifier.animateItem())
                }
            }

            else -> LazyColumn(
                contentPadding = PaddingValues(
                    start = tokens.screenPadding - 4.dp,
                    end = tokens.screenPadding - 4.dp,
                    top = 8.dp,
                    bottom = bottom,
                ),
                modifier = Modifier.fillMaxSize(),
            ) {
                item { ResultCount(state.books.size, state.totalBooks, Modifier.padding(horizontal = 4.dp)) }
                items(state.books, key = { it.id }) { book ->
                    BookListItem(book, onClick = { onOpenBook(book.id) }, modifier = Modifier.animateItem())
                }
            }
        }
    }
}

@Composable
private fun ResultCount(shown: Int, total: Int, modifier: Modifier = Modifier) {
    Text(
        if (shown == total) "$total ${if (total == 1) "libro" else "libros"}" else "$shown de $total libros",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(vertical = 4.dp),
    )
}

private fun emptyFilterExplanation(filter: LibraryFilter): String = when (filter) {
    LibraryFilter.ALL -> ""
    LibraryFilter.PROCESSING -> "No hay libros preparándose ahora mismo. Los nuevos aparecerán aquí mientras se analizan y se genera su audio."
    LibraryFilter.AVAILABLE -> "Cuando un libro tenga al menos un capítulo con audio aparecerá aquí."
    LibraryFilter.COMPLETED -> "Los libros que escuches hasta el final se guardan aquí."
    LibraryFilter.FAVORITES -> "Marca un libro con el corazón en su ficha para encontrarlo rápido."
    LibraryFilter.ERRORS -> "Ningún libro tiene problemas. Si algo falla, lo verás aquí con lo que puedes hacer."
}

private const val SKELETON_ROWS = 4
