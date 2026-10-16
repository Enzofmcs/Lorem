package dev.lorem.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.lorem.app.domain.model.LocalProfile
import dev.lorem.app.domain.model.ThemePreference
import dev.lorem.app.domain.sync.*
import dev.lorem.app.ui.components.LoremScaffold
import dev.lorem.app.ui.navigation.LoremDestination
import dev.lorem.app.ui.screens.ConfigurationScreen
import dev.lorem.app.ui.screens.HomeScreen
import dev.lorem.app.ui.theme.LoremTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w320dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeConfigurationLayoutTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var renderedView: View
    private val profile = LocalProfile("tourist", "Nome público comprido para testar a leitura", null, 1200, null, 1)
    private val saved = SynchronizationState(lastSuccessEpochMillis = 1_000_000)

    @Test fun `first access error loading recovery and active home remain readable at 320dp font200 in both themes`() {
        verifyLayouts(fontScale = 2f, suffix = "320-font200")
    }

    @Test
    @Config(qualifiers = "w640dp-h360dp-land")
    fun `landscape exposes form actions preparation and active home in both themes`() {
        verifyLayouts(fontScale = 1f, suffix = "landscape")
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp")
    fun `standard phone renders first access settings and home in both themes`() {
        verifyLayouts(fontScale = 1f, suffix = "360")
    }

    private fun verifyLayouts(fontScale: Float, suffix: String) {
        val dark = mutableStateOf(false)
        val screen = mutableStateOf(0)
        val loading = mutableStateOf(false)
        val error = "Sem conexão com o Codeforces. Verifique a internet e tente novamente."
        var recoveries = 0
        var continuations = 0
        compose.setContent {
            renderedView = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                LoremTheme(darkTheme = dark.value) {
                    LoremScaffold(if (screen.value == 2) LoremDestination.Home else LoremDestination.Configuration,
                        screen.value == 2, false, {}, {}, {}) {
                        when (screen.value) {
                            0 -> ConfigurationScreen(ConfigurationUiState(handle = "tourist", isLoading = loading.value,
                                errorMessage = error.takeUnless { loading.value }), null, {}, {})
                            1 -> ConfigurationScreen(ConfigurationUiState(handle = "tourist"), profile, {}, {},
                                preparation = PreparationUiState(ProfileLink.of("tourist", 1), saved,
                                    saved.copy(failure = "offline", failureKind = SyncFailureKind.NETWORK), saved, saved),
                                onRefreshCatalog = { recoveries++ })
                            else -> HomeScreen(profile, false, { continuations++ }, hasActiveIpsum = true,
                                latestFinishedIpsumId = 1, finishedIpsumCount = 3, acceptedIpsumCount = 1)
                        }
                    }
                }
            }
        }
        for (isDark in listOf(false, true)) {
            compose.runOnIdle { dark.value = isDark; screen.value = 0 }
            val theme = if (isDark) "dark" else "light"
            compose.onNodeWithText(error).performScrollTo().assertIsDisplayed()
            assertCompleteText()
            compose.onNodeWithText("Conectar Codeforces").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            screenshot("connect-$suffix-$theme")
            assertCompleteText()
            compose.runOnIdle { loading.value = true }
            compose.onNodeWithText("Validando perfil…").performScrollTo().assertIsNotEnabled()
            compose.onNodeWithText("Handle do Codeforces").assertIsNotEnabled()
            assertCompleteText()
            compose.runOnIdle { loading.value = false; screen.value = 1 }
            compose.onNodeWithText("Sem conexão · dados salvos").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Atualizar catálogo agora").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
            screenshot("preparation-$suffix-$theme")
            assertCompleteText()
            compose.onNodeWithTag("theme-DARK").performScrollTo().assertHeightIsAtLeast(48.dp)
            compose.runOnIdle { screen.value = 2 }
            compose.onNodeWithText("Continuar Ipsum").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
            screenshot("home-active-$suffix-$theme")
            assertCompleteText()
            compose.onNodeWithText("Ver último resultado").performScrollTo().assertIsDisplayed()
            assertCompleteText()
        }
        assertEquals(2, recoveries)
        assertEquals(2, continuations)
    }

    private fun assertCompleteText() {
        val nodes = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes()
        compose.runOnIdle {
            nodes.forEach { node ->
                val results = mutableListOf<TextLayoutResult>()
                node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
                results.forEach { layout ->
                    for (line in 0 until layout.lineCount) {
                        assertFalse("Ellipsized: ${layout.layoutInput.text}", layout.isLineEllipsized(line))
                        assertTrue("Clipped width: ${layout.layoutInput.text}",
                            layout.getLineRight(line) - layout.getLineLeft(line) <= layout.size.width + 1f)
                    }
                    assertTrue("Clipped height: ${layout.layoutInput.text}",
                        layout.getLineBottom(layout.lineCount - 1) <= layout.size.height + 1f)
                }
            }
        }
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(renderedView.width, renderedView.height, Bitmap.Config.ARGB_8888)
            renderedView.draw(Canvas(bitmap))
            val file = File("build/reports/visual/$name.png").apply { parentFile!!.mkdirs() }
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
