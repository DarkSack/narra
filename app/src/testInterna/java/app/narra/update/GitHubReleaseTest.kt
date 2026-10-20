package app.narra.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleaseTest {

    @Test
    fun comparaVersionesNumeroPorNumero() {
        assertTrue(isNewer("3.1.0", "3.0.0"))
        assertTrue(isNewer("3.10.0", "3.9.1"))
        assertTrue(isNewer("4", "3.9.9"))
        assertTrue(isNewer("3.0.1", "3.0"))
        assertFalse(isNewer("3.0.0", "3.0.0"))
        assertFalse(isNewer("3.0", "3.0.0"))
        assertFalse(isNewer("2.9.9", "3.0.0"))
    }

    @Test
    fun leeLaEtiquetaYElApkDeLaRespuestaDeGitHub() {
        val release = parseRelease(
            """
            {
              "url": "https://api.github.com/repos/DarkSack/narra/releases/1",
              "tag_name": "v3.1.0",
              "name": "Narra 3.1.0",
              "draft": false,
              "assets": [
                {"name": "notas.txt", "size": 10, "browser_download_url": "https://github.com/x/notas.txt", "content_type": "text/plain"},
                {"name": "narra-3.1.0.apk", "size": 49327090, "browser_download_url": "https://github.com/x/narra-3.1.0.apk", "content_type": "application/vnd.android.package-archive"}
              ]
            }
            """.trimIndent(),
        )
        assertEquals("3.1.0", release.version)
        assertEquals("narra-3.1.0.apk", release.apk?.name)
        assertEquals(49327090L, release.apk?.size)
        assertEquals("https://github.com/x/narra-3.1.0.apk", release.apk?.url)
    }

    @Test
    fun unaVersionSinApkNoTieneQueDescargar() {
        assertNull(parseRelease("""{"tag_name": "v3.1.0", "assets": []}""").apk)
    }
}
