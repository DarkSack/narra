package app.narra.data.tts

import java.util.IllformedLocaleException
import java.util.Locale

/**
 * Algunos motores (eSpeak, RHVoice) describen sus voces con códigos de tres letras ("spa-ESP").
 * Se pasan a los de dos ("es-ES") para que el filtro por idioma y los nombres coincidan con los
 * de los demás motores y con el idioma detectado en el libro.
 */
internal object LocaleCodes {
    private val languages: Map<String, String> by lazy {
        Locale.getISOLanguages().associateBy { Locale.forLanguageTag(it).isO3Language }
    }

    private val countries: Map<String, String> by lazy {
        Locale.getISOCountries().associateBy { Locale.Builder().setRegion(it).build().isO3Country }
    }

    fun normalize(locale: Locale): Locale {
        val language = languages[locale.language] ?: locale.language
        val country = countries[locale.country] ?: locale.country
        if (language == locale.language && country == locale.country) return locale
        return try {
            Locale.Builder().setLanguage(language).setRegion(country).build()
        } catch (_: IllformedLocaleException) {
            locale
        }
    }
}
