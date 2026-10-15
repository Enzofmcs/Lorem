package dev.lorem.app.ui

import dev.lorem.app.domain.sync.ProfileLinkChanged
import dev.lorem.app.domain.sync.SyncDataset
import dev.lorem.app.domain.sync.SynchronizationState
import dev.lorem.app.domain.sync.syncKey
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withTimeoutOrNull
import dev.lorem.app.domain.sync.ProfileLink
import dev.lorem.app.domain.sync.SynchronizationCoordinator
import kotlinx.coroutines.CancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.lorem.app.domain.model.LocalProfile
import dev.lorem.app.domain.model.ProblemHistory
import dev.lorem.app.domain.ProblemHistorySyncResult
import dev.lorem.app.domain.ProblemCatalogSyncResult
import dev.lorem.app.domain.model.CodeforcesProblem
import dev.lorem.app.domain.repository.CodeforcesRepository
import dev.lorem.app.domain.repository.LoremRepository
import dev.lorem.app.domain.repository.UserLookupResult
import dev.lorem.app.domain.RandomSource
import dev.lorem.app.domain.RecommendIpsum
import dev.lorem.app.domain.ConsolidationProgress
import dev.lorem.app.domain.calculateConsolidatedRating
import dev.lorem.app.domain.StartIpsum
import dev.lorem.app.domain.StartIpsumResult
import dev.lorem.app.domain.VerifyIpsumResult
import dev.lorem.app.domain.UpdatePendingIpsumsResult
import dev.lorem.app.domain.IpsumStatistics
import dev.lorem.app.domain.buildIpsumStatistics
import dev.lorem.app.domain.model.Ipsum
import dev.lorem.app.domain.model.IpsumFailureReason
import dev.lorem.app.domain.model.IpsumResult
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

sealed interface LoremUiState {
    data object Loading : LoremUiState
    data class Ready(val profile: LocalProfile?, val link: ProfileLink? = null) : LoremUiState
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

sealed interface SubmissionCheckUiState {
    data object Idle : SubmissionCheckUiState
    data object Loading : SubmissionCheckUiState
    data class Success(val newSubmissionCount: Int, val errorCount: Int, val completed: Boolean, val ipsumId: Long? = null) : SubmissionCheckUiState
    data class Error(val message: String) : SubmissionCheckUiState
}

sealed interface IpsumResultUiState {
    data object Idle : IpsumResultUiState
    data object Loading : IpsumResultUiState
    data class Ready(val result: IpsumResult) : IpsumResultUiState
    data class Error(val message: String) : IpsumResultUiState
}

sealed interface PendingUpdateUiState {
    data object Idle : PendingUpdateUiState
    data object Loading : PendingUpdateUiState
    data class Success(val movedCount: Int) : PendingUpdateUiState
    data class Error(val message: String) : PendingUpdateUiState
}

sealed interface StatisticsUiState {
    data object Loading : StatisticsUiState
    data object NoProfile : StatisticsUiState
    data class Ready(val statistics: IpsumStatistics) : StatisticsUiState
}

data class ConsolidationUiState(val ownerHandle: String, val progress: ConsolidationProgress)

data class PreparationUiState(
    val owner: ProfileLink?,
    val history: SynchronizationState = SynchronizationState(),
    val catalog: SynchronizationState = SynchronizationState(),
    val pending: SynchronizationState = SynchronizationState(),
    val metadata: SynchronizationState = SynchronizationState(),
    val hasExpiredData: Boolean = false,
)

data class IpsumTrackingUiState(
    val owner: ProfileLink?,
    val activeIpsumId: Long?,
    val lastFinishedIpsumId: Long?,
    val synchronization: SynchronizationState = SynchronizationState(),
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LoremViewModel(
    private val repository: LoremRepository,
    private val codeforcesRepository: CodeforcesRepository,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    randomSource: RandomSource = RandomSource { Random.Default.nextInt(it) },
    statisticsDispatcher: CoroutineDispatcher = Dispatchers.Default,
    synchronizationCoordinator: SynchronizationCoordinator? = null,
    submissionPollingWait: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) : ViewModel() {
    private val coordinator = synchronizationCoordinator ?: SynchronizationCoordinator(
        repository, codeforcesRepository, viewModelScope, nowMillis = nowMillis,
    )
    private val submissionMonitor = dev.lorem.app.domain.sync.IpsumSubmissionMonitor(repository, coordinator, submissionPollingWait)
    suspend fun monitorIpsum(ipsumId: Long) = submissionMonitor.monitor(ipsumId)

    val preparationState: StateFlow<PreparationUiState?> = repository.profileLink.distinctUntilChanged().flatMapLatest { link ->
        fun observe(dataset: SyncDataset) = if (link == null && dataset != SyncDataset.CATALOG)
            kotlinx.coroutines.flow.flowOf(SynchronizationState()) else coordinator.store.observe(syncKey(dataset, link))
        combine(observe(SyncDataset.HISTORY), observe(SyncDataset.CATALOG), observe(SyncDataset.PENDING), observe(SyncDataset.PROFILE)) { history, catalog, pending, metadata ->
            val now = nowMillis()
            val expired = listOf(history to dev.lorem.app.domain.sync.BackgroundSynchronization.QUARTER_HOUR,
                pending to dev.lorem.app.domain.sync.BackgroundSynchronization.QUARTER_HOUR,
                catalog to dev.lorem.app.domain.sync.BackgroundSynchronization.DAY,
                metadata to dev.lorem.app.domain.sync.BackgroundSynchronization.DAY)
                .any { (state, ttl) -> state.lastSuccessEpochMillis?.let { now - it >= ttl } == true }
            PreparationUiState(link, history, catalog, pending, metadata, expired)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val trackingState: StateFlow<IpsumTrackingUiState?> = repository.profileLink.distinctUntilChanged().flatMapLatest { link ->
        combine(repository.activeIpsum, repository.ipsums) { active, attempts ->
            val owned = attempts.filter { ProfileLink.of(it.ownerHandle, link?.version ?: 0) == link }
            IpsumTrackingUiState(link,
                active?.takeIf { ProfileLink.of(it.ownerHandle, link?.version ?: 0) == link }?.id,
                owned.lastOrNull { it.endedAtEpochMillis != null }?.id)
        }.flatMapLatest { session ->
            if (link == null || session.activeIpsumId == null) kotlinx.coroutines.flow.flowOf(session)
            else coordinator.store.observe(syncKey(SyncDataset.SUBMISSIONS, link, session.activeIpsumId))
                .map { session.copy(synchronization = it) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val mutableDataSourceNotice = MutableStateFlow<String?>(null)
    val dataSourceNotice = mutableDataSourceNotice.asStateFlow()
    private val startIpsum = StartIpsum(repository, RecommendIpsum(randomSource), nowMillis)
    val uiState: StateFlow<LoremUiState> = combine(repository.profile, repository.profileLink) { profile, link ->
        LoremUiState.Ready(profile, link?.takeIf { it.handle == profile?.handle?.let { handle -> ProfileLink.of(handle, 0).handle } }) as LoremUiState
    }
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
    val ipsums: StateFlow<List<Ipsum>> = repository.ipsums
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val statisticsState: StateFlow<StatisticsUiState> = combine(
        repository.profile, repository.ipsums, repository.problemCatalog,
    ) { profile, attempts, catalog ->
        if (profile == null) StatisticsUiState.NoProfile
        else StatisticsUiState.Ready(buildIpsumStatistics(profile, attempts, catalog))
    }.flowOn(statisticsDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatisticsUiState.Loading)
    val consolidationState: StateFlow<ConsolidationUiState?> = combine(repository.profile, repository.ipsums) { profile, attempts ->
        profile?.let { ConsolidationUiState(it.handle, calculateConsolidatedRating(attempts, it.consolidatedRating, it.handle, it.loremRating)) }
    }.flowOn(statisticsDispatcher).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val mutableConfigurationState = MutableStateFlow(ConfigurationUiState())
    val configurationState: StateFlow<ConfigurationUiState> =
        mutableConfigurationState.asStateFlow()

    private val mutableHistorySyncState = MutableStateFlow<HistorySyncUiState>(HistorySyncUiState.Idle)
    val historySyncState: StateFlow<HistorySyncUiState> = mutableHistorySyncState.asStateFlow()
    private val mutableCatalogSyncState = MutableStateFlow<CatalogSyncUiState>(CatalogSyncUiState.Idle)
    val catalogSyncState: StateFlow<CatalogSyncUiState> = mutableCatalogSyncState.asStateFlow()
    private val mutableStartIpsumState = MutableStateFlow<StartIpsumUiState>(StartIpsumUiState.Idle)
    val startIpsumState: StateFlow<StartIpsumUiState> = mutableStartIpsumState.asStateFlow()
    private val mutableSubmissionCheckState = MutableStateFlow<SubmissionCheckUiState>(SubmissionCheckUiState.Idle)
    val submissionCheckState: StateFlow<SubmissionCheckUiState> = mutableSubmissionCheckState.asStateFlow()
    private val mutableIpsumResultState = MutableStateFlow<IpsumResultUiState>(IpsumResultUiState.Idle)
    val ipsumResultState: StateFlow<IpsumResultUiState> = mutableIpsumResultState.asStateFlow()
    private val mutablePendingUpdateState = MutableStateFlow<PendingUpdateUiState>(PendingUpdateUiState.Idle)
    val pendingUpdateState: StateFlow<PendingUpdateUiState> = mutablePendingUpdateState.asStateFlow()

    init {
        viewModelScope.launch {
            var previous = repository.profileLink.first()
            repository.profileLink.collect { link ->
                if (link != previous) {
                    mutableConfigurationState.value = mutableConfigurationState.value.copy(isLoading = false)
                    mutableDataSourceNotice.value = null
                    mutableHistorySyncState.value = HistorySyncUiState.Idle
                    mutableSubmissionCheckState.value = SubmissionCheckUiState.Idle
                    mutablePendingUpdateState.value = PendingUpdateUiState.Idle
                    mutableIpsumResultState.value = IpsumResultUiState.Idle
                    previous = link
                }
            }
        }
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
            val link = repository.profileLink.first()
            when (val result = coordinator.lookupUser(handle)) {
                is UserLookupResult.Success -> persist(result, link)
                UserLookupResult.UserNotFound -> showError("Usuário não encontrado no Codeforces.")
                is UserLookupResult.ApiFailure -> showError("Codeforces recusou a consulta: ${result.message}")
                UserLookupResult.RateLimited -> showError("Limite do Codeforces atingido. Aguarde e tente novamente.")
                UserLookupResult.NetworkFailure -> showError("Sem conexão com o Codeforces. Verifique a internet e tente novamente.")
            }
        }
    }

    private suspend fun persist(result: UserLookupResult.Success, link: ProfileLink?) {
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
            if (!sameOwner && repository.activeIpsum.first() != null) {
                showError("Encerre o Ipsum ativo antes de trocar o handle.")
                return
            }
            if (sameOwner && link != null) repository.withProfileLink(link) {
                repository.updateProfileMetadata(user.handle, user.displayName, user.rating)
            } else if (!repository.saveProfileIfCurrent(link, profile)) {
                showError("O perfil mudou durante a consulta. Tente novamente.")
                return
            }
            if (!sameOwner) {
                mutableHistorySyncState.value = HistorySyncUiState.Idle
                mutableSubmissionCheckState.value = SubmissionCheckUiState.Idle
                mutableIpsumResultState.value = IpsumResultUiState.Idle
                mutablePendingUpdateState.value = PendingUpdateUiState.Idle
                mutableStartIpsumState.value = StartIpsumUiState.Idle
            }
            val persisted = repository.profile.first() ?: profile
            mutableConfigurationState.value = mutableConfigurationState.value.copy(
                handle = profile.handle,
                isLoading = false,
                savedProfile = persisted,
                navigateHome = true,
            )
        } catch (error: Exception) {
            if (error is CancellationException) throw error
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
            val owner = repository.profileLink.first()
            val result = coordinator.synchronizeHistory()
            if (!isCurrentOwner(owner)) return@launch
            mutableHistorySyncState.value = when (result) {
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
            mutableCatalogSyncState.value = when (val result = coordinator.synchronizeCatalog()) {
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

    fun refreshProfile() {
        viewModelScope.launch {
            try { coordinator.refreshProfile() } catch (_: ProfileLinkChanged) { /* Account was removed. */ }
        }
    }

    fun startNewIpsum() {
        if (mutableStartIpsumState.value == StartIpsumUiState.Loading) return
        mutableIpsumResultState.value = IpsumResultUiState.Idle
        mutableSubmissionCheckState.value = SubmissionCheckUiState.Idle
        mutableStartIpsumState.value = StartIpsumUiState.Loading
        viewModelScope.launch {
            val owner = repository.profileLink.first()
            try {
                val profile = repository.profile.first()
                if (owner != null && profile != null && profile.lastSyncEpochMillis > 0 && repository.activeIpsum.first() == null) {
                    // A bounded attempt keeps offline training usable. A successful initial import remains mandatory.
                    val refreshed = withTimeoutOrNull(12_000) { coordinator.synchronizeHistory(owner) }
                    if (!isCurrentOwner(owner)) throw ProfileLinkChanged()
                    mutableDataSourceNotice.value = if (refreshed is ProblemHistorySyncResult.Success) null else
                        "Usando dados salvos · última sincronização: ${dev.lorem.app.ui.screens.formatSyncTime(profile.lastSyncEpochMillis)}"
                }
                mutableStartIpsumState.value = when (val result = startIpsum(owner)) {
                    is StartIpsumResult.Success -> StartIpsumUiState.Success(result.ipsum.id)
                    StartIpsumResult.AlreadyActive -> StartIpsumUiState.Error("Já existe um Ipsum ativo. Continue-o antes de iniciar outro.")
                    StartIpsumResult.NoCandidate -> StartIpsumUiState.Error(
                        "Nenhum problema inédito foi encontrado até 300 pontos da faixa. Atualize catálogo e histórico e tente novamente.",
                    )
                    StartIpsumResult.NoProfile -> StartIpsumUiState.Error("Configure um perfil antes de iniciar um Ipsum.")
                    StartIpsumResult.NeedsHistorySync -> StartIpsumUiState.Error("Aguarde a preparação automática do histórico do Codeforces antes de iniciar o primeiro Ipsum.")
                    StartIpsumResult.PersistenceFailure -> StartIpsumUiState.Error("Não foi possível salvar o Ipsum. Tente novamente.")
                }
            } catch (_: ProfileLinkChanged) {
                mutableDataSourceNotice.value = null
                mutableStartIpsumState.value = StartIpsumUiState.Error("O perfil mudou durante a preparação. Tente novamente.")
            }
        }
    }

    fun ipsumNavigationHandled() {
        if (mutableStartIpsumState.value is StartIpsumUiState.Success) {
            mutableStartIpsumState.value = StartIpsumUiState.Idle
        }
    }

    fun revealActiveIpsumHint() {
        viewModelScope.launch {
            val ipsum = repository.activeIpsum.first() ?: return@launch
            if (ipsum.hintRevealedAtEpochMillis != null) return@launch
            repository.revealIpsumHint(ipsum.id, nowMillis())
        }
    }

    fun verifyActiveIpsumSubmissions() {
        if (mutableSubmissionCheckState.value == SubmissionCheckUiState.Loading) return
        mutableSubmissionCheckState.value = SubmissionCheckUiState.Loading
        viewModelScope.launch {
            val owner = repository.profileLink.first()
            val ipsumId = repository.activeIpsum.first()?.id
            val result = coordinator.verifyActiveIpsum()
            if (!isCurrentOwner(owner)) return@launch
            val activeId = repository.activeIpsum.first()?.id
            if (activeId != null && activeId != ipsumId) return@launch
            mutableSubmissionCheckState.value = when (result) {
                is VerifyIpsumResult.Success -> SubmissionCheckUiState.Success(
                    result.newSubmissionCount, result.errorCount, result.completed, ipsumId,
                )
                VerifyIpsumResult.NoActiveIpsum -> SubmissionCheckUiState.Error("Não há Ipsum ativo para verificar.")
                is VerifyIpsumResult.ApiFailure -> SubmissionCheckUiState.Error("Codeforces recusou a consulta: ${result.message}")
                VerifyIpsumResult.RateLimited -> SubmissionCheckUiState.Error("Limite do Codeforces atingido. Aguarde e tente novamente.")
                VerifyIpsumResult.NetworkFailure -> SubmissionCheckUiState.Error("Sem conexão com o Codeforces. Tente novamente.")
                is VerifyIpsumResult.HttpFailure -> SubmissionCheckUiState.Error("Falha HTTP ${result.statusCode}. Tente novamente.")
                VerifyIpsumResult.InvalidResponse -> SubmissionCheckUiState.Error("O Codeforces enviou uma resposta inválida. Tente novamente.")
                VerifyIpsumResult.PersistenceFailure -> SubmissionCheckUiState.Error("Não foi possível salvar as submissões. Tente novamente.")
            }
        }
    }

    fun endActiveIpsum(reason: IpsumFailureReason?) {
        if (reason == null) {
            mutableIpsumResultState.value = IpsumResultUiState.Error("Selecione exatamente um motivo para encerrar sem AC.")
            return
        }
        if (mutableIpsumResultState.value == IpsumResultUiState.Loading) return
        mutableIpsumResultState.value = IpsumResultUiState.Loading
        viewModelScope.launch {
            val ipsum = repository.activeIpsum.first()
            if (ipsum == null) {
                mutableIpsumResultState.value = IpsumResultUiState.Error("Não há Ipsum ativo para encerrar.")
                return@launch
            }
            mutableIpsumResultState.value = runCatching {
                val ended = repository.endIpsumWithoutAc(ipsum.id, reason, nowMillis())
                repository.getIpsumResult(ipsum.id)?.let(IpsumResultUiState::Ready)
                    ?: IpsumResultUiState.Error(
                        if (ended) "Resultado não encontrado."
                        else "O Ipsum já foi encerrado ou não pôde ser salvo.",
                    )
            }.getOrElse {
                IpsumResultUiState.Error("Não foi possível salvar o resultado. Tente novamente.")
            }
        }
    }

    fun openIpsumResult(ipsumId: Long) {
        mutableIpsumResultState.value = IpsumResultUiState.Loading
        viewModelScope.launch {
            mutableIpsumResultState.value = runCatching { repository.getIpsumResult(ipsumId) }
                .fold(
                    onSuccess = {
                        it?.let(IpsumResultUiState::Ready)
                            ?: IpsumResultUiState.Error("Resultado não encontrado.")
                    },
                    onFailure = { IpsumResultUiState.Error("Não foi possível abrir o resultado salvo.") },
                )
        }
    }

    fun updatePendingIpsums() {
        if (mutablePendingUpdateState.value == PendingUpdateUiState.Loading) return
        mutablePendingUpdateState.value = PendingUpdateUiState.Loading
        viewModelScope.launch {
            val owner = repository.profileLink.first()
            val result = coordinator.updatePendingIpsums()
            if (!isCurrentOwner(owner)) return@launch
            mutablePendingUpdateState.value = when (result) {
                is UpdatePendingIpsumsResult.Success -> PendingUpdateUiState.Success(result.movedCount)
                UpdatePendingIpsumsResult.NoActiveProfile -> PendingUpdateUiState.Error("Nenhum perfil ativo para atualizar.")
                is UpdatePendingIpsumsResult.Failed -> PendingUpdateUiState.Error("Codeforces recusou a consulta: ${result.message}")
                UpdatePendingIpsumsResult.RateLimited -> PendingUpdateUiState.Error("Limite do Codeforces atingido. Aguarde e tente novamente.")
                UpdatePendingIpsumsResult.NetworkFailure -> PendingUpdateUiState.Error("Sem conexão com o Codeforces. Tente novamente.")
                is UpdatePendingIpsumsResult.HttpFailure -> PendingUpdateUiState.Error("Falha HTTP ${result.statusCode}. Tente novamente.")
                UpdatePendingIpsumsResult.InvalidResponse -> PendingUpdateUiState.Error("O Codeforces enviou uma resposta inválida. Tente novamente.")
                UpdatePendingIpsumsResult.PersistenceFailure -> PendingUpdateUiState.Error("Não foi possível salvar a atualização. Tente novamente.")
            }
        }
    }

    private suspend fun isCurrentOwner(link: ProfileLink?): Boolean =
        repository.profileLink.first() == link

    companion object {
        const val INITIAL_UNRATED_RATING = 800

        fun factory(
            repository: LoremRepository,
            codeforcesRepository: CodeforcesRepository,
            synchronizationCoordinator: SynchronizationCoordinator? = null,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(LoremViewModel::class.java))
                return LoremViewModel(repository, codeforcesRepository, synchronizationCoordinator = synchronizationCoordinator) as T
            }
        }
    }
}
