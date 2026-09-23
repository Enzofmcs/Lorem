package dev.lorem.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import dev.lorem.app.domain.model.CodeforcesProblem
import dev.lorem.app.domain.model.Ipsum
import dev.lorem.app.domain.model.IpsumCategory
import dev.lorem.app.domain.model.IpsumResult
import dev.lorem.app.domain.model.IpsumStatus
import dev.lorem.app.domain.model.ProblemId
import dev.lorem.app.ui.screens.IpsumResultScreen
import dev.lorem.app.ui.theme.LoremTheme
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
            errorCount = 2, status = IpsumStatus.COMPLETED,
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
            "Tempo total: 01:01:01", "Dica usada: sim", "Erros antes do primeiro AC: 2",
            "WRONG_ANSWER (1), COMPILATION_ERROR (1)", "Alteração do Rating Lorem",
        ).forEach { composeRule.onNodeWithText(it, substring = true).performScrollTo().assertIsDisplayed() }
    }
}
