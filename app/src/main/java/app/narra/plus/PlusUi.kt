package app.narra.plus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import app.narra.core.designsystem.component.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.dp

/** Libros que caben en la biblioteca sin Narra Plus. */
const val FREE_BOOK_LIMIT = 3

/** Narra Plus para las pantallas: si está activo y cómo ofrecerlo cuando no. */
class PlusAccess(val active: Boolean, val offer: () -> Unit) {
    /** Hace [action] si Plus está activo; si no, ofrece Plus. */
    fun require(action: () -> Unit) = if (active) action() else offer()
}

val LocalPlus = compositionLocalOf { PlusAccess(active = true, offer = {}) }

@Composable
fun PlusDialog(price: String?, onBuy: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Narra Plus") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Un solo pago y es tuyo para siempre:")
                Text("• Libros sin límite (sin Plus caben $FREE_BOOK_LIMIT)")
                Text("• Exportar el audio a la carpeta que elijas")
                Text("• Reconocer el texto de PDF escaneados")
                if (price == null) {
                    Text("Conectando con Google Play…", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = onBuy, enabled = price != null) {
                Text(if (price != null) "Desbloquear por $price" else "Desbloquear")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Ahora no") } },
    )
}
