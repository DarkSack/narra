package app.narra.presentation.community

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.narra.core.ui.formatBytes
import app.narra.data.community.SharedBook
import app.narra.plus.LocalPlus
import app.narra.presentation.navigation.LocalBottomOverlayPadding

@Composable
fun CatalogScreen(onAccount: () -> Unit, onOpenBook: (String, Boolean) -> Unit, viewModel: CatalogViewModel = hiltViewModel()) {
    val email by viewModel.email.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val plus=LocalPlus.current
    var upload by remember { mutableStateOf<Uri?>(null) }
    var report by remember { mutableStateOf<SharedBook?>(null) }
    var withdraw by remember { mutableStateOf<SharedBook?>(null) }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { upload=it }
    LifecycleResumeEffect(email) { viewModel.refresh(); onPauseOrDispose {} }
    LaunchedEffect(viewModel) { viewModel.events.collect { event -> when(event) {
        is CatalogEvent.Open -> onOpenBook(event.id,event.review)
        CatalogEvent.NeedPlus -> plus.offer()
    } } }
    LazyColumn(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),contentPadding=PaddingValues(start=24.dp,end=24.dp,top=20.dp,bottom=LocalBottomOverlayPadding.current+24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { Text("Catálogo",style=MaterialTheme.typography.displaySmall) }
        item { Text("Libros que viajan. Voces que se quedan contigo.",style=MaterialTheme.typography.titleMedium) }
        item { Text("Descarga un PDF revisado y conviértelo en audiolibro con las voces de tu teléfono. No almacenamos ni compartimos audios.") }
        item { TextButton(onClick=onAccount){Text(if(email==null) "Iniciar sesión o crear cuenta" else "Mi cuenta · $email")} }
        if(!viewModel.sharingAvailable) item {
            OutlinedCard { Text("El catálogo compartido está en preparación. Ya puedes crear tu cuenta; las subidas y descargas se activarán cuando el servicio de revisión esté disponible.",Modifier.padding(16.dp)) }
        }
        if(email!=null) {
            item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(!state.mine,{viewModel.mine(false)},label={Text("Comunidad")},enabled=!state.busy)
                FilterChip(state.mine,{viewModel.mine(true)},label={Text("Mis publicaciones")},enabled=!state.busy)
            } }
            if(!state.mine) item { Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(state.query,viewModel::query,Modifier.fillMaxWidth(),label={Text("Buscar por título")},singleLine=true,enabled=!state.busy)
                OutlinedButton(onClick={viewModel.refresh()},enabled=!state.busy){Text("Buscar")}
            } }
            item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Button(onClick={picker.launch(arrayOf("application/pdf"))},enabled=!state.busy && viewModel.sharingAvailable){Text("Compartir PDF")}
                TextButton(onClick={viewModel.refresh()},enabled=!state.busy){Text("Actualizar")}
            } }
        }
        if(state.busy) item {
            state.progress?.let { sent -> Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text("Subiendo PDF · ${(sent*100).toInt()} %",style=MaterialTheme.typography.labelLarge)
                LinearProgressIndicator(progress={sent},modifier=Modifier.fillMaxWidth())
            } } ?: LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        state.message?.let { message -> item { Text(message,color=if(state.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) } }
        if(email!=null && state.books.isEmpty() && !state.busy) item {
            Text(if(state.mine) "Todavía no tienes publicaciones. Comparte un PDF del que tengas derechos." else "No hay libros publicados para esta búsqueda.",style=MaterialTheme.typography.bodyLarge)
        }
        items(state.books,key={it.id}) { book ->
            OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text(book.title,style=MaterialTheme.typography.titleLarge)
                if(book.author.isNotBlank()) Text(book.author,style=MaterialTheme.typography.bodyMedium)
                if(book.description.isNotBlank()) Text(book.description,maxLines=3)
                if(book.bytes>0) Text("${formatBytes(book.bytes)} · ${book.pages} páginas",style=MaterialTheme.typography.labelLarge)
                if(book.originalBytes>0 && book.bytes>0) Text(if(book.bytes<book.originalBytes) "${100-book.bytes*100/book.originalBytes}% menos que el original" else "Copia reconstruida para compartir",style=MaterialTheme.typography.bodySmall)
                if(state.mine) {
                    Text(statusLabel(book.status),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
                    book.reason?.let { Text(it,style=MaterialTheme.typography.bodySmall) }
                    TextButton(onClick={withdraw=book},enabled=!state.busy && viewModel.sharingAvailable && book.status!="blocked"){Text("Retirar publicación")}
                }
                if(book.status=="published") {
                    Button(onClick={viewModel.download(book)},enabled=!state.busy && viewModel.sharingAvailable){Text("Descargar y convertir")}
                    if(!book.mine) {
                        TextButton(onClick={report=book},enabled=!state.busy && viewModel.sharingAvailable){Text("Denunciar contenido")}
                        TextButton(onClick={viewModel.block(book)},enabled=!state.busy && viewModel.sharingAvailable){Text("Bloquear usuario que lo compartió")}
                    }
                }
            } }
        }
        if(state.hasMore && !state.busy) item { TextButton(onClick={viewModel.refresh(true)}){Text("Ver más libros")} }
        item { Text("Los PDF compartidos pasan por antivirus, reconstrucción y revisión de contenido. Estas comprobaciones reducen riesgos, pero pueden fallar. Denuncia cualquier contenido inapropiado.",style=MaterialTheme.typography.bodySmall) }
    }
    val context=LocalContext.current
    upload?.let { uri ->
        // El nombre del archivo como título inicial: casi siempre es el del libro.
        val name=remember(uri) { runCatching { context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { if(it.moveToFirst()) it.getString(0) else null } }.getOrNull() }
        ShareDialog(initialTitle=name?.substringBeforeLast('.')?.replace('_',' ')?.trim().orEmpty(),onDismiss={upload=null},onShare={title,author->upload=null;viewModel.upload(uri,title,author)})
    }
    report?.let { book -> ReportDialog(onDismiss={report=null},onReport={reason->report=null;viewModel.report(book,reason)}) }
    withdraw?.let { book -> AlertDialog(onDismissRequest={withdraw=null},title={Text("¿Retirar «${book.title}»?")},text={Text("Se retirará del catálogo y se eliminará la copia compartida. Las descargas anteriores se conservan en sus teléfonos.")},confirmButton={TextButton(onClick={withdraw=null;viewModel.withdraw(book)}){Text("Retirar")}},dismissButton={TextButton(onClick={withdraw=null}){Text("Cancelar")}}) }
}

private fun statusLabel(value: String)=when(value) {
    "uploading"->"Recibiendo PDF";"queued"->"En cola de revisión";"processing"->"Comprobando seguridad y contenido"
    "review"->"Pendiente de revisión humana";"published"->"Publicado";"rejected"->"No aprobado";else->"Bloqueado"
}
@Composable
private fun ShareDialog(initialTitle: String,onDismiss: () -> Unit,onShare: (String,String) -> Unit) {
    var title by remember { mutableStateOf(initialTitle.take(160)) }; var author by remember { mutableStateOf("") }
    var rights by remember { mutableStateOf(false) }; var consent by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest=onDismiss,title={Text("Compartir un PDF")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Solo PDF, hasta 100 MB y 500 páginas. Se comprimirá y revisará antes de aparecer en el catálogo.")
        OutlinedTextField(title,{title=it.take(160)},label={Text("Título")},singleLine=true)
        OutlinedTextField(author,{author=it.take(160)},label={Text("Autor")},singleLine=true)
        // Toda la fila marca la casilla (y se anuncia como casilla), no solo el cuadrito.
        Row(Modifier.toggleable(rights,role=Role.Checkbox,onValueChange={rights=it}),verticalAlignment=Alignment.CenterVertically) { Checkbox(rights,null); Text("Tengo derechos o permiso para distribuir este libro. No contiene pornografía ni material sexual explícito.",style=MaterialTheme.typography.bodySmall) }
        Row(Modifier.toggleable(consent,role=Role.Checkbox,onValueChange={consent=it}),verticalAlignment=Alignment.CenterVertically) { Checkbox(consent,null); Text("Acepto compartirlo con otros usuarios y enviar todas sus páginas y texto a revisión de seguridad y moderación. El OCR puede introducir errores.",style=MaterialTheme.typography.bodySmall) }
    }},confirmButton={TextButton(onClick={onShare(title,author)},enabled=rights && consent && title.isNotBlank()){Text("Enviar a revisión")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancelar")}})
}
@Composable
private fun ReportDialog(onDismiss: () -> Unit,onReport: (String) -> Unit) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest=onDismiss,title={Text("Denunciar libro")},text={OutlinedTextField(reason,{reason=it.take(1000)},label={Text("Contenido obsceno, derechos u otro problema")},minLines=3)},confirmButton={TextButton(onClick={onReport(reason)},enabled=reason.trim().length>=3){Text("Enviar denuncia")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancelar")}})
}
