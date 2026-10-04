package app.narra.presentation.onboarding

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.narra.core.designsystem.theme.NarraTheme
import app.narra.domain.model.AppSettings
import app.narra.domain.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** La introducción, página a página, con las comprobaciones de accesibilidad en claro y oscuro. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class OnboardingAccessibilityTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun temaClaro() = recorrer(ThemeMode.LIGHT)

    @Test
    fun temaOscuro() = recorrer(ThemeMode.DARK)

    private fun recorrer(theme: ThemeMode) {
        var finished: Boolean? = null
        rule.setContent {
            NarraTheme(AppSettings(themeMode = theme)) { OnboardingScreen(onFinish = { finished = it }) }
        }
        rule.enableAccessibilityChecks()
        rule.onRoot().tryPerformAccessibilityChecks()
        rule.onNodeWithText("Siguiente").performClick()
        rule.onNodeWithText("Siguiente").performClick()
        rule.onNodeWithText("Importar tu primer libro").performClick()
        assertEquals(true, finished)
    }
}
