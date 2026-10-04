package app.narra.presentation.about

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.narra.core.designsystem.theme.NarraTheme
import app.narra.presentation.navigation.LocalBottomOverlayPadding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** Una pieza de terceros incluida en la app y la licencia que la cubre. */
private data class Component(
    val name: String,
    val holder: String,
    val license: String,
    /** Texto completo en `assets/licenses`; null si la licencia no es de código abierto. */
    val asset: String?,
    val note: String? = null,
)

private const val APACHE = "Licencia Apache 2.0"
private const val APACHE_ASSET = "licenses/apache-2.0.txt"

private val COMPONENTS = listOf(
    Component(
        "Android Jetpack",
        "The Android Open Source Project",
        APACHE,
        APACHE_ASSET,
        note = "Compose, Material 3, Room, WorkManager, Navigation, Lifecycle, DataStore, Media3 y otras bibliotecas AndroidX.",
    ),
    Component("Dagger y Hilt", "Google", APACHE, APACHE_ASSET),
    Component("Kotlin, kotlinx.coroutines y kotlinx.serialization", "JetBrains", APACHE, APACHE_ASSET),
    Component("Coil", "Coil Contributors", APACHE, APACHE_ASSET),
    Component("Okio", "Square", APACHE, APACHE_ASSET),
    Component("Guava", "Google", APACHE, APACHE_ASSET),
    Component(
        "PdfBox-Android",
        "Tom Roush, a partir de Apache PDFBox de The Apache Software Foundation",
        APACHE,
        APACHE_ASSET,
    ),
    Component("Bouncy Castle", "The Legion of the Bouncy Castle Inc.", "Licencia MIT", "licenses/bouncycastle-MIT.txt"),
    Component("Literata", "The Literata Project Authors", "SIL Open Font License 1.1", "licenses/literata-OFL.txt"),
    Component("Inter", "The Inter Project Authors", "SIL Open Font License 1.1", "licenses/inter-OFL.txt"),
    Component(
        "ML Kit · reconocimiento de texto",
        "Google",
        "Términos de servicio de ML Kit",
        null,
        note = "No es código abierto. Funciona en el teléfono, sin enviar tus páginas a ningún sitio.",
    ),
)

/** Créditos y licencias de lo que Narra incluye de terceros. */
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Licencias") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver") }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + LocalBottomOverlayPadding.current + NarraTheme.tokens.sectionSpacing,
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Text(
                    "Narra se apoya en el trabajo de estas personas y proyectos. Gracias.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = NarraTheme.tokens.screenPadding, vertical = 8.dp),
                )
            }
            items(COMPONENTS, key = { it.name }) { component ->
                val isOpen = expanded == component.name
                ComponentItem(component, isOpen) { expanded = if (isOpen) null else component.name }
            }
        }
    }
}

@Composable
private fun ComponentItem(component: Component, isOpen: Boolean, onToggle: () -> Unit) {
    val padding = NarraTheme.tokens.screenPadding
    Column {
        ListItem(
            headlineContent = { Text(component.name) },
            supportingContent = {
                Column {
                    Text("${component.holder} · ${component.license}")
                    component.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            },
            trailingContent = component.asset?.let {
                { Icon(if (isOpen) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null) }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier
                .then(
                    if (component.asset != null) {
                        Modifier
                            .clickable(role = Role.Button, onClickLabel = if (isOpen) "Ocultar la licencia" else "Ver la licencia", onClick = onToggle)
                            .semantics { stateDescription = if (isOpen) "Licencia visible" else "Licencia oculta" }
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = padding - 16.dp),
        )
        component.asset?.let { asset ->
            AnimatedVisibility(visible = isOpen) {
                LicenseText(asset, Modifier.padding(horizontal = padding, vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun LicenseText(asset: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val text by produceState<String?>(null, asset) {
        value = withContext(Dispatchers.IO) {
            try {
                context.assets.open(asset).bufferedReader().use { it.readText() }.reflow()
            } catch (_: IOException) {
                null
            }
        }
    }
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Text(
            text ?: "",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp),
        )
    }
}

/**
 * Los textos de licencia vienen cortados a 80 columnas: se unen las líneas de cada párrafo para
 * que se ajusten al ancho de la pantalla.
 */
private fun String.reflow(): String =
    split(PARAGRAPH_BREAK).joinToString("\n\n") { it.trim().replace(WHITESPACE, " ") }.trim()

private val PARAGRAPH_BREAK = Regex("\\n\\s*\\n")
private val WHITESPACE = Regex("\\s+")
