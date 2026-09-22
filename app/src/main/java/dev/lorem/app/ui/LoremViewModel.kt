package dev.lorem.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.lorem.app.domain.model.LocalProfile
import dev.lorem.app.domain.repository.CodeforcesRepository
import dev.lorem.app.domain.repository.LoremRepository
import dev.lorem.app.domain.repository.UserLookupResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface LoremUiState {
    data object Loading : LoremUiState
    data class Ready(val profile: LocalProfile?) : LoremUiState
}

data class ConfigurationUiState(
    val handle: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val savedProfile: LocalProfile? = null,
    val navigateHome: Boolean = false,
)

class LoremViewModel(
    private val repository: LoremRepository,
    private val codeforcesRepository: CodeforcesRepository,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    val uiState: StateFlow<LoremUiState> = repository.profile
        .map<LocalProfile?, LoremUiState>(LoremUiState::Ready)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LoremUiState.Loading,
        )

    private val mutableConfigurationState = MutableStateFlow(ConfigurationUiState())
    val configurationState: StateFlow<ConfigurationUiState> =
        mutableConfigurationState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.profile.collect { profile ->
                if (profile != null && mutableConfigurationState.value.handle.isBlank()) {
                    mutableConfigurationState.value = mutableConfigurationState.value.copy(
                        handle = profile.handle,
                        savedProfile = profile,
                    )
                }
            }
        }
    }

    fun updateHandle(handle: String) {
        if (mutableConfigurationState.value.isLoading) return
        mutableConfigurationState.value = mutableConfigurationState.value.copy(
            handle = handle,
            errorMessage = null,
            navigateHome = false,
        )
    }

    fun confirmHandle() {
        val state = mutableConfigurationState.value
        val handle = state.handle.trim()
        if (handle.isBlank() || state.isLoading) return
        mutableConfigurationState.value = state.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = codeforcesRepository.user(handle)) {
                is UserLookupResult.Success -> persist(result)
                UserLookupResult.UserNotFound -> showError("Usuário não encontrado no Codeforces.")
                is UserLookupResult.ApiFailure -> showError("Codeforces recusou a consulta: ${result.message}")
                UserLookupResult.RateLimited -> showError("Limite do Codeforces atingido. Aguarde e tente novamente.")
                UserLookupResult.NetworkFailure -> showError("Sem conexão com o Codeforces. Verifique a internet e tente novamente.")
            }
        }
    }

    private suspend fun persist(result: UserLookupResult.Success) {
        val user = result.user
        val profile = LocalProfile(
            handle = user.handle,
            displayName = user.displayName,
            officialRating = user.rating,
            loremRating = user.rating ?: INITIAL_UNRATED_RATING,
            consolidatedRating = null,
            lastSyncEpochMillis = nowMillis(),
        )
        try {
            repository.saveProfile(profile)
            mutableConfigurationState.value = mutableConfigurationState.value.copy(
                handle = profile.handle,
                isLoading = false,
                savedProfile = profile,
                navigateHome = true,
            )
        } catch (_: Exception) {
            showError("Não foi possível salvar o perfil. Tente novamente.")
        }
    }

    private fun showError(message: String) {
        mutableConfigurationState.value = mutableConfigurationState.value.copy(
            isLoading = false,
            errorMessage = message,
            navigateHome = false,
        )
    }

    fun homeNavigationHandled() {
        mutableConfigurationState.value = mutableConfigurationState.value.copy(navigateHome = false)
    }

    companion object {
        const val INITIAL_UNRATED_RATING = 800

        fun factory(
            repository: LoremRepository,
            codeforcesRepository: CodeforcesRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(LoremViewModel::class.java))
                return LoremViewModel(repository, codeforcesRepository) as T
            }
        }
    }
}
