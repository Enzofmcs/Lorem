package dev.lorem.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.lorem.app.domain.model.LocalProfile
import dev.lorem.app.domain.model.ProblemHistory
import dev.lorem.app.domain.model.ProblemId
import dev.lorem.app.ui.screens.HomeScreen
import dev.lorem.app.ui.theme.LoremTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `restored synchronization displays persisted history instead of import prompt`() {
        composeRule.setContent {
            LoremTheme {
                HomeScreen(
                    profile = profile(lastSyncEpochMillis = 1_700_000_000_000L),
                    syncState = HistorySyncUiState.Idle,
                    problemHistory = listOf(
                        history(100L, "A", accepted = true),
                        history(101L, "B", accepted = false),
                    ),
                    onSynchronize = {},
                    onNavigate = {},
                )
            }
        }

        composeRule.onNodeWithText("Histórico salvo neste dispositivo.").assertIsDisplayed()
        composeRule.onNodeWithText("Problemas tentados: 2").assertIsDisplayed()
        composeRule.onNodeWithText("Problemas resolvidos: 1").assertIsDisplayed()
        composeRule.onNodeWithText("Sincronize para importar seu histórico do Codeforces.")
            .assertDoesNotExist()
    }

    @Test
    fun `profile never synchronized displays import prompt`() {
        composeRule.setContent {
            LoremTheme {
                HomeScreen(
                    profile = profile(lastSyncEpochMillis = 0L),
                    syncState = HistorySyncUiState.Idle,
                    problemHistory = emptyList(),
                    onSynchronize = {},
                    onNavigate = {},
                )
            }
        }

        composeRule.onNodeWithText("Sincronize para importar seu histórico do Codeforces.")
            .assertIsDisplayed()
    }

    private fun profile(lastSyncEpochMillis: Long) = LocalProfile(
        handle = "tourist",
        displayName = "Tourist",
        officialRating = 3800,
        loremRating = 3800,
        consolidatedRating = null,
        lastSyncEpochMillis = lastSyncEpochMillis,
    )

    private fun history(contestId: Long, index: String, accepted: Boolean) = ProblemHistory(
        problemId = ProblemId(contestId, index),
        attempted = true,
        hasAcceptedSubmission = accepted,
    )
}
