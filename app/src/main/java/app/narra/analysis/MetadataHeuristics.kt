package app.narra.analysis

/** Metadatos tal como vienen en el PDF (diccionario Info y catálogo). */
data class PdfMetadata(
    val title: String? = null,
    val author: String? = null,
    val subject: String? = null,
    val keywords: String? = null,
    val language: String? = null,
)

/** Metadatos finales tras combinar lo que dice el PDF con lo que se deduce del texto. */
data class DetectedMetadata(
    val title: String?,
    val author: String?,
    val description: String?,
    val language: String?,
    val isbn: String?,
    val keywords: List<String>,
)

object MetadataHeuristics {
    private val junkTitle = Regex(
        "^(untitled|sin t[ií]tulo|documento\\d*|document\\d*|microsoft word.*|.*\\.(docx?|pdf|odt|rtf|indd|tex)|[\\w\\-]+_[\\w\\-_]+|\\d+)$",
        RegexOption.IGNORE_CASE,
    )
    private val junkAuthor = Regex("^(user|usuario|admin(istrator|istrador)?|owner|propietario|unknown|desconocido|microsoft.*|.*@.*)$", RegexOption.IGNORE_CASE)
    private val isbnPattern = Regex("ISBN(?:-1[03])?[:\\s]*((?:97[89][\\s-]?)?(?:\\d[\\s-]?){9}[\\dXx])", RegexOption.IGNORE_CASE)
    private val keywordSeparators = Regex("[;,\\n]")
    private const val MAX_TITLE = 200
    private const val MAX_KEYWORDS = 12

    fun combine(pdf: PdfMetadata, profile: DocumentProfile): DetectedMetadata {
        val title = pdf.title?.trim()?.takeIf(::isPlausibleTitle) ?: titleFromFirstPages(profile)
        val author = pdf.author?.trim()?.takeIf { it.isNotBlank() && !junkAuthor.matches(it) && it.length < MAX_TITLE }
        return DetectedMetadata(
            title = title,
            author = author,
            description = pdf.subject?.trim()?.takeIf { it.length > 3 && !it.equals(title, ignoreCase = true) },
            language = pdf.language?.takeIf { it.isNotBlank() }?.let(::normalizeLanguage) ?: LanguageDetector.detect(profile.textSample),
            isbn = findIsbn(profile.frontText) ?: findIsbn(profile.backText),
            keywords = pdf.keywords.orEmpty().split(keywordSeparators).map { it.trim() }.filter { it.length > 1 }.distinct().take(MAX_KEYWORDS),
        )
    }

    fun isPlausibleTitle(title: String): Boolean =
        title.length in 2..MAX_TITLE && title.any { it.isLetter() } && !junkTitle.matches(title.trim())

    /** La línea más grande de las primeras páginas suele ser el título. */
    private fun titleFromFirstPages(profile: DocumentProfile): String? = profile.earlyPages
        .take(TITLE_SEARCH_PAGES)
        .flatMap { it.lines }
        .filter { it.text.length in 2..MAX_TITLE && it.text.any { c -> c.isLetter() } && !DocumentProfile.inMarginZone(it) }
        .maxByOrNull { it.fontSize }
        ?.takeIf { it.fontSize >= profile.bodyFontSize * TITLE_SIZE_FACTOR }
        ?.text?.trim()

    fun findIsbn(text: String): String? = isbnPattern.findAll(text)
        .map { it.groupValues[1].filter { c -> c.isDigit() || c == 'X' || c == 'x' }.uppercase() }
        .firstOrNull(::isValidIsbn)

    fun isValidIsbn(isbn: String): Boolean = when (isbn.length) {
        10 -> isbn.withIndex().sumOf { (i, c) -> (10 - i) * (if (c == 'X') 10 else c.digitToIntOrNull() ?: return false) } % 11 == 0
        13 -> isbn.all { it.isDigit() } &&
            isbn.withIndex().sumOf { (i, c) -> c.digitToInt() * (if (i % 2 == 0) 1 else 3) } % 10 == 0
        else -> false
    }

    /** "es_ES", "ES-es" → "es-ES". */
    private fun normalizeLanguage(tag: String): String {
        val parts = tag.trim().replace('_', '-').split('-')
        val language = parts.first().lowercase()
        return if (parts.size > 1 && parts[1].length == 2) "$language-${parts[1].uppercase()}" else language
    }

    private const val TITLE_SEARCH_PAGES = 3
    private const val TITLE_SIZE_FACTOR = 1.5f
}

/** Idioma por palabras frecuentes: suficiente para elegir voz, sin modelos ni red. */
object LanguageDetector {
    private val stopwords = mapOf(
        "es" to setOf("de", "la", "que", "el", "en", "y", "los", "se", "del", "las", "un", "por", "con", "no", "una", "su", "para", "es", "al", "lo", "como", "más", "pero", "sus", "le", "ya", "o", "fue", "este", "ha"),
        "en" to setOf("the", "of", "and", "to", "in", "is", "you", "that", "it", "he", "was", "for", "on", "are", "as", "with", "his", "they", "at", "be", "this", "have", "from", "or", "had", "by", "not", "but", "what", "were"),
        "pt" to setOf("de", "a", "o", "que", "e", "do", "da", "em", "um", "para", "é", "com", "não", "uma", "os", "no", "se", "na", "por", "mais", "as", "dos", "como", "mas", "foi", "ao", "ele", "das", "tem", "seu"),
        "fr" to setOf("de", "la", "le", "et", "les", "des", "en", "un", "du", "une", "que", "est", "pour", "qui", "dans", "a", "par", "plus", "pas", "au", "sur", "ne", "se", "ce", "il", "sont", "avec", "son", "elle", "mais"),
        "it" to setOf("di", "e", "il", "la", "che", "a", "per", "un", "in", "è", "del", "non", "una", "della", "le", "si", "con", "i", "da", "al", "dei", "come", "ma", "sono", "più", "anche", "nel", "lo", "gli", "ha"),
        "de" to setOf("der", "die", "und", "in", "den", "von", "zu", "das", "mit", "sich", "des", "auf", "für", "ist", "im", "dem", "nicht", "ein", "die", "eine", "als", "auch", "es", "an", "werden", "aus", "er", "hat", "dass", "sie"),
    )
    private val word = Regex("\\p{L}+")
    private const val MIN_WORDS = 30
    private const val MIN_SHARE = 0.12f

    fun detect(sample: String): String? {
        val words = word.findAll(sample.lowercase()).map { it.value }.take(MAX_WORDS).toList()
        if (words.size < MIN_WORDS) return null
        val scores = stopwords.mapValues { (_, set) -> words.count { it in set } }
        val best = scores.maxByOrNull { it.value } ?: return null
        return best.key.takeIf { best.value >= words.size * MIN_SHARE }
    }

    private const val MAX_WORDS = 4_000
}
