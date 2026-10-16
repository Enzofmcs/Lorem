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
import dev.lorem.app.domain.sync.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.channels.Channel

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

    private fun awaitDisplayed(text: String) = await {
        runCatching { compose.onNodeWithText(text).assertIsDisplayed(); true }.getOrDefault(false)
    }

    @Test fun `visible Ipsum detects WA TESTING AC without pressing recovery and keeps hidden data private`() {
        val env = environment()
        runBlocking {
            env.repository.saveProfile(StatisticsFixtures.profile.copy(loremRating = 1200))
            env.repository.replaceProblemCatalog(env.remote.catalog, 1)
        }
        val ticks = Channel<Unit>(Channel.UNLIMITED)
        val vm = LoremViewModel(env.repository, env.remote, nowMillis = { 1_000_000 },
            randomSource = RandomSource { 0 }, submissionPollingWait = { ticks.receive() })
            .also { modelStore.put("auto", it) }
        compose.setContent { LoremTheme { LoremApp(env.repository, env.remote, viewModel = vm) } }
        click("Iniciar Ipsum")
        await { vm.activeIpsum.value != null && vm.trackingState.value?.synchronization?.lastSuccessEpochMillis != null }
        val first = vm.activeIpsum.value!!
        val wa = CodeforcesSubmission(10, first.problem.id, "WRONG_ANSWER", Instant.ofEpochMilli(1_001_000))
        val provisional = CodeforcesSubmission(11, first.problem.id, "TESTING", Instant.ofEpochMilli(1_002_000))
        env.remote.submissions = SubmissionHistoryResult.Success(listOf(wa, provisional, wa))
        ticks.trySend(Unit)
        await { vm.activeIpsum.value?.errorCount == 1 }
        compose.onNodeWithText("Erros definitivos: 1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Tópicos: dp").assertDoesNotExist()
        compose.onNodeWithText("CURRENT_LEVEL", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Por que este problema?").assertDoesNotExist()
        env.remote.submissions = SubmissionHistoryResult.Success(listOf(wa, provisional.copy(verdict = "OK")))
        ticks.trySend(Unit)
        await { (vm.ipsumResultState.value as? IpsumResultUiState.Ready)?.result?.ipsum?.id == first.id }
        awaitDisplayed("Resultado do Ipsum")
        compose.onNodeWithText("Resultado do Ipsum").assertIsDisplayed()
        val saved = runBlocking { env.repository.getIpsumResult(first.id)!!.ipsum }
        assertEquals(1, saved.errorCount)
        assertEquals(1_002_000L, saved.endedAtEpochMillis)
        ticks.trySend(Unit)
        click("Voltar ao início")
        assertEquals(saved, runBlocking { env.repository.getIpsumResult(first.id)!!.ipsum })
    }

    @Test fun `background AC waits for resumed Ipsum route and survives saved state restoration`() {
        val env = environment()
        val active = runBlocking {
            env.repository.saveProfile(StatisticsFixtures.profile)
            env.repository.createActiveIpsum(StatisticsFixtures.attempt(1, IpsumStatus.ACTIVE).copy(id = 0))
        }
        val coordinator = SynchronizationCoordinator(env.repository, env.remote, storageScope)
        val ticks = Channel<Unit>(Channel.UNLIMITED)
        val vm = LoremViewModel(env.repository, env.remote, synchronizationCoordinator = coordinator,
            submissionPollingWait = { ticks.receive() }).also { modelStore.put("background", it) }
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry.createUnsafe(this).also { it.currentState = Lifecycle.State.RESUMED }
            override val lifecycle: Lifecycle = registry
        }
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                LoremTheme { LoremApp(env.repository, env.remote, viewModel = vm) }
            }
        }
        await { vm.trackingState.value?.synchronization?.lastSuccessEpochMillis != null }
        compose.onNodeWithText("Ipsum ativo").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        env.remote.submissions = SubmissionHistoryResult.Success(listOf(
            CodeforcesSubmission(99, active.problem.id, "OK", Instant.ofEpochMilli(active.startedAtEpochMillis + 2000))))
        val outcome = runBlocking {
            BackgroundSynchronization(env.repository, coordinator).profile(env.repository.profileLink.first()!!)
        }
        assertEquals(SyncWorkResult.SUCCESS, outcome)
        await { vm.activeIpsum.value == null }
        compose.onNodeWithText("Resultado do Ipsum").assertDoesNotExist()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Resultado do Ipsum").assertDoesNotExist()
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        await { (vm.ipsumResultState.value as? IpsumResultUiState.Ready)?.result?.ipsum?.id == active.id }
        awaitDisplayed("Resultado do Ipsum")
        compose.onNodeWithText("Resultado do Ipsum").assertIsDisplayed()
        val result = (vm.ipsumResultState.value as IpsumResultUiState.Ready).result.ipsum
        click("Voltar ao início")
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.onNodeWithText("Resultado do Ipsum").assertDoesNotExist()
        assertEquals(result, runBlocking { env.repository.getIpsumResult(active.id)!!.ipsum })
    }

    @Test fun `profile sync recommendation hint WA AC history upsolve statistics and next attempt work together`() {
        val env = environment()
        var now = 1_000_000L
        val coordinator = SynchronizationCoordinator(env.repository, env.remote, storageScope, nowMillis = { now })
        val vm = LoremViewModel(env.repository, env.remote, nowMillis = { now },
            randomSource = RandomSource { if (it == 3) 1 else 0 }, synchronizationCoordinator = coordinator)
            .also { modelStore.put("journey", it) }
        compose.setContent { LoremTheme { LoremApp(env.repository, env.remote, viewModel = vm) } }
        await { compose.onAllNodesWithText("Handle do Codeforces").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Handle do Codeforces").performTextInput("tourist")
        click("Conectar Codeforces")
        await { vm.configurationState.value.savedProfile != null }
        compose.onNodeWithText("Iniciar Ipsum").performScrollTo().assertIsNotEnabled()
        assertNull(vm.activeIpsum.value)
        // The application's scheduler runs this service after binding. No manual sync action is used.
        val preparation = BackgroundSynchronization(env.repository, coordinator, nowMillis = { now })
        runBlocking {
            preparation.profile(env.repository.profileLink.first()!!)
            preparation.catalog()
        }
        await { vm.uiState.value.let { it is LoremUiState.Ready && it.profile!!.lastSyncEpochMillis > 0 } && vm.problemCatalog.value.isNotEmpty() }
        await { runCatching { compose.onNodeWithText("Iniciar Ipsum").assertIsEnabled(); true }.getOrDefault(false) }
        click("Iniciar Ipsum")
        await { vm.activeIpsum.value != null }
        val first = vm.activeIpsum.value!!
        awaitDisplayed("Ipsum ativo")
        compose.onNodeWithText("Ipsum ativo").assertIsDisplayed()
        compose.onNodeWithText("Por que este problema?").assertDoesNotExist()
        click("Revelar tópicos")
        compose.onNodeWithText("Revelar").performClick()
        await { vm.activeIpsum.value?.hintRevealedAtEpochMillis != null }
        val wa = CodeforcesSubmission(10, first.problem.id, "WRONG_ANSWER", Instant.ofEpochMilli(now + 1000))
        env.remote.submissions = SubmissionHistoryResult.Success(listOf(wa))
        click("Atualizar agora")
        await { (vm.submissionCheckState.value as? SubmissionCheckUiState.Success)?.errorCount == 1 }
        env.remote.submissions = SubmissionHistoryResult.Success(listOf(wa,
            CodeforcesSubmission(11, first.problem.id, "OK", Instant.ofEpochMilli(now + 2000))))
        click("Atualizar agora")
        await { (vm.ipsumResultState.value as? IpsumResultUiState.Ready)?.result?.ipsum?.id == first.id }
        awaitDisplayed("Resultado do Ipsum")
        compose.onNodeWithText("Resultado do Ipsum").assertIsDisplayed()
        compose.onNodeWithText("Por que este problema?").performScrollTo().assertIsDisplayed()
        click("Voltar ao início")
        now += 10_000
        click("Iniciar Ipsum")
        await { vm.activeIpsum.value?.id?.let { it != first.id } == true }
        val second = vm.activeIpsum.value!!
        awaitDisplayed("Ipsum ativo")
        compose.onNodeWithText("Ipsum ativo").assertIsDisplayed()
        compose.onNodeWithText("Resultado do Ipsum").assertDoesNotExist()
        assertNotEquals(first.problem.id, second.problem.id)
        now += 1000
        click("Encerrar sem AC")
        compose.onNodeWithText("Não encontrei a lógica.").performScrollTo().performClick()
        compose.onNodeWithText("Encerrar").performClick()
        await { (vm.ipsumResultState.value as? IpsumResultUiState.Ready)?.result?.ipsum?.id == second.id }
        val originalRating = (vm.uiState.value as LoremUiState.Ready).profile!!.loremRating
        click("Voltar ao início")
        compose.onNodeWithTag("tab-history").performClick()
        compose.onNodeWithText("Histórico e pendências").assertIsDisplayed()
        env.remote.submissions = SubmissionHistoryResult.Success(listOf(
            CodeforcesSubmission(12, second.problem.id, "OK", Instant.ofEpochMilli(now + 2000))))
        click("Atualizar agora")
        await { (vm.pendingUpdateState.value as? PendingUpdateUiState.Success)?.movedCount == 1 }
        click("Atualizar agora")
        await { (vm.pendingUpdateState.value as? PendingUpdateUiState.Success)?.movedCount == 0 }
        click("Voltar ao Início")
        compose.onNodeWithTag("tab-statistics").performClick()
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
        click("Iniciar Ipsum")
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
        awaitDisplayed("Ipsum ativo")
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
