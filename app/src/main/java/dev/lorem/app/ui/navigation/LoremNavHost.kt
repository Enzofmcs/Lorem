package dev.lorem.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.lorem.app.domain.model.LocalProfile
import dev.lorem.app.domain.model.ProblemHistory
import dev.lorem.app.ui.ConfigurationUiState
import dev.lorem.app.ui.CatalogSyncUiState
import dev.lorem.app.ui.HistorySyncUiState
import dev.lorem.app.ui.screens.ConfigurationScreen
import dev.lorem.app.ui.screens.HomeScreen
import dev.lorem.app.ui.screens.PlaceholderScreen
import dev.lorem.app.ui.screens.IpsumScreen
import dev.lorem.app.domain.model.Ipsum
import dev.lorem.app.ui.StartIpsumUiState
import dev.lorem.app.ui.SubmissionCheckUiState
import dev.lorem.app.ui.IpsumResultUiState
import dev.lorem.app.ui.PendingUpdateUiState
import dev.lorem.app.domain.model.IpsumFailureReason
import dev.lorem.app.ui.screens.IpsumResultScreen
import dev.lorem.app.ui.screens.HistoryScreen
import dev.lorem.app.ui.StatisticsUiState
import dev.lorem.app.ui.ConsolidationUiState
import dev.lorem.app.ui.screens.StatisticsScreen
import androidx.navigation.NavType
import androidx.navigation.navArgument

@Composable
fun LoremNavHost(
    initialProfile: LocalProfile?,
    configurationState: ConfigurationUiState,
    onHandleChange: (String) -> Unit,
    onConfirmHandle: () -> Unit,
    onHomeNavigationHandled: () -> Unit,
    historySyncState: HistorySyncUiState,
    problemHistory: List<ProblemHistory>,
    onSynchronizeHistory: () -> Unit,
    problemCatalogCount: Int,
    catalogUpdatedAtEpochMillis: Long?,
    catalogSyncState: CatalogSyncUiState,
    onSynchronizeCatalog: () -> Unit,
    activeIpsum: Ipsum?,
    ipsums: List<Ipsum>,
    startIpsumState: StartIpsumUiState,
    onStartIpsum: () -> Unit,
    onIpsumNavigationHandled: () -> Unit,
    onRevealIpsumHint: () -> Unit,
    submissionCheckState: SubmissionCheckUiState,
    onVerifyIpsumSubmissions: () -> Unit,
    onEndIpsum: (IpsumFailureReason?) -> Unit,
    ipsumResultState: IpsumResultUiState,
    onOpenIpsumResult: (Long) -> Unit,
    pendingUpdateState: PendingUpdateUiState,
    onUpdatePendingIpsums: () -> Unit,
    statisticsState: StatisticsUiState = StatisticsUiState.Loading,
    consolidationState: ConsolidationUiState? = null,
    preparationState: dev.lorem.app.ui.PreparationUiState? = null,
    dataSourceNotice: String? = null,
    trackingState: dev.lorem.app.ui.IpsumTrackingUiState? = null,
    onMonitorIpsum: suspend (Long) -> Unit = {},
    onRefreshProfile: () -> Unit = {},
    navController: NavHostController = rememberNavController(),
) {
    val profile = initialProfile ?: configurationState.savedProfile
    val ownedIpsums = ipsums.filter { it.ownerHandle.trim().equals(profile?.handle?.trim(), true) }
    val consolidation = consolidationState?.takeIf { it.ownerHandle.trim().equals(profile?.handle?.trim(), true) }?.progress
    val startDestination = if (initialProfile == null) {
        LoremDestination.Configuration.route
    } else {
        LoremDestination.Home.route
    }
    var resumedActive by remember { mutableStateOf(false) }
    var requestedIpsumId by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(activeIpsum?.id) {
        if (!resumedActive && activeIpsum != null) {
            resumedActive = true
            if (navController.currentDestination?.route == LoremDestination.Home.route) {
                requestedIpsumId = activeIpsum.id
                navController.navigate(LoremDestination.Ipsum.route) { launchSingleTop = true }
            }
        }
    }
    LaunchedEffect(configurationState.navigateHome) {
        if (configurationState.navigateHome) {
            navController.navigate(LoremDestination.Home.route) {
                popUpTo(LoremDestination.Configuration.route) { inclusive = true }
                launchSingleTop = true
            }
            onHomeNavigationHandled()
        }
    }
    LaunchedEffect(startIpsumState) {
        if (startIpsumState is StartIpsumUiState.Success) {
            requestedIpsumId = startIpsumState.ipsumId
            navController.navigate(LoremDestination.Ipsum.route) { launchSingleTop = true }
            onIpsumNavigationHandled()
        }
    }
    NavHost(navController = navController, startDestination = startDestination) {
        composable(LoremDestination.Configuration.route) {
            ConfigurationScreen(
                state = configurationState,
                existingProfile = profile,
                onHandleChange = onHandleChange,
                onConfirm = onConfirmHandle,
                preparation = preparationState,
                onRefreshHistory = onSynchronizeHistory,
                onRefreshCatalog = onSynchronizeCatalog,
                onRefreshPending = onUpdatePendingIpsums,
                onRefreshProfile = onRefreshProfile,
            )
        }
        composable(LoremDestination.Home.route) {
            profile?.let {
                HomeScreen(
                    profile = it,
                    syncState = historySyncState,
                    problemHistory = problemHistory,
                    onSynchronize = onSynchronizeHistory,
                    problemCatalogCount = problemCatalogCount,
                    catalogUpdatedAtEpochMillis = catalogUpdatedAtEpochMillis,
                    catalogSyncState = catalogSyncState,
                    onSynchronizeCatalog = onSynchronizeCatalog,
                    hasActiveIpsum = activeIpsum != null,
                    latestFinishedIpsumId = ownedIpsums.lastOrNull { it.endedAtEpochMillis != null }?.id,
                    startIpsumState = startIpsumState,
                    onStartIpsum = onStartIpsum,
                    onNavigate = { route ->
                        if (route == LoremDestination.Ipsum.route) requestedIpsumId = activeIpsum?.id
                        navController.navigate(route)
                    },
                    consolidation = consolidation,
                    preparation = preparationState?.takeIf { state -> state.owner?.handle == dev.lorem.app.domain.sync.ProfileLink.of(it.handle, 0).handle },
                    dataSourceNotice = dataSourceNotice,
                )
            }
        }
        composable(LoremDestination.Ipsum.route) {
            var viewedIpsumId by rememberSaveable { mutableStateOf(requestedIpsumId) }
            val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
            LaunchedEffect(activeIpsum?.id) { activeIpsum?.id?.let { viewedIpsumId = it } }
            val savedCompletion = viewedIpsumId?.let { id -> ownedIpsums.firstOrNull { it.id == id && it.endedAtEpochMillis != null }?.id }
            val manualCompletion = (submissionCheckState as? SubmissionCheckUiState.Success)?.takeIf { it.completed }?.ipsumId
                ?: (ipsumResultState as? IpsumResultUiState.Ready)?.result?.ipsum
                    ?.takeIf { it.ownerHandle.trim().equals(profile?.handle?.trim(), true) }?.id
            // A restored Ipsum route may load after a worker already completed it. Raw repository
            // streams in trackingState distinguish that case from the initial null StateFlow cache.
            val restoredCompletion = trackingState?.takeIf {
                viewedIpsumId == null && it.activeIpsumId == null && activeIpsum == null &&
                    ownedIpsums.none { attempt -> attempt.status == dev.lorem.app.domain.model.IpsumStatus.ACTIVE }
            }?.lastFinishedIpsumId
            val completedId = savedCompletion ?: manualCompletion ?: restoredCompletion
            LaunchedEffect(completedId, viewedIpsumId, lifecycleState) {
                if (completedId != null && (viewedIpsumId == null || completedId == viewedIpsumId) && lifecycleState == Lifecycle.State.RESUMED) {
                    navController.navigate("${LoremDestination.Result.route}/$completedId") {
                        popUpTo(LoremDestination.Ipsum.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
            IpsumScreen(
                ipsum = activeIpsum,
                dataSourceNotice = dataSourceNotice,
                trackingState = trackingState,
                onMonitorIpsum = onMonitorIpsum,
                onRevealHint = onRevealIpsumHint,
                submissionCheckState = submissionCheckState,
                onVerifySubmissions = onVerifyIpsumSubmissions,
                onEndWithoutAc = onEndIpsum,
                onNavigateHome = {
                    if (!navController.popBackStack(LoremDestination.Home.route, inclusive = false)) {
                        navController.navigate(LoremDestination.Home.route) { launchSingleTop = true }
                    }
                },
            )
        }
        composable(LoremDestination.History.route) {
            HistoryScreen(
                ipsums = ownedIpsums,
                updateState = pendingUpdateState,
                onUpdatePending = onUpdatePendingIpsums,
                onNavigateHome = {
                    if (!navController.popBackStack(LoremDestination.Home.route, false)) {
                        navController.navigate(LoremDestination.Home.route) { launchSingleTop = true }
                    }
                },
            )
        }
        composable(LoremDestination.Statistics.route) {
            // A cached StateFlow snapshot must not show the previous account on a profile switch.
            val visibleState = if (statisticsState is StatisticsUiState.Ready &&
                !statisticsState.statistics.ownerHandle.trim().equals(profile?.handle?.trim(), ignoreCase = true)
            ) StatisticsUiState.Loading else statisticsState
            StatisticsScreen(
                state = visibleState,
                consolidation = consolidation,
                onNavigateHome = {
                    if (!navController.popBackStack(LoremDestination.Home.route, false)) {
                        navController.navigate(LoremDestination.Home.route) { launchSingleTop = true }
                    }
                },
            )
        }
        composable(
            route = "${LoremDestination.Result.route}/{ipsumId}",
            arguments = listOf(navArgument("ipsumId") { type = NavType.LongType }),
        ) { entry ->
            val ipsumId = entry.arguments?.getLong("ipsumId") ?: return@composable
            LaunchedEffect(ipsumId, profile?.handle) {
                val loaded = (ipsumResultState as? IpsumResultUiState.Ready)?.result?.ipsum
                    ?.takeIf { it.ownerHandle.trim().equals(profile?.handle?.trim(), true) }?.id
                if (loaded != ipsumId) onOpenIpsumResult(ipsumId)
            }
            IpsumResultScreen(
                state = if (ipsumResultState is IpsumResultUiState.Ready &&
                    !ipsumResultState.result.ipsum.ownerHandle.trim().equals(profile?.handle?.trim(), true)
                ) IpsumResultUiState.Loading else ipsumResultState,
                onClose = {
                    if (!navController.popBackStack(LoremDestination.Home.route, false)) {
                        navController.navigate(LoremDestination.Home.route) { launchSingleTop = true }
                    }
                },
            )
        }
        LoremDestination.all.filterNot {
            it == LoremDestination.Configuration || it == LoremDestination.Home ||
                it == LoremDestination.Ipsum || it == LoremDestination.Result ||
                it == LoremDestination.History || it == LoremDestination.Statistics
        }.forEach { destination ->
            composable(destination.route) {
                PlaceholderScreen(
                    destination = destination,
                    profile = profile,
                    onNavigate = navController::navigate,
                )
            }
        }
    }
}
