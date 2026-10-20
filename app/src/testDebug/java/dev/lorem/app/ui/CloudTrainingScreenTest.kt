package dev.lorem.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.lorem.app.domain.cloud.*
import dev.lorem.app.domain.*
import dev.lorem.app.domain.model.*
import dev.lorem.app.ui.cloud.*
import dev.lorem.app.ui.theme.LoremTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CloudTrainingScreenTest {
    @get:Rule val compose = createComposeRule()
    private val active = CloudIpsum("id", "tourist", 0, CloudIpsumState.ACTIVE,
        Ipsum(1, "Tourist", CodeforcesProblem(ProblemId(2000, "A"), "Private training", 1200, setOf("math", "dp")),
            923, IpsumCategory.CHALLENGE, 1100, 1200, 1200, 0, 1000, status = IpsumStatus.ACTIVE,
            scoringSnapshot = ScoringSnapshot(TIME_CURVE_POLICY, TimeLimits(1800000, 3600000, 7200000),
                modelVersion = "synthetic-v1", artifactSha256 = "a".repeat(64), inputRating = 1200, unavailableReason = null)))
    private fun state(attempt: CloudIpsum? = active) = CloudTrainingUiState(CloudSnapshot(
        listOf(CloudHandle("tourist", "Tourist", 923, null, 0)), attempt, listOfNotNull(attempt), true), "tourist")
    @Test fun `active hides rating category and tags until confirmed hint and expiry removes training controls`() {
        val time = mutableStateOf<Long?>(1000)
        var hints = 0
        compose.setContent { LoremTheme {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                CloudTrainingScreen(state(), time.value, {}, {}, {}, { hints++ }, {}, {}, {})
            }
        } }
        compose.onNodeWithText("Rating Lorem: 923").assertDoesNotExist()
        compose.onNodeWithText("1200", substring = true).assertDoesNotExist()
        compose.onNodeWithText("CHALLENGE", substring = true).assertDoesNotExist()
        compose.onNodeWithText("math", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Ganho base", substring = true).assertDoesNotExist()
        compose.onNodeWithText("time-curve-v1", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Revelar dica").performScrollTo().performClick()
        assertEquals(0, hints)
        compose.onNodeWithText("Revelar", useUnmergedTree = true).performClick()
        assertEquals(1, hints)
        compose.runOnIdle { time.value = active.expiresAtMillis }
        compose.onNodeWithText("Revelar dica").assertDoesNotExist()
        compose.onNodeWithText("Abrir problema no Codeforces").assertDoesNotExist()
        compose.onNodeWithText("Treino fechado · aguardando verificação").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Iniciar Ipsum · até 2h").assertDoesNotExist()
    }
    @Test fun `offline without trusted time cannot continue training or start another attempt`() {
        compose.setContent { LoremTheme {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                CloudTrainingScreen(state().let { it.copy(snapshot = it.snapshot!!.copy(online = false)) }, null, {}, {}, {}, {}, {}, {}, {})
            }
        } }
        compose.onNodeWithText("Treino fechado · aguardando verificação").assertExists()
        compose.onNodeWithText("Revelar dica").assertDoesNotExist()
        compose.onNodeWithText("Adicionar handle").assertDoesNotExist()
        compose.onNodeWithText("Iniciar Ipsum · até 2h").assertDoesNotExist()
    }
    @Test fun `finished AC with zero gain explains saved base time band and hint`() {
        val gain = applyTimeScoring(24, 7200000, TimeLimits(1800000, 3600000, 7200000), 1.25, .5, true, TIME_CURVE_POLICY)
        val final = active.copy(state = CloudIpsumState.FINAL, ipsum = active.ipsum.copy(status = IpsumStatus.COMPLETED,
            endedAtEpochMillis = active.expiresAtMillis, ratingDelta = 0, finalLoremRating = 923, timeGain = gain))
        compose.setContent { LoremTheme {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                CloudTrainingScreen(state(null).let { it.copy(snapshot = it.snapshot!!.copy(attempts = listOf(final))) },
                    active.expiresAtMillis + 1000, {}, {}, {}, {}, {}, {}, {})
            }
        } }
        compose.onNodeWithText("Rating 923 → 923 (0)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Sem ganho positivo. Ganho base: 24", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("ajuste por tempo: -24", substring = true).assertExists()
        compose.onNodeWithText("ganho final: 0", substring = true).assertExists()
    }
    @Test fun `finished timeout exposes original result and upsolve action without changing original status`() {
        val final = active.copy(state = CloudIpsumState.FINAL, cutoffMillis = active.expiresAtMillis,
            closeReason = CloudCloseReason.TIME_LIMIT_EXCEEDED, ipsum = active.ipsum.copy(status = IpsumStatus.PENDING,
                endedAtEpochMillis = active.expiresAtMillis, failureReason = IpsumFailureReason.TIME_EXPIRED,
                ratingDelta = -20, finalLoremRating = 903))
        var upsolves = 0
        compose.setContent { LoremTheme {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                CloudTrainingScreen(state(null).let { it.copy(snapshot = it.snapshot!!.copy(attempts = listOf(final))) },
                    active.expiresAtMillis + 1000, {}, {}, {}, {}, {}, {}, { upsolves++ })
            }
        } }
        compose.onNodeWithText("Faltou tempo.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Rating 923 → 903 (-20)").assertExists()
        compose.onNodeWithText("Verificar AC posterior").performScrollTo().performClick()
        assertEquals(1, upsolves)
    }
}
