package app.narra.data.pdf

import app.narra.analysis.PageText
import kotlinx.serialization.json.Json
import java.io.BufferedWriter
import java.io.File

/**
 * Páginas extraídas en disco, una por línea (JSON Lines). Permite recorrer el libro varias
 * veces sin volver a leer el PDF ni tenerlo entero en memoria.
 */
object PageStore {
    @PublishedApi
    internal val json = Json { ignoreUnknownKeys = true }

    fun write(writer: BufferedWriter, page: PageText) {
        writer.write(json.encodeToString(PageText.serializer(), page))
        writer.newLine()
    }

    /** Recorre las páginas guardadas. La secuencia solo es válida dentro de [block]. */
    inline fun <T> read(file: File, block: (Sequence<PageText>) -> T): T =
        file.bufferedReader().use { reader ->
            block(reader.lineSequence().filter { it.isNotBlank() }.map { json.decodeFromString(PageText.serializer(), it) })
        }

    fun readPage(file: File, index: Int): PageText? = read(file) { pages -> pages.firstOrNull { it.index == index } }
}
