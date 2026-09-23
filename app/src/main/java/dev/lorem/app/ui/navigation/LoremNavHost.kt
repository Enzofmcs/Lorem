package dev.lorem.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import dev.lorem.app.domain.model.IpsumFailureReason
import dev.lorem.app.ui.screens.IpsumResultScreen
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
    navController: NavHostController = rememberNavController(),
) {
    val profile = initialProfile ?: configurationState.savedProfile
    val startDestination = if (initialProfile == null) {
        LoremDestination.Configuration.route
    } else {
        LoremDestination.Home.route
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
            navController.navigate(LoremDestination.Ipsum.route) { launchSingleTop = true }
            onIpsumNavigationHandled()
        }
    }
    LaunchedEffect(submissionCheckState, ipsumResultState) {
        val completedId = (submissionCheckState as? SubmissionCheckUiState.Success)
            ?.takeIf { it.completed }?.ipsumId
        val manuallyEndedId = (ipsumResultState as? IpsumResultUiState.Ready)?.result?.ipsum?.id
        val id = completedId ?: manuallyEndedId
        if (id != null && navController.currentDestination?.route == LoremDestination.Ipsum.route) {
            navController.navigate("${LoremDestination.Result.route}/$id") { launchSingleTop = true }
        }
    }
    NavHost(navController = navController, startDestination = startDestination) {
        composable(LoremDestination.Configuration.route) {
            ConfigurationScreen(
                state = configurationState,
                existingProfile = profile,
                onHandleChange = onHandleChange,
                onConfirm = onConfirmHandle,
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
                    latestFinishedIpsumId = ipsums.lastOrNull { it.endedAtEpochMillis != null }?.id,
                    startIpsumState = startIpsumState,
                    onStartIpsum = onStartIpsum,
                    onNavigate = navController::navigate,
                )
            }
        }
        composable(LoremDestination.Ipsum.route) {
            IpsumScreen(
                ipsum = activeIpsum,
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
        composable(
            route = "${LoremDestination.Result.route}/{ipsumId}",
            arguments = listOf(navArgument("ipsumId") { type = NavType.LongType }),
        ) { entry ->
            val ipsumId = entry.arguments?.getLong("ipsumId") ?: return@composable
            LaunchedEffect(ipsumId) {
                val loaded = (ipsumResultState as? IpsumResultUiState.Ready)?.result?.ipsum?.id
                if (loaded != ipsumId) onOpenIpsumResult(ipsumId)
            }
            IpsumResultScreen(
                state = ipsumResultState,
                onClose = {
                    if (!navController.popBackStack(LoremDestination.Home.route, false)) {
                        navController.navigate(LoremDestination.Home.route) { launchSingleTop = true }
                    }
                },
            )
        }
        LoremDestination.all.filterNot {
            it == LoremDestination.Configuration || it == LoremDestination.Home ||
                it == LoremDestination.Ipsum || it == LoremDestination.Result
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
