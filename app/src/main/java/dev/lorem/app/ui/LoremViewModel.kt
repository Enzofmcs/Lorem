package dev.lorem.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.lorem.app.domain.model.LocalProfile
import dev.lorem.app.domain.ProblemHistorySyncResult
import dev.lorem.app.domain.SynchronizeProblemHistory
import dev.lorem.app.domain.repository.CodeforcesRepository
import dev.lorem.app.domain.repository.LoremRepository
import dev.lorem.app.domain.repository.UserLookupResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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

sealed interface HistorySyncUiState {
    data object Idle : HistorySyncUiState
    data object Loading : HistorySyncUiState
    data class Success(val problemCount: Int) : HistorySyncUiState
    data class Error(val message: String) : HistorySyncUiState
}

class LoremViewModel(
    private val repository: LoremRepository,
    private val codeforcesRepository: CodeforcesRepository,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val synchronizeProblemHistory = SynchronizeProblemHistory(
        loremRepository = repository,
        codeforcesRepository = codeforcesRepository,
        nowMillis = nowMillis,
    )
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

    private val mutableHistorySyncState = MutableStateFlow<HistorySyncUiState>(HistorySyncUiState.Idle)
    val historySyncState: StateFlow<HistorySyncUiState> = mutableHistorySyncState.asStateFlow()

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
        val savedProfile = repository.profile.first()
        val sameOwner = savedProfile?.handle?.trim()?.equals(user.handle.trim(), ignoreCase = true) == true
        val profile = LocalProfile(
            handle = user.handle,
            displayName = user.displayName,
            officialRating = user.rating,
            loremRating = savedProfile?.loremRating?.takeIf { sameOwner }
                ?: user.rating
                ?: INITIAL_UNRATED_RATING,
            consolidatedRating = savedProfile?.consolidatedRating?.takeIf { sameOwner },
            lastSyncEpochMillis = savedProfile?.lastSyncEpochMillis?.takeIf { sameOwner } ?: 0L,
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

    fun synchronizeHistory() {
        if (mutableHistorySyncState.value == HistorySyncUiState.Loading) return
        mutableHistorySyncState.value = HistorySyncUiState.Loading
        viewModelScope.launch {
            mutableHistorySyncState.value = when (val result = synchronizeProblemHistory()) {
                is ProblemHistorySyncResult.Success -> HistorySyncUiState.Success(result.problemCount)
                ProblemHistorySyncResult.NoActiveProfile -> HistorySyncUiState.Error("Nenhum perfil ativo para sincronizar.")
                is ProblemHistorySyncResult.Failed -> HistorySyncUiState.Error(
                    "Codeforces recusou a sincronização: ${result.message}",
                )
                ProblemHistorySyncResult.RateLimited -> HistorySyncUiState.Error(
                    "Limite do Codeforces atingido. Aguarde e tente novamente.",
                )
                ProblemHistorySyncResult.NetworkFailure -> HistorySyncUiState.Error(
                    "Sem conexão com o Codeforces. Verifique a internet e tente novamente.",
                )
                is ProblemHistorySyncResult.HttpFailure -> HistorySyncUiState.Error(
                    "Falha HTTP ${result.statusCode} ao sincronizar. Tente novamente.",
                )
                ProblemHistorySyncResult.InvalidResponse -> HistorySyncUiState.Error(
                    "O Codeforces enviou uma resposta inválida. Tente novamente.",
                )
                ProblemHistorySyncResult.PersistenceFailure -> HistorySyncUiState.Error(
                    "Não foi possível salvar o histórico. Tente novamente.",
                )
            }
        }
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
