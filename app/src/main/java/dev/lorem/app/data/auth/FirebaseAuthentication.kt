package dev.lorem.app.data.auth

import android.content.Context
import android.annotation.SuppressLint
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import dev.lorem.app.domain.auth.AuthenticationFailure
import dev.lorem.app.domain.auth.AuthenticationRepository
import dev.lorem.app.domain.auth.AuthenticationState
import dev.lorem.app.domain.auth.GoogleAccount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await

data class FirebaseAuthentication(
    val repository: AuthenticationRepository,
    val googleWebClientId: String?,
) {
    companion object {
        private const val REGION = "us-central1"

        fun create(context: Context, scope: CoroutineScope): FirebaseAuthentication = try {
            configured(context, scope)
        } catch (_: IllegalArgumentException) {
            unavailable()
        } catch (_: IllegalStateException) {
            unavailable()
        }

        private fun unavailable() = FirebaseAuthentication(UnconfiguredAuthenticationRepository(), null)

        // google-services generates this optional resource only for a configured OAuth client.
        @SuppressLint("DiscouragedApi")
        private fun configured(context: Context, scope: CoroutineScope): FirebaseAuthentication {
            val app = FirebaseApp.initializeApp(context)
            val clientIdResource = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            val clientId = clientIdResource.takeIf { it != 0 }?.let(context::getString)?.takeIf { it.isNotBlank() }
            if (app == null || clientId == null || app.options.projectId.isNullOrBlank()) {
                return unavailable()
            }
            val auth = FirebaseAuth.getInstance(app)
            val functions = FirebaseFunctions.getInstance(app, REGION)
            val bootstrap = AccountBootstrap {
                val result = firebaseCall { functions.getHttpsCallable("bootstrapAccount").call(emptyMap<String, Any>()).await() }
                if ((result.data as? Map<*, *>)?.get("created") !is Boolean) {
                    throw AuthenticationException(AuthenticationFailure.CONFIGURATION)
                }
            }
            return FirebaseAuthentication(GoogleSessionRepository(FirebaseGoogleIdentitySource(auth), bootstrap, scope), clientId)
        }
    }
}

private class FirebaseGoogleIdentitySource(private val auth: FirebaseAuth) : GoogleIdentitySource {
    override val currentAccount: GoogleAccount? get() = auth.currentUser?.account()
    override val accounts = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.account()) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    override suspend fun signIn(idToken: String) {
        firebaseCall { auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await() }
    }

    override suspend fun validate(expectedUid: String): GoogleAccount = firebaseCall {
        val user = auth.currentUser?.takeIf { it.uid == expectedUid }
            ?: throw AuthenticationException(AuthenticationFailure.INVALID_SESSION)
        val claims = user.getIdToken(true).await().claims["firebase"] as? Map<*, *>
        if (claims?.get("sign_in_provider") != "google.com") {
            throw AuthenticationException(AuthenticationFailure.PROVIDER_NOT_ALLOWED)
        }
        if (auth.currentUser?.uid != expectedUid) throw AuthenticationException(AuthenticationFailure.INVALID_SESSION)
        user.account()
    }

    override fun signOut() = auth.signOut()
    private fun FirebaseUser.account() = GoogleAccount(uid, displayName, email)
}

private class UnconfiguredAuthenticationRepository : AuthenticationRepository {
    override val state = MutableStateFlow<AuthenticationState>(
        AuthenticationState.Failed(AuthenticationFailure.CONFIGURATION, false),
    )
    override suspend fun signInWithGoogle(idToken: String) = AuthenticationFailure.CONFIGURATION
    override fun signOut() = Unit
    override fun retry() = Unit
}

private suspend fun <T> firebaseCall(block: suspend () -> T): T = try {
    block()
} catch (error: Exception) {
    if (error is CancellationException || error is AuthenticationException) throw error
    val reason = when (error) {
        is FirebaseNetworkException -> AuthenticationFailure.NETWORK
        is FirebaseAuthInvalidUserException, is FirebaseAuthInvalidCredentialsException -> AuthenticationFailure.INVALID_SESSION
        is FirebaseFunctionsException -> when (error.code) {
            FirebaseFunctionsException.Code.UNAUTHENTICATED -> AuthenticationFailure.INVALID_SESSION
            FirebaseFunctionsException.Code.PERMISSION_DENIED -> AuthenticationFailure.PROVIDER_NOT_ALLOWED
            FirebaseFunctionsException.Code.UNAVAILABLE, FirebaseFunctionsException.Code.DEADLINE_EXCEEDED -> AuthenticationFailure.NETWORK
            FirebaseFunctionsException.Code.NOT_FOUND, FirebaseFunctionsException.Code.FAILED_PRECONDITION -> AuthenticationFailure.CONFIGURATION
            else -> AuthenticationFailure.UNKNOWN
        }
        else -> AuthenticationFailure.UNKNOWN
    }
    throw AuthenticationException(reason)
}
