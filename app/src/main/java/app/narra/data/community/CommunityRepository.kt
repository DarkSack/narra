package app.narra.data.community

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import app.narra.BuildConfig
import app.narra.data.pdf.PdfIntakePolicy
import app.narra.data.pdf.PdfStructurePolicy
import app.narra.domain.model.ErrorKind
import app.narra.domain.model.NarraException
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection
import javax.inject.Inject
import javax.inject.Singleton

data class SharedBook(
    val id: String, val title: String, val author: String, val description: String,
    val status: String, val reason: String?, val bytes: Long, val originalBytes: Long,
    val sha256: String, val pages: Int, val mine: Boolean = false,
)
data class BlockedPublisher(val id: String, val label: String)
/** PDF ya copiado y validado para compartir, con el título y autor que trae su ficha. */
class PreparedShare(val file: File, val title: String, val author: String)
private class ApiFailure(val status: Int, message: String) : Exception(message)

@Singleton
class CommunityRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vault: SessionVault,
) {
    private var session = vault.read()
    private val refreshLock = Mutex()
    private val _email = MutableStateFlow(session?.optJSONObject("user")?.optString("email")?.takeIf { it.isNotBlank() })
    val email = _email.asStateFlow()
    val sharingAvailable get() = BuildConfig.CATALOG_URL.isNotBlank()

    suspend fun authenticate(email: String, password: String, register: Boolean): Boolean = withContext(Dispatchers.IO) {
        require(email.contains('@') && email.length <= 254) { "Escribe un correo válido" }
        require(password.length in (if (register) 12 else 1)..256) { "La contraseña debe tener entre 12 y 256 caracteres" }
        val response = auth(if (register) "/users" else "/sessions", JSONObject().put("email",email.trim()).put("password",password))
        if (response.optBoolean("requireEmailVerification")) return@withContext false
        save(response)
        true
    }
    suspend fun verify(email: String, code: String) = withContext(Dispatchers.IO) {
        require(code.matches(Regex("[0-9]{6}"))) { "El código tiene seis dígitos" }
        save(auth("/email/verify",JSONObject().put("email",email.trim()).put("otp",code)))
    }
    suspend fun resend(email: String) = withContext(Dispatchers.IO) {
        auth("/email/send-verification",JSONObject().put("email",email.trim())); Unit
    }
    suspend fun sendReset(email: String) = withContext(Dispatchers.IO) {
        auth("/email/send-reset-password",JSONObject().put("email",email.trim())); Unit
    }
    suspend fun resetPassword(email: String, code: String, password: String) = withContext(Dispatchers.IO) {
        require(password.length in 12..256) { "La contraseña debe tener entre 12 y 256 caracteres" }
        val token = auth("/email/exchange-reset-password-token",JSONObject().put("email",email.trim()).put("code",code)).getString("token")
        auth("/email/reset-password",JSONObject().put("newPassword",password).put("otp",token)); Unit
    }
    suspend fun logout() = withContext(Dispatchers.IO) {
        refreshLock.withLock {
            try { session?.let { auth("/logout",JSONObject().put("refreshToken",it.optString("refreshToken"))) } }
            finally { clear() }
        }
    }
    private fun clear() { vault.clear(); session=null; _email.value=null }
    private fun save(value: JSONObject) {
        require(!value.optString("accessToken").isNullOrBlank() && !value.isNull("accessToken")) { "La cuenta requiere verificar el correo" }
        require(!value.optString("refreshToken").isNullOrBlank() && !value.isNull("refreshToken")) { "No se recibió una sesión válida" }
        vault.write(value); session=value; _email.value=value.getJSONObject("user").getString("email")
    }
    private fun auth(path: String, body: JSONObject): JSONObject = JSONObject(request(
        "${BuildConfig.INSFORGE_URL}/api/auth$path?client_type=mobile", "POST", body=body.toString(),
    ))
    private suspend fun <T> authorized(action: (String) -> T): T {
        val token = session?.optString("accessToken") ?: error("Inicia sesión para continuar")
        try { return action(token) } catch (e: ApiFailure) { if (e.status != 401) throw e }
        val fresh = refreshLock.withLock {
            if (session?.optString("accessToken") == token) {
                try { save(auth("/refresh",JSONObject().put("refreshToken",session?.getString("refreshToken")))) }
                catch (e: ApiFailure) { if (e.status in setOf(400,401,403)) clear(); throw e }
            }
            session?.getString("accessToken") ?: error("Vuelve a iniciar sesión")
        }
        return action(fresh)
    }
    suspend fun books(query: String, mine: Boolean, offset: Int = 0): List<SharedBook> = withContext(Dispatchers.IO) {
        // Sin la API del catálogo no hay nada que listar: sus tablas no se leen con el token del usuario.
        if (!sharingAvailable) return@withContext emptyList()
        authorized { token ->
            val url = "${BuildConfig.CATALOG_URL}/${if (mine) "me/books" else "books"}?q=${encode(query)}&offset=$offset"
            val array = JSONArray(request(url,token=token))
            List(array.length()) { index -> array.getJSONObject(index).let { b ->
                SharedBook(b.getString("id"),b.getString("title"),b.optString("author"),b.optString("description"),
                    b.getString("status"),b.optString("reason").takeUnless { it.isBlank() || it=="null" },
                    b.optLong("pdf_bytes"),b.optLong("original_bytes"),b.optString("sha256"),b.optInt("pages"),b.optBoolean("mine",mine))
            } }
        }
    }
    /** Copia y valida el PDF al elegirlo: los errores salen antes de rellenar el formulario. */
    suspend fun prepare(uri: Uri): PreparedShare = withContext(Dispatchers.IO) {
        require(sharingAvailable) { "Compartir libros todavía no está disponible" }
        sweep("upload-") // restos de una subida que el sistema cortó
        val file = File.createTempFile("upload-", ".pdf",context.cacheDir)
        try {
            var name = "libro.pdf"
            context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { cursor ->
                if (cursor.moveToFirst()) name = cursor.getString(0) ?: name
            }
            val info = try {
                PdfIntakePolicy.validateName(name,context.contentResolver.getType(uri))
                val input = context.contentResolver.openInputStream(uri) ?: error("No se pudo abrir el archivo")
                input.use { source -> file.outputStream().use { PdfIntakePolicy.copy(source,it) } }
                // Las mismas reglas que el servidor, antes de gastar datos y cupo.
                val document = try { PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly()) }
                    catch (e: IOException) { throw IllegalArgumentException("No se pudo leer el PDF o está protegido con contraseña", e) }
                document.use { doc ->
                    PdfStructurePolicy.validate(doc)
                    runCatching { doc.documentInformation.let { it.title to it.author } }.getOrNull()
                }
            } catch (e: NarraException) { throw IllegalArgumentException(shareError(e.kind), e) }
            // La ficha del PDF suele traer título y autor; un título que es un nombre de archivo no sirve.
            val title = info?.first?.trim()?.takeUnless { it.isBlank() || it.startsWith("Microsoft") || Regex("""\.\w{2,4}$""").containsMatchIn(it) }
                ?: name.substringBeforeLast('.').replace('_',' ').trim()
            PreparedShare(file, title.take(160), info?.second?.trim().orEmpty().take(160))
        } catch (e: Exception) { file.delete(); throw e }
    }
    suspend fun upload(share: PreparedShare, title: String, author: String, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        try { authorized { token -> request("${BuildConfig.CATALOG_URL}/books?filename=libro.pdf&title=${encode(title)}&author=${encode(author)}&rights=true&consent=true", "POST", token, file=share.file, onProgress=onProgress) } }
        finally { share.file.delete() }
    }
    private fun sweep(prefix: String) = context.cacheDir.listFiles { file -> file.name.startsWith(prefix) }?.forEach { it.delete() }
    private fun shareError(kind: ErrorKind) = when (kind) {
        ErrorKind.PDF_TOO_LARGE -> "Para compartir, el PDF debe pesar hasta 100 MB y tener hasta ${PdfStructurePolicy.MAX_PAGES} páginas"
        ErrorKind.PDF_ENCRYPTED -> "No se pueden compartir PDF protegidos"
        ErrorKind.PDF_UNSAFE -> "Solo se pueden compartir PDF sin scripts, adjuntos ni formularios activos. En tu teléfono sí puedes convertirlo en audiolibro"
        else -> "El archivo no es un PDF válido"
    }
    suspend fun download(book: SharedBook): File = withContext(Dispatchers.IO) {
        require(sharingAvailable) { "Las descargas todavía no están disponibles" }
        require(book.status=="published" && book.sha256.matches(Regex("[a-f0-9]{64}"))) { "Este libro no está aprobado" }
        sweep("download-") // restos de una descarga que el sistema cortó
        val target = File.createTempFile("download-", ".pdf",context.cacheDir)
        try {
            authorized { token ->
                val connection = connection("${BuildConfig.CATALOG_URL}/books/${book.id}/pdf", "GET",token)
                try {
                    checkResponse(connection)
                    require(connection.contentType?.substringBefore(';')=="application/pdf" && connection.getHeaderField("X-PDF-SHA256")==book.sha256) { "La descarga no superó la comprobación de integridad" }
                    connection.inputStream.use { input -> target.outputStream().use { PdfIntakePolicy.copy(input,it) } }
                    val digest=MessageDigest.getInstance("SHA-256")
                    target.inputStream().use { input -> val buffer=ByteArray(65536); while (true) { val read=input.read(buffer); if (read<0) break; digest.update(buffer,0,read) } }
                    val actual=digest.digest().joinToString("") { "%02x".format(it) }
                    require(actual==book.sha256 && target.length()==book.bytes) { "La descarga está incompleta o fue alterada" }
                } finally { connection.disconnect() }
            }
            target
        } catch(e: Exception) { target.delete(); throw e }
    }
    suspend fun report(book: SharedBook, reason: String) = withContext(Dispatchers.IO) {
        authorized { request("${BuildConfig.CATALOG_URL}/books/${book.id}/report","POST",it,JSONObject().put("reason",reason).toString()) }; Unit
    }
    suspend fun withdraw(book: SharedBook) = withContext(Dispatchers.IO) {
        authorized { request("${BuildConfig.CATALOG_URL}/me/books/${book.id}","DELETE",it) }; Unit
    }
    suspend fun block(book: SharedBook) = withContext(Dispatchers.IO) {
        authorized { request("${BuildConfig.CATALOG_URL}/books/${book.id}/block","POST",it) }; Unit
    }
    suspend fun blocked(): List<BlockedPublisher> = withContext(Dispatchers.IO) {
        authorized { token ->
            val array=JSONArray(request("${BuildConfig.CATALOG_URL}/me/blocks",token=token))
            List(array.length()) { index -> array.getJSONObject(index).let { BlockedPublisher(it.getString("id"),it.getString("label")) } }
        }
    }
    suspend fun unblock(id: String) = withContext(Dispatchers.IO) {
        authorized { request("${BuildConfig.CATALOG_URL}/me/blocks/$id","DELETE",it) }; Unit
    }
    suspend fun deleteAccount() = withContext(Dispatchers.IO) {
        require(sharingAvailable) { "La eliminación de cuenta todavía no está disponible; contacta con soporte" }
        authorized { request("${BuildConfig.CATALOG_URL}/me","DELETE",it) }; refreshLock.withLock { clear() }
    }
    private fun connection(url: String, method: String, token: String?): HttpsURLConnection = (URL(url).openConnection() as HttpsURLConnection).apply {
        requestMethod=method; connectTimeout=15_000; readTimeout=180_000; instanceFollowRedirects=false
        setRequestProperty("Accept","application/json")
        if (token!=null) setRequestProperty("Authorization","Bearer $token")
    }
    private fun checkResponse(c: HttpsURLConnection) {
        val status=c.responseCode
        if (status in 200..299) return
        val message=when(status) {
            400,422 -> "Revisa los datos introducidos o el código recibido"
            401 -> "La sesión o las credenciales no son válidas. Vuelve a iniciar sesión"
            403 -> "Verifica tu correo para continuar"
            404 -> "Este libro ya no está disponible"
            409 -> "La cuenta o la denuncia ya existe"
            413 -> "El archivo supera el límite de 100 MB"
            415 -> "Solo se permiten archivos PDF"
            429 -> "Has llegado al límite de solicitudes. Intenta más tarde"
            else -> "El servicio no está disponible. Intenta más tarde"
        }
        // El catálogo explica el motivo en español (cupo, denuncias, PDF rechazado…).
        val server = if (BuildConfig.CATALOG_URL.isNotBlank() && c.url.toString().startsWith(BuildConfig.CATALOG_URL)) runCatching {
            c.errorStream?.bufferedReader()?.use { reader -> CharArray(4096).let { b -> String(b,0,maxOf(0,reader.read(b))) } }
                ?.let { JSONObject(it).optString("message") }
        }.getOrNull()?.takeIf { it.isNotBlank() }?.take(300) else null
        throw ApiFailure(status,server ?: message)
    }
    private fun request(url: String, method: String="GET", token: String?=null, body: String?=null, file: File?=null, onProgress: (Float) -> Unit = {}): String {
        val c=connection(url,method,token)
        try {
            if (file!=null) {
                c.doOutput=true; c.setRequestProperty("Content-Type","application/pdf"); c.setFixedLengthStreamingMode(file.length())
                file.inputStream().use { source -> c.outputStream.use { out ->
                    val buffer=ByteArray(65536); var sent=0L
                    while(true) { val read=source.read(buffer); if(read<0) break; out.write(buffer,0,read); sent+=read; onProgress(sent.toFloat()/file.length()) }
                } }
            } else if (body!=null) {
                val bytes=body.toByteArray(Charsets.UTF_8)
                c.doOutput=true; c.setRequestProperty("Content-Type","application/json"); c.setFixedLengthStreamingMode(bytes.size)
                c.outputStream.use { it.write(bytes) }
            }
            checkResponse(c)
            return c.inputStream.bufferedReader().use { reader ->
                val result=StringBuilder(); val buffer=CharArray(8192)
                while(true) { val read=reader.read(buffer); if(read<0) break; require(result.length+read<=2_000_000) { "Respuesta demasiado grande" }; result.append(buffer,0,read) }
                result.toString()
            }
        } finally { c.disconnect() }
    }
    private fun encode(value: String)=URLEncoder.encode(value,"UTF-8")
}
