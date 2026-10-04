package app.narra.presentation

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.narra.MainActivity
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pasa las comprobaciones de accesibilidad de Google (Accessibility Test Framework) por las
 * pantallas principales con los datos que haya en el dispositivo: tamaño de las zonas táctiles,
 * contraste, etiquetas para lectores de pantalla… Solo navega; no cambia nada.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AccessibilityAuditTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        rule.enableAccessibilityChecks()
        // En un dispositivo nuevo aparece antes la introducción.
        rule.waitUntil(LOAD_TIMEOUT_MS) { exists("Inicio") || exists("Saltar") }
        if (exists("Saltar")) clickText("Saltar")
        rule.waitUntil(LOAD_TIMEOUT_MS) { exists("Inicio") }
    }

    @Test
    fun inicio() {
        rule.onRoot().tryPerformAccessibilityChecks()
    }

    @Test
    fun biblioteca() {
        clickTab("Biblioteca")
        rule.onRoot().tryPerformAccessibilityChecks()
    }

    @Test
    fun ajustes() {
        clickTab("Ajustes")
        rule.onRoot().tryPerformAccessibilityChecks()
        scrollTo("Versión")
        rule.onRoot().tryPerformAccessibilityChecks()
    }

    @Test
    fun almacenamiento() {
        clickTab("Ajustes")
        scrollTo("Espacio y archivos")
        clickText("Espacio y archivos")
        rule.waitUntil(LOAD_TIMEOUT_MS) { exists("Por libro") || exists("Archivos temporales") }
        rule.onRoot().tryPerformAccessibilityChecks()
    }

    @Test
    fun licencias() {
        clickTab("Ajustes")
        scrollTo("Licencias de código abierto")
        clickText("Licencias de código abierto")
        clickText("Bouncy Castle")
        rule.onRoot().tryPerformAccessibilityChecks()
    }

    @Test
    fun fichaDeUnLibro() {
        clickTab("Biblioteca")
        // Un libro ya creado abre su ficha; uno pendiente de revisar abriría la revisión.
        val createdBook = hasClickAction() and SemanticsMatcher("libro ya creado") { node ->
            val description = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString()
            "capítulo" in description && "revisar" !in description
        }
        val books = rule.onAllNodes(createdBook)
        assumeTrue("No hay libros creados en el dispositivo", books.fetchSemanticsNodes().isNotEmpty())
        books.onFirst().performClick()
        rule.waitUntil(LOAD_TIMEOUT_MS) { exists("Información") || exists("Capítulos") }
        rule.onRoot().tryPerformAccessibilityChecks()
    }

    private fun exists(text: String): Boolean = rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun clickText(text: String) {
        rule.onAllNodesWithText(text).onFirst().performClick()
        rule.waitForIdle()
    }

    private fun clickTab(label: String) {
        rule.onAllNodes(hasText(label) and hasClickAction()).onFirst().performClick()
        rule.waitForIdle()
    }

    private fun scrollTo(text: String) {
        rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(text))
        rule.waitForIdle()
    }

    private companion object {
        const val LOAD_TIMEOUT_MS = 10_000L
    }
}
