package app.narra.presentation.settings

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.narra.core.designsystem.theme.Inter
import app.narra.core.designsystem.theme.Literata
import app.narra.core.designsystem.theme.NarraTheme
import app.narra.core.ui.formatPercent
import app.narra.domain.model.AppSettings
import app.narra.domain.model.ChapterEndBehavior
import app.narra.domain.model.ThemeMode
import app.narra.domain.model.UiDensity
import app.narra.domain.model.UiStyle
import app.narra.presentation.common.summary
import app.narra.presentation.navigation.LocalBottomOverlayPadding
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    onChooseDefaultVoice: () -> Unit,
    onOpenLicenses: (() -> Unit)?,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val current = settings ?: return
    val tokens = NarraTheme.tokens
    val update = viewModel::update

    LazyColumn(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(bottom = LocalBottomOverlayPadding.current + tokens.sectionSpacing),
    ) {
        item {
            Text(
                "Ajustes",
                style = if (tokens.editorial) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = tokens.screenPadding, vertical = 16.dp).semantics { heading() },
            )
        }

        item { Group("Apariencia") }
        item {
            Setting("Estilo de la interfaz", "Cambia la composición; las funciones son las mismas.") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StyleOption(UiStyle.READING, current.uiStyle, Modifier.weight(1f)) { update { s -> s.copy(uiStyle = it) } }
                    StyleOption(UiStyle.MODERN, current.uiStyle, Modifier.weight(1f)) { update { s -> s.copy(uiStyle = it) } }
                }
            }
        }
        item {
            Setting("Tema") {
                Segmented(
                    options = ThemeMode.entries,
                    selected = current.themeMode,
                    label = { mode ->
                        when (mode) {
                            ThemeMode.SYSTEM -> "Sistema"
                            ThemeMode.LIGHT -> "Claro"
                            ThemeMode.DARK -> "Oscuro"
                        }
                    },
                    onSelect = { mode -> update { it.copy(themeMode = mode) } },
                )
            }
        }
        item {
            Setting("Densidad") {
                Segmented(
                    options = UiDensity.entries,
                    selected = current.density,
                    label = { if (it == UiDensity.COMFORTABLE) "Cómoda" else "Compacta" },
                    onSelect = { density -> update { it.copy(density = density) } },
                )
            }
        }
        item { TextScaleSetting(current.textScale) { scale -> update { it.copy(textScale = scale) } } }
        item {
            SwitchSetting("Reducir movimiento", "Cambios instantáneos en lugar de animaciones.", current.reduceMotion) { on ->
                update { it.copy(reduceMotion = on) }
            }
        }
        item {
            SwitchSetting("Alto contraste", "Texto y bordes más marcados.", current.highContrast) { on ->
                update { it.copy(highContrast = on) }
            }
        }

        item { Group("Reproducción") }
        item {
            Setting("Retroceder") {
                SkipChips(current.skipBackSeconds) { seconds -> update { it.copy(skipBackSeconds = seconds) } }
            }
        }
        item {
            Setting("Avanzar") {
                SkipChips(current.skipForwardSeconds) { seconds -> update { it.copy(skipForwardSeconds = seconds) } }
            }
        }
        item {
            SwitchSetting(
                "Continuar con el siguiente capítulo",
                "Si lo desactivas, la reproducción se pausa al final de cada capítulo.",
                current.chapterEndBehavior == ChapterEndBehavior.CONTINUE,
            ) { on -> update { it.copy(chapterEndBehavior = if (on) ChapterEndBehavior.CONTINUE else ChapterEndBehavior.PAUSE) } }
        }

        item { Group("Voz y audio") }
        item {
            ListItem(
                headlineContent = { Text("Voz para libros nuevos") },
                supportingContent = { Text(current.defaultVoice.summary()) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier
                    .clickable(role = Role.Button, onClick = onChooseDefaultVoice)
                    .padding(horizontal = tokens.screenPadding - 16.dp),
            )
        }

        if (onOpenLicenses != null) {
            item { Group("Acerca de") }
            item {
                ListItem(
                    headlineContent = { Text("Licencias de código abierto") },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable(role = Role.Button, onClick = onOpenLicenses),
                )
            }
        }
    }
}

@Composable
private fun Group(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(horizontal = NarraTheme.tokens.screenPadding)
            .padding(top = 24.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

@Composable
private fun Setting(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = NarraTheme.tokens.screenPadding, vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun SwitchSetting(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = NarraTheme.tokens.screenPadding - 16.dp),
    )
}

@Composable
private fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) { Text(label(option)) }
        }
    }
}

@Composable
private fun SkipChips(selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppSettings.SKIP_OPTIONS.forEach { seconds ->
            FilterChip(selected = seconds == selected, onClick = { onSelect(seconds) }, label = { Text("$seconds s") })
        }
    }
}

@Composable
private fun TextScaleSetting(scale: Float, onChange: (Float) -> Unit) {
    var value by remember(scale) { mutableFloatStateOf(scale) }
    Setting("Tamaño del texto", formatPercent(value)) {
        Slider(
            value = value,
            onValueChange = { value = (it * SCALE_STEPS).roundToInt() / SCALE_STEPS },
            onValueChangeFinished = { onChange(value) },
            valueRange = AppSettings.MIN_TEXT_SCALE..AppSettings.MAX_TEXT_SCALE,
        )
    }
}

/** Tarjeta de vista previa de un estilo: muestra cómo se ve, no solo su nombre. */
@Composable
private fun StyleOption(style: UiStyle, selected: UiStyle, modifier: Modifier = Modifier, onSelect: (UiStyle) -> Unit) {
    val isSelected = style == selected
    val (name, description, family) = when (style) {
        UiStyle.READING -> Triple("Lectura", "Editorial, portadas grandes", Literata)
        UiStyle.MODERN -> Triple("Moderno", "Compacto y funcional", Inter)
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier.selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(style) }),
    ) {
        Column(Modifier.padding(14.dp)) {
            StylePreview(style)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.titleMedium.copy(fontFamily = family), modifier = Modifier.weight(1f))
                if (isSelected) Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StylePreview(style: UiStyle) {
    val block = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val accent = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    Box(Modifier.fillMaxWidth().height(72.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp)).padding(8.dp)) {
        if (style == UiStyle.READING) {
            Row(verticalAlignment = Alignment.Bottom) {
                Box(Modifier.size(width = 36.dp, height = 54.dp).background(accent, RoundedCornerShape(3.dp)))
                Spacer(Modifier.width(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(width = 48.dp, height = 8.dp).background(block, RoundedCornerShape(2.dp)))
                    Box(Modifier.size(width = 32.dp, height = 6.dp).background(block, RoundedCornerShape(2.dp)))
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(PREVIEW_ROWS) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(width = 12.dp, height = 16.dp).background(accent, RoundedCornerShape(2.dp)))
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.size(width = 56.dp, height = 6.dp).background(block, RoundedCornerShape(2.dp)))
                    }
                }
            }
        }
    }
}

private const val SCALE_STEPS = 20f
private const val PREVIEW_ROWS = 3
