package dev.lorem.app.ui

import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.lorem.app.domain.auth.*
import dev.lorem.app.ui.auth.AuthenticationScreen
import dev.lorem.app.ui.theme.LoremTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AuthenticationScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `account switch clears private navigation inputs and sign out removes content`() {
        val state = mutableStateOf<AuthenticationState>(AuthenticationState.SignedIn(GoogleAccount("a", "Conta A", null)))
        compose.setContent {
            LoremTheme {
                AuthenticationScreen(state.value, false, null, {}, {}, { state.value = AuthenticationState.SignedOut }) {
                    var input by remember { mutableStateOf("") }
                    OutlinedTextField(input, { input = it }, Modifier.testTag("private-input"))
                }
            }
        }
        compose.onNodeWithTag("private-input").performTextInput("only-account-a")
        compose.runOnIdle { state.value = AuthenticationState.SignedIn(GoogleAccount("b", "Conta B", null)) }
        compose.onNodeWithText("Conta A").assertDoesNotExist()
        compose.onNodeWithText("only-account-a").assertDoesNotExist()
        compose.onNodeWithText("Conta B").assertIsDisplayed()
        compose.onNodeWithTag("google-sign-out").performClick()
        compose.onNodeWithTag("private-input").assertDoesNotExist()
        compose.onNodeWithText("Conta B").assertDoesNotExist()
        compose.onNodeWithTag("google-sign-in").assertIsDisplayed()
    }

    @Test fun `missing configuration disables Google while retryable network error offers recovery`() {
        val state = mutableStateOf<AuthenticationState>(AuthenticationState.Failed(AuthenticationFailure.CONFIGURATION, false))
        var retries = 0
        compose.setContent { LoremTheme { AuthenticationScreen(state.value, false, null, {}, { retries++ }, {}) } }
        compose.onNodeWithTag("google-sign-in").assertIsNotEnabled()
        compose.onNodeWithText("Tentar novamente").assertDoesNotExist()
        compose.runOnIdle { state.value = AuthenticationState.Failed(AuthenticationFailure.NETWORK, true) }
        compose.onNodeWithText("Tentar novamente").performClick()
        assertEquals(1, retries)
        compose.onNodeWithTag("google-sign-in").assertIsEnabled()
    }

    @Test fun `validating session hides authenticated destination and legacy training`() {
        compose.setContent { LoremTheme { AuthenticationScreen(AuthenticationState.Loading, false, null, {}, {}, {}) } }
        compose.onNodeWithTag("handle-onboarding").assertDoesNotExist()
        compose.onNodeWithTag("tab-home").assertDoesNotExist()
        compose.onNodeWithTag("google-sign-in").assertDoesNotExist()
        compose.onNodeWithText("Preparando sua sessão…").assertIsDisplayed()
    }
    @Test fun `interrupted account selection shows feedback and permits retry instead of a silent return`() {
        var attempts = 0
        compose.setContent { LoremTheme { AuthenticationScreen(AuthenticationState.SignedOut, false,
            AuthenticationFailure.CANCELLED, { attempts++ }, {}, {}) } }
        compose.onNodeWithTag("google-sign-in-feedback").assertIsDisplayed()
        compose.onNodeWithText("A entrada foi interrompida pelo Google. Tente novamente.").assertExists()
        compose.onNodeWithTag("google-sign-in").assertIsEnabled().performClick()
        assertEquals(1, attempts)
    }
    @Test fun `a sign in error remains visible while a previous session is loading`() {
        compose.setContent { LoremTheme { AuthenticationScreen(AuthenticationState.Loading, false,
            AuthenticationFailure.CREDENTIAL_UNAVAILABLE, {}, {}, {}) } }
        compose.onNodeWithTag("google-sign-in-feedback").assertIsDisplayed()
        compose.onNodeWithText("O Google não retornou uma credencial de acesso. Tente novamente.").assertExists()
        compose.onNodeWithTag("handle-onboarding").assertDoesNotExist()
    }

}
