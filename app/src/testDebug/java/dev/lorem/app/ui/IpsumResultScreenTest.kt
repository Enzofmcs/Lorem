package dev.lorem.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import dev.lorem.app.domain.model.CodeforcesProblem
import dev.lorem.app.domain.model.Ipsum
import dev.lorem.app.domain.model.IpsumCategory
import dev.lorem.app.domain.model.IpsumFailureReason
import dev.lorem.app.domain.model.IpsumResult
import dev.lorem.app.domain.model.IpsumStatus
import dev.lorem.app.domain.model.ProblemId
import dev.lorem.app.ui.screens.IpsumResultScreen
import dev.lorem.app.ui.theme.LoremTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.performClick
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IpsumResultScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `result reveals stable timing errors hint and problem information`() {
        val ipsum = Ipsum(
            id = 1, ownerHandle = "tourist",
            problem = CodeforcesProblem(ProblemId(100, "A"), "Secret", 1500, setOf("dp", "graphs")),
            initialLoremRating = 1200, category = IpsumCategory.CHALLENGE,
            desiredRatingMin = 1300, desiredRatingMax = 1400, selectedRating = 1500,
            fallbackDistance = 100, startedAtEpochMillis = 1_000,
            hintRevealedAtEpochMillis = 2_000, endedAtEpochMillis = 3_662_000,
            errorCount = 2, ratingDelta = 24, finalLoremRating = 1224,
            expectedTimeMillis = 90 * 60_000L, status = IpsumStatus.COMPLETED,
        )
        composeRule.setContent {
            LoremTheme {
                IpsumResultScreen(IpsumResultUiState.Ready(IpsumResult(
                    ipsum,
                    listOf("WRONG_ANSWER", "COMPILATION_ERROR"),
                ))) {}
            }
        }

        listOf(
            "Rating do problema: 1500", "Categoria: Desafio", "Tópicos: dp, graphs",
            "Tempo total: 01:01:01", "Dica usada: sim", "Erros definitivos: 2",
            "WRONG_ANSWER (1), COMPILATION_ERROR (1)", "Alteração do Rating Lorem",
            "1200 → 1224", "mais difícil que seu nível anterior",
            "tópicos foram revelados", "Tempo esperado: 01:30:00",
        ).forEach { composeRule.onNodeWithText(it, substring = true).performScrollTo().assertIsDisplayed() }
    }

    @Test
    fun `legacy final result without end time shows recoverable state instead of crashing`() {
        val ipsum = Ipsum(
            id = 2, ownerHandle = "tourist",
            problem = CodeforcesProblem(ProblemId(101, "B"), "Legacy", 1200, setOf("math")),
            initialLoremRating = 1200, category = IpsumCategory.CURRENT_LEVEL,
            desiredRatingMin = 1200, desiredRatingMax = 1200, selectedRating = 1200,
            fallbackDistance = 0, startedAtEpochMillis = 1_000,
            endedAtEpochMillis = null, status = IpsumStatus.COMPLETED,
        )

        composeRule.setContent {
            LoremTheme {
                IpsumResultScreen(IpsumResultUiState.Ready(IpsumResult(ipsum, emptyList()))) {}
            }
        }

        composeRule.onNodeWithText("Tempo total indisponível").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Voltar ao início").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `pending result reveals failure reason and empty attempt details`() {
        val ipsum = Ipsum(
            id = 3, ownerHandle = "tourist",
            problem = CodeforcesProblem(ProblemId(102, "C"), "Pending", 1300, setOf("greedy")),
            initialLoremRating = 1200, category = IpsumCategory.CURRENT_LEVEL,
            desiredRatingMin = 1200, desiredRatingMax = 1300, selectedRating = 1300,
            fallbackDistance = 0, startedAtEpochMillis = 1_000,
            endedAtEpochMillis = 61_000, errorCount = 0,
            failureReason = IpsumFailureReason.CONTENT_UNKNOWN,
            status = IpsumStatus.PENDING,
        )

        composeRule.setContent {
            LoremTheme {
                IpsumResultScreen(IpsumResultUiState.Ready(IpsumResult(ipsum, emptyList()))) {}
            }
        }

        listOf(
            "Encerrado sem AC",
            "Tempo total: 00:01:00",
            "Dica usada: não",
            "Erros definitivos: 0",
            "Tipos de erro: nenhum",
            "Motivo: Não conhecia o conteúdo.",
        ).forEach { text ->
            composeRule.onNodeWithText(text).performScrollTo().assertIsDisplayed()
        }
    }
    @Test
    fun `upsolve keeps the original failure assessment and provides problem and home actions`() {
        val attempt = dev.lorem.app.StatisticsFixtures.attempt(50, IpsumStatus.SOLVED_OUTSIDE).copy(
            initialLoremRating = 1200, ratingDelta = -20, finalLoremRating = 1180,
            solvedOutsideAtEpochMillis = 1_700_000_000_000, failureReason = IpsumFailureReason.LOGIC_NOT_FOUND)
        var opened: String? = null
        var closed = 0
        composeRule.setContent { CompositionLocalProvider(LocalUriHandler provides object : UriHandler {
            override fun openUri(uri: String) { opened = uri }
        }) { LoremTheme { IpsumResultScreen(IpsumResultUiState.Ready(IpsumResult(attempt, emptyList())), { closed++ }) } } }
        composeRule.onNodeWithText("Encerrado sem AC").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("AC no Ipsum").assertDoesNotExist()
        composeRule.onNodeWithText("Resolvido depois do Ipsum").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("AC posterior:", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("1200 → 1180").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Variação: −20 pontos").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Sem AC, a tentativa aplica a perda completa; desistir cedo não reduz essa perda.").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Abrir no Codeforces").performScrollTo().performClick()
        assertEquals("https://codeforces.com/contest/100/problem/50", opened)
        composeRule.onNodeWithText("Voltar ao início").performScrollTo().performClick()
        assertEquals(1, closed)
        assertEquals(-20, attempt.ratingDelta)
        assertEquals(1180, attempt.finalLoremRating)
    }

    @Test
    fun `active payload cannot reveal protected data through the result route`() {
        val active = dev.lorem.app.StatisticsFixtures.attempt(51, IpsumStatus.ACTIVE, rating = 1900, tags = setOf("dp"))
        composeRule.setContent { LoremTheme { IpsumResultScreen(IpsumResultUiState.Ready(IpsumResult(active, emptyList())), {}) } }
        composeRule.onNodeWithText("Este Ipsum ainda está ativo.", substring = true).assertIsDisplayed()
        for (unmerged in listOf(false, true)) {
            composeRule.onNodeWithText("1900", substring = true, useUnmergedTree = unmerged).assertDoesNotExist()
            composeRule.onNodeWithText("Tópicos:", substring = true, useUnmergedTree = unmerged).assertDoesNotExist()
            composeRule.onNodeWithText("Categoria:", substring = true, useUnmergedTree = unmerged).assertDoesNotExist()
        }
    }

}
