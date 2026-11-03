package app.narra.analysis

import app.narra.data.pdf.PdfStructurePolicy
import app.narra.domain.model.ErrorKind
import app.narra.domain.model.NarraException
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class PdfStructurePolicyTest {
    @Test fun allowsPlainPagesButRejectsNestedScripts() {
        PDDocument().use { doc ->
            doc.addPage(PDPage())
            PdfStructurePolicy.validate(doc)
            // Enlaces y página inicial son habituales en libros: se aceptan.
            val link=COSDictionary().apply { setName(COSName.S,"URI");setString(COSName.getPDFName("URI"),"https://example.com") }
            doc.documentCatalog.cosObject.setItem(COSName.getPDFName("Custom"),link)
            doc.documentCatalog.cosObject.setItem(COSName.getPDFName("OpenAction"),COSArray().apply { add(doc.getPage(0).cosObject);add(COSName.getPDFName("Fit")) })
            PdfStructurePolicy.validate(doc)
            val script=COSDictionary().apply { setName(COSName.S,"JavaScript");setString(COSName.getPDFName("JS"),"app.alert(1)") }
            doc.documentCatalog.cosObject.setItem(COSName.getPDFName("OpenAction"),script)
            try { PdfStructurePolicy.validate(doc);fail("No debe aceptar JavaScript") }
            catch(e: NarraException) { assertEquals(ErrorKind.PDF_UNSAFE,e.kind) }
        }
    }
    @Test fun cyclicObjectsDoNotLoopForever() {
        PDDocument().use { doc ->
            doc.addPage(PDPage())
            val cycle=COSDictionary()
            cycle.setItem(COSName.getPDFName("Loop"),cycle)
            doc.documentCatalog.cosObject.setItem(COSName.getPDFName("Custom"),cycle)
            PdfStructurePolicy.validate(doc)
        }
    }
}
