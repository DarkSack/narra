package app.narra.presentation.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.narra.domain.model.LibraryFilter
import app.narra.domain.model.LibrarySort

val LibraryFilter.label: String
    get() = when (this) {
        LibraryFilter.ALL -> "Todos"
        LibraryFilter.PROCESSING -> "Procesando"
        LibraryFilter.AVAILABLE -> "Disponibles"
        LibraryFilter.COMPLETED -> "Completados"
        LibraryFilter.FAVORITES -> "Favoritos"
        LibraryFilter.ERRORS -> "Con errores"
    }

val LibrarySort.label: String
    get() = when (this) {
        LibrarySort.RECENT -> "Recientes"
        LibrarySort.TITLE -> "Título"
        LibrarySort.AUTHOR -> "Autor"
        LibrarySort.PROGRESS -> "Progreso"
        LibrarySort.DURATION -> "Duración"
        LibrarySort.IMPORTED -> "Fecha de importación"
    }

/** Campo de búsqueda en forma de píldora. */
@Composable
fun NarraSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Buscar por título, autor o capítulo",
) {
    val focus = LocalFocusManager.current
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, maxLines = 1) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Borrar búsqueda")
                }
            }
        } else {
            null
        },
        singleLine = true,
        shape = CircleShape,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

/** Filtros, orden y vista de la biblioteca en una franja. */
@Composable
fun LibraryControls(
    filter: LibraryFilter,
    sort: LibrarySort,
    counts: Map<LibraryFilter, Int>,
    onFilterChange: (LibraryFilter) -> Unit,
    onSortChange: (LibrarySort) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LibraryFilter.entries.forEach { option ->
            val count = counts[option] ?: 0
            if (option == LibraryFilter.ALL || count > 0 || option == filter) {
                FilterChip(
                    selected = option == filter,
                    onClick = { onFilterChange(option) },
                    label = { Text(if (option == LibraryFilter.ALL) option.label else "${option.label} · $count") },
                    leadingIcon = if (option == filter) {
                        { Icon(Icons.Rounded.Check, contentDescription = null) }
                    } else {
                        null
                    },
                )
            }
        }
        SortMenu(sort, onSortChange)
    }
}

@Composable
private fun SortMenu(sort: LibrarySort, onSortChange: (LibrarySort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = false,
            onClick = { expanded = true },
            label = { Text(sort.label) },
            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "Ordenar por") },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            LibrarySort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    trailingIcon = if (option == sort) {
                        { Icon(Icons.Rounded.Check, contentDescription = "Seleccionado") }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        onSortChange(option)
                    },
                )
            }
        }
    }
}
