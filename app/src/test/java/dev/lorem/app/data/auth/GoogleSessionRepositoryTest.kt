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
        override val currentAccount get() = exchangeAccount ?: accounts.value
        var signInFailure: AuthenticationFailure? = null
        var validationReply: CompletableDeferred<GoogleAccount>? = null
        var validationFailure: AuthenticationFailure? = null
        var tokens = mutableListOf<String>()
        var exchangeAccount: GoogleAccount? = null
        var exchangeWait: CompletableDeferred<Unit>? = null
        override suspend fun signIn(idToken: String) {
            signInFailure?.let { throw AuthenticationException(it) }
            exchangeWait?.await()
            tokens += idToken
        }
        override suspend fun validate(expectedUid: String): GoogleAccount {
            validationFailure?.let { throw AuthenticationException(it) }
            validationReply?.let { return it.await() }
            return checkNotNull(currentAccount?.takeIf { it.uid == expectedUid })
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
        val requestedUids = mutableListOf<String>()
        val repo = GoogleSessionRepository(source, AccountBootstrap { uid ->
            requestedUids += uid
            if (++requests == 1) withContext(NonCancellable) { oldReply.await() }
        }, backgroundScope)
        runCurrent()
        source.accounts.value = b
        runCurrent()
        assertEquals(AuthenticationState.SignedIn(b), repo.state.value)
        assertEquals(listOf(a.uid, b.uid), requestedUids)
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
        assertEquals(AuthenticationFailure.INVALID_SESSION, repo.signInWithGoogle("google-token"))
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

    @Test fun `quota or unpublished Rules never authorize a session and retry retains the account`() = runTest {
        for (failure in listOf(AuthenticationFailure.QUOTA, AuthenticationFailure.CONFIGURATION)) {
            val source = Source(a)
            var unavailable = true
            val repo = GoogleSessionRepository(source, AccountBootstrap { uid ->
                assertEquals(a.uid, uid)
                if (unavailable) throw AuthenticationException(failure)
            }, backgroundScope)
            runCurrent()
            assertEquals(AuthenticationState.Failed(failure, true), repo.state.value)
            assertEquals(a, source.currentAccount)
            unavailable = false
            repo.retry()
            runCurrent()
            assertEquals(AuthenticationState.SignedIn(a), repo.state.value)
        }
    }
    @Test fun `Google exchange resumes validation when auth listener has not emitted the selected account`() = runTest {
        val source = Source()
        val server = CompletableDeferred<Unit>()
        var checked = 0
        val repo = GoogleSessionRepository(source, AccountBootstrap { checked++; server.await() }, backgroundScope)
        runCurrent()
        source.exchangeAccount = a
        assertNull(repo.signInWithGoogle("token"))
        runCurrent()
        assertEquals(AuthenticationState.Loading, repo.state.value)
        assertEquals(1, checked)
        server.complete(Unit); runCurrent()
        assertEquals(AuthenticationState.SignedIn(a), repo.state.value)
    }
    @Test fun `selecting the same Google account after failed bootstrap restarts validation without a new auth event`() = runTest {
        val source = Source(a)
        var unavailable = true
        val repo = GoogleSessionRepository(source, AccountBootstrap {
            if (unavailable) throw AuthenticationException(AuthenticationFailure.NETWORK)
        }, backgroundScope)
        runCurrent()
        assertEquals(AuthenticationState.Failed(AuthenticationFailure.NETWORK, true), repo.state.value)
        unavailable = false
        assertNull(repo.signInWithGoogle("retry")); runCurrent()
        assertEquals(AuthenticationState.SignedIn(a), repo.state.value)
    }

    @Test fun `hung verification has a bounded recoverable failure and resume cannot postpone it`() = runTest {
        val source = Source(a)
        val server = CompletableDeferred<Unit>()
        val repo = GoogleSessionRepository(source, AccountBootstrap { server.await() }, backgroundScope, timeoutMillis = 1000)
        runCurrent(); advanceTimeBy(750)
        repo.retry(); runCurrent() // The Activity returned from the picker; do not restart its clock.
        advanceTimeBy(251); runCurrent()
        assertEquals(AuthenticationState.Failed(AuthenticationFailure.NETWORK, true), repo.state.value)
        server.complete(Unit)
        repo.retry(); runCurrent()
        assertEquals(AuthenticationState.SignedIn(a), repo.state.value)
    }
    @Test fun `hung token exchange returns an actionable error instead of leaving the button busy`() = runTest {
        val source = Source().apply { exchangeWait = CompletableDeferred() }
        val repo = GoogleSessionRepository(source, AccountBootstrap {}, backgroundScope, timeoutMillis = 1000)
        runCurrent()
        var failure: AuthenticationFailure? = null
        val job = launch { failure = repo.signInWithGoogle("sensitive-token") }
        runCurrent(); advanceTimeBy(1001); runCurrent()
        assertEquals(AuthenticationFailure.NETWORK, failure)
        assertTrue(job.isCompleted)
        assertEquals(AuthenticationState.SignedOut, repo.state.value)
    }
    @Test fun `auth notification and explicit sign in continuation share one validation and log no identity`() = runTest {
        val source = Source(a)
        val server = CompletableDeferred<Unit>()
        var count = 0
        val events = mutableListOf<String>()
        val repo = GoogleSessionRepository(source, AccountBootstrap { count++; server.await() }, backgroundScope,
            diagnostic = events::add)
        runCurrent()
        assertNull(repo.signInWithGoogle("sensitive-token")); runCurrent()
        assertEquals(1, count)
        server.complete(Unit); runCurrent()
        assertEquals(AuthenticationState.SignedIn(a), repo.state.value)
        assertTrue(events.containsAll(listOf("firebase_exchange_started", "firebase_exchange_succeeded", "session_validation_started",
            "session_token_validated", "account_bootstrap_started", "account_bootstrap_succeeded", "session_ready")))
        for (privateValue in listOf("sensitive-token", a.uid, a.email!!)) assertFalse(events.joinToString().contains(privateValue))
    }

    @Test fun `a late validation error for old uid cannot sign out the newly selected SDK account`() = runTest {
        val source = Source(a)
        val reply = CompletableDeferred<GoogleAccount>()
        source.validationReply = reply
        val repo = GoogleSessionRepository(source, AccountBootstrap {}, backgroundScope)
        runCurrent()
        // SDK identity changed, but its queued auth notification has not been collected yet.
        source.exchangeAccount = b
        reply.complete(a); runCurrent()
        assertEquals(b, source.currentAccount)
        assertEquals(a, source.accounts.value) // Old validation must not call signOut on the newer SDK identity.
        assertEquals(AuthenticationState.Loading, repo.state.value)
        source.validationReply = null
        source.accounts.value = b; runCurrent()
        assertEquals(AuthenticationState.SignedIn(b), repo.state.value)
    }

}
