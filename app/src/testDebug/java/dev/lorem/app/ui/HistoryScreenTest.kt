package dev.lorem.app.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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

        composeRule.onNodeWithTag("rating-1400").performScrollTo().performClick()
        composeRule.onNodeWithTag("tag-dp").performScrollTo().performClick()
        composeRule.onAllNodesWithText("Other problem").assertCountEquals(0)
        listOf(
            "Selected problem", "Problema: 100A", "Rating: 1400", "Tags: dp, graphs",
            "Tempo: 00:01:00", "Dica utilizada: sim", "Erros: 3", "Data do Ipsum:",
            "Resultado atual: Resolvidos em Ipsums", "Variação do Rating Lorem: +18",
        ).forEach { composeRule.onNodeWithText(it, substring = true).performScrollTo().assertIsDisplayed() }

        composeRule.onNodeWithText("Abrir no Codeforces").performScrollTo().performClick()
        assertEquals("https://codeforces.com/contest/100/problem/A", openedUrl)
    }

    @Test
    fun `changes tabs returns to prior tab and exposes empty states`() {
        setContent(listOf(
            ipsum(1, "A", "Solved", IpsumStatus.COMPLETED),
            ipsum(2, "B", "Pending", IpsumStatus.PENDING),
        ))

        composeRule.onNodeWithTag("situation-${IpsumHistorySituation.PENDING.name}").performScrollTo().performClick()
        composeRule.onNodeWithText("Pending").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("Solved").assertCountEquals(0)

        composeRule.onNodeWithTag("situation-${IpsumHistorySituation.SOLVED_OUTSIDE_IPSUM.name}")
            .performScrollTo().performClick()
        composeRule.onNodeWithText("Nenhum item nesta categoria com os filtros atuais.")
            .performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("situation-${IpsumHistorySituation.PENDING.name}").performScrollTo().performClick()
        composeRule.onNodeWithText("Pending").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Limpar filtros").performScrollTo().performClick()
        composeRule.onNodeWithText("Solved").performScrollTo().assertIsDisplayed()
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
