package dev.lorem.app.data.auth

import dev.lorem.app.domain.auth.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GoogleSessionRepositoryTest {
    private val a = GoogleAccount("uid-a", "Conta A", "a@example.invalid")
    private val b = GoogleAccount("uid-b", "Conta B", "b@example.invalid")

    private class Source(initial: GoogleAccount? = null) : GoogleIdentitySource {
        override val accounts = MutableStateFlow(initial)
        override val currentAccount get() = accounts.value
        var signInFailure: AuthenticationFailure? = null
        var validationFailure: AuthenticationFailure? = null
        var tokens = mutableListOf<String>()
        override suspend fun signIn(idToken: String) {
            signInFailure?.let { throw AuthenticationException(it) }
            tokens += idToken
        }
        override suspend fun validate(expectedUid: String): GoogleAccount {
            validationFailure?.let { throw AuthenticationException(it) }
            return checkNotNull(accounts.value?.takeIf { it.uid == expectedUid })
        }
        override fun signOut() { accounts.value = null }
    }

    @Test fun `restored identity stays hidden until refreshed token and server account succeed`() = runTest {
        val source = Source(a)
        val ready = CompletableDeferred<Unit>()
        val repo = GoogleSessionRepository(source, AccountBootstrap { ready.await() }, backgroundScope)
        runCurrent()
        assertEquals(AuthenticationState.Loading, repo.state.value)
        ready.complete(Unit)
        runCurrent()
        assertEquals(AuthenticationState.SignedIn(a), repo.state.value)
    }

    @Test fun `sign out invalidates a noncancellable late server reply`() = runTest {
        val source = Source(a)
        val reply = CompletableDeferred<Unit>()
        val repo = GoogleSessionRepository(source, AccountBootstrap {
            withContext(NonCancellable) { reply.await() }
        }, backgroundScope)
        runCurrent()
        repo.signOut()
        assertEquals(AuthenticationState.SignedOut, repo.state.value)
        runCurrent()
        reply.complete(Unit)
        runCurrent()
        assertEquals(AuthenticationState.SignedOut, repo.state.value)
        assertNull(source.currentAccount)
    }

    @Test fun `account switch never exposes old identity after pending validation`() = runTest {
        val source = Source(a)
        val oldReply = CompletableDeferred<Unit>()
        var requests = 0
        val repo = GoogleSessionRepository(source, AccountBootstrap {
            if (++requests == 1) withContext(NonCancellable) { oldReply.await() }
        }, backgroundScope)
        runCurrent()
        source.accounts.value = b
        runCurrent()
        assertEquals(AuthenticationState.SignedIn(b), repo.state.value)
        oldReply.complete(Unit)
        runCurrent()
        assertEquals(AuthenticationState.SignedIn(b), repo.state.value)
    }

    @Test fun `network failure has no signed in content and retry restores same account`() = runTest {
        val source = Source(a)
        var offline = true
        val repo = GoogleSessionRepository(source, AccountBootstrap {
            if (offline) throw AuthenticationException(AuthenticationFailure.NETWORK)
        }, backgroundScope)
        runCurrent()
        assertEquals(AuthenticationState.Failed(AuthenticationFailure.NETWORK, true), repo.state.value)
        assertEquals(a, source.currentAccount)
        offline = false
        repo.retry()
        runCurrent()
        assertEquals(AuthenticationState.SignedIn(a), repo.state.value)
    }

    @Test fun `revoked or wrong provider session signs out without creating account`() = runTest {
        for (reason in listOf(AuthenticationFailure.INVALID_SESSION, AuthenticationFailure.PROVIDER_NOT_ALLOWED)) {
            val source = Source(a).apply { validationFailure = reason }
            var bootstraps = 0
            val repo = GoogleSessionRepository(source, AccountBootstrap { bootstraps++ }, backgroundScope)
            runCurrent()
            assertEquals(AuthenticationState.Failed(reason, false), repo.state.value)
            assertEquals(0, bootstraps)
            assertNull(source.currentAccount)
        }
    }

    @Test fun `exchanging token alone does not authorize a session and empty token is refused`() = runTest {
        val source = Source()
        val repo = GoogleSessionRepository(source, AccountBootstrap {}, backgroundScope)
        runCurrent()
        assertNull(repo.signInWithGoogle("google-token"))
        assertEquals(listOf("google-token"), source.tokens)
        assertEquals(AuthenticationState.SignedOut, repo.state.value)
        assertEquals(AuthenticationFailure.INVALID_SESSION, repo.signInWithGoogle(" "))
        source.signInFailure = AuthenticationFailure.NETWORK
        assertEquals(AuthenticationFailure.NETWORK, repo.signInWithGoogle("retry-token"))
        assertEquals(AuthenticationState.SignedOut, repo.state.value)
    }

    @Test fun `closing repository scope releases identity observer`() = runTest {
        val source = Source()
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        GoogleSessionRepository(source, AccountBootstrap {}, scope)
        runCurrent()
        assertEquals(1, source.accounts.subscriptionCount.value)
        scope.cancel()
        runCurrent()
        assertEquals(0, source.accounts.subscriptionCount.value)
    }
}
