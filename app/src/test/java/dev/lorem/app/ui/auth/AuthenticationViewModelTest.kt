package dev.lorem.app.ui.auth

import dev.lorem.app.domain.auth.*
import dev.lorem.app.ui.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AuthenticationViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private class Repository : AuthenticationRepository {
        override val state = MutableStateFlow<AuthenticationState>(AuthenticationState.SignedOut)
        val tokens = mutableListOf<String>()
        var failure: AuthenticationFailure? = null
        override suspend fun signInWithGoogle(idToken: String): AuthenticationFailure? { tokens += idToken; return failure }
        override fun signOut() { state.value = AuthenticationState.SignedOut }
        override fun retry() = Unit
    }

    @Test fun `double tap opens only one picker and cancel permits another login`() = runTest {
        val repo = Repository()
        val model = AuthenticationViewModel(repo)
        val picker = CompletableDeferred<String>()
        var pickers = 0
        model.signIn { pickers++; picker.await() }
        model.signIn { pickers++; "should-not-run" }
        runCurrent()
        assertEquals(1, pickers)
        assertTrue(model.busy.value)
        picker.completeExceptionally(GoogleSignInCancelled())
        runCurrent()
        assertFalse(model.busy.value)
        assertNull(model.error.value)
        assertTrue(repo.tokens.isEmpty())
        model.signIn { "valid-token" }
        runCurrent()
        assertEquals(listOf("valid-token"), repo.tokens)
    }

    @Test fun `sign out hides account before credential cleanup and cancels pending picker`() = runTest {
        val repo = Repository()
        val model = AuthenticationViewModel(repo)
        val picker = CompletableDeferred<String>()
        model.signIn { picker.await() }
        runCurrent()
        repo.state.value = AuthenticationState.SignedIn(GoogleAccount("a", "A", null))
        model.signOut { error("credential provider unavailable") }
        assertEquals(AuthenticationState.SignedOut, model.session.value)
        runCurrent()
        picker.complete("late-token")
        runCurrent()
        assertTrue(repo.tokens.isEmpty())
        assertFalse(model.busy.value)
        assertNull(model.error.value)
    }

    @Test fun `sign in failure stays actionable and a retry clears stale error`() = runTest {
        val repo = Repository().apply { failure = AuthenticationFailure.NETWORK }
        val model = AuthenticationViewModel(repo)
        model.signIn { "token" }
        runCurrent()
        assertEquals(AuthenticationFailure.NETWORK, model.error.value)
        assertFalse(model.busy.value)
        repo.failure = null
        model.signIn { "new-token" }
        runCurrent()
        assertNull(model.error.value)
        assertEquals(AuthenticationState.SignedOut, model.session.value)
    }
}
