package app.narra.presentation.text

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.narra.core.designsystem.component.EmptyState
import app.narra.core.designsystem.theme.NarraTheme
import app.narra.domain.model.Paragraph
import app.narra.domain.model.SegmentKind

/** El texto limpio de un capítulo, tal como lo leerá la voz. */
@Composable
fun ChapterTextScreen(onBack: () -> Unit, viewModel: ChapterTextViewModel = hiltViewModel()) {
    val paragraphs by viewModel.paragraphs.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(viewModel.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver") }
                },
            )
        },
    ) { padding ->
        val list = paragraphs ?: return@Scaffold
        if (list.isEmpty()) {
            EmptyState(
                icon = Icons.AutoMirrored.Rounded.Notes,
                title = "Este capítulo no tiene texto",
                explanation = "Puede que sus páginas sean imágenes o que solo contenga el título. Puedes excluirlo en la revisión.",
                modifier = Modifier.padding(padding).padding(top = 48.dp),
            )
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(
                start = NarraTheme.tokens.screenPadding,
                end = NarraTheme.tokens.screenPadding,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize(),
        ) {
            items(list) { paragraph -> ParagraphText(paragraph) }
        }
    }
}

@Composable
private fun ParagraphText(paragraph: Paragraph) {
    val base = Modifier.widthIn(max = READING_WIDTH).padding(bottom = 14.dp)
    when (paragraph.kind) {
        SegmentKind.HEADING -> Text(paragraph.text, style = MaterialTheme.typography.titleLarge, modifier = base.padding(top = 8.dp))
        SegmentKind.QUOTE -> Text(
            paragraph.text,
            style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = base.padding(start = 24.dp),
        )
        SegmentKind.LIST -> Text(paragraph.text, style = MaterialTheme.typography.bodyLarge, modifier = base.padding(start = 12.dp))
        SegmentKind.DIALOGUE, SegmentKind.NARRATION -> Text(paragraph.text, style = MaterialTheme.typography.bodyLarge, modifier = base)
    }
}

private val READING_WIDTH = 640.dp
