package app.narra.presentation.common

import app.narra.core.ui.plural
import app.narra.domain.model.AnalysisReport
import app.narra.domain.model.ChapterSource

/** De dónde salieron los capítulos, dicho para una persona. */
fun ChapterSource.explanation(): String = when (this) {
    ChapterSource.OUTLINE -> "Capítulos tomados del índice interno del PDF."
    ChapterSource.TABLE_OF_CONTENTS -> "Capítulos tomados del índice impreso del libro."
    ChapterSource.HEADINGS -> "Capítulos detectados por sus títulos. Revísalos por si falta alguno."
    ChapterSource.PAGES -> "El PDF no indica capítulos: lo dividimos en partes de unos 30 minutos. Puedes renombrarlas."
    ChapterSource.MANUAL -> "Capítulos editados por ti."
}

/** Lo que la limpieza hizo por el usuario, en frases cortas. Solo lo que realmente ocurrió. */
fun AnalysisReport.highlights(): List<String> = buildList {
    val margins = headersRemoved + footersRemoved
    if (margins > 0) add("Quitamos ${plural(margins, "encabezado o pie repetido", "encabezados y pies repetidos")}")
    if (pageNumbersRemoved > 0) add("Omitimos ${plural(pageNumbersRemoved, "número de página", "números de página")}")
    if (hyphenationsFixed > 0) add("Unimos ${plural(hyphenationsFixed, "palabra cortada", "palabras cortadas")} al final de línea")
    if (footnotes > 0) add("Apartamos ${plural(footnotes, "nota al pie", "notas al pie")} para no interrumpir la lectura")
    if (dialogues > 0) add("Reconocimos ${plural(dialogues, "diálogo", "diálogos")}")
    if (quotes > 0) add("Reconocimos ${plural(quotes, "cita", "citas")}")
    if (tables > 0) add("${plural(tables, "tabla", "tablas")} se leerán fila por fila")
    if (lists > 0) add("${plural(lists, "elemento de lista", "elementos de lista")}")
    if (references > 0) add("${plural(references, "sección", "secciones")} de referencia (índice, bibliografía) sin incluir")
    if (ocrPages > 0) add("Reconocimos el texto de ${plural(ocrPages, "página escaneada", "páginas escaneadas")}")
    if (scannedPages > 0) add("${plural(scannedPages, "página parece escaneada", "páginas parecen escaneadas")}: no tienen texto que leer")
}

/** Un título que ya empieza por su número ("Capítulo 1", "III", "2. El viaje") no necesita otro delante. */
private val NUMBERED_TITLE = Regex("""^\s*((cap[ií]tulo|chapter|parte|part|libro|book)\s+)?(\d+|[IVXLC]+)\b""", RegexOption.IGNORE_CASE)

/** Etiqueta para una lista de capítulos: añade la posición solo si el título no la lleva. */
fun chapterLabel(number: Int, title: String): String =
    if (NUMBERED_TITLE.containsMatchIn(title)) title else "$number. $title"

/** "Capítulo 3 · El viaje", o solo el título si ya dice qué capítulo es. */
fun chapterHeading(number: Int, title: String?): String = when {
    title.isNullOrBlank() -> "Capítulo $number"
    NUMBERED_TITLE.containsMatchIn(title) -> title
    else -> "Capítulo $number · $title"
}
