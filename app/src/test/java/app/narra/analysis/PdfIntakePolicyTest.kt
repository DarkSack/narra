package app.narra.analysis

import app.narra.data.pdf.PdfIntakePolicy
import app.narra.domain.model.ErrorKind
import app.narra.domain.model.NarraException
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream

class PdfIntakePolicyTest {
    @Test fun acceptsPdfAndPreservesBytes() {
        val source = "%PDF-1.7\nexample".toByteArray()
        val output = ByteArrayOutputStream()
        assertEquals(source.size.toLong(), PdfIntakePolicy.copy(source.inputStream(), output))
        assertArrayEquals(source, output.toByteArray())
        PdfIntakePolicy.validateName("Libro.PDF", "application/pdf")
        PdfIntakePolicy.validateName("msf:1000000123", "application/pdf") // proveedor sin extensión
        assertFailure(ErrorKind.PDF_UNSAFE) { PdfIntakePolicy.validateName("msf:1000000123", "application/octet-stream") }
    }

    @Test fun rejectsFakePdfAndOversizeStreamsWithoutTrustingDeclaredSize() {
        assertFailure(ErrorKind.PDF_CORRUPT) { PdfIntakePolicy.copy("MZfake.pdf".byteInputStream(), ByteArrayOutputStream()) }
        assertFailure(ErrorKind.PDF_TOO_LARGE) { PdfIntakePolicy.copy("%PDF-123456".byteInputStream(), ByteArrayOutputStream(), 8) }
        assertFailure(ErrorKind.PDF_UNSAFE) { PdfIntakePolicy.validateName("book.pdf.exe", "application/pdf") }
        assertFailure(ErrorKind.PDF_UNSAFE) { PdfIntakePolicy.validateName("book.pdf", "application/zip") }
    }

    private fun assertFailure(kind: ErrorKind, block: () -> Unit) {
        try { block(); fail("Se esperaba rechazo") } catch (e: NarraException) { assertEquals(kind, e.kind) }
    }
}
