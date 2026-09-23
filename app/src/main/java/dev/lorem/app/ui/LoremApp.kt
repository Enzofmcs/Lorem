package dev.lorem.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.lorem.app.domain.repository.CodeforcesRepository
import dev.lorem.app.domain.repository.LoremRepository
import dev.lorem.app.ui.navigation.LoremNavHost

@Composable
fun LoremApp(
    repository: LoremRepository,
    codeforcesRepository: CodeforcesRepository,
    viewModel: LoremViewModel = viewModel(
        factory = LoremViewModel.factory(repository, codeforcesRepository),
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val configurationState by viewModel.configurationState.collectAsStateWithLifecycle()
    val historySyncState by viewModel.historySyncState.collectAsStateWithLifecycle()
    val problemHistory by viewModel.problemHistory.collectAsStateWithLifecycle()
    val problemCatalog by viewModel.problemCatalog.collectAsStateWithLifecycle()
    val catalogUpdatedAt by viewModel.catalogLastUpdatedEpochMillis.collectAsStateWithLifecycle()
    val catalogSyncState by viewModel.catalogSyncState.collectAsStateWithLifecycle()
    val activeIpsum by viewModel.activeIpsum.collectAsStateWithLifecycle()
    val startIpsumState by viewModel.startIpsumState.collectAsStateWithLifecycle()
    when (val state = uiState) {
        LoremUiState.Loading -> Box(
            modifier = Modifier.fillMaxSize(),
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
            startIpsumState = startIpsumState,
            onStartIpsum = viewModel::startNewIpsum,
        )
    }
}
