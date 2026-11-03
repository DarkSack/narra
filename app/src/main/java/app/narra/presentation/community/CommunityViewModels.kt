package app.narra.presentation.community

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.narra.data.community.CommunityRepository
import app.narra.data.community.SharedBook
import app.narra.data.community.BlockedPublisher
import app.narra.data.community.PreparedShare
import app.narra.domain.repository.BookRepository
import app.narra.domain.repository.ImportRepository
import app.narra.domain.repository.ImportResult
import app.narra.plus.FREE_BOOK_LIMIT
import app.narra.plus.Plus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.net.ssl.SSLException

// Los errores de red de Android vienen en inglés y sin contexto.
private fun Exception.userMessage() = when (this) {
    is SocketException, is UnknownHostException, is SocketTimeoutException, is SSLException ->
        "No se pudo conectar o se cortó la conexión. Revisa tu red e inténtalo otra vez"
    else -> message ?: "No se pudo conectar con el servicio"
}

enum class AccountMode { LOGIN, REGISTER, VERIFY, RESET_REQUEST, RESET_FINISH }
@HiltViewModel
class AccountViewModel @Inject constructor(private val repository: CommunityRepository) : ViewModel() {
    val email = repository.email
    val sharingAvailable = repository.sharingAvailable
    val mode = MutableStateFlow(AccountMode.LOGIN)
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val failed = MutableStateFlow(false)
    val blocks = MutableStateFlow<List<BlockedPublisher>>(emptyList())
    fun submit(email: String, password: String, code: String) = run {
        when (mode.value) {
            AccountMode.LOGIN, AccountMode.REGISTER -> {
                if (!repository.authenticate(email,password,mode.value==AccountMode.REGISTER)) {
                    mode.value=AccountMode.VERIFY; message.value="Te enviamos un código para verificar el correo"
                }
            }
            AccountMode.VERIFY -> repository.verify(email,code)
            AccountMode.RESET_REQUEST -> { repository.sendReset(email); mode.value=AccountMode.RESET_FINISH; message.value="Si el correo está registrado, recibirás un código" }
            AccountMode.RESET_FINISH -> { repository.resetPassword(email,code,password); mode.value=AccountMode.LOGIN; message.value="Contraseña actualizada. Inicia sesión" }
        }
    }
    fun resend(email: String) = run { repository.resend(email); message.value="Si el correo está registrado, recibirás otro código" }
    fun logout() = run { repository.logout() }
    fun deleteAccount() = run { repository.deleteAccount(); message.value="Tu cuenta y tus publicaciones se eliminaron" }
    fun loadBlocks() = run { blocks.value=repository.blocked() }
    fun unblock(id: String) = run { repository.unblock(id); blocks.value=repository.blocked() }
    private fun run(action: suspend () -> Unit) {
        if (busy.value) return
        busy.value=true; message.value=null; failed.value=false
        viewModelScope.launch {
            try { action() } catch(e: CancellationException) { throw e }
            catch(e: Exception) { failed.value=true; message.value=e.userMessage() }
            finally { busy.value=false }
        }
    }
}

data class CatalogState(
    val books: List<SharedBook> = emptyList(), val busy: Boolean = false,
    val message: String? = null, val mine: Boolean = false, val query: String = "", val hasMore: Boolean = false,
    val failed: Boolean = false, val progress: Float? = null,
)
sealed interface CatalogEvent {
    data class Open(val id: String, val review: Boolean): CatalogEvent
    data object NeedPlus: CatalogEvent
}
@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val repository: CommunityRepository,
    private val importer: ImportRepository,
    private val books: BookRepository,
    private val plus: Plus,
) : ViewModel() {
    val email=repository.email
    val sharingAvailable=repository.sharingAvailable
    val state=MutableStateFlow(CatalogState())
    private val channel=Channel<CatalogEvent>(Channel.BUFFERED)
    val events=channel.receiveAsFlow()
    fun query(value: String) { state.value=state.value.copy(query=value.take(100)) }
    fun mine(value: Boolean) { if (!state.value.busy) { state.value=state.value.copy(mine=value,books=emptyList()); refresh() } }
    fun refresh(more: Boolean=false) = run {
        val before=state.value
        if (email.value==null) { state.value=before.copy(books=emptyList()); return@run }
        val next=repository.books(before.query,before.mine,if(more) before.books.size else 0)
        state.value=state.value.copy(books=if(more) (before.books+next).distinctBy { it.id } else next,hasMore=next.size==30)
    }
    /** El PDF elegido, ya validado, mientras se rellena el formulario de compartir. */
    val share=MutableStateFlow<PreparedShare?>(null)
    fun prepare(uri: Uri) = run { share.value=repository.prepare(uri) }
    fun cancelShare() { share.value?.file?.delete(); share.value=null }
    override fun onCleared() { share.value?.file?.delete() }
    fun upload(title: String,author: String) {
        val prepared=share.value ?: return
        if (state.value.busy) return
        share.value=null
        upload(prepared,title,author)
    }
    private fun upload(prepared: PreparedShare,title: String,author: String) = run {
        repository.upload(prepared,title.trim(),author.trim()) { sent ->
            val percent=(sent*100).toInt()/100f // un cambio por punto, no por bloque enviado
            if (state.value.progress!=percent) state.value=state.value.copy(progress=percent)
        }
        state.value=state.value.copy(mine=true,books=repository.books("",true),message="PDF enviado. Se publicará cuando termine su revisión")
    }
    fun download(book: SharedBook) = run {
        if (!plus.active.value && books.observeBooks().first().size>=FREE_BOOK_LIMIT) { channel.send(CatalogEvent.NeedPlus); return@run }
        val file=repository.download(book)
        try {
            when(val result=importer.importPdf(Uri.fromFile(file))) {
                is ImportResult.Imported -> channel.send(CatalogEvent.Open(result.bookId,true))
                is ImportResult.AlreadyInLibrary -> channel.send(CatalogEvent.Open(result.bookId,false))
                is ImportResult.Failed -> error("No se pudo importar el PDF descargado: ${result.kind}")
            }
        } finally { file.delete() }
    }
    fun report(book: SharedBook,reason: String) = run {
        repository.report(book,reason)
        state.value=state.value.copy(books=state.value.books.filterNot { it.id==book.id },message="Denuncia recibida. Gracias: la revisaremos y ya no verás este libro aquí")
    }
    fun withdraw(book: SharedBook) = run {
        repository.withdraw(book)
        state.value=state.value.copy(books=repository.books("",true),message="Publicación retirada")
    }
    fun block(book: SharedBook) = run {
        repository.block(book)
        state.value=state.value.copy(books=repository.books(state.value.query,false),message="Usuario bloqueado. Sus publicaciones ya no aparecerán en tu catálogo")
    }
    private fun run(action: suspend () -> Unit) {
        if(state.value.busy) return
        state.value=state.value.copy(busy=true,message=null,failed=false)
        viewModelScope.launch {
            try { action() } catch(e: CancellationException) { throw e }
            catch(e: Exception) { state.value=state.value.copy(failed=true,message=e.userMessage()) }
            finally { state.value=state.value.copy(busy=false,progress=null) }
        }
    }
}
