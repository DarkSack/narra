package app.narra.data.ocr

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import app.narra.analysis.PageText
import app.narra.analysis.TextLine
import app.narra.domain.model.ErrorKind
import app.narra.domain.model.NarraException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Reconoce el texto de páginas escaneadas en el propio teléfono (ML Kit, modelo latino incluido
 * en la app: no hace falta conexión). Cada página se dibuja, se reconoce y se descarta antes de
 * pasar a la siguiente, así la memoria no crece con el tamaño del libro.
 */
class PageRecognizer @Inject constructor() {

    /**
     * Reconoce las páginas [indexes] de [pdf] en orden y entrega cada una a [onPage] con el mismo
     * formato que el extractor de texto, para que el resto del análisis no distinga su origen.
     */
    suspend fun recognize(pdf: File, indexes: List<Int>, onPage: suspend (PageText) -> Unit) {
        if (indexes.isEmpty()) return
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val descriptor = try {
                ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)
            } catch (e: IOException) {
                throw NarraException(ErrorKind.FILE_MISSING, e.message, e)
            }
            descriptor.use {
                PdfRenderer(it).use { renderer ->
                    for (index in indexes.sorted()) {
                        currentCoroutineContext().ensureActive()
                        if (index !in 0 until renderer.pageCount) continue
                        val (bitmap, heightPt) = render(renderer, index)
                        val width = bitmap.width
                        val height = bitmap.height
                        val text = try {
                            recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            throw NarraException(ErrorKind.OCR_FAILED, "página ${index + 1}: ${e.message}", e)
                        } finally {
                            bitmap.recycle()
                        }
                        onPage(PageText(index = index, heightPt = heightPt, lines = text.toLines(width, height, heightPt), images = 1))
                    }
                }
            }
        } finally {
            recognizer.close()
        }
    }

    /** Dibuja la página a una resolución suficiente para el reconocimiento (≈ 220 ppp en A4). */
    private suspend fun render(renderer: PdfRenderer, index: Int): Pair<Bitmap, Float> = withContext(Dispatchers.IO) {
        renderer.openPage(index).use { page ->
            val scale = (OCR_DPI / POINTS_PER_INCH).coerceAtMost(MAX_WIDTH_PX.toFloat() / page.width)
            val width = (page.width * scale).roundToInt().coerceAtLeast(1)
            val height = (page.height * scale).roundToInt().coerceAtLeast(1)
            val bitmap = createBitmap(width, height)
            // Las páginas sin fondo se dibujan transparentes: el reconocedor necesita papel blanco.
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap to page.height.toFloat()
        }
    }

    /**
     * Convierte el resultado a líneas con posiciones relativas. El tamaño de letra se estima a
     * partir de la altura de la caja de cada línea, que incluye ascendentes y descendentes.
     */
    private fun Text.toLines(width: Int, height: Int, heightPt: Float): List<TextLine> =
        textBlocks.flatMap { block ->
            block.lines.mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                val text = line.text.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                TextLine(
                    text = text,
                    fontSize = box.height() * heightPt / height / LINE_BOX_TO_FONT_SIZE,
                    x = box.left.toFloat() / width,
                    y = box.bottom.toFloat() / height,
                    width = box.width().toFloat() / width,
                )
            }
        }

    private companion object {
        const val OCR_DPI = 220f
        const val POINTS_PER_INCH = 72f
        const val MAX_WIDTH_PX = 2_000

        /** La caja de una línea mide aproximadamente 1,25 veces el cuerpo de la letra. */
        const val LINE_BOX_TO_FONT_SIZE = 1.25f
    }
}
