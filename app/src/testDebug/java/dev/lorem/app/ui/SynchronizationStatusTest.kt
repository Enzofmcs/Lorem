package dev.lorem.app.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.lorem.app.domain.model.LocalProfile
import dev.lorem.app.domain.sync.*
import dev.lorem.app.ui.screens.ConfigurationScreen
import dev.lorem.app.ui.theme.LoremTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SynchronizationStatusTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `settings retain per dataset success during offline partial refresh and recovery uses the real action`() {
        val success = SynchronizationState(lastSuccessEpochMillis = 1_000_000)
        val state = mutableStateOf(PreparationUiState(ProfileLink.of("tourist", 1), success,
            success.copy(failure = "NetworkFailure", failureKind = SyncFailureKind.NETWORK), success, success))
        var recovered = 0
        compose.setContent {
            LoremTheme {
                ConfigurationScreen(ConfigurationUiState(handle = "tourist"),
                    LocalProfile("tourist", "Tourist", 1500, 1200, null, 1), {}, {},
                    preparation = state.value, onRefreshCatalog = { recovered++ })
            }
        }
        compose.onNodeWithText("Sem conexão · dados salvos").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Dados atualizados").assertDoesNotExist()
        compose.onNodeWithText("Catálogo · último sucesso:", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Atualizar catálogo agora").performScrollTo().performClick()
        assertEquals(1, recovered)
        compose.runOnIdle { state.value = state.value.copy(catalog = success.copy(running = true)) }
        compose.onNodeWithText("Atualizando dados…").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Dados atualizados").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(catalog = success) }
        compose.onNodeWithText("Dados atualizados").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(hasExpiredData = true) }
        compose.onNodeWithText("Dados salvos · atualização pendente").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Dados atualizados").assertDoesNotExist()
    }
    @Test fun `initial offline preparation exposes per stage recovery without inventing a saved success`() {
        val state = mutableStateOf(PreparationUiState(ProfileLink.of("tourist", 1),
            history = SynchronizationState(failure = "offline", failureKind = SyncFailureKind.NETWORK)))
        var recovered = 0
        compose.setContent { LoremTheme {
            ConfigurationScreen(ConfigurationUiState(handle = "tourist"),
                LocalProfile("tourist", "Tourist", null, 800, null, 0), {}, {},
                preparation = state.value, onRefreshHistory = { recovered++ })
        } }
        compose.onNodeWithText("Sem conexão · preparação pendente").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Histórico · último sucesso: nenhuma consulta concluída").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Último sucesso de todos os dados:", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Atualizar histórico agora").performScrollTo().performClick()
        assertEquals(1, recovered)
        compose.runOnIdle { state.value = state.value.copy(history = SynchronizationState(running = true, importedCount = 500)) }
        compose.onNodeWithText("Histórico: 500 registros processados; importação em andamento").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Atualizar histórico agora").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Dados atualizados").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(history = SynchronizationState(lastSuccessEpochMillis = 1000)) }
        compose.onNodeWithText("Atualizar histórico agora").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("Dados atualizados").assertDoesNotExist()
    }

}
