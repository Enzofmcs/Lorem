package dev.lorem.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.lorem.app.domain.model.LocalProfile
import dev.lorem.app.domain.model.ProblemHistory
import dev.lorem.app.domain.ProblemHistorySyncResult
import dev.lorem.app.domain.ProblemCatalogSyncResult
import dev.lorem.app.domain.SynchronizeProblemCatalog
import dev.lorem.app.domain.model.CodeforcesProblem
import dev.lorem.app.domain.SynchronizeProblemHistory
import dev.lorem.app.domain.repository.CodeforcesRepository
import dev.lorem.app.domain.repository.LoremRepository
import dev.lorem.app.domain.repository.UserLookupResult
import dev.lorem.app.domain.RandomSource
import dev.lorem.app.domain.RecommendIpsum
import dev.lorem.app.domain.StartIpsum
import dev.lorem.app.domain.StartIpsumResult
import dev.lorem.app.domain.model.Ipsum
import kotlin.random.Random
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

sealed interface CatalogSyncUiState {
    data object Idle : CatalogSyncUiState
    data object Loading : CatalogSyncUiState
    data class Success(val problemCount: Int, val updatedAtEpochMillis: Long) : CatalogSyncUiState
    data class Error(val message: String, val savedProblemCount: Int) : CatalogSyncUiState
}

sealed interface StartIpsumUiState {
    data object Idle : StartIpsumUiState
    data object Loading : StartIpsumUiState
    data class Success(val ipsumId: Long) : StartIpsumUiState
    data class Error(val message: String) : StartIpsumUiState
}

class LoremViewModel(
    private val repository: LoremRepository,
    private val codeforcesRepository: CodeforcesRepository,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    randomSource: RandomSource = RandomSource { Random.Default.nextInt(it) },
) : ViewModel() {
    private val synchronizeProblemHistory = SynchronizeProblemHistory(
        loremRepository = repository,
        codeforcesRepository = codeforcesRepository,
        nowMillis = nowMillis,
    )
    private val synchronizeProblemCatalog = SynchronizeProblemCatalog(repository, codeforcesRepository, nowMillis)
    private val startIpsum = StartIpsum(repository, RecommendIpsum(randomSource), nowMillis)
    val uiState: StateFlow<LoremUiState> = repository.profile
        .map<LocalProfile?, LoremUiState>(LoremUiState::Ready)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LoremUiState.Loading,
        )

    val problemHistory: StateFlow<List<ProblemHistory>> = repository.problemHistory
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )
    val problemCatalog: StateFlow<List<CodeforcesProblem>> = repository.problemCatalog
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val catalogLastUpdatedEpochMillis: StateFlow<Long?> = repository.catalogLastUpdatedEpochMillis
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val activeIpsum: StateFlow<Ipsum?> = repository.activeIpsum
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val mutableConfigurationState = MutableStateFlow(ConfigurationUiState())
    val configurationState: StateFlow<ConfigurationUiState> =
        mutableConfigurationState.asStateFlow()

    private val mutableHistorySyncState = MutableStateFlow<HistorySyncUiState>(HistorySyncUiState.Idle)
    val historySyncState: StateFlow<HistorySyncUiState> = mutableHistorySyncState.asStateFlow()
    private val mutableCatalogSyncState = MutableStateFlow<CatalogSyncUiState>(CatalogSyncUiState.Idle)
    val catalogSyncState: StateFlow<CatalogSyncUiState> = mutableCatalogSyncState.asStateFlow()
    private val mutableStartIpsumState = MutableStateFlow<StartIpsumUiState>(StartIpsumUiState.Idle)
    val startIpsumState: StateFlow<StartIpsumUiState> = mutableStartIpsumState.asStateFlow()

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

    fun synchronizeCatalog() {
        if (mutableCatalogSyncState.value == CatalogSyncUiState.Loading) return
        mutableCatalogSyncState.value = CatalogSyncUiState.Loading
        viewModelScope.launch {
            mutableCatalogSyncState.value = when (val result = synchronizeProblemCatalog()) {
                is ProblemCatalogSyncResult.Success -> CatalogSyncUiState.Success(
                    result.eligibleProblemCount,
                    result.updatedAtEpochMillis,
                )
                is ProblemCatalogSyncResult.Failed -> catalogError(
                    "Codeforces recusou a atualização: ${result.message}", result.savedProblemCount,
                )
                is ProblemCatalogSyncResult.RateLimited -> catalogError(
                    "Limite do Codeforces atingido. Aguarde e tente novamente.", result.savedProblemCount,
                )
                is ProblemCatalogSyncResult.NetworkFailure -> catalogError(
                    "Sem conexão. O catálogo salvo continua disponível.", result.savedProblemCount,
                )
                is ProblemCatalogSyncResult.HttpFailure -> catalogError(
                    "Falha HTTP ${result.statusCode}. O catálogo salvo continua disponível.", result.savedProblemCount,
                )
                is ProblemCatalogSyncResult.InvalidResponse -> catalogError(
                    "Resposta inválida. O catálogo salvo continua disponível.", result.savedProblemCount,
                )
                is ProblemCatalogSyncResult.PersistenceFailure -> catalogError(
                    "Não foi possível salvar o catálogo.", result.savedProblemCount,
                )
            }
        }
    }

    private fun catalogError(message: String, savedProblemCount: Int) =
        CatalogSyncUiState.Error(message, savedProblemCount)

    fun startNewIpsum() {
        if (mutableStartIpsumState.value == StartIpsumUiState.Loading) return
        mutableStartIpsumState.value = StartIpsumUiState.Loading
        viewModelScope.launch {
            mutableStartIpsumState.value = when (val result = startIpsum()) {
                is StartIpsumResult.Success -> StartIpsumUiState.Success(result.ipsum.id)
                StartIpsumResult.AlreadyActive -> StartIpsumUiState.Error("Já existe um Ipsum ativo. Continue-o antes de iniciar outro.")
                StartIpsumResult.NoCandidate -> StartIpsumUiState.Error(
                    "Nenhum problema inédito foi encontrado até 300 pontos da faixa. Atualize catálogo e histórico e tente novamente.",
                )
                StartIpsumResult.NoProfile -> StartIpsumUiState.Error("Configure um perfil antes de iniciar um Ipsum.")
                StartIpsumResult.PersistenceFailure -> StartIpsumUiState.Error("Não foi possível salvar o Ipsum. Tente novamente.")
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
