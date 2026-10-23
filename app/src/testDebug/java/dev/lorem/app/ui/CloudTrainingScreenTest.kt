package dev.lorem.app.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
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
    private val active = CloudIpsum("command", "cf_tourist", 0, CloudIpsumState.ACTIVE,
        Ipsum(1, "Tourist", CodeforcesProblem(ProblemId(2000, "A"), "Private training", 1200, setOf("math", "dp")),
            923, IpsumCategory.CHALLENGE, 1100, 1200, 1200, 0, 1000, status = IpsumStatus.ACTIVE,
            scoringSnapshot = ScoringSnapshot(LEGACY_SCORING_POLICY, null, modelVersion = null, artifactSha256 = null,
                inputRating = 1200, unavailableReason = "activation_disabled")))
    private fun final(status: IpsumStatus = IpsumStatus.COMPLETED) = active.copy(state = CloudIpsumState.FINAL,
        ipsum = active.ipsum.copy(status = status, endedAtEpochMillis = 61000, ratingDelta = 24, finalLoremRating = 947,
            solvedOutsideAtEpochMillis = if (status == IpsumStatus.SOLVED_OUTSIDE) 62000 else null))
    private fun state(attempt: CloudIpsum? = active, history: List<CloudIpsum> = emptyList(), dolor: Long? = null) = CloudTrainingUiState(CloudSnapshot(
        listOf(CloudHandle("cf_tourist", "Tourist", 923, null, 0, duelRatingMicros = dolor)), attempt, history + listOfNotNull(attempt), true), "cf_tourist")
    @Test fun `home and statistics show persisted Dolor fractions independent of activation and clear account values`() {
        val uid = mutableStateOf("A")
        val screen = mutableStateOf(state(null, dolor = 1456123456))
        compose.setContent { LoremTheme { CloudTrainingApp(uid.value, screen.value, 1000, {}, {}, {}, {}, {}, {}, {}) } }
        compose.onNodeWithTag("rating-dolor").performScrollTo().assertTextContains("1456,123456")
        compose.onNodeWithText("Rating Lorem").assertExists()
        compose.onNodeWithTag("tab-statistics").performClick()
        compose.onNodeWithTag("statistics-list").performScrollToNode(hasTestTag("rating-dolor"))
        compose.onNodeWithTag("rating-dolor").assertTextContains("1456,123456")
        compose.onNodeWithText("Rating Lorem atual: 923").assertExists()
        compose.runOnIdle { uid.value = "B"; screen.value = state(null) }
        compose.onNodeWithTag("rating-dolor").performScrollTo().assertTextContains("Ainda sem avaliação Dolor.")
        compose.onNodeWithText("1456,123456", useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle { screen.value = state(null, dolor = 0) }
        compose.onNodeWithTag("rating-dolor").assertTextContains("0")
        compose.runOnIdle {
            screen.value = screen.value.copy(selectedKey = "cf_petr", snapshot = screen.value.snapshot!!.copy(
                handles = listOf(CloudHandle("cf_petr", "Petr", 1600, null, 0, duelRatingMicros = 1742000000))))
        }
        compose.onNodeWithTag("rating-dolor").performScrollTo().assertTextContains("1742")
        compose.onNodeWithText("1600").assertExists()
    }

    @Test fun `result and history keep opposite Lorem and Dolor deltas distinct without rounding fractions`() {
        val saved = VirtualDuelResult(800000000, -16, 799999984, 500000, 500001, 800000)
        val result = final().let { it.copy(ipsum = it.ipsum.copy(studyRecordingVersion = CLOUD_RULE_VERSION, virtualDuelResult = saved)) }
        compose.setContent { LoremTheme { CloudTrainingApp("A", state(null, listOf(result), dolor = 799999984), 1000, {}, {}, {}, {}, {}, {}, {}) } }
        compose.onNodeWithText("Ver último resultado").performScrollTo().performClick()
        compose.onNodeWithText("Variação: +24 pontos").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("rating-dolor").performScrollTo().assertTextContains("Agora: 799,999984")
        compose.onNodeWithTag("rating-dolor").assertTextContains("Variação Dolor: −0,000016 pontos")
        compose.onNodeWithText("Voltar ao início").performScrollTo().performClick()
        compose.onNodeWithTag("tab-history").performClick()
        compose.onNodeWithTag("history-list").performScrollToNode(hasText("Variação do Rating Dolor: −0,000016"))
        compose.onNodeWithText("Variação do Rating Lorem: +24").assertExists()
        compose.onNodeWithText("Rating Dolor: 799,999984").assertExists()
    }

    @Test fun `old cloud result does not borrow the current Dolor balance`() {
        val old = final().let { it.copy(ipsum = it.ipsum.copy(studyRecordingVersion = CLOUD_RULE_VERSION)) }
        compose.setContent { LoremTheme { CloudTrainingApp("A", state(null, listOf(old), dolor = 1500000000), 1000, {}, {}, {}, {}, {}, {}, {}) } }
        compose.onNodeWithText("Ver último resultado").performScrollTo().performClick()
        compose.onNodeWithTag("rating-dolor").performScrollTo().assertTextContains("Sem avaliação Dolor neste Ipsum.")
        compose.onNodeWithText("1500", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Variação: +24 pontos").assertExists()
    }

    @Test fun `experimental active hides hint and all scoring metadata in semantics`() {
        val trial = active.copy(ipsum = active.ipsum.copy(virtualDuelSnapshot = VirtualDuelSnapshot(800*DUEL_SCALE,1200,400)))
        compose.setContent { LoremTheme { CloudTrainingApp("A", state(trial), 1000, {}, {}, {}, {}, {}, {}, {}) } }
        compose.onNodeWithText("Revelar tópicos").assertDoesNotExist()
        for (secret in listOf("1200", "math", "virtual-duel", "800", "Rating Dolor")) {
            compose.onNodeWithText(secret,substring=true,useUnmergedTree=true).assertDoesNotExist()
        }
    }
    @Test fun `active resumes after recreation hides metadata and closes into saved result then last result`() {
        val restoration = StateRestorationTester(compose)
        val state = mutableStateOf(state())
        restoration.setContent { LoremTheme { CloudTrainingApp("A", state.value, 1000, {}, {}, {}, {}, {}, {}, {}) } }
        compose.onNodeWithText("Private training").assertExists()
        compose.onNodeWithText("1200", substring = true).assertDoesNotExist()
        compose.onNodeWithText("math", substring = true).assertDoesNotExist()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Private training").assertExists()
        compose.runOnIdle { state.value = state(null, listOf(final())) }
        compose.onNodeWithText("Resultado do Ipsum").assertExists()
        compose.onNodeWithText("Voltar ao início").performScrollTo().performClick()
        compose.onNodeWithText("Ver último resultado").performScrollTo().performClick()
        compose.onNodeWithText("Resultado do Ipsum").assertExists()
    }
    @Test fun `history and statistics stay accessible during active and filters reset across accounts`() {
        val uid = mutableStateOf("A")
        val previous = final(IpsumStatus.SOLVED_OUTSIDE).copy(id = "previous", ipsum = final(IpsumStatus.SOLVED_OUTSIDE).ipsum.copy(
            id = 2, problem = CodeforcesProblem(ProblemId(2001, "B"), "Earlier problem", 800, setOf("greedy"))))
        val state = mutableStateOf(state(history = listOf(previous)))
        compose.setContent { LoremTheme { CloudTrainingApp(uid.value, state.value, 1000, {}, {}, {}, {}, {}, {}, {}) } }
        compose.onNodeWithText("Voltar ao início").performScrollTo().performClick()
        compose.onNodeWithTag("tab-history").performClick()
        compose.onNodeWithTag("situation-SOLVED_OUTSIDE_IPSUM").performClick()
        compose.onNodeWithTag("history-list").performScrollToNode(hasText("Earlier problem"))
        compose.onNodeWithText("Earlier problem").assertExists()
        compose.onNodeWithText("Private training").assertDoesNotExist()
        compose.onNodeWithTag("tab-statistics").performClick()
        compose.onNodeWithTag("statistics-list").performScrollToNode(hasText("AC no Ipsum: 0"))
        compose.onNodeWithText("AC no Ipsum: 0").assertExists()
        compose.onNodeWithTag("statistics-list").performScrollToNode(hasTestTag("statistics-evolution-chart"))
        compose.onNodeWithTag("statistics-evolution-chart").assertExists()
        compose.onNodeWithTag("tab-history").performClick()
        compose.onNodeWithTag("history-list").performScrollToIndex(0)
        compose.onNodeWithTag("situation-SOLVED_OUTSIDE_IPSUM").assertIsSelected()
        compose.runOnIdle { uid.value = "B"; state.value = state(null) }
        compose.onNodeWithTag("tab-history").performClick()
        compose.onNodeWithTag("situation-SOLVED_IN_IPSUM").assertIsSelected()
        compose.onNodeWithText("Earlier problem").assertDoesNotExist()
    }
    @Test fun `home start hint confirmation and close reason callbacks traverse the cloud journey`() {
        val state = mutableStateOf(state(null))
        var hinted = false
        var reason: IpsumFailureReason? = null
        compose.setContent { LoremTheme { CloudTrainingApp("A", state.value, 1000, {}, {},
            onStart = { state.value = state() }, onHint = {
                hinted = true
                state.value = state(active.copy(ipsum = active.ipsum.copy(hintRevealedAtEpochMillis = 1000)))
            }, onClose = { selected ->
                reason = selected
                state.value = state(null, listOf(final(IpsumStatus.PENDING).let {
                    it.copy(ipsum = it.ipsum.copy(failureReason = selected, ratingDelta = -20, finalLoremRating = 903))
                }))
            }, onRefresh = {}, onPending = {}) } }
        compose.onNodeWithText("Iniciar Ipsum").performScrollTo().performClick()
        compose.onNodeWithText("Revelar tópicos").performScrollTo().performClick()
        assertFalse(hinted)
        compose.onNodeWithText("Revelar", useUnmergedTree = true).performClick()
        assertTrue(hinted)
        compose.onNodeWithText("Tópicos: dp, math").assertExists()
        compose.onNodeWithText("Encerrar sem AC").performScrollTo().performClick()
        compose.onNodeWithText(IpsumFailureReason.LOGIC_NOT_FOUND.label).performClick()
        compose.onNodeWithText("Encerrar", useUnmergedTree = true).performClick()
        assertEquals(IpsumFailureReason.LOGIC_NOT_FOUND, reason)
        compose.onNodeWithText("Resultado do Ipsum").assertExists()
    }
    @Test fun `offline untrusted clock and awaiting verification never present absence or training controls`() {
        val state = state().let { it.copy(snapshot = it.snapshot!!.copy(online = false)) }
        compose.setContent { LoremTheme { CloudTrainingApp("A", state, null, {}, {}, {}, {}, {}, {}, {}) } }
        compose.onNodeWithText("Confirmando prazo do Ipsum salvo…").assertExists()
        compose.onNodeWithText("Revelar tópicos").assertDoesNotExist()
        compose.onNodeWithText("Abrir no Codeforces").assertDoesNotExist()
        compose.onNodeWithText("Voltar ao início").performClick()
        compose.onNodeWithText("Continuar Ipsum").assertExists()
    }
    @Test fun `closed and elapsed deadline keep distinct status without training controls`() {
        val state = mutableStateOf(state(active.copy(state = CloudIpsumState.CLOSED_AWAITING_VERIFICATION)))
        compose.setContent { LoremTheme { CloudTrainingApp("A", state.value, active.expiresAtMillis, {}, {}, {}, {}, {}, {}, {}) } }
        compose.onNodeWithText("Treino fechado · aguardando verificação").assertExists()
        compose.onNodeWithText("Revelar tópicos").assertDoesNotExist()
        compose.runOnIdle { state.value = state() }
        compose.onNodeWithText("Prazo atingido · aguardando verificação").assertExists()
        compose.onNodeWithText("Encerrar sem AC").assertDoesNotExist()
    }
    @Test fun `settings moves account and theme out of training and locks handle selection during active`() {
        var selected = 0; var changed: ThemePreference? = null
        compose.setContent { LoremTheme { CloudTrainingApp("A", state(), 1000, {}, { selected++ }, {}, {}, {}, {}, {},
            onThemeChange = { changed = it }, settingsContent = { androidx.compose.material3.Text("Account controls") }) } }
        compose.onNodeWithText("Account controls").assertDoesNotExist()
        compose.onNodeWithText("Voltar ao início").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Configurações").performClick()
        compose.onNodeWithText("Tourist · selecionado").assertIsNotEnabled()
        compose.onNodeWithText("Escuro").performScrollTo().performClick()
        assertEquals(ThemePreference.DARK, changed); assertEquals(0, selected)
        compose.onNodeWithText("Account controls").performScrollTo().assertIsDisplayed()
    }
}
