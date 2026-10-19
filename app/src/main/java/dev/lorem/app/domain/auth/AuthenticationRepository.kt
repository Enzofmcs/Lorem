package dev.lorem.app.domain.auth

import kotlinx.coroutines.flow.StateFlow

data class GoogleAccount(val uid: String, val displayName: String?, val email: String?)

enum class AuthenticationFailure {
    CONFIGURATION, NETWORK, INVALID_SESSION, PROVIDER_NOT_ALLOWED, UNKNOWN,
}

sealed interface AuthenticationState {
    data object Loading : AuthenticationState
    data object SignedOut : AuthenticationState
    data class SignedIn(val account: GoogleAccount) : AuthenticationState
    data class Failed(val reason: AuthenticationFailure, val canRetry: Boolean) : AuthenticationState
}

interface AuthenticationRepository {
    val state: StateFlow<AuthenticationState>
    suspend fun signInWithGoogle(idToken: String): AuthenticationFailure?
    fun signOut()
    fun retry()
}
