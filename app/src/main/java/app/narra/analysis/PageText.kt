package app.narra.analysis

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Una línea de texto tal como aparece en la página. Las posiciones son relativas al tamaño de la
 * página (0–1) para que las heurísticas funcionen igual con un A5 que con un A4; el tamaño de
 * letra se guarda en puntos. Los nombres cortos mantienen pequeño el archivo de páginas.
 */
@Serializable
data class TextLine(
    @SerialName("t") val text: String,
    @SerialName("s") val fontSize: Float,
    /** Borde izquierdo, 0 = margen izquierdo de la hoja. */
    @SerialName("x") val x: Float,
    /** Línea base, 0 = borde superior de la hoja. */
    @SerialName("y") val y: Float,
    @SerialName("w") val width: Float,
    @SerialName("b") val bold: Boolean = false,
    /** Bloques separados por huecos grandes: más de dos sugiere una fila de tabla. */
    @SerialName("c") val columns: Int = 1,
) {
    val right: Float get() = x + width
}

@Serializable
data class PageText(
    @SerialName("i") val index: Int,
    /** Alto de la página en puntos, para convertir distancias relativas. */
    @SerialName("h") val heightPt: Float,
    @SerialName("l") val lines: List<TextLine>,
    @SerialName("img") val images: Int = 0,
) {
    val charCount: Int get() = lines.sumOf { it.text.length }

    /** Sin texto extraíble: probablemente una página escaneada (o una ilustración). */
    val looksScanned: Boolean get() = charCount < SCANNED_PAGE_MAX_CHARS

    companion object {
        const val SCANNED_PAGE_MAX_CHARS = 20

        /** Separador que el extractor inserta entre bloques de una fila con columnas. */
        const val COLUMN_SEPARATOR = " | "
    }
}

/** Entrada del índice interno (marcadores) del PDF. */
data class OutlineEntry(val title: String, val pageIndex: Int, val level: Int)
