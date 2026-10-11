package dev.lorem.app.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import dev.lorem.app.StatisticsFixtures
import dev.lorem.app.domain.buildIpsumStatistics
import dev.lorem.app.ui.screens.StatisticsScreen
import dev.lorem.app.ui.theme.LoremTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StatisticsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `summary displays known totals samples and persisted rating evolution`() {
        setReady()
        see("Rating Lorem atual: 1202")
        see("Ipsums encerrados: 4")
        see("Taxa de AC no Ipsum: 50,0% (2/4)")
        see("AC sem dica: 33,3% (1/3)")
        see("AC com dica: 100,0% (1/1)")
        see("Tempo médio: 40,0 min (4 tentativa(s) com tempo)")
        see("Erros antes do AC: 3")
        see("Média de erros antes do AC: 1,5 (2 AC no Ipsum)")
        see("Pendentes atuais: 1")
        see("AC posterior: 1")
        see("1200 → 1224 (+24)")
        see("1222 → 1202 (-20)")
    }

    @Test
    fun `rating and tag sections display groups rankings and zero exposure`() {
        setReady()
        select("RATING")
        see("Rating 1200")
        see("Tempo médio: 37,5 min (2 tentativa(s) com tempo)")
        see("Rating 1400")
        see("Taxa de AC no Ipsum: 0,0% (0/2)")
        select("TAGS")
        see("dp: 3 tentativa(s)")
        see("greedy: 0 tentativa(s)")
        see("math: 100,0% (1/1)")
        see("dp: 66,7% (2/3)")
        see("Taxa de AC no Ipsum: 33,3% (1/3)")
        see("Taxa de AC no Ipsum: Sem dados (0/0)")
        select("SUMMARY")
        see("Ipsums encerrados: 4")
    }

    @Test
    fun `empty history loading no profile and incomplete rating have clear states`() {
        val state = mutableStateOf<StatisticsUiState>(StatisticsUiState.Loading)
        composeRule.setContent { LoremTheme { StatisticsScreen(state.value) } }
        composeRule.onNodeWithText("Carregando estatísticas…").assertIsDisplayed()
        composeRule.runOnIdle { state.value = StatisticsUiState.NoProfile }
        composeRule.onNodeWithText("Vincule um handle para visualizar suas estatísticas.").assertIsDisplayed()
        composeRule.runOnIdle {
            state.value = StatisticsUiState.Ready(buildIpsumStatistics(StatisticsFixtures.profile, emptyList()))
        }
        see("Nenhum Ipsum encerrado. Conclua um treino para ver seu desempenho.")
        see("Taxa de AC no Ipsum: Sem dados (0/0)")
        see("Sem avaliações de rating registradas.")
        composeRule.runOnIdle {
            state.value = StatisticsUiState.Ready(buildIpsumStatistics(StatisticsFixtures.profile,
                listOf(StatisticsFixtures.finished.first().copy(ratingDelta = null, finalLoremRating = null))))
        }
        see("1 encerramento(s) sem avaliação completa; não entram na evolução.")
    }

    @Test
    fun `switching accounts resets section and hides previous rating and history`() {
        val state = mutableStateOf<StatisticsUiState>(StatisticsUiState.Ready(
            buildIpsumStatistics(StatisticsFixtures.profile, StatisticsFixtures.finished)))
        composeRule.setContent { LoremTheme { StatisticsScreen(state.value) } }
        select("TAGS")
        composeRule.runOnIdle {
            state.value = StatisticsUiState.Ready(buildIpsumStatistics(
                StatisticsFixtures.profile.copy(handle = "other", loremRating = 800), StatisticsFixtures.finished))
        }
        see("Rating Lorem atual: 800")
        see("Ipsums encerrados: 0")
        assertTrue(composeRule.onAllNodesWithText("1200 → 1224 (+24)").fetchSemanticsNodes().isEmpty())
    }

    private fun setReady() {
        val stats = buildIpsumStatistics(StatisticsFixtures.profile, StatisticsFixtures.finished, StatisticsFixtures.catalog)
        composeRule.setContent { LoremTheme { StatisticsScreen(StatisticsUiState.Ready(stats)) } }
    }

    private fun select(section: String) {
        composeRule.onNodeWithTag("statistics-list").performScrollToNode(hasText("Resumo"))
        composeRule.onNodeWithTag("statistics-section-$section").performClick()
    }

    private fun see(text: String) {
        composeRule.onNodeWithTag("statistics-list").performScrollToNode(hasText(text))
        composeRule.onNodeWithText(text).performScrollTo().assertIsDisplayed()
    }
}
