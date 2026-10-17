package dev.lorem.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import dev.lorem.app.data.local.toEntity
import dev.lorem.app.domain.model.Ipsum
import dev.lorem.app.domain.model.ThemePreference
import dev.lorem.app.ui.LoremApp
import dev.lorem.app.ui.LoremViewModel
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppearanceNavigationTest {
    @get:Rule val compose = createComposeRule()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val models = ViewModelStore()
    private var env: TrainingHarness? = null
    @After fun close() { models.clear(); scope.cancel(); env?.close() }
    private fun await(condition: () -> Boolean) = compose.waitUntil(10_000) {
        ShadowLooper.idleMainLooper(); condition()
    }
    private fun see(text: String) {
        await { compose.onAllNodesWithText(text).fetchSemanticsNodes().size == 1 }
        compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()
    }
    private fun tab(route: String) {
        compose.onNodeWithTag("tab-$route").performClick()
        await { runCatching { compose.onNodeWithTag("tab-$route").assertIsSelected(); true }.getOrDefault(false) }
    }
    private fun setup(history: List<Ipsum> = emptyList()): Pair<LoremViewModel, StateRestorationTester> {
        val storage = TrainingHarness(scope).also { env = it }
        runBlocking {
            storage.repository.saveProfile(StatisticsFixtures.profile)
            storage.repository.replaceProblemCatalog(storage.remote.catalog, 1000)
            history.forEach { storage.database.ipsumDao().insert(it.toEntity()) }
        }
        val model = LoremViewModel(storage.repository, storage.remote, nowMillis = { 1_000_000 },
            appearanceRepository = storage.appearance).also { models.put("app", it) }
        val restorer = StateRestorationTester(compose)
        restorer.setContent { LoremApp(storage.repository, storage.remote, viewModel = model) }
        see("Iniciar Ipsum")
        return model to restorer
    }

    @Test fun `tabs preserve filters scroll and statistics section through settings theme and recreation`() {
        val (model, restorer) = setup()
        tab("history")
        see("Histórico e pendências")
        compose.onNodeWithTag("rating-1200").assertDoesNotExist() // No fabricated history.
        compose.onNodeWithTag("situation-PENDING").performScrollTo().performClick()
        compose.onNodeWithTag("history-list").performScrollToNode(hasText("Seu histórico começa no primeiro Ipsum."))
        compose.onNodeWithText("Seu histórico começa no primeiro Ipsum.").performScrollTo().assertIsDisplayed()
        val historyScroll = compose.onNodeWithTag("history-list").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value()
        tab("statistics")
        see("Rating Lorem atual: 1202")
        compose.onNodeWithTag("statistics-list").performScrollToNode(hasTestTag("statistics-section-TAGS"))
        compose.onNodeWithTag("statistics-section-TAGS").performScrollTo().performClick()
        compose.onNodeWithTag("statistics-list").performScrollToNode(hasText("greedy: 0 tentativa(s)"))
        val statisticsScroll = compose.onNodeWithTag("statistics-list").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value()
        compose.onNodeWithContentDescription("Configurações").performClick()
        see("Conectado como @tourist")
        compose.onNodeWithTag("tab-statistics").assertDoesNotExist()
        compose.onNodeWithTag("theme-DARK").performScrollTo().performClick()
        await { model.themePreference.value == ThemePreference.DARK }
        compose.onNodeWithTag("theme-DARK").assertIsSelected()
        restorer.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("theme-DARK").assertIsSelected()
        compose.onNodeWithContentDescription("Voltar").performClick()
        tab("statistics")
        see("greedy: 0 tentativa(s)")
        assertEquals(statisticsScroll, compose.onNodeWithTag("statistics-list").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value(), 1f)
        tab("history")
        assertEquals(historyScroll, compose.onNodeWithTag("history-list").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value(), 1f)
        compose.onNodeWithTag("situation-PENDING").performScrollTo().assertIsSelected()
        tab("home")
        compose.onNodeWithText("Iniciar Ipsum").performScrollTo().assertIsDisplayed()
    }

    @Test fun `real history filters and card position survive tab switching and recreation`() {
        val (_, restorer) = setup(StatisticsFixtures.finished)
        tab("history")
        see("Histórico e pendências")
        compose.onNodeWithTag("situation-PENDING").performScrollTo().performClick()
        compose.onNodeWithTag("history-advanced").performScrollTo().performClick()
        compose.onNodeWithTag("rating-1400").performScrollTo().performClick()
        compose.onNodeWithTag("tag-dp").performScrollTo().performClick()
        compose.onNodeWithTag("history-list").performScrollToNode(hasText("Variação do Rating Lorem: -20"))
        val scroll = compose.onNodeWithTag("history-list").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value()
        tab("statistics")
        see("Rating Lorem atual: 1202")
        tab("history")
        assertEquals(scroll, compose.onNodeWithTag("history-list").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value(), 1f)
        restorer.emulateSavedInstanceStateRestore()
        assertEquals(scroll, compose.onNodeWithTag("history-list").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value(), 1f)
        compose.onNodeWithTag("history-list").performScrollToNode(hasText("Filtros ativos: Pendente · rating 1400 · tag dp"))
        compose.onNodeWithText("Filtros ativos: Pendente · rating 1400 · tag dp").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("rating-1400").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("tag-dp").performScrollTo().assertIsSelected()
    }

    @Test fun `dedicated training back preserves active attempt and returning result has no tabs`() {
        val (model, restorer) = setup()
        compose.onNodeWithText("Iniciar Ipsum").performClick()
        see("Ipsum ativo")
        val id = model.activeIpsum.value!!.id
        compose.onNodeWithTag("tab-home").assertDoesNotExist()
        compose.onNodeWithContentDescription("Voltar").performClick()
        see("Continuar Ipsum")
        assertEquals(id, model.activeIpsum.value!!.id)
        restorer.emulateSavedInstanceStateRestore()
        see("Continuar Ipsum")
        compose.onNodeWithText("Continuar Ipsum").performClick()
        see("Ipsum ativo")
        assertEquals(id, model.activeIpsum.value!!.id)
        compose.runOnIdle { model.endActiveIpsum(dev.lorem.app.domain.model.IpsumFailureReason.LOGIC_NOT_FOUND) }
        see("Resultado do Ipsum")
        compose.onNodeWithTag("tab-home").assertDoesNotExist()
        compose.onNodeWithContentDescription("Voltar").performClick()
        see("Iniciar Ipsum")
        assertNull(model.activeIpsum.value)
    }
}
