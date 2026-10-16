package dev.lorem.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.inspector.WindowInspector
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.lorem.app.domain.model.*
import dev.lorem.app.domain.sync.*
import dev.lorem.app.ui.components.LoremScaffold
import dev.lorem.app.ui.navigation.LoremDestination
import dev.lorem.app.ui.screens.IpsumScreen
import dev.lorem.app.ui.screens.IpsumResultScreen
import dev.lorem.app.ui.theme.LoremTheme
import org.robolectric.RuntimeEnvironment
import org.junit.After
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
class IpsumLayoutTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var renderedView: View
    private val active = Ipsum(1, "tourist", CodeforcesProblem(ProblemId(123, "A"),
        "Um problema com nome comprido para conferir a leitura por completo", 1900,
        setOf("chinese remainder theorem", "data structures")), 1200, IpsumCategory.CHALLENGE,
        1300, 1400, 1900, 200, 1000, errorCount = 2, status = IpsumStatus.ACTIVE)

    @Test fun `320dp font200 keeps elapsed time confirmations and original results readable in both themes`() {
        verifyLayouts(2f, "320-font200")
    }
    @Test
    @Config(qualifiers = "w640dp-h360dp-land")
    fun `landscape keeps confirmation choices and result actions reachable in both themes`() {
        verifyLayouts(1f, "landscape")
    }
    @Test
    @Config(qualifiers = "w360dp-h800dp")
    fun `standard phone renders training AC and upsolve results in both themes`() {
        verifyLayouts(1f, "360")
    }

    @After fun resetFontScale() { RuntimeEnvironment.setFontScale(1f) }

    private fun verifyLayouts(scale: Float, suffix: String) {
        // Dialogs have a separate Android window and resolve density from Resources.
        RuntimeEnvironment.setFontScale(scale)
        val dark = mutableStateOf(false)
        val screen = mutableStateOf(0)
        val hint = mutableStateOf(false)
        var reveals = 0
        var returns = 0
        compose.setContent {
            renderedView = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) {
                LoremTheme(darkTheme = dark.value) {
                    LoremScaffold(if (screen.value == 0) LoremDestination.Ipsum else LoremDestination.Result,
                        false, true, {}, {}, {}) {
                        if (screen.value == 0) IpsumScreen(active.copy(hintRevealedAtEpochMillis = if (hint.value) 2000 else null),
                            nowMillis = { 445_501_000L }, onRevealHint = { reveals++; hint.value = true },
                            trackingState = IpsumTrackingUiState(ProfileLink.of("tourist", 1), 1, null,
                                SynchronizationState(lastSuccessEpochMillis = 1_700_000_000_000,
                                    failure = "offline", failureKind = SyncFailureKind.NETWORK)))
                        else IpsumResultScreen(IpsumResultUiState.Ready(IpsumResult(active.copy(
                            status = if (screen.value == 1) IpsumStatus.COMPLETED else IpsumStatus.SOLVED_OUTSIDE,
                            endedAtEpochMillis = 445_501_000L, hintRevealedAtEpochMillis = 2000,
                            ratingDelta = if (screen.value == 1) 24 else -20,
                            finalLoremRating = if (screen.value == 1) 1224 else 1180,
                            expectedTimeMillis = 5_400_000,
                            failureReason = if (screen.value == 1) null else IpsumFailureReason.IMPLEMENTATION_NOT_COMPLETED,
                            solvedOutsideAtEpochMillis = if (screen.value == 2) 1_700_000_000_000 else null),
                            listOf("WRONG_ANSWER", "COMPILATION_ERROR"))), { returns++ })
                    }
                }
            }
        }
        for (isDark in listOf(false, true)) {
            compose.runOnIdle { dark.value = isDark; screen.value = 0; hint.value = false }
            val theme = if (isDark) "dark" else "light"
            compose.onNodeWithTag("elapsed-timer").performScrollTo().assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag("elapsed-value", useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals("123:45:00", layouts.single().layoutInput.text.text)
            assertEquals(1, layouts.single().lineCount)
            assertTrue("Timer must fit its actual viewport", layouts.single().multiParagraph.intrinsics.maxIntrinsicWidth <= layouts.single().layoutInput.constraints.maxWidth)
            assertEquals("tnum", layouts.single().layoutInput.style.fontFeatureSettings)
            assertCompleteText()
            screenshot("ipsum-$suffix-$theme")
            compose.onNodeWithText("Abrir no Codeforces").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            compose.onNodeWithText("Última consulta concluída:", substring = true).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Tentar novamente").performScrollTo().assertIsEnabled()
            compose.onNodeWithText("Encerrar sem AC").performScrollTo().performClick()
            compose.onNodeWithText("Encerrar").assertIsNotEnabled()
            val dialogLayouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText("Encerrar sem AC?", useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(dialogLayouts) }
            assertEquals(scale, dialogLayouts.single().layoutInput.density.fontScale, 0.01f)
            compose.onNodeWithText(IpsumFailureReason.TIME_EXPIRED.label).performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
            compose.onNodeWithText("Encerrar").assertIsEnabled().assertHeightIsAtLeast(48.dp)
            assertCompleteText()
            screenshot("end-confirmation-$suffix-$theme")
            compose.onNodeWithText("Cancelar").performClick()
            compose.onNodeWithText("Revelar tópicos").performScrollTo().performClick()
            compose.onNodeWithText("Revelar").assertHeightIsAtLeast(48.dp)
            assertCompleteText()
            screenshot("hint-confirmation-$suffix-$theme")
            compose.onNodeWithText("Revelar").performClick()
            compose.onNodeWithText("Tópicos: chinese remainder theorem, data structures").performScrollTo().assertIsDisplayed()
            assertCompleteText()
            for (result in listOf(1, 2)) {
                compose.runOnIdle { screen.value = result }
                compose.onNodeWithText(if (result == 1) "AC no Ipsum" else "Encerrado sem AC").performScrollTo().assertIsDisplayed()
                compose.onNodeWithText(if (result == 1) "1200 → 1224" else "1200 → 1180").performScrollTo().assertIsDisplayed()
                screenshot("result-$result-$suffix-$theme")
                assertCompleteText()
                compose.onNodeWithText("Voltar ao início").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
                compose.onNodeWithText("Abrir no Codeforces").performScrollTo().assertHeightIsAtLeast(48.dp)
                compose.onNodeWithText("Tópicos:", substring = true).performScrollTo().assertIsDisplayed()
                assertCompleteText()
            }
        }
        assertEquals(2, reveals)
        assertEquals(4, returns)
    }

    private fun assertCompleteText() {
        val nodes = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true).fetchSemanticsNodes()
        compose.runOnIdle {
            nodes.forEach { node ->
                val layouts = mutableListOf<TextLayoutResult>()
                node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
                layouts.forEach { layout ->
                    for (line in 0 until layout.lineCount) {
                        assertFalse("Ellipsized: ${layout.layoutInput.text}", layout.isLineEllipsized(line))
                        assertTrue("Clipped width: ${layout.layoutInput.text}",
                            layout.getLineRight(line) - layout.getLineLeft(line) <= layout.size.width + 1f)
                    }
                    assertTrue("Clipped height: ${layout.layoutInput.text}", layout.getLineBottom(layout.lineCount - 1) <= layout.size.height + 1f)
                }
            }
        }
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        compose.runOnIdle {
            val view = WindowInspector.getGlobalWindowViews().lastOrNull { it.isShown && it.width > 0 } ?: renderedView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File("build/reports/visual/$name.png").apply { parentFile!!.mkdirs() }
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
