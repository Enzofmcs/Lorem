package dev.lorem.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.lorem.app.domain.model.CodeforcesProblem
import dev.lorem.app.domain.model.LocalProfile
import dev.lorem.app.domain.repository.CodeforcesRepository
import dev.lorem.app.domain.repository.CodeforcesUser
import dev.lorem.app.domain.repository.LoremRepository
import dev.lorem.app.domain.repository.UserLookupResult
import dev.lorem.app.ui.LoremApp
import dev.lorem.app.ui.theme.LoremTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `valid handle persists profile and opens home`() {
        val local = MemoryLoremRepository()
        setApp(local, UserLookupResult.Success(CodeforcesUser("tourist", "Gennady", 4009)))

        composeRule.onNodeWithText("Handle do Codeforces").performTextInput("tourist")
        composeRule.onNodeWithText("Validar e abrir Início").performClick()
        composeRule.waitUntil(5_000) { local.profile.value != null }

        composeRule.onNodeWithText("Início").assertIsDisplayed()
        composeRule.onNodeWithText("Gennady (@tourist)").assertIsDisplayed()
        assertEquals(4009, local.profile.value?.loremRating)
    }

    @Test
    fun `invalid handle shows error and does not navigate`() {
        setApp(MemoryLoremRepository(), UserLookupResult.UserNotFound)
        composeRule.onNodeWithText("Handle do Codeforces").performTextInput("missing")
        composeRule.onNodeWithText("Validar e abrir Início").performClick()

        composeRule.onNodeWithText("Usuário não encontrado no Codeforces.").assertIsDisplayed()
        composeRule.onNodeWithText("Vincular Codeforces").assertIsDisplayed()
    }

    @Test
    fun `network error preserves typed handle and stays on configuration`() {
        setApp(MemoryLoremRepository(), UserLookupResult.NetworkFailure)
        composeRule.onNodeWithText("Handle do Codeforces").performTextInput("keep-me")
        composeRule.onNodeWithText("Validar e abrir Início").performClick()

        composeRule.onNodeWithText("Handle do Codeforces")
            .assertTextEquals("Handle do Codeforces", "keep-me", "Sem conexão com o Codeforces. Verifique a internet e tente novamente.")
        composeRule.onNodeWithText("Vincular Codeforces").assertIsDisplayed()
    }

    private fun setApp(local: LoremRepository, result: UserLookupResult) {
        composeRule.setContent {
            LoremTheme {
                LoremApp(local, ResultCodeforcesRepository(result))
            }
        }
        composeRule.waitForIdle()
    }
}

private class MemoryLoremRepository : LoremRepository {
    override val profile = MutableStateFlow<LocalProfile?>(null)
    override suspend fun saveProfile(profile: LocalProfile) { this.profile.value = profile }
    override suspend fun clearProfile() { profile.value = null }
}

private class ResultCodeforcesRepository(
    private val result: UserLookupResult,
) : CodeforcesRepository {
    override suspend fun user(handle: String) = result
    override suspend fun problems(): List<CodeforcesProblem> = emptyList()
}
