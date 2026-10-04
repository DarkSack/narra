package app.narra.data.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import kotlin.math.roundToInt

/** Dibuja la primera página como portada con el renderizador del sistema. */
class CoverRenderer @Inject constructor() {

    /**
     * Guarda la portada en [target] y devuelve true. Devuelve false si la primera página está
     * prácticamente en blanco o no se puede dibujar: en ese caso se usa la portada generada.
     */
    suspend fun render(pdf: File, target: File): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    if (renderer.pageCount == 0) return@runCatching false
                    renderer.openPage(0).use { page ->
                        val height = (COVER_WIDTH_PX * page.height.toFloat() / page.width).roundToInt().coerceIn(1, MAX_HEIGHT_PX)
                        val bitmap = createBitmap(COVER_WIDTH_PX, height)
                        bitmap.eraseColor(Color.WHITE) // las páginas sin fondo son transparentes
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val useful = !bitmap.isNearlyBlank()
                        if (useful) save(bitmap, target)
                        bitmap.recycle()
                        useful
                    }
                }
            }
        }.getOrDefault(false)
    }

    private fun save(bitmap: Bitmap, target: File) {
        target.parentFile?.mkdirs()
        val partial = File(target.parentFile, "${target.name}.part")
        partial.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        if (!partial.renameTo(target)) partial.delete()
    }

    /** Muestrea una rejilla: si casi todos los puntos son blancos, la página no sirve de portada. */
    private fun Bitmap.isNearlyBlank(): Boolean {
        var inked = 0
        var total = 0
        for (yi in 0 until SAMPLE_GRID) {
            for (xi in 0 until SAMPLE_GRID) {
                val pixel = this[xi * (width - 1) / (SAMPLE_GRID - 1), yi * (height - 1) / (SAMPLE_GRID - 1)]
                val luminance = (Color.red(pixel) * 299 + Color.green(pixel) * 587 + Color.blue(pixel) * 114) / 1000
                if (luminance < INK_LUMINANCE) inked++
                total++
            }
        }
        return inked.toFloat() / total < MIN_INK_FRACTION
    }

    private companion object {
        const val COVER_WIDTH_PX = 720
        const val MAX_HEIGHT_PX = 1440
        const val JPEG_QUALITY = 88
        const val SAMPLE_GRID = 40
        const val INK_LUMINANCE = 235
        const val MIN_INK_FRACTION = 0.01f
    }
}
