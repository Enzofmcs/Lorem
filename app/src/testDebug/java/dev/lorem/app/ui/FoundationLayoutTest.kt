package dev.lorem.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.lorem.app.domain.model.ThemePreference
import dev.lorem.app.ui.components.*
import dev.lorem.app.ui.navigation.LoremDestination
import dev.lorem.app.ui.screens.ConfigurationScreen
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
class FoundationLayoutTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var renderedView: View

    @Test fun `320dp at 200 percent exposes complete tab labels and scrollable settings in both themes`() {
        val dark = mutableStateOf(false)
        val destination = mutableStateOf(LoremDestination.Home)
        val selection = mutableStateOf(ThemePreference.SYSTEM)
        compose.setContent {
            renderedView = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                LoremTheme(darkTheme = dark.value) {
                    LoremScaffold(destination.value, destination.value == LoremDestination.Home,
                        destination.value == LoremDestination.Configuration,
                        { destination.value = LoremDestination.Home },
                        { destination.value = LoremDestination.Configuration }, { destination.value = it }) {
                        if (destination.value == LoremDestination.Configuration) {
                            ConfigurationScreen(ConfigurationUiState(), null, {}, {},
                                themePreference = selection.value, onThemeChange = { selection.value = it })
                        } else Gallery()
                    }
                }
            }
        }
        for (isDark in listOf(false, true)) {
            compose.runOnIdle { dark.value = isDark }
            for (route in listOf("home", "history", "statistics")) {
                compose.onNodeWithTag("tab-$route").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
                compose.onNodeWithTag("tab-$route").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            }
            screenshot("foundation-320-font200-${if (isDark) "dark" else "light"}")
            assertNoTextOverflow()
            compose.onNodeWithContentDescription("Configurações").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
            compose.onNodeWithTag("theme-DARK").performScrollTo().assertHeightIsAtLeast(48.dp).performClick().assertIsSelected()
            assertNoTextOverflow()
            screenshot("settings-320-font200-${if (isDark) "dark" else "light"}")
            assertNoTextOverflow()
            compose.onNodeWithContentDescription("Voltar").performClick()
        }
    }

    @Test
    @Config(qualifiers = "w640dp-h360dp-land")
    fun `landscape keeps navigation and primary action reachable with system insets`() {
        compose.setContent {
            renderedView = LocalView.current
            LoremTheme(darkTheme = true) {
            LoremScaffold(LoremDestination.Home, true, false, {}, {}, {}) { Gallery() }
        } }
        compose.runOnIdle {
            ViewCompat.dispatchApplyWindowInsets(renderedView, WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 24, 0, 24))
                .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(24, 0, 0, 0)).build())
        }
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        val settings = compose.onNodeWithContentDescription("Configurações").getUnclippedBoundsInRoot()
        val tab = compose.onNodeWithTag("tab-statistics").getUnclippedBoundsInRoot()
        assertTrue("Status bar inset", settings.top >= root.top + 24.dp)
        assertTrue("Navigation bar inset", tab.bottom <= root.bottom - 24.dp)
        compose.onNodeWithText("Iniciar novo Ipsum").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("tab-statistics").assertIsDisplayed()
        screenshot("foundation-landscape-dark")
        assertNoTextOverflow()
    }

    @Test fun `theme switch updates rendered colors without resetting a running composition`() {
        val preference = mutableStateOf(ThemePreference.LIGHT)
        var compositionIdentity: Any? = null
        compose.setContent {
            renderedView = LocalView.current
            LoremTheme(preference.value) {
                compositionIdentity = remember { Any() }
                LoremScaffold(LoremDestination.Home, true, false, {}, {}, {}) { Gallery() }
            }
        }
        compose.waitForIdle()
        val original = compositionIdentity
        val light = render().getPixel(0, 100)
        screenshot("foundation-light")
        compose.runOnIdle { preference.value = ThemePreference.DARK }
        val dark = render().getPixel(0, 100)
        screenshot("foundation-dark")
        assertNotEquals(light, dark)
        assertSame(original, compositionIdentity)
    }

    @Composable private fun Gallery() {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            LoremMetricCard("Rating Lorem", "1.200")
            LoremPrimaryAction("Iniciar novo Ipsum", {})
            LoremSyncStatus("Dados atualizados", LoremTone.SUCCESS, "Histórico pronto para treinar.")
            LoremBadge("Resolvido no Ipsum", LoremTone.SUCCESS)
            LoremSyncStatus("Aguardando conexão", LoremTone.ATTENTION)
            LoremEmptyState("Nenhum treino encerrado", "Conclua um Ipsum para ver seu progresso.")
            LoremErrorState("Não foi possível atualizar. Tente novamente.")
        }
    }
    private fun assertNoTextOverflow() {
        val nodes = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes()
        compose.runOnIdle {
            nodes.forEach { node ->
                val results = mutableListOf<TextLayoutResult>()
                node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
                results.forEach { layout ->
                    // Native text metrics can exceed rounded layout sizes by a fraction of a pixel.
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
    private fun render(): Bitmap {
        compose.waitForIdle()
        lateinit var bitmap: Bitmap
        compose.runOnIdle {
            bitmap = Bitmap.createBitmap(renderedView.width, renderedView.height, Bitmap.Config.ARGB_8888)
            renderedView.draw(Canvas(bitmap))
        }
        return bitmap
    }
    private fun screenshot(name: String) {
        val file = File("build/reports/visual/$name.png").apply { parentFile!!.mkdirs() }
        file.outputStream().use { render().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
