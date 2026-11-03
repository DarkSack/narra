package app.narra.data.pdf

import app.narra.domain.model.ErrorKind
import app.narra.domain.model.NarraException
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSObject
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.util.Collections
import java.util.IdentityHashMap

/** Solo al compartir: las mismas reglas que aplica el servidor (backend/renderer/sanitize.py). */
object PdfStructurePolicy {
    const val MAX_PAGES = 500
    // OpenAction y URI son comunes (página inicial, enlaces); lo que cuelga de ellos se revisa igual.
    private val forbiddenKeys = setOf("JS", "JavaScript", "AA", "EmbeddedFiles", "EF", "XFA", "RichMediaContent")
    private val forbiddenActions = setOf("JavaScript", "Launch", "GoToR", "GoToE", "SubmitForm", "ImportData", "Rendition", "Movie", "Sound")

    fun validate(doc: PDDocument) {
        if (doc.isEncrypted) throw NarraException(ErrorKind.PDF_ENCRYPTED)
        if (doc.numberOfPages > MAX_PAGES) throw NarraException(ErrorKind.PDF_TOO_LARGE)
        val seen = Collections.newSetFromMap(IdentityHashMap<COSBase, Boolean>())
        val queue = java.util.ArrayDeque<COSBase>()
        queue.add(doc.documentCatalog.cosObject)
        doc.document.objects.forEach { queue.add(it) }
        while (queue.isNotEmpty()) {
            val value = queue.removeFirst()
            // Solo cuentan los contenedores: nombres, números y textos no esconden acciones.
            if (value !is COSObject && value !is COSDictionary && value !is COSArray || !seen.add(value)) continue
            if (seen.size > 200_000) throw NarraException(ErrorKind.PDF_TOO_LARGE)
            when (value) {
                is COSObject -> value.`object`?.let(queue::add)
                is COSDictionary -> {
                    if (value.keySet().any { it.name in forbiddenKeys } ||
                        value.getNameAsString(com.tom_roush.pdfbox.cos.COSName.S) in forbiddenActions) {
                        throw NarraException(ErrorKind.PDF_UNSAFE)
                    }
                    value.values.forEach { queue.add(it) }
                }
                is COSArray -> value.forEach { queue.add(it) }
            }
        }
    }
}
