package dev.lorem.app.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.lorem.app.StatisticsFixtures
import dev.lorem.app.domain.auth.*
import dev.lorem.app.domain.model.*
import dev.lorem.app.domain.rollout.*
import dev.lorem.app.ui.auth.AuthenticationScreen
import dev.lorem.app.ui.rollout.LegacyArchiveDestination
import dev.lorem.app.ui.theme.LoremTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LegacyArchiveScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun `device archive stays separate and read only when Google accounts switch or sign out`() {
        val session = mutableStateOf<AuthenticationState>(AuthenticationState.SignedIn(GoogleAccount("a", "Conta A", null)))
        val archive = LegacyArchive(true, StatisticsFixtures.profile, StatisticsFixtures.finished, null, null)
        val migration = object : LegacyMigration {
            override suspend fun reconcile() = archive
            override suspend fun close(reason: IpsumFailureReason): LegacyArchive = error("Archive must stay read only")
            override suspend fun exclusions(handle: String) = emptySet<ProblemId>()
        }
        compose.setContent { LoremTheme {
            AuthenticationScreen(session.value, false, null, {}, {}, { session.value = AuthenticationState.SignedOut },
                deviceContent = { LegacyArchiveDestination(migration) },
                signedInContent = { Text("Dados da conta ${it.uid}", Modifier.testTag("account-training")) })
        } }
        compose.onNodeWithText("Consultar 4 tentativas anteriores").performScrollTo().performClick()
        compose.onNodeWithText("tourist · Rating anterior: 1202 · faixa: sem amostra").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Iniciar Ipsum").assertDoesNotExist()
        compose.runOnIdle { session.value = AuthenticationState.SignedIn(GoogleAccount("b", "Conta B", null)) }
        compose.onNodeWithText("Dados da conta a").assertDoesNotExist()
        compose.onNodeWithText("Dados da conta b").assertExists()
        compose.onNodeWithText("Arquivo local deste aparelho").assertExists()
        compose.onNodeWithTag("google-sign-out").performScrollTo().performClick()
        compose.onNodeWithTag("account-training").assertDoesNotExist()
        compose.onNodeWithText("Arquivo local deste aparelho").assertExists()
        compose.onNodeWithText("tourist · Rating anterior: 1202 · faixa: sem amostra").assertExists()
    }
}
