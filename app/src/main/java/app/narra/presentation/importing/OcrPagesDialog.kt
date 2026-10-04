package app.narra.presentation.importing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Confirma el reconocimiento de texto y deja limitarlo a un tramo de páginas: útil en un libro
 * largo del que solo interesa una parte, o para probar el resultado antes de procesarlo entero.
 *
 * @param pageCount páginas del PDF.
 * @param initial tramo elegido la vez anterior (páginas desde 0), o null si fueron todas.
 * @param losesChapterEdits el libro ya tiene capítulos revisados que se volverán a detectar.
 * @param onConfirm recibe el tramo elegido (páginas desde 0) o null para todas.
 */
@Composable
fun OcrPagesDialog(
    pageCount: Int,
    scannedPages: Int,
    initial: IntRange?,
    losesChapterEdits: Boolean,
    onConfirm: (IntRange?) -> Unit,
    onDismiss: () -> Unit,
) {
    val canChoose = pageCount >= MIN_PAGES_TO_CHOOSE
    var onlySome by rememberSaveable { mutableStateOf(initial != null && canChoose) }
    // Números de página tal como los ve el usuario (desde 1).
    var from by rememberSaveable { mutableFloatStateOf(((initial?.first ?: 0) + 1).toFloat()) }
    var to by rememberSaveable { mutableFloatStateOf(((initial?.last ?: (pageCount - 1)) + 1).coerceAtMost(pageCount).toFloat()) }
    val first = from.roundToInt()
    val last = to.roundToInt()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿Reconocer el texto escaneado?") },
        text = {
            Column {
                Text(
                    buildString {
                        append("Leeremos las páginas como imágenes, en tu teléfono, sin enviarlas a ningún sitio.")
                        if (losesChapterEdits) append(" Después volveremos a buscar los capítulos, así que se perderán los cambios que hayas hecho en ellos.")
                    },
                )
                if (canChoose) {
                    Spacer(Modifier.height(16.dp))
                    Column(Modifier.selectableGroup()) {
                        Choice(
                            label = if (scannedPages > 0) "Todas las páginas escaneadas ($scannedPages)" else "Todas las páginas escaneadas",
                            selected = !onlySome,
                            onSelect = { onlySome = false },
                        )
                        Choice(label = "Solo unas páginas", selected = onlySome, onSelect = { onlySome = true })
                    }
                    AnimatedVisibility(onlySome) {
                        Column(Modifier.padding(top = 8.dp)) {
                            Text(
                                if (first == last) "Solo la página $first" else "De la página $first a la $last",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            RangeSlider(
                                value = from..to,
                                onValueChange = { range ->
                                    from = range.start
                                    to = range.endInclusive
                                },
                                valueRange = 1f..pageCount.toFloat(),
                                // Un punto por página; en libros muy largos el arrastre sigue siendo continuo.
                                steps = if (pageCount <= MAX_STEPPED_PAGES) pageCount - 2 else 0,
                                modifier = Modifier.semantics { stateDescription = "De la página $first a la $last" },
                            )
                            Text(
                                "Las páginas escaneadas fuera de este tramo no se leerán en voz alta.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(if (onlySome) (first - 1)..(last - 1) else null) }) { Text("Reconocer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun Choice(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MIN_TOUCH_HEIGHT)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
    }
}

/** Con menos páginas no tiene sentido elegir un tramo. */
private const val MIN_PAGES_TO_CHOOSE = 2

/** Por encima, los puntos de cada página se apiñan y el deslizador se vuelve continuo. */
private const val MAX_STEPPED_PAGES = 60

private val MIN_TOUCH_HEIGHT = 48.dp
