package dev.lorem.app.data.auth

import androidx.annotation.MainThread
import dev.lorem.app.domain.auth.AuthenticationFailure
import dev.lorem.app.domain.auth.AuthenticationRepository
import dev.lorem.app.domain.auth.AuthenticationState
import dev.lorem.app.domain.auth.GoogleAccount
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
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
) : AuthenticationRepository {
    private val mutableState = MutableStateFlow<AuthenticationState>(AuthenticationState.Loading)
    override val state: StateFlow<AuthenticationState> = mutableState.asStateFlow()
    private val generation = AtomicLong()
    private var verification: Job? = null
    private var signedOutFailure: AuthenticationFailure? = null

    init {
        scope.launch { source.accounts.collectLatest { verify(it) } }
    }

    private fun verify(account: GoogleAccount?) {
        val expectedGeneration = generation.incrementAndGet()
        verification?.cancel()
        if (account == null) {
            mutableState.value = signedOutFailure?.let { AuthenticationState.Failed(it, false) }
                ?: AuthenticationState.SignedOut
            return
        }
        mutableState.value = AuthenticationState.Loading
        verification = scope.launch {
            try {
                val validated = source.validate(account.uid)
                if (validated.uid != account.uid || source.currentAccount?.uid != account.uid) return@launch
                bootstrap.ensureAccount(account.uid)
                if (generation.get() == expectedGeneration && source.currentAccount?.uid == account.uid) {
                    mutableState.value = AuthenticationState.SignedIn(validated)
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (generation.get() != expectedGeneration) return@launch
                val failure = failureOf(error)
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
            source.signIn(idToken)
            null // Only token validation + a confirmed Firestore transaction can publish SignedIn.
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            failureOf(error)
        }
    }

    override fun signOut() {
        generation.incrementAndGet()
        verification?.cancel()
        signedOutFailure = null
        mutableState.value = AuthenticationState.SignedOut
        source.signOut()
    }

    override fun retry() = verify(source.currentAccount)

    private fun failureOf(error: Exception) = (error as? AuthenticationException)?.reason
        ?: AuthenticationFailure.UNKNOWN
}
