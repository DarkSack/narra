package app.narra.data.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.narra.analysis.PageText
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * OCR en el dispositivo con un PDF "escaneado" generado aquí: el texto se dibuja como imagen,
 * así el PDF no tiene capa de texto, igual que un libro pasado por un escáner.
 */
@RunWith(AndroidJUnit4::class)
class PageRecognizerTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun reconoceElTextoDeUnaPaginaEscaneada() = runTest {
        val pdf = scannedPdf(File(context.cacheDir, "escaneado.pdf"))
        val pages = mutableListOf<PageText>()

        PageRecognizer().recognize(pdf, listOf(0, 1)) { pages += it }

        assertEquals(listOf(0, 1), pages.map { it.index })
        val first = pages.first()
        val text = first.lines.joinToString(" ") { it.text }.lowercase()
        assertTrue(text, "capítulo 1" in text || "capitulo 1" in text)
        assertTrue(text, "faro" in text && "pescadores" in text)
        // Las líneas llegan de arriba abajo y el título se reconoce más grande que el cuerpo.
        assertTrue(first.lines.zipWithNext().all { (a, b) -> b.y >= a.y })
        val body = first.lines.drop(1).map { it.fontSize }.sorted()[first.lines.size / 2]
        assertTrue("título ${first.lines.first().fontSize} > cuerpo $body", first.lines.first().fontSize > body * 1.3f)
        assertTrue(first.lines.all { it.x in 0f..1f && it.y in 0f..1f })
    }

    /**
     * Libro de prueba escaneado, también útil para probar la importación a mano: queda copiado en
     * la carpeta de la app en el almacenamiento externo.
     */
    @Test
    fun generaUnLibroEscaneadoDePrueba() {
        val target = File(context.getExternalFilesDir(null), "faro_escaneado.pdf")
        scannedPdf(target)
        assertTrue(target.length() > 0)
    }

    private fun scannedPdf(target: File): File {
        val document = PdfDocument()
        CHAPTERS.forEachIndexed { index, (title, paragraphs) ->
            val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH_PT, PAGE_HEIGHT_PT, index + 1).create())
            val bitmap = renderPage(title, paragraphs)
            page.canvas.drawBitmap(bitmap, null, Rect(0, 0, PAGE_WIDTH_PT, PAGE_HEIGHT_PT), null)
            bitmap.recycle()
            document.finishPage(page)
        }
        target.parentFile?.mkdirs()
        target.outputStream().use { document.writeTo(it) }
        document.close()
        return target
    }

    /** Dibuja una página a 150 ppp, como la imagen que produciría un escáner. */
    private fun renderPage(title: String, paragraphs: List<String>): Bitmap {
        val bitmap = Bitmap.createBitmap(PAGE_WIDTH_PX, PAGE_HEIGHT_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val heading = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 56f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD) }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 30f; typeface = Typeface.SERIF }
        var y = 220f
        canvas.drawText(title, MARGIN, y, heading)
        y += 110f
        paragraphs.forEach { paragraph ->
            wrap(paragraph, body, PAGE_WIDTH_PX - 2 * MARGIN).forEach { line ->
                canvas.drawText(line, MARGIN, y, body)
                y += 46f
            }
            y += 30f
        }
        return bitmap
    }

    private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
        val lines = mutableListOf<String>()
        var current = ""
        text.split(' ').forEach { word ->
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) > maxWidth && current.isNotEmpty()) {
                lines += current
                current = word
            } else {
                current = candidate
            }
        }
        if (current.isNotEmpty()) lines += current
        return lines
    }

    private companion object {
        const val PAGE_WIDTH_PT = 595
        const val PAGE_HEIGHT_PT = 842
        const val PAGE_WIDTH_PX = 1240
        const val PAGE_HEIGHT_PX = 1754
        const val MARGIN = 130f

        val CHAPTERS = listOf(
            "Capítulo 1. La isla" to listOf(
                "Nadie recordaba cuándo se había encendido el faro por primera vez. Los pescadores decían que su luz no servía para guiar barcos, sino para guiar historias.",
                "Marta llegó a la isla un martes de niebla, con una maleta de cartón y un cuaderno lleno de frases a medias. El farero la esperaba en el muelle.",
            ),
            "Capítulo 2. El cuaderno" to listOf(
                "Cada noche, cuando la lámpara empezaba a girar, Marta abría el cuaderno y leía en voz alta una página distinta.",
                "Las palabras salían por la ventana, cruzaban el agua oscura y llegaban a las casas del pueblo como si alguien las susurrara al oído.",
            ),
            "Capítulo 3. La última luz" to listOf(
                "El invierno trajo tormentas que apagaron todas las luces de la costa, menos una. Aquella noche nadie durmió en la isla.",
                "Al amanecer, el cuaderno estaba lleno. Marta lo cerró, apagó la lámpara y comprendió que ya no hacía falta encenderla.",
            ),
        )
    }
}
