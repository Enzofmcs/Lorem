package dev.lorem.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.lorem.app.domain.auth.AuthenticationFailure
import dev.lorem.app.domain.auth.AuthenticationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthenticationViewModel(private val repository: AuthenticationRepository) : ViewModel() {
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
                mutableError.value = repository.signInWithGoogle(requestToken())
            } catch (_: GoogleSignInCancelled) {
                // Dismissing the account picker is a normal return to login.
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableError.value = AuthenticationFailure.UNKNOWN
            } finally {
                mutableBusy.value = false
            }
        }
    }

    fun signOut(clearCredentialState: suspend () -> Unit = {}) {
        signInJob?.cancel()
        mutableError.value = null
        repository.signOut() // Hide authenticated content before any asynchronous cleanup.
        viewModelScope.launch {
            try { clearCredentialState() }
            catch (error: Exception) { if (error is CancellationException) throw error }
        }
    }

    fun refresh() = repository.retry()

    companion object {
        fun factory(repository: AuthenticationRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AuthenticationViewModel(repository) as T
        }
    }
}
