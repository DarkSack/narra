package app.narra.presentation.community

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.narra.presentation.navigation.LocalBottomOverlayPadding

@Composable
fun AccountScreen(onBack: () -> Unit, viewModel: AccountViewModel = hiltViewModel()) {
    val signedIn by viewModel.email.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val failed by viewModel.failed.collectAsStateWithLifecycle()
    val blocks by viewModel.blocks.collectAsStateWithLifecycle()
    var email by rememberSaveable { mutableStateOf("") }
    // Contraseñas y códigos no entran en el estado guardado de la actividad.
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(signedIn,mode) { password=""; code="" }
    Scaffold(topBar={ TopAppBar(title={Text("Tu cuenta")},navigationIcon={IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Rounded.ArrowBack,"Volver")}}) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding(), contentPadding=PaddingValues(start=24.dp,end=24.dp,bottom=LocalBottomOverlayPadding.current+24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            item { Text("Comparte lecturas",style=MaterialTheme.typography.headlineMedium) }
            item { Text("Tu cuenta sirve para compartir y descargar PDF. El audio se crea y se guarda en cada teléfono.",style=MaterialTheme.typography.bodyLarge) }
            if(signedIn!=null) {
                item { Text(signedIn!!,style=MaterialTheme.typography.titleMedium) }
                item { OutlinedButton(onClick=viewModel::logout,enabled=!busy){Text("Cerrar sesión")} }
                item { TextButton(onClick=viewModel::loadBlocks,enabled=!busy && viewModel.sharingAvailable){Text("Ver usuarios bloqueados")} }
                blocks.forEach { blocked -> item(key=blocked.id) {
                    OutlinedCard { Column(Modifier.padding(12.dp)) {
                        Text("Bloqueado desde «${blocked.label}»")
                        TextButton(onClick={viewModel.unblock(blocked.id)},enabled=!busy){Text("Desbloquear usuario")}
                    } }
                } }
                item { TextButton(onClick={confirmDelete=true},enabled=!busy && viewModel.sharingAvailable){Text("Eliminar cuenta y publicaciones",color=MaterialTheme.colorScheme.error)} }
                item { Text("Eliminar la cuenta retira tus publicaciones. Los libros que otras personas ya descargaron permanecen en sus teléfonos.",style=MaterialTheme.typography.bodySmall) }
                if(!viewModel.sharingAvailable) item { Text("Para solicitar la eliminación de tu cuenta mientras se activa el catálogo, escribe a donsackito@protonmail.com.",style=MaterialTheme.typography.bodySmall) }
            } else {
                item { Text(when(mode){AccountMode.LOGIN->"Iniciar sesión";AccountMode.REGISTER->"Crear cuenta";AccountMode.VERIFY->"Verificar correo";AccountMode.RESET_REQUEST->"Recuperar contraseña";AccountMode.RESET_FINISH->"Elegir nueva contraseña"},style=MaterialTheme.typography.titleLarge) }
                item { OutlinedTextField(email,{email=it.take(254)},Modifier.fillMaxWidth(),enabled=!busy,label={Text("Correo electrónico")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email)) }
                if(mode in setOf(AccountMode.LOGIN,AccountMode.REGISTER,AccountMode.RESET_FINISH)) item {
                    OutlinedTextField(password,{password=it.take(256)},Modifier.fillMaxWidth(),enabled=!busy,label={Text(if(mode==AccountMode.RESET_FINISH) "Nueva contraseña" else "Contraseña")},supportingText={if(mode!=AccountMode.LOGIN) Text("Al menos 12 caracteres")},singleLine=true,visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password))
                }
                if(mode in setOf(AccountMode.VERIFY,AccountMode.RESET_FINISH)) item {
                    OutlinedTextField(code,{code=it.filter(Char::isDigit).take(6)},Modifier.fillMaxWidth(),enabled=!busy,label={Text("Código del correo")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.NumberPassword))
                }
                item { Button(onClick={viewModel.submit(email,password,code);password=""},enabled=!busy && email.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text(if(mode==AccountMode.RESET_REQUEST) "Enviar código" else "Continuar")} }
                if(mode==AccountMode.LOGIN) {
                    item { TextButton(onClick={viewModel.mode.value=AccountMode.REGISTER},enabled=!busy){Text("Crear una cuenta")} }
                    item { TextButton(onClick={viewModel.mode.value=AccountMode.RESET_REQUEST},enabled=!busy){Text("Olvidé mi contraseña")} }
                    item { TextButton(onClick={viewModel.mode.value=AccountMode.VERIFY},enabled=!busy){Text("Tengo un código de verificación")} }
                } else item { TextButton(onClick={viewModel.mode.value=AccountMode.LOGIN},enabled=!busy){Text("Volver a iniciar sesión")} }
                if(mode==AccountMode.VERIFY) item { TextButton(onClick={viewModel.resend(email)},enabled=!busy && email.isNotBlank()){Text("Reenviar código")} }
            }
            if(busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if(message!=null) item { Text(message!!,color=if(failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
            item { HorizontalDivider() }
            item { Text("Solo el PDF que decidas compartir se sube a revisión. Sus páginas y texto se envían al servicio de moderación. El uso personal de Narra sigue funcionando sin cuenta.",style=MaterialTheme.typography.bodySmall) }
        }
    }
    if(confirmDelete) AlertDialog(onDismissRequest={confirmDelete=false},title={Text("¿Eliminar tu cuenta?")},text={Text("Se eliminarán tu cuenta y las copias compartidas de tus PDF. Esta acción no se puede deshacer. Tu biblioteca local se conserva.")},confirmButton={TextButton(onClick={confirmDelete=false;viewModel.deleteAccount()}){Text("Eliminar cuenta")}},dismissButton={TextButton(onClick={confirmDelete=false}){Text("Cancelar")}})
}
