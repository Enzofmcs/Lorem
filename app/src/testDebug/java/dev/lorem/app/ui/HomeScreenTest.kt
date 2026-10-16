package dev.lorem.app.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.lorem.app.domain.model.LocalProfile
import dev.lorem.app.domain.sync.*
import dev.lorem.app.ui.screens.HomeScreen
import dev.lorem.app.ui.theme.LoremTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeScreenTest {
    @get:Rule val compose = createComposeRule()
    private val profile = LocalProfile("tourist", "Tourist", 3800, 1200, null, 1)

    @Test fun `initial import and missing catalog explain blocking until local data is ready`() {
        val saved = mutableStateOf(profile.copy(lastSyncEpochMillis = 0))
        val catalog = mutableStateOf(false)
        var starts = 0
        var route: String? = null
        compose.setContent { LoremTheme {
            HomeScreen(saved.value, catalog.value, { route = it }, onStartIpsum = { starts++ })
        } }
        compose.onNodeWithText("Iniciar Ipsum").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Seu histórico está sendo preparado.", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Ver preparação").performScrollTo().performClick()
        assertEquals("configuration", route)
        compose.runOnIdle { saved.value = profile }
        compose.onNodeWithText("Iniciar Ipsum").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Estamos preparando os problemas", substring = true).performScrollTo().assertIsDisplayed()
        compose.runOnIdle { catalog.value = true }
        compose.onNodeWithText("Iniciar Ipsum").performScrollTo().assertIsEnabled().performClick()
        assertEquals(1, starts)
        compose.onNodeWithText("Atualizar histórico agora").assertDoesNotExist()
        compose.onNodeWithText("Atualizar catálogo agora").assertDoesNotExist()
    }

    @Test fun `loading blocks repeated start and active attempt can continue even without preparation`() {
        val starting = mutableStateOf<StartIpsumUiState>(StartIpsumUiState.Loading)
        val active = mutableStateOf(false)
        var route: String? = null
        compose.setContent { LoremTheme {
            HomeScreen(profile, true, { route = it }, hasActiveIpsum = active.value, startIpsumState = starting.value)
        } }
        compose.onNodeWithText("Iniciar Ipsum").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { starting.value = StartIpsumUiState.Error("Não foi possível salvar. Tente novamente.") }
        compose.onNodeWithText("Não foi possível salvar. Tente novamente.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Iniciar Ipsum").performScrollTo().assertIsEnabled()
        compose.runOnIdle { active.value = true }
        compose.onNodeWithText("Continuar Ipsum").performScrollTo().assertIsEnabled().performClick()
        assertEquals("ipsum", route)
        compose.onNodeWithText("Iniciar Ipsum").assertDoesNotExist()
    }

    @Test fun `local summary and last result remain available during offline partial refresh`() {
        val success = SynchronizationState(lastSuccessEpochMillis = 1_700_000_000_000)
        val offline = success.copy(failure = "offline", failureKind = SyncFailureKind.NETWORK)
        var route: String? = null
        compose.setContent { LoremTheme {
            HomeScreen(profile, true, { route = it }, latestFinishedIpsumId = 42,
                finishedIpsumCount = 5, acceptedIpsumCount = 2,
                preparation = PreparationUiState(ProfileLink.of("tourist", 1), success, offline, success, success))
        } }
        compose.onNodeWithText("Olá, Tourist").assertIsDisplayed()
        compose.onNodeWithText("@tourist").assertIsDisplayed()
        compose.onNodeWithText("Rating oficial Codeforces: 3800", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Sem conexão · dados salvos").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Último sucesso de todos os dados:", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Dados atualizados").assertDoesNotExist()
        compose.onNodeWithText("Ipsums encerrados: 5").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("AC durante o Ipsum: 2 de 5").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Ver último resultado").performScrollTo().performClick()
        assertEquals("result/42", route)
        compose.onNodeWithText("Iniciar Ipsum").performScrollTo().assertIsEnabled()
    }

    @Test fun `empty profile has no invented training or complete success`() {
        compose.setContent { LoremTheme {
            HomeScreen(profile.copy(officialRating = null, lastSyncEpochMillis = 0), false, {},
                preparation = PreparationUiState(ProfileLink.of("tourist", 1),
                    history = SynchronizationState(importedCount = 50, running = true)))
        } }
        compose.onNodeWithText("Preparando seu treino").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Último sucesso de todos os dados:", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Seu primeiro Ipsum começa aqui.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Ver último resultado").assertDoesNotExist()
        compose.onNodeWithText("Faixa consolidada: 0").assertDoesNotExist()
    }
    @Test fun `consolidation reuses missing sample and permanent milestone with expandable requirements`() {
        val progress = mutableStateOf(dev.lorem.app.domain.calculateConsolidatedRating(emptyList(), estimatedRating = 1200))
        compose.setContent { LoremTheme { HomeScreen(profile, true, {}, consolidation = progress.value) } }
        compose.onNodeWithText("Faixa consolidada: ainda não consolidada").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Ver progresso da faixa").performScrollTo().performClick()
        compose.onNodeWithText("AC na faixa: sem dados (0/0); mínimo 70%.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Tentativas na faixa: 0/8; faltam 8.").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { progress.value = dev.lorem.app.domain.calculateConsolidatedRating(emptyList(), previousRating = 1300) }
        compose.onNodeWithText("Faixa consolidada: 1300").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Próxima faixa: 1400–1499").performScrollTo().assertIsDisplayed()
    }

}
