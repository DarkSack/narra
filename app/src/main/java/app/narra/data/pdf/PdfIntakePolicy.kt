package app.narra.data.pdf

import app.narra.domain.model.ErrorKind
import app.narra.domain.model.NarraException
import java.io.InputStream
import java.io.OutputStream

/** Prevalidación; no sustituye el antivirus ni la revisión del catálogo en el servidor. */
object PdfIntakePolicy {
    const val MAX_BYTES = 100L * 1024 * 1024
    const val MAX_PAGES = 1500

    fun validateName(name: String, mime: String?) {
        val extension = name.substringAfterLast('.', "").lowercase()
        // Algunos proveedores dan nombres sin extensión; el contenido se comprueba igual por su firma.
        if (!(extension == "pdf" || extension.isEmpty() && mime == "application/pdf") || name.any { it == '\u0000' } ||
            (mime != null && mime !in setOf("application/pdf", "application/octet-stream"))) {
            throw NarraException(ErrorKind.PDF_UNSAFE)
        }
    }

    fun copy(input: InputStream, output: OutputStream, limit: Long = MAX_BYTES): Long {
        val signature = input.readNBytesCompat(5)
        if (!signature.contentEquals("%PDF-".toByteArray(Charsets.US_ASCII))) {
            throw NarraException(ErrorKind.PDF_CORRUPT)
        }
        var count = signature.size.toLong()
        if (count > limit) throw NarraException(ErrorKind.PDF_TOO_LARGE)
        output.write(signature)
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            count += read
            if (count > limit) throw NarraException(ErrorKind.PDF_TOO_LARGE)
            output.write(buffer, 0, read)
        }
        return count
    }

    private fun InputStream.readNBytesCompat(size: Int): ByteArray {
        val bytes = ByteArray(size)
        var count = 0
        while (count < size) {
            val read = read(bytes, count, size - count)
            if (read == -1) break
            count += read
        }
        return bytes.copyOf(count)
    }
}
