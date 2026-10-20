package dev.lorem.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.lorem.app.data.auth.authLog
import dev.lorem.app.domain.auth.AuthenticationFailure
import dev.lorem.app.domain.auth.AuthenticationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthenticationViewModel(private val repository: AuthenticationRepository,
    private val diagnostic: (String) -> Unit = {}) : ViewModel() {
    val session = repository.state
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private val mutableError = MutableStateFlow<AuthenticationFailure?>(null)
    val error = mutableError.asStateFlow()
    private var signInJob: Job? = null

    fun signIn(requestToken: suspend () -> String) {
        if (signInJob?.isActive == true) return
        signInJob = viewModelScope.launch {
            mutableBusy.value = true
            mutableError.value = null
            try {
                diagnostic("google_picker_opened")
                val token = requestToken()
                diagnostic("google_credential_received")
                mutableError.value = repository.signInWithGoogle(token)
                mutableError.value?.let { diagnostic("sign_in_failed:${it.name}") }
            } catch (_: GoogleSignInCancelled) {
                diagnostic("google_picker_interrupted")
                mutableError.value = AuthenticationFailure.CANCELLED
            } catch (_: GoogleCredentialUnavailable) {
                diagnostic("google_credential_unavailable")
                mutableError.value = AuthenticationFailure.CREDENTIAL_UNAVAILABLE
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                diagnostic("sign_in_exception:${error.javaClass.simpleName}")
                mutableError.value = AuthenticationFailure.UNKNOWN
            } finally {
                mutableBusy.value = false
            }
        }
    }

    fun signOut(clearCredentialState: suspend () -> Unit = {}) {
        diagnostic("sign_out")
        signInJob?.cancel()
        mutableError.value = null
        repository.signOut() // Hide authenticated content before any asynchronous cleanup.
        viewModelScope.launch {
            try { clearCredentialState() }
            catch (error: Exception) { if (error is CancellationException) throw error }
        }
    }

    fun refresh() {
        // Returning from the account picker raises ON_START; it must not restart an owned sign-in.
        if (signInJob?.isActive != true) repository.retry()
    }

    companion object {
        fun factory(repository: AuthenticationRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AuthenticationViewModel(repository, ::authLog) as T
        }
    }
}
