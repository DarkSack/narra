package app.narra.presentation.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.narra.core.designsystem.theme.NarraTheme
import app.narra.core.designsystem.theme.narraTween
import app.narra.presentation.common.openVoiceInstaller
import kotlinx.coroutines.launch

/**
 * Introducción corta: qué hace Narra, cómo pasa de PDF a audiolibro escuchando mientras se crea,
 * y lo que necesita del teléfono. Termina invitando a importar el primer libro.
 */
@Composable
fun OnboardingScreen(onFinish: (importNow: Boolean) -> Unit) {
    val pager = rememberPagerState { PAGE_COUNT }
    val scope = rememberCoroutineScope()
    val isLast = pager.currentPage == PAGE_COUNT - 1

    // Surface y no un fondo pintado: fija también el color del texto para el tema activo.
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                if (!isLast) TextButton(onClick = { onFinish(false) }) { Text("Saltar") }
            }
            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = NarraTheme.tokens.screenPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Column(Modifier.widthIn(max = MAX_CONTENT_WIDTH), horizontalAlignment = Alignment.CenterHorizontally) {
                        when (page) {
                            0 -> WhatItDoes()
                            1 -> ListenWhileCreating()
                            else -> WhatItNeeds()
                        }
                    }
                }
            }
            PageDots(pager.currentPage, Modifier.align(Alignment.CenterHorizontally).padding(vertical = 16.dp))
            Column(
                Modifier.fillMaxWidth().padding(horizontal = NarraTheme.tokens.screenPadding).padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val fade = narraTween<Float>()
                AnimatedContent(isLast, transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) }, label = "acciones") { last ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = MAX_CONTENT_WIDTH)) {
                        if (last) {
                            Button(onClick = { onFinish(true) }, modifier = Modifier.fillMaxWidth().heightIn(min = BUTTON_HEIGHT)) {
                                Text("Importar tu primer libro")
                            }
                            TextButton(onClick = { onFinish(false) }) { Text("Explorar primero") }
                        } else {
                            Button(
                                onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                                modifier = Modifier.fillMaxWidth().heightIn(min = BUTTON_HEIGHT),
                            ) {
                                Text("Siguiente")
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            // Ocupa lo mismo que «Explorar primero» para que el botón no salte al llegar al final.
                            Spacer(Modifier.height(SECONDARY_ACTION_HEIGHT))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WhatItDoes() {
    Title("Tus libros, en voz alta", "Narra convierte tus PDF en audiolibros organizados por capítulos, en tu teléfono y sin conexión.")
    Spacer(Modifier.height(32.dp))
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowStep(Icons.Rounded.PictureAsPdf, "Importa\nun PDF")
        Arrow()
        FlowStep(Icons.Rounded.AutoStories, "Revisa\ncapítulos")
        Arrow()
        FlowStep(Icons.Rounded.Headphones, "Escucha")
    }
    Spacer(Modifier.height(24.dp))
    Text(
        "Detectamos título, autor, portada y capítulos, y quitamos encabezados, números de página y notas para que la lectura sea limpia.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun ListenWhileCreating() {
    Title("Escucha mientras se crea", "No hace falta esperar al libro entero.")
    Spacer(Modifier.height(28.dp))
    val chapters = listOf("Capítulo 1" to "Listo para escuchar", "Capítulo 2" to "Creándose ahora", "Capítulo 3" to "En cola")
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            chapters.forEachIndexed { index, (title, status) ->
                val color = when (index) {
                    0 -> NarraTheme.colors.available
                    1 -> NarraTheme.colors.processing
                    else -> NarraTheme.colors.pending
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(STATUS_DOT).clip(CircleShape).background(color))
                    Spacer(Modifier.width(12.dp))
                    Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    Spacer(Modifier.height(24.dp))
    Text(
        "El audio se crea capítulo a capítulo. En cuanto el primero está listo puedes empezar a escuchar; los demás se añaden " +
            "solos, también con la app cerrada. Si necesitas uno antes, tócalo y pasará delante.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun WhatItNeeds() {
    val context = LocalContext.current
    val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    var notificationsAllowed by remember {
        mutableStateOf(
            !needsPermission ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> notificationsAllowed = granted }
    var voiceInstallerMissing by remember { mutableStateOf(false) }

    Title("Antes de empezar", "Narra solo necesita dos cosas del teléfono.")
    Spacer(Modifier.height(24.dp))
    Requirement(
        icon = Icons.Rounded.Notifications,
        title = "Avisos",
        text = "Para enseñarte el progreso mientras se crea el audio y avisarte cuando un libro esté listo.",
        done = notificationsAllowed,
        doneLabel = "Avisos permitidos",
        actionLabel = "Permitir avisos",
        onAction = { if (needsPermission) request.launch(Manifest.permission.POST_NOTIFICATIONS) },
    )
    Spacer(Modifier.height(12.dp))
    Requirement(
        icon = Icons.Rounded.RecordVoiceOver,
        title = "Una voz",
        text = if (voiceInstallerMissing) {
            "Instala un motor de voz desde Google Play, como «Servicios de voz de Google»."
        } else {
            "Narra usa las voces de tu teléfono. Si no tienes ninguna en tu idioma, puedes descargarla ahora o al elegir la voz."
        },
        done = false,
        doneLabel = "",
        actionLabel = "Descargar voces",
        onAction = { voiceInstallerMissing = !context.openVoiceInstaller() },
    )
    Spacer(Modifier.height(20.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "Tus libros y su audio no salen del teléfono.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Requirement(
    icon: ImageVector,
    title: String,
    text: String,
    done: Boolean,
    doneLabel: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(2.dp))
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                if (done) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = NarraTheme.colors.available, modifier = Modifier.size(18.dp))
                        Text(doneLabel, style = MaterialTheme.typography.labelLarge, color = NarraTheme.colors.available)
                    }
                } else {
                    OutlinedButton(onClick = onAction) { Text(actionLabel) }
                }
            }
        }
    }
}

@Composable
private fun Title(title: String, subtitle: String) {
    Text(
        title,
        style = if (NarraTheme.tokens.editorial) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { heading() },
    )
    Spacer(Modifier.height(12.dp))
    Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
}

@Composable
private fun FlowStep(icon: ImageVector, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(FLOW_STEP_WIDTH)) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(FLOW_ICON_SIZE)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Arrow() {
    Icon(
        Icons.AutoMirrored.Rounded.ArrowForward,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.outline,
        // Centrada con los círculos, no con las etiquetas de debajo.
        modifier = Modifier.padding(top = (FLOW_ICON_SIZE - ARROW_SIZE) / 2).size(ARROW_SIZE),
    )
}

@Composable
private fun PageDots(current: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.semantics { contentDescription = "Página ${current + 1} de $PAGE_COUNT" },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(PAGE_COUNT) { index ->
            val selected = index == current
            val width by animateDpAsState(if (selected) DOT_SELECTED_WIDTH else DOT_SIZE, narraTween(), label = "punto")
            val color by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                narraTween(),
                label = "color",
            )
            Box(Modifier.height(DOT_SIZE).width(width).clip(CircleShape).background(color))
        }
    }
}

private const val PAGE_COUNT = 3
private val MAX_CONTENT_WIDTH = 480.dp
private val BUTTON_HEIGHT = 52.dp

/** Altura mínima de un botón de texto de Material 3 (área táctil). */
private val SECONDARY_ACTION_HEIGHT = 48.dp
private val STATUS_DOT = 10.dp
private val FLOW_STEP_WIDTH = 80.dp
private val FLOW_ICON_SIZE = 64.dp
private val ARROW_SIZE = 20.dp
private val DOT_SIZE = 8.dp
private val DOT_SELECTED_WIDTH = 24.dp
