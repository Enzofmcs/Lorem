package dev.lorem.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.lorem.app.StatisticsFixtures
import dev.lorem.app.domain.buildIpsumStatistics
import dev.lorem.app.domain.model.IpsumStatus
import dev.lorem.app.ui.components.LoremScaffold
import dev.lorem.app.ui.navigation.LoremDestination
import dev.lorem.app.ui.screens.HistoryScreen
import dev.lorem.app.ui.screens.StatisticsScreen
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
class HistoryStatisticsLayoutTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var renderedView: View

    @Test fun `history and statistics stay readable at 320dp font200 in both themes`() = verify(2f, "320-font200")
    @Test @Config(qualifiers = "w640dp-h360dp-land")
    fun `history and statistics stay readable in landscape in both themes`() = verify(1f, "landscape")
    @Test @Config(qualifiers = "w360dp-h800dp")
    fun `history and statistics render standard phone in both themes`() = verify(1f, "360")

    private fun verify(fontScale: Float, suffix: String) {
        val dark = mutableStateOf(false)
        val statistics = mutableStateOf(false)
        val longName = "Um nome longo de problema para revisar o treino sem perder informação nem cortar palavras"
        val longTag = "expression parsing and very long official topic label"
        val attempts = StatisticsFixtures.finished.map { it.copy(problem = it.problem.copy(name = longName, tags = setOf(longTag, "dp"))) }
        val stats = buildIpsumStatistics(StatisticsFixtures.profile, attempts, StatisticsFixtures.catalog)
        compose.setContent {
            renderedView = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                LoremTheme(darkTheme = dark.value) {
                    LoremScaffold(if (statistics.value) LoremDestination.Statistics else LoremDestination.History,
                        true, false, {}, {}, {}) {
                        if (statistics.value) StatisticsScreen(StatisticsUiState.Ready(stats))
                        else HistoryScreen(attempts, updateState = PendingUpdateUiState.Error("Sem conexão. Os dados salvos continuam disponíveis offline."))
                    }
                }
            }
        }
        for (isDark in listOf(false, true)) {
            compose.runOnIdle { dark.value = isDark; statistics.value = false }
            val theme = if (isDark) "dark" else "light"
            history("Situação atual")
            compose.onNodeWithTag("situation-SOLVED_OUTSIDE_IPSUM").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
            history("Filtros avançados")
            compose.onNodeWithTag("history-advanced").performClick()
            history(longTag)
            compose.onNodeWithTag("tag-$longTag").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
            completeText()
            history("Limpar filtros")
            screenshot("history-filters-$suffix-$theme")
            history("Avaliação original: encerrado sem AC")
            completeText()
            screenshot("history-upsolve-$suffix-$theme")
            history("O AC posterior não altera o rating nem a taxa original de AC.")
            completeText()
            history("Abrir no Codeforces")
            compose.onNodeWithText("Abrir no Codeforces").assertHeightIsAtLeast(48.dp)
            completeText()
            compose.runOnIdle { statistics.value = true }
            seeStats("Taxa de AC no Ipsum: 50,0% (2/4)")
            screenshot("statistics-summary-$suffix-$theme")
            completeText()
            seeStats("4 avaliação(ões) registrada(s) · rating de 1202 a 1242")
            screenshot("statistics-curve-$suffix-$theme")
            completeText()
            seeStats("1222 → 1202 (-20)")
            completeText()
            seeStats("Resumo")
            compose.onNodeWithTag("statistics-section-TAGS").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
            seeStats("greedy: 0 tentativa(s)")
            completeText()
            seeStats("Taxa de AC no Ipsum: Sem amostra (0/0)")
            screenshot("statistics-zero-sample-$suffix-$theme")
            completeText()
        }
    }

    private fun history(text: String) {
        compose.onNodeWithTag("history-list").performScrollToNode(hasText(text))
        compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()
    }
    private fun seeStats(text: String) {
        compose.onNodeWithTag("statistics-list").performScrollToNode(hasText(text))
        compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()
    }
    private fun completeText() {
        val nodes = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true).fetchSemanticsNodes()
        compose.runOnIdle {
            nodes.forEach { node ->
                val results = mutableListOf<TextLayoutResult>()
                node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
                results.forEach { layout ->
                    for (line in 0 until layout.lineCount) {
                        assertFalse("Ellipsized: ${layout.layoutInput.text}", layout.isLineEllipsized(line))
                        assertTrue("Clipped width: ${layout.layoutInput.text}", layout.getLineRight(line) - layout.getLineLeft(line) <= layout.size.width + 1f)
                    }
                    assertTrue("Clipped height: ${layout.layoutInput.text}", layout.getLineBottom(layout.lineCount - 1) <= layout.size.height + 1f)
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
