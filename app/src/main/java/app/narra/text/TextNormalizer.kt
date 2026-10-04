package app.narra.text

import java.text.Normalizer

/** Limpia el texto crudo de una página de PDF para que la voz lo lea con naturalidad. */
object TextNormalizer {
    // Caracteres de control: se cambian por un espacio.
    private val controlChars = Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F]")

    // De ancho cero y de reemplazo: se quitan sin más para no partir palabras.
    private val zeroWidthChars = Regex("[\\u200B-\\u200D\\u2060\\uFEFF\\uFFFD]")

    // "pala-\nbra" -> "palabra" (solo si la línea siguiente sigue en minúscula).
    private val hyphenatedLineBreak = Regex("(\\p{L})[-\\u00AD\\u2010]\\s*\\n\\s*(\\p{Ll})")

    // Líneas que solo contienen un número: casi siempre son números de página.
    private val pageNumberLine = Regex("(?m)^\\s*\\d{1,4}\\s*$")

    private val whitespace = Regex("\\s+")

    fun normalizePage(raw: String): String {
        if (raw.isBlank()) return ""
        // NFKC separa ligaduras tipográficas ("ﬁ" -> "fi") y normaliza formas de ancho completo.
        var text = Normalizer.normalize(raw, Normalizer.Form.NFKC)
        text = text.replace("\r\n", "\n").replace('\r', '\n')
        text = controlChars.replace(text, " ")
        text = zeroWidthChars.replace(text, "")
        text = hyphenatedLineBreak.replace(text, "$1$2")
        text = text.replace(Char(0x00AD).toString(), "") // guiones blandos que quedaron sueltos
        text = pageNumberLine.replace(text, " ")
        return whitespace.replace(text, " ").trim()
    }
}
