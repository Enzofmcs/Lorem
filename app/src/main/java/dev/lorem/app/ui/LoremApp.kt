package dev.lorem.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.lorem.app.domain.model.ThemePreference
import dev.lorem.app.domain.repository.AppearanceRepository
import dev.lorem.app.domain.repository.CodeforcesRepository
import dev.lorem.app.domain.repository.LoremRepository
import dev.lorem.app.domain.sync.SynchronizationCoordinator
import dev.lorem.app.ui.navigation.LoremNavHost
import dev.lorem.app.ui.theme.LoremTheme

@Composable
fun LoremApp(
    repository: LoremRepository,
    codeforcesRepository: CodeforcesRepository,
    synchronizationCoordinator: SynchronizationCoordinator? = null,
    appearanceRepository: AppearanceRepository? = null,
    viewModel: LoremViewModel = viewModel(
        factory = LoremViewModel.factory(repository, codeforcesRepository, synchronizationCoordinator, appearanceRepository),
    ),
) {
    val themePreference by viewModel.themePreference.collectAsStateWithLifecycle()
    val themeError by viewModel.themeError.collectAsStateWithLifecycle()
    val trackingState by viewModel.trackingState.collectAsStateWithLifecycle()
    val preparationState by viewModel.preparationState.collectAsStateWithLifecycle()
    val dataSourceNotice by viewModel.dataSourceNotice.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val configurationState by viewModel.configurationState.collectAsStateWithLifecycle()
    val historySyncState by viewModel.historySyncState.collectAsStateWithLifecycle()
    val problemHistory by viewModel.problemHistory.collectAsStateWithLifecycle()
    val problemCatalog by viewModel.problemCatalog.collectAsStateWithLifecycle()
    val catalogUpdatedAt by viewModel.catalogLastUpdatedEpochMillis.collectAsStateWithLifecycle()
    val catalogSyncState by viewModel.catalogSyncState.collectAsStateWithLifecycle()
    val activeIpsum by viewModel.activeIpsum.collectAsStateWithLifecycle()
    val startIpsumState by viewModel.startIpsumState.collectAsStateWithLifecycle()
    val submissionCheckState by viewModel.submissionCheckState.collectAsStateWithLifecycle()
    val ipsums by viewModel.ipsums.collectAsStateWithLifecycle()
    val ipsumResultState by viewModel.ipsumResultState.collectAsStateWithLifecycle()
    val pendingUpdateState by viewModel.pendingUpdateState.collectAsStateWithLifecycle()
    val statisticsState by viewModel.statisticsState.collectAsStateWithLifecycle()
    val consolidationState by viewModel.consolidationState.collectAsStateWithLifecycle()
    LoremTheme(preference = themePreference ?: ThemePreference.SYSTEM) {
        if (themePreference == null) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else when (val state = uiState) {
            LoremUiState.Loading -> Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            is LoremUiState.Ready -> LoremNavHost(
                initialProfile = state.profile,
                configurationState = configurationState,
                onHandleChange = viewModel::updateHandle,
                onConfirmHandle = viewModel::confirmHandle,
                onHomeNavigationHandled = viewModel::homeNavigationHandled,
                historySyncState = historySyncState,
                problemHistory = problemHistory,
                onSynchronizeHistory = viewModel::synchronizeHistory,
                problemCatalogCount = problemCatalog.size,
                catalogUpdatedAtEpochMillis = catalogUpdatedAt,
                catalogSyncState = catalogSyncState,
                onSynchronizeCatalog = viewModel::synchronizeCatalog,
                activeIpsum = activeIpsum,
                ipsums = ipsums,
                startIpsumState = startIpsumState,
                onStartIpsum = viewModel::startNewIpsum,
                onIpsumNavigationHandled = viewModel::ipsumNavigationHandled,
                onRevealIpsumHint = viewModel::revealActiveIpsumHint,
                submissionCheckState = submissionCheckState,
                onVerifyIpsumSubmissions = viewModel::verifyActiveIpsumSubmissions,
                onEndIpsum = viewModel::endActiveIpsum,
                ipsumResultState = ipsumResultState,
                onOpenIpsumResult = viewModel::openIpsumResult,
                pendingUpdateState = pendingUpdateState,
                onUpdatePendingIpsums = viewModel::updatePendingIpsums,
                statisticsState = statisticsState,
                consolidationState = consolidationState,
                preparationState = preparationState?.takeIf { it.owner == state.link },
                trackingState = trackingState?.takeIf { it.owner == state.link },
                onMonitorIpsum = viewModel::monitorIpsum,
                onRefreshProfile = viewModel::refreshProfile,
                dataSourceNotice = dataSourceNotice,
                themePreference = requireNotNull(themePreference),
                onThemeChange = viewModel::selectTheme,
                themeError = themeError,
            )
        }
    }
}
