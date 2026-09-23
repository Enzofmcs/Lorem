package dev.lorem.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import dev.lorem.app.domain.model.LocalProfile
import dev.lorem.app.domain.model.ProblemHistory
import dev.lorem.app.domain.model.ProblemId
import dev.lorem.app.ui.screens.HomeScreen
import dev.lorem.app.ui.theme.LoremTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `start new ipsum action is available`() {
        var started = false
        composeRule.setContent {
            LoremTheme {
                HomeScreen(
                    profile = profile(0),
                    syncState = HistorySyncUiState.Idle,
                    problemHistory = emptyList(),
                    onSynchronize = {},
                    problemCatalogCount = 1,
                    catalogUpdatedAtEpochMillis = 1,
                    catalogSyncState = CatalogSyncUiState.Idle,
                    onSynchronizeCatalog = {},
                    onStartIpsum = { started = true },
                    onNavigate = {},
                )
            }
        }

        composeRule.onNodeWithText("Iniciar novo Ipsum").performClick()
        assertTrue(started)
    }

    @Test
    fun `saved catalog remains visible when refresh is offline`() {
        composeRule.setContent {
            LoremTheme {
                HomeScreen(
                    profile = profile(lastSyncEpochMillis = 0L),
                    syncState = HistorySyncUiState.Idle,
                    problemHistory = emptyList(),
                    onSynchronize = {},
                    onNavigate = {},
                    problemCatalogCount = 42,
                    catalogUpdatedAtEpochMillis = 1_700_000_000_000L,
                    catalogSyncState = CatalogSyncUiState.Error(
                        "Sem conexão. O catálogo salvo continua disponível.",
                        42,
                    ),
                    onSynchronizeCatalog = {},
                )
            }
        }

        composeRule.onNodeWithText("42 problemas com rating disponíveis localmente.")
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(
            "Sem conexão. O catálogo salvo continua disponível. Usando 42 problemas salvos.",
        ).performScrollTo().assertIsDisplayed()
    }

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
                    problemCatalogCount = 0,
                    catalogUpdatedAtEpochMillis = null,
                    catalogSyncState = CatalogSyncUiState.Idle,
                    onSynchronizeCatalog = {},
                )
            }
        }

        composeRule.onNodeWithText("Histórico salvo neste dispositivo.").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Problemas tentados: 2").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Problemas resolvidos: 1").performScrollTo().assertIsDisplayed()
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
                    problemCatalogCount = 0,
                    catalogUpdatedAtEpochMillis = null,
                    catalogSyncState = CatalogSyncUiState.Idle,
                    onSynchronizeCatalog = {},
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
