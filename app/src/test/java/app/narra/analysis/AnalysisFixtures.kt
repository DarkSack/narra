package app.narra.analysis

/** Construye páginas sintéticas con la geometría típica de un libro de bolsillo. */
object AnalysisFixtures {
    const val PAGE_HEIGHT = 595f
    const val BODY = 11f
    private const val LINE_STEP = 15f / PAGE_HEIGHT
    private const val TOP = 0.15f

    class PageBuilder(private val index: Int) {
        private val lines = ArrayList<TextLine>()
        private var y = TOP
        var images = 0

        fun header(text: String) {
            lines += TextLine(text, 8f, 0.12f, 0.05f, 0.4f)
        }

        fun footer(text: String) {
            lines += TextLine(text, 9f, 0.48f, 0.96f, 0.04f)
        }

        fun heading(text: String, size: Float = 18f) {
            lines += TextLine(text, size, 0.12f, y, 0.5f, bold = true)
            y += LINE_STEP * 3
        }

        /** Línea de cuerpo; [short] = la última de un párrafo (no llega al margen derecho). */
        fun line(text: String, indent: Boolean = false, short: Boolean = false, size: Float = BODY) {
            val x = if (indent) 0.16f else 0.12f
            lines += TextLine(text, size, x, y, if (short) 0.35f else 0.88f - x)
            y += LINE_STEP
        }

        fun gap(lines: Float = 1.5f) {
            y += LINE_STEP * lines
        }

        fun footnote(text: String) {
            lines += TextLine(text, 8f, 0.12f, 0.88f, 0.6f)
        }

        fun row(vararg cells: String) {
            lines += TextLine(cells.joinToString(PageText.COLUMN_SEPARATOR), BODY, 0.12f, y, 0.76f, columns = cells.size)
            y += LINE_STEP
        }

        fun build() = PageText(index, PAGE_HEIGHT, lines.sortedBy { it.y }, images)
    }

    fun page(index: Int, block: PageBuilder.() -> Unit): PageText = PageBuilder(index).apply(block).build()

    fun profile(pages: List<PageText>): DocumentProfile =
        DocumentProfile.Builder(pages.size).apply { pages.forEach(::accept) }.build()

    val filler = "Era una noche tranquila en la isla y el faro giraba despacio sobre el agua oscura"
}
