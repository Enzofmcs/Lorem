package dev.lorem.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.lorem.app.domain.model.CodeforcesProblem
import dev.lorem.app.domain.model.Ipsum
import dev.lorem.app.domain.model.IpsumCategory
import dev.lorem.app.domain.model.IpsumStatus
import dev.lorem.app.domain.model.ProblemId
import dev.lorem.app.ui.screens.IpsumScreen
import dev.lorem.app.ui.theme.LoremTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IpsumScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `active screen hides rating tags and category`() {
        composeRule.setContent { LoremTheme { IpsumScreen(ipsum(), nowMillis = { 3_661_001L }) } }

        composeRule.onNodeWithText("Secret Problem").assertIsDisplayed()
        composeRule.onNodeWithText("O rating, as tags e a categoria ficam ocultos durante o Ipsum.").assertIsDisplayed()
        composeRule.onNodeWithText("1500", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("dynamic programming", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("CHALLENGE", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Problema 100A").assertIsDisplayed()
        composeRule.onNodeWithText("Abrir problema no Codeforces").assertIsDisplayed()
        composeRule.onNodeWithText("Tempo decorrido: 01:01:01").assertIsDisplayed()
    }

    @Test
    fun `hint confirmation reveals only tags and essential actions explain their boundary`() {
        var revealRequested = false
        composeRule.setContent {
            LoremTheme { IpsumScreen(ipsum(), onRevealHint = { revealRequested = true }, nowMillis = { 1L }) }
        }

        composeRule.onNodeWithText("dynamic programming", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Revelar tópicos").performClick()
        composeRule.onNodeWithText("O uso da dica será registrado e não poderá ser desfeito.").assertIsDisplayed()
        composeRule.onNodeWithText("Revelar").performClick()
        composeRule.waitForIdle()
        assertTrue(revealRequested)

        composeRule.onNodeWithText("Verificar submissões").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("A consulta real ao Codeforces será implementada na próxima etapa.").assertIsDisplayed()
        composeRule.onNodeWithText("Entendi").performClick()
        composeRule.onNodeWithText("Encerrar sem AC").performScrollTo().performClick()
        composeRule.onNodeWithText("Encerrar sem AC?").assertIsDisplayed()
    }

    @Test
    fun `persisted hint displays tags but keeps rating and category hidden`() {
        composeRule.setContent {
            LoremTheme { IpsumScreen(ipsum().copy(hintRevealedAtEpochMillis = 2L), nowMillis = { 2L }) }
        }

        composeRule.onNodeWithText("Tópicos: dynamic programming").assertIsDisplayed()
        composeRule.onNodeWithText("1500", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("CHALLENGE", substring = true).assertDoesNotExist()
    }

    private fun ipsum() = Ipsum(
        id = 1,
        ownerHandle = "tourist",
        problem = CodeforcesProblem(ProblemId(100, "A"), "Secret Problem", 1500, setOf("dynamic programming")),
        initialLoremRating = 1200,
        category = IpsumCategory.CHALLENGE,
        desiredRatingMin = 1300,
        desiredRatingMax = 1400,
        selectedRating = 1500,
        fallbackDistance = 100,
        startedAtEpochMillis = 1,
        status = IpsumStatus.ACTIVE,
    )
}
