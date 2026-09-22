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
import dev.lorem.app.ui.HistorySyncUiState
import dev.lorem.app.ui.screens.ConfigurationScreen
import dev.lorem.app.ui.screens.HomeScreen
import dev.lorem.app.ui.screens.PlaceholderScreen

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
    navController: NavHostController = rememberNavController(),
) {
    val profile = configurationState.savedProfile ?: initialProfile
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
                    onNavigate = navController::navigate,
                )
            }
        }
        LoremDestination.all.filterNot {
            it == LoremDestination.Configuration || it == LoremDestination.Home
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
