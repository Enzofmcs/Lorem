package dev.lorem.app.data.auth

import androidx.annotation.MainThread
import dev.lorem.app.domain.auth.AuthenticationFailure
import dev.lorem.app.domain.auth.AuthenticationRepository
import dev.lorem.app.domain.auth.AuthenticationState
import dev.lorem.app.domain.auth.GoogleAccount
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** SDK boundary: tests control identities and pending network responses without Firebase fakes in production. */
interface GoogleIdentitySource {
    val accounts: Flow<GoogleAccount?>
    val currentAccount: GoogleAccount?
    suspend fun signIn(idToken: String)
    suspend fun validate(expectedUid: String): GoogleAccount
    fun signOut()
}

fun interface AccountBootstrap {
    suspend fun ensureAccount(expectedUid: String)
}

class AuthenticationException(val reason: AuthenticationFailure) : Exception()

/** Commands and the owned scope use the main dispatcher, including task completion callbacks. */
@MainThread
class GoogleSessionRepository(
    private val source: GoogleIdentitySource,
    private val bootstrap: AccountBootstrap,
    private val scope: CoroutineScope,
    private val timeoutMillis: Long = 30_000,
    private val diagnostic: (String) -> Unit = {},
) : AuthenticationRepository {
    private val mutableState = MutableStateFlow<AuthenticationState>(AuthenticationState.Loading)
    override val state: StateFlow<AuthenticationState> = mutableState.asStateFlow()
    private val generation = AtomicLong()
    private var verification: Job? = null
    private var verifyingUid: String? = null
    private var signedOutFailure: AuthenticationFailure? = null

    init {
        scope.launch { source.accounts.collectLatest { verify(it) } }
    }

    private fun verify(account: GoogleAccount?) {
        if (account != null && verifyingUid == account.uid && verification?.isActive == true) return
        val expectedGeneration = generation.incrementAndGet()
        verification?.cancel()
        verifyingUid = account?.uid
        if (account == null) {
            mutableState.value = signedOutFailure?.let { AuthenticationState.Failed(it, false) }
                ?: AuthenticationState.SignedOut
            return
        }
        diagnostic("session_validation_started")
        mutableState.value = AuthenticationState.Loading
        verification = scope.launch {
            try {
                val validated = withTimeout(timeoutMillis) {
                    val result = source.validate(account.uid)
                    if (result.uid != account.uid || source.currentAccount?.uid != account.uid) {
                        throw AuthenticationException(AuthenticationFailure.INVALID_SESSION)
                    }
                    diagnostic("session_token_validated")
                    diagnostic("account_bootstrap_started")
                    bootstrap.ensureAccount(account.uid)
                    diagnostic("account_bootstrap_succeeded")
                    result
                }
                if (generation.get() == expectedGeneration && source.currentAccount?.uid == account.uid) {
                    diagnostic("session_ready")
                    mutableState.value = AuthenticationState.SignedIn(validated)
                }
            } catch (error: Exception) {
                if (error is CancellationException && error !is TimeoutCancellationException) throw error
                if (generation.get() != expectedGeneration || source.currentAccount?.uid != account.uid) return@launch
                val failure = failureOf(error)
                diagnostic("session_failed:${failure.name}")
                val invalid = failure == AuthenticationFailure.INVALID_SESSION ||
                    failure == AuthenticationFailure.PROVIDER_NOT_ALLOWED
                if (invalid) {
                    signedOutFailure = failure
                    source.signOut()
                }
                mutableState.value = AuthenticationState.Failed(failure, !invalid)
            }
        }
    }

    override suspend fun signInWithGoogle(idToken: String): AuthenticationFailure? {
        if (idToken.isBlank()) return AuthenticationFailure.INVALID_SESSION
        signedOutFailure = null
        return try {
            diagnostic("firebase_exchange_started")
            withTimeout(timeoutMillis) { source.signIn(idToken) }
            diagnostic("firebase_exchange_succeeded")
            val account = source.currentAccount ?: throw AuthenticationException(AuthenticationFailure.INVALID_SESSION)
            // The SDK may emit the same identity again, or its listener may arrive after the Task.
            // Do not depend on a distinct auth notification to resume the selected account.
            val authorized = mutableState.value as? AuthenticationState.SignedIn
            if (authorized?.account?.uid != account.uid) verify(account)
            null // Only validation + confirmed Firestore transaction may publish SignedIn.
        } catch (error: Exception) {
            if (error is CancellationException && error !is TimeoutCancellationException) throw error
            failureOf(error).also { diagnostic("firebase_exchange_failed:${it.name}") }
        }
    }

    override fun signOut() {
        generation.incrementAndGet()
        verification?.cancel()
        signedOutFailure = null
        mutableState.value = AuthenticationState.SignedOut
        source.signOut()
    }

    override fun retry() {
        if (verification?.isActive != true) verify(source.currentAccount)
    }

    private fun failureOf(error: Exception) = when (error) {
        is TimeoutCancellationException -> AuthenticationFailure.NETWORK
        is AuthenticationException -> error.reason
        else -> AuthenticationFailure.UNKNOWN
    }
}
