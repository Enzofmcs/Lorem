package dev.lorem.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.lorem.app.domain.model.LocalProfile
import dev.lorem.app.ui.screens.ConfigurationScreen
import dev.lorem.app.ui.screens.PlaceholderScreen

@Composable
fun LoremNavHost(
    initialProfile: LocalProfile?,
    onSaveTestProfile: (String) -> Unit,
    onClearTestProfile: () -> Unit,
    navController: NavHostController = rememberNavController(),
) {
    val startDestination = if (initialProfile == null) {
        LoremDestination.Configuration.route
    } else {
        LoremDestination.Home.route
    }
    NavHost(navController = navController, startDestination = startDestination) {
        composable(LoremDestination.Configuration.route) {
            ConfigurationScreen(
                persistedName = initialProfile?.displayName.orEmpty(),
                onSave = { name ->
                    onSaveTestProfile(name)
                    navController.navigate(LoremDestination.Home.route) {
                        popUpTo(LoremDestination.Configuration.route) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onClear = onClearTestProfile,
                onNavigate = navController::navigate,
            )
        }
        LoremDestination.all
            .filterNot { it == LoremDestination.Configuration }
            .forEach { destination ->
                composable(destination.route) {
                    PlaceholderScreen(
                        destination = destination,
                        persistedName = initialProfile?.displayName,
                        onNavigate = navController::navigate,
                    )
                }
            }
    }
}
