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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import dev.lorem.app.domain.auth.AuthenticationFailure
import dev.lorem.app.domain.auth.AuthenticationRepository
import dev.lorem.app.domain.auth.AuthenticationState
import dev.lorem.app.domain.auth.GoogleAccount
import dev.lorem.app.data.handles.HandlesRemoteStore
import dev.lorem.app.data.handles.FirestoreHandlesStore
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
    val handlesStore: HandlesRemoteStore? = null,
    val firestore: FirebaseFirestore? = null,
    val auth: FirebaseAuth? = null,
) {
    companion object {
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
            val firestore = FirebaseFirestore.getInstance(app)
            val bootstrap = FirestoreAccountBootstrap(auth, firestore)
            return FirebaseAuthentication(GoogleSessionRepository(FirebaseGoogleIdentitySource(auth), bootstrap, scope),
                clientId, FirestoreHandlesStore(auth, firestore), firestore, auth)
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

internal suspend fun <T> firebaseCall(block: suspend () -> T): T = try {
    block()
} catch (error: Exception) {
    if (error is CancellationException || error is AuthenticationException) throw error
    throw AuthenticationException(firebaseFailureOf(error))
}

internal fun firebaseFailureOf(error: Exception): AuthenticationFailure = when (error) {
    is FirebaseNetworkException -> AuthenticationFailure.NETWORK
    is FirebaseAuthInvalidUserException, is FirebaseAuthInvalidCredentialsException -> AuthenticationFailure.INVALID_SESSION
    is FirebaseFirestoreException -> when (error.code) {
        FirebaseFirestoreException.Code.UNAUTHENTICATED -> AuthenticationFailure.INVALID_SESSION
        // The Google claim was already validated. Rules may be missing or incompatible.
        FirebaseFirestoreException.Code.PERMISSION_DENIED,
        FirebaseFirestoreException.Code.NOT_FOUND,
        FirebaseFirestoreException.Code.FAILED_PRECONDITION -> AuthenticationFailure.CONFIGURATION
        FirebaseFirestoreException.Code.UNAVAILABLE,
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
        FirebaseFirestoreException.Code.ABORTED -> AuthenticationFailure.NETWORK
        FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED -> AuthenticationFailure.QUOTA
        else -> AuthenticationFailure.UNKNOWN
    }
    else -> AuthenticationFailure.UNKNOWN
}
