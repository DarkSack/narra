package app.narra.presentation.voices

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.narra.core.designsystem.component.ErrorState
import app.narra.core.designsystem.component.SectionHeader
import app.narra.core.designsystem.theme.NarraTheme
import app.narra.core.ui.formatBytes
import app.narra.core.ui.formatSpeed
import app.narra.core.ui.languageLabel
import app.narra.core.ui.toErrorText
import app.narra.domain.model.AudioQuality
import app.narra.domain.model.Voice
import app.narra.presentation.common.openVoiceInstaller
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

/** Elegir la voz de un libro o la voz por defecto, escuchándolas antes. */
@Composable
fun VoicesScreen(onBack: () -> Unit, viewModel: VoicesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var askRegenerate by remember { mutableStateOf(false) }
    val installVoices: () -> Unit = {
        if (!context.openVoiceInstaller()) {
            scope.launch { snackbar.showSnackbar("Este teléfono no permite descargar voces desde aquí. Búscalas en los ajustes de texto a voz.") }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                VoicesEvent.Saved -> onBack()
                is VoicesEvent.Message -> snackbar.showSnackbar(event.text)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (state.book != null) "Voz del libro" else "Voz predeterminada")
                        state.book?.let {
                            Text(it.title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver") }
                },
            )
        },
        bottomBar = {
            if (state.engineError == null && !state.loading) {
                Surface(tonalElevation = 3.dp) {
                    Button(
                        onClick = { if (state.hasAudio && state.dirty) askRegenerate = true else viewModel.save() },
                        enabled = state.canSave,
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(horizontal = NarraTheme.tokens.screenPadding, vertical = 12.dp)
                            .height(52.dp),
                    ) { Text(if (state.dirty || !state.stoppedByVoice) "Guardar" else "Reintentar con esta voz") }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val engineError = state.engineError
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding))
            engineError != null -> {
                ErrorState(
                    engineError.toErrorText(),
                    Modifier.padding(padding).padding(NarraTheme.tokens.screenPadding),
                    onAction = installVoices,
                )
            }
            else -> VoicesContent(state, viewModel, installVoices, PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()))
        }
    }

    if (askRegenerate) {
        AlertDialog(
            onDismissRequest = { askRegenerate = false },
            title = { Text("¿Regenerar el audio ya creado?") },
            text = { Text("Parte del libro ya tiene audio con la voz anterior. Puedes usar la nueva solo para lo que falta o volver a crearlo todo.") },
            confirmButton = {
                TextButton(onClick = { askRegenerate = false; viewModel.save(regenerate = true) }) { Text("Regenerar todo") }
            },
            dismissButton = {
                TextButton(onClick = { askRegenerate = false; viewModel.save() }) { Text("Solo lo que falta") }
            },
        )
    }
}

@Composable
private fun VoicesContent(state: VoicesUiState, viewModel: VoicesViewModel, onInstall: () -> Unit, padding: PaddingValues) {
    val tokens = NarraTheme.tokens
    val draft = state.draft
    LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp)) {
        item(key = "current") {
            CurrentVoice(state, onPreview = { viewModel.togglePreview() }, modifier = Modifier.padding(horizontal = tokens.screenPadding, vertical = 8.dp))
        }

        if (state.missingLanguageVoices) {
            item(key = "missing") {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(tokens.cardRadius),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = tokens.screenPadding, vertical = 8.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Falta descargar una voz en ${languageLabel(state.baseLanguage)?.lowercase() ?: "este idioma"}",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            "Sin ella no se puede crear el audio. La descarga la hace el motor de voz del teléfono y solo se necesita una vez.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = onInstall, modifier = Modifier.padding(top = 4.dp)) { Text("Descargar voces") }
                    }
                }
            }
        }

        item(key = "reading") {
            Column(Modifier.padding(horizontal = tokens.screenPadding)) {
                SectionHeader("Cómo lee", modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                LabeledSlider("Velocidad", draft.speechRate, RATE_RANGE, RATE_STEP, label = ::formatSpeed, onChange = viewModel::setRate)
                LabeledSlider("Tono", draft.pitch, PITCH_RANGE, PITCH_STEP, label = ::oneDecimal, onChange = viewModel::setPitch)
                LabeledSlider(
                    "Pausa entre párrafos",
                    draft.paragraphPauseMs.toFloat(),
                    PAUSE_RANGE,
                    PAUSE_STEP_MS,
                    label = { "${oneDecimal(it / MS_PER_SECOND)} s" },
                    onChange = { viewModel.setParagraphPause(it.roundToInt()) },
                )
                Text("Calidad del audio", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp))
                Text(
                    "≈ ${formatBytes(draft.quality.bytesPerSecond * SECONDS_PER_HOUR)} por hora de audio",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    AudioQuality.entries.forEachIndexed { index, quality ->
                        SegmentedButton(
                            selected = quality == draft.quality,
                            onClick = { viewModel.setQuality(quality) },
                            shape = SegmentedButtonDefaults.itemShape(index, AudioQuality.entries.size),
                        ) { Text(quality.label()) }
                    }
                }
            }
        }

        item(key = "voices-header") {
            Column(Modifier.padding(horizontal = tokens.screenPadding)) {
                SectionHeader(
                    "Voces",
                    subtitle = "Toca una voz para escucharla con un fragmento del libro.",
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                )
                val language = state.baseLanguage
                if (language != null && state.voices.any { Locale.forLanguageTag(it.languageTag).language == language }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.languageFilter == language,
                            onClick = { viewModel.setLanguageFilter(language) },
                            label = { Text(languageLabel(language) ?: language) },
                        )
                        FilterChip(
                            selected = state.languageFilter == null,
                            onClick = { viewModel.setLanguageFilter(null) },
                            label = { Text("Todos los idiomas") },
                        )
                    }
                }
            }
        }

        if (state.visibleVoices.isEmpty()) {
            item(key = "no-voices") {
                Column(Modifier.padding(horizontal = tokens.screenPadding, vertical = 12.dp)) {
                    Text("No hay voces instaladas para este idioma.", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onInstall) { Text("Descargar voces") }
                }
            }
        }

        items(state.visibleVoices, key = { it.key }) { voice ->
            VoiceRow(
                voice = voice,
                selected = voice.key == state.draftKey,
                previewing = state.previewing && state.previewingKey == voice.key,
                onSelect = { viewModel.select(voice) },
                onPreview = { viewModel.togglePreview(voice) },
                onInstall = onInstall,
            )
        }
    }
}

@Composable
private fun CurrentVoice(state: VoicesUiState, onPreview: () -> Unit, modifier: Modifier = Modifier) {
    val voice = state.selectedVoice
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(NarraTheme.tokens.cardRadius), modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.RecordVoiceOver, contentDescription = null)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    voice?.let { "${it.displayName} · ${it.regionLabel ?: it.languageLabel}" }
                        ?: "Voz predeterminada del teléfono",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    voice?.languageLabel ?: languageLabel(state.draft.languageTag) ?: "Idioma del sistema",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            FilledTonalIconButton(onClick = onPreview) {
                val playing = state.previewing && state.previewingKey == state.draftKey
                Icon(
                    if (playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                    contentDescription = if (playing) "Detener la muestra" else "Escuchar la voz elegida",
                )
            }
        }
    }
}

@Composable
private fun VoiceRow(
    voice: Voice,
    selected: Boolean,
    previewing: Boolean,
    onSelect: () -> Unit,
    onPreview: () -> Unit,
    onInstall: () -> Unit,
) {
    val tags = buildList {
        add(voice.engineLabel)
        add(if (voice.requiresNetwork) "Usa internet" else "Sin conexión")
        if (voice.quality >= HIGH_QUALITY) add("Alta calidad")
        if (!voice.isInstalled) add("Sin descargar")
    }
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (voice.isInstalled) {
                    Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
                } else {
                    Modifier.clickable(onClickLabel = "Descargar voz", role = Role.Button, onClick = onInstall)
                },
            )
            .padding(horizontal = NarraTheme.tokens.screenPadding - 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (voice.isInstalled) {
            RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(12.dp))
        } else {
            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.padding(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text("${voice.displayName} · ${voice.regionLabel ?: voice.languageLabel}", style = MaterialTheme.typography.bodyLarge)
            Text(
                (listOf(voice.languageLabel) + tags).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (voice.isInstalled) {
            IconButton(onClick = onPreview) {
                Icon(
                    if (previewing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                    contentDescription = if (previewing) "Detener la muestra" else "Escuchar ${voice.displayName}",
                )
            }
        }
    }
}

@Composable
private fun LabeledSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    label: (Float) -> String,
    onChange: (Float) -> Unit,
) {
    var current by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(label(current), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = current,
            onValueChange = { current = ((it / step).roundToInt() * step).coerceIn(range) },
            onValueChangeFinished = { onChange(current) },
            valueRange = range,
        )
    }
}

/** "1,5": la interfaz está en español, con coma decimal sea cual sea el idioma del teléfono. */
private fun oneDecimal(value: Float): String = String.format(Locale.ROOT, "%.1f", value).replace('.', ',')

private fun AudioQuality.label(): String = when (this) {
    AudioQuality.COMPACT -> "Compacta"
    AudioQuality.STANDARD -> "Estándar"
    AudioQuality.HIGH -> "Alta"
}

private val RATE_RANGE = 0.5f..2f
private const val RATE_STEP = 0.1f
private val PITCH_RANGE = 0.5f..1.5f
private const val PITCH_STEP = 0.05f
private val PAUSE_RANGE = 0f..1_500f
private const val PAUSE_STEP_MS = 50f
private const val MS_PER_SECOND = 1_000f
private const val SECONDS_PER_HOUR = 3_600L

/** Calidad (0–100) a partir de la cual una voz se marca como de alta calidad. */
private const val HIGH_QUALITY = 80
