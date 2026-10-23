package dev.lorem.app.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.mutableStateOf
import dev.lorem.app.domain.IpsumHistorySituation
import dev.lorem.app.domain.model.CodeforcesProblem
import dev.lorem.app.domain.model.Ipsum
import dev.lorem.app.domain.model.IpsumCategory
import dev.lorem.app.domain.model.IpsumStatus
import dev.lorem.app.domain.model.ProblemId
import dev.lorem.app.ui.screens.HistoryScreen
import dev.lorem.app.ui.theme.LoremTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HistoryScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `shows main fields filters and opens the Codeforces problem`() {
        var openedUrl: String? = null
        val solved = ipsum(1, "A", "Selected problem", IpsumStatus.COMPLETED, 1400, setOf("dp", "graphs"))
            .copy(hintRevealedAtEpochMillis = 2_000, errorCount = 3, ratingDelta = 18)
        val other = ipsum(2, "B", "Other problem", IpsumStatus.COMPLETED, 1500, setOf("math"))
        setContent(listOf(solved, other)) { openedUrl = it }

        see("Filtros avançados")
        composeRule.onNodeWithTag("history-advanced").performClick()
        composeRule.onNodeWithTag("rating-1400").performScrollTo().performClick()
        composeRule.onNodeWithTag("tag-dp").performScrollTo().performClick()
        composeRule.onAllNodesWithText("Other problem").assertCountEquals(0)
        listOf(
            "Selected problem", "Problema: 100A", "Rating do problema: 1400", "Tags: dp, graphs",
            "Tempo: 00:01:00", "Dica utilizada: sim", "Erros: 3", "Data do Ipsum:",
            "Avaliação original: AC no Ipsum", "Variação do Rating Lorem: +18",
        ).forEach { see(it, substring = true) }

        see("Abrir no Codeforces")
        composeRule.onNodeWithText("Abrir no Codeforces").performClick()
        assertEquals("https://codeforces.com/contest/100/problem/A", openedUrl)
    }

    @Test
    fun `changes tabs returns to prior tab and exposes empty states`() {
        setContent(listOf(
            ipsum(1, "A", "Solved", IpsumStatus.COMPLETED),
            ipsum(2, "B", "Pending", IpsumStatus.PENDING),
        ))

        composeRule.onNodeWithTag("history-list").performScrollToNode(hasTestTag("situation-${IpsumHistorySituation.PENDING.name}"))
        composeRule.onNodeWithTag("situation-${IpsumHistorySituation.PENDING.name}").performScrollTo().performClick()
        see("Pending")
        composeRule.onAllNodesWithText("Solved").assertCountEquals(0)

        composeRule.onNodeWithTag("history-list").performScrollToNode(hasTestTag("situation-${IpsumHistorySituation.SOLVED_OUTSIDE_IPSUM.name}"))
        composeRule.onNodeWithTag("situation-${IpsumHistorySituation.SOLVED_OUTSIDE_IPSUM.name}")
            .performScrollTo().performClick()
        see("Nenhum resultado para estes filtros.")

        composeRule.onNodeWithTag("history-list").performScrollToNode(hasTestTag("situation-${IpsumHistorySituation.PENDING.name}"))
        composeRule.onNodeWithTag("situation-${IpsumHistorySituation.PENDING.name}").performScrollTo().performClick()
        see("Pending")
        see("Limpar filtros")
        composeRule.onNodeWithText("Limpar filtros").performClick()
        see("Solved")
    }

    @Test
    fun `update action shows loading unchanged changed and recoverable error states`() {
        var clicks = 0
        val state = mutableStateOf<PendingUpdateUiState>(PendingUpdateUiState.Loading)
        composeRule.setContent {
            LoremTheme {
                HistoryScreen(emptyList(), updateState = state.value, onUpdatePending = { clicks++ })
            }
        }
        composeRule.onNodeWithTag("update-pending").assertIsNotEnabled()

        composeRule.runOnIdle { state.value = PendingUpdateUiState.Success(0) }
        composeRule.onNodeWithText("Nenhuma pendência foi alterada.").assertIsDisplayed()
        composeRule.onNodeWithTag("update-pending").performClick()
        assertEquals(1, clicks)

        composeRule.runOnIdle { state.value = PendingUpdateUiState.Success(2) }
        composeRule.onNodeWithText("2 pendência(s) movida(s).").assertIsDisplayed()
        composeRule.runOnIdle { state.value = PendingUpdateUiState.Error("Tente novamente.") }
        composeRule.onNodeWithText("Tente novamente.").assertIsDisplayed()
    }

    @Test
    fun `solved outside item shows persisted first AC date`() {
        val upsolved = ipsum(3, "C", "Upsolved", IpsumStatus.SOLVED_OUTSIDE)
            .copy(solvedOutsideAtEpochMillis = 120_000)
        setContent(listOf(upsolved))

        composeRule.onNodeWithTag("history-list").performScrollToNode(hasTestTag("situation-${IpsumHistorySituation.SOLVED_OUTSIDE_IPSUM.name}"))
        composeRule.onNodeWithTag("situation-${IpsumHistorySituation.SOLVED_OUTSIDE_IPSUM.name}")
            .performScrollTo().performClick()
        see("AC posterior:", substring = true)
        see("Avaliação original: encerrado sem AC")
        see("O AC posterior não altera os ratings nem a taxa original de AC.")
    }

    @Test fun `empty history differs from filtered results and active attempts never leak`() {
        val data = mutableStateOf(listOf(ipsum(1, "A", "Active secret", IpsumStatus.ACTIVE)))
        composeRule.setContent { LoremTheme { HistoryScreen(data.value) } }
        see("Seu histórico começa no primeiro Ipsum.")
        composeRule.onNodeWithText("Active secret", useUnmergedTree = true).assertDoesNotExist()
        composeRule.runOnIdle { data.value = listOf(ipsum(2, "B", "Pending", IpsumStatus.PENDING)) }
        see("Nenhum resultado para estes filtros.")
        composeRule.onNodeWithTag("history-list").performScrollToNode(hasTestTag("situation-PENDING"))
        composeRule.onNodeWithTag("situation-PENDING").performScrollTo().performClick()
        see("Pending")
    }

    @Test fun `filters survive collapsed panel and restoration then reset on profile switch`() {
        val owner = mutableStateOf("tourist")
        val attempts = listOf(ipsum(1, "A", "Solved", IpsumStatus.COMPLETED),
            ipsum(2, "B", "Other account", IpsumStatus.COMPLETED).copy(ownerHandle = "other"))
        val restorer = StateRestorationTester(composeRule)
        restorer.setContent { LoremTheme { HistoryScreen(attempts, ownerHandle = owner.value) } }
        composeRule.onNodeWithTag("history-advanced").performScrollTo().performClick()
        composeRule.onNodeWithTag("rating-1200").performScrollTo().performClick()
        composeRule.onNodeWithTag("tag-implementation").performScrollTo().performClick()
        composeRule.onNodeWithTag("history-advanced").performScrollTo().performClick()
        see("Filtros ativos: AC no Ipsum · rating 1200 · tag implementation")
        restorer.emulateSavedInstanceStateRestore()
        see("Filtros ativos: AC no Ipsum · rating 1200 · tag implementation")
        composeRule.onNodeWithTag("rating-1200").assertDoesNotExist()
        composeRule.runOnIdle { owner.value = "other" }
        see("Filtros ativos: AC no Ipsum")
        see("Other account")
        composeRule.onNodeWithText("Solved", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun `large history stays chronological and supports reaching oldest local attempt`() {
        setContent((1L..500L).map { id -> ipsum(id, id.toString(), "Attempt $id", IpsumStatus.COMPLETED)
            .copy(startedAtEpochMillis = id * 1000, endedAtEpochMillis = id * 1000 + 500) })
        see("Attempt 500")
        composeRule.onNodeWithTag("history-list").performScrollToIndex(500)
        composeRule.onNodeWithText("Attempt 1").performScrollTo().assertIsDisplayed()
    }

    private fun see(text: String, substring: Boolean = false) {
        composeRule.onNodeWithTag("history-list").performScrollToNode(hasText(text, substring = substring))
        composeRule.onNodeWithText(text, substring = substring).performScrollTo().assertIsDisplayed()
    }

    private fun setContent(ipsums: List<Ipsum>, onOpen: (String) -> Unit = {}) {
        composeRule.setContent { LoremTheme { HistoryScreen(ipsums, onOpen) } }
    }

    private fun ipsum(
        id: Long,
        index: String,
        name: String,
        status: IpsumStatus,
        rating: Int = 1200,
        tags: Set<String> = setOf("implementation"),
    ) = Ipsum(
        id, "tourist", CodeforcesProblem(ProblemId(100, index), name, rating, tags),
        1200, IpsumCategory.CURRENT_LEVEL, rating, rating, rating, 0,
        startedAtEpochMillis = 1_000, endedAtEpochMillis = 61_000,
        status = status,
    )
}
