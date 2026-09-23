package dev.lorem.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.lorem.app.domain.model.CodeforcesProblem
import dev.lorem.app.domain.model.Ipsum
import dev.lorem.app.domain.model.IpsumCategory
import dev.lorem.app.domain.model.IpsumStatus
import dev.lorem.app.domain.model.ProblemId
import dev.lorem.app.ui.screens.IpsumScreen
import dev.lorem.app.ui.theme.LoremTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IpsumScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `active screen hides rating tags and category`() {
        composeRule.setContent { LoremTheme { IpsumScreen(ipsum()) } }

        composeRule.onNodeWithText("Secret Problem").assertIsDisplayed()
        composeRule.onNodeWithText("O rating, as tags e a categoria ficam ocultos durante o Ipsum.").assertIsDisplayed()
        composeRule.onNodeWithText("1500", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("dynamic programming", substring = true).assertDoesNotExist()
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
