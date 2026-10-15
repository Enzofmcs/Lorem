package dev.lorem.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import dev.lorem.app.domain.*
import dev.lorem.app.domain.model.*
import dev.lorem.app.domain.repository.*
import dev.lorem.app.ui.*
import dev.lorem.app.ui.screens.StatisticsScreen
import dev.lorem.app.ui.theme.LoremTheme
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CompleteAppJourneyTest {
    @get:Rule val compose = createComposeRule()
    private val storageScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val modelStore = ViewModelStore()
    private var environment: TrainingHarness? = null
    private fun environment() = TrainingHarness(storageScope).also { environment = it }
    @After fun close() {
        modelStore.clear()
        storageScope.cancel()
        environment?.close()
    }
    private fun model(env: TrainingHarness, now: () -> Long) = LoremViewModel(
        env.repository, env.remote, now, RandomSource { if (it == 3) 1 else 0 },
    ).also { modelStore.put("journey", it) }
    private fun click(text: String) {
        await { compose.onAllNodesWithText(text).fetchSemanticsNodes().size == 1 }
        compose.onNodeWithText(text).performScrollTo().performClick()
    }
    private fun await(condition: () -> Boolean) = compose.waitUntil(10_000) {
        // Real Room/DataStore resume Android Main through Robolectric's paused looper.
        ShadowLooper.idleMainLooper()
        condition()
    }

    @Test fun `profile sync recommendation hint WA AC history upsolve statistics and next attempt work together`() {
        val env = environment()
        var now = 1_000_000L
        val vm = model(env) { now }
        compose.setContent { LoremTheme { LoremApp(env.repository, env.remote, viewModel = vm) } }
        await { compose.onAllNodesWithText("Handle do Codeforces").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Handle do Codeforces").performTextInput("tourist")
        click("Validar e abrir Início")
        await { vm.configurationState.value.savedProfile != null }
        click("Iniciar novo Ipsum")
        await { vm.startIpsumState.value is StartIpsumUiState.Error }
        assertNull(vm.activeIpsum.value)
        click("Sincronizar histórico")
        await { vm.historySyncState.value is HistorySyncUiState.Success }
        click("Atualizar catálogo")
        await { vm.catalogSyncState.value is CatalogSyncUiState.Success }
        click("Iniciar novo Ipsum")
        await { vm.activeIpsum.value != null }
        val first = vm.activeIpsum.value!!
        compose.onNodeWithText("Ipsum ativo").assertIsDisplayed()
        compose.onNodeWithText("Por que este problema?").assertDoesNotExist()
        click("Revelar tópicos")
        compose.onNodeWithText("Revelar").performClick()
        await { vm.activeIpsum.value?.hintRevealedAtEpochMillis != null }
        val wa = CodeforcesSubmission(10, first.problem.id, "WRONG_ANSWER", Instant.ofEpochMilli(now + 1000))
        env.remote.submissions = SubmissionHistoryResult.Success(listOf(wa))
        click("Verificar submissões")
        await { (vm.submissionCheckState.value as? SubmissionCheckUiState.Success)?.errorCount == 1 }
        env.remote.submissions = SubmissionHistoryResult.Success(listOf(wa,
            CodeforcesSubmission(11, first.problem.id, "OK", Instant.ofEpochMilli(now + 2000))))
        click("Verificar submissões")
        await { (vm.ipsumResultState.value as? IpsumResultUiState.Ready)?.result?.ipsum?.id == first.id }
        compose.onNodeWithText("Resultado do Ipsum").assertIsDisplayed()
        compose.onNodeWithText("Por que este problema?").performScrollTo().assertIsDisplayed()
        click("Fechar resultado")
        now += 10_000
        click("Iniciar novo Ipsum")
        await { vm.activeIpsum.value?.id?.let { it != first.id } == true }
        val second = vm.activeIpsum.value!!
        compose.onNodeWithText("Ipsum ativo").assertIsDisplayed()
        compose.onNodeWithText("Resultado do Ipsum").assertDoesNotExist()
        assertNotEquals(first.problem.id, second.problem.id)
        now += 1000
        click("Encerrar sem AC")
        compose.onNodeWithText("Não encontrei a lógica.").performScrollTo().performClick()
        compose.onNodeWithText("Encerrar").performClick()
        await { (vm.ipsumResultState.value as? IpsumResultUiState.Ready)?.result?.ipsum?.id == second.id }
        val originalRating = (vm.uiState.value as LoremUiState.Ready).profile!!.loremRating
        click("Fechar resultado")
        click("Abrir Histórico")
        compose.onNodeWithText("Histórico e pendências").assertIsDisplayed()
        env.remote.submissions = SubmissionHistoryResult.Success(listOf(
            CodeforcesSubmission(12, second.problem.id, "OK", Instant.ofEpochMilli(now + 2000))))
        click("Atualizar pendências")
        await { (vm.pendingUpdateState.value as? PendingUpdateUiState.Success)?.movedCount == 1 }
        click("Atualizar pendências")
        await { (vm.pendingUpdateState.value as? PendingUpdateUiState.Success)?.movedCount == 0 }
        click("Voltar ao Início")
        click("Abrir Estatísticas")
        await { (vm.statisticsState.value as? StatisticsUiState.Ready)?.statistics?.overall?.solvedOutside == 1 }
        compose.onNodeWithTag("statistics-list").performScrollToNode(hasTestTag("statistics-overall"))
        compose.onNodeWithText("Ipsums encerrados: 2").assertIsDisplayed()
        val stats = (vm.statisticsState.value as StatisticsUiState.Ready).statistics
        assertEquals(AcRate(1, 2), stats.overall.acRate)
        assertEquals(0, stats.overall.pending)
        assertEquals(originalRating, stats.currentRating)
        compose.onNodeWithTag("statistics-list").performScrollToNode(hasText("Voltar ao Início"))
        click("Voltar ao Início")
        now += 10_000
        click("Iniciar novo Ipsum")
        await { vm.activeIpsum.value != null }
        assertTrue(vm.activeIpsum.value!!.problem.id !in setOf(first.problem.id, second.problem.id))
    }

    @Test fun `saved active attempt automatically resumes and keeps hint through saved state restoration offline`() {
        val env = environment()
        runBlocking {
            env.repository.saveProfile(StatisticsFixtures.profile)
            env.repository.createActiveIpsum(StatisticsFixtures.attempt(1, IpsumStatus.ACTIVE)
                .copy(id = 0, hintRevealedAtEpochMillis = 1L,
                    recommendationFactors = RecommendationFactors(listOf(TagNeed("implementation", 0, 0, 0, 0)), 0)))
        }
        env.remote.submissions = SubmissionHistoryResult.NetworkFailure
        val vm = model(env) { 5_000_000 }
        val restoration = StateRestorationTester(compose)
        restoration.setContent { LoremTheme { LoremApp(env.repository, env.remote, viewModel = vm) } }
        await { vm.activeIpsum.value != null }
        compose.onNodeWithText("Ipsum ativo").assertIsDisplayed()
        compose.onNodeWithText("Tópicos: implementation").performScrollTo().assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Ipsum ativo").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Tópicos: implementation").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Por que este problema?").assertDoesNotExist()
        assertEquals(1L, vm.activeIpsum.value!!.hintRevealedAtEpochMillis)
    }

    @Test fun `statistics show permanent milestone separately from estimated rating and next band requirements`() {
        val profile = StatisticsFixtures.profile.copy(loremRating = 1518, consolidatedRating = 1400)
        val progress = calculateConsolidatedRating(emptyList(), profile.consolidatedRating, profile.handle, profile.loremRating)
        compose.setContent {
            LoremTheme { StatisticsScreen(StatisticsUiState.Ready(buildIpsumStatistics(profile, emptyList())), consolidation = progress) }
        }
        compose.onNodeWithText("Rating Lorem atual: 1518").assertIsDisplayed()
        compose.onNodeWithTag("statistics-list").performScrollToNode(hasText("Faixa consolidada: 1400"))
        compose.onNodeWithText("Faixa consolidada: 1400").assertIsDisplayed()
        compose.onNodeWithText("Próxima faixa: 1500–1599").assertIsDisplayed()
        compose.onNodeWithTag("statistics-list").performScrollToNode(hasText("ACs sem dica:", substring = true))
        compose.onNodeWithText("ACs sem dica: 0/5; faltam 5.").assertIsDisplayed()
    }
}
