package com.fitnessark.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.*
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.fitnessark.ui.checkin.CheckinScreen
import com.fitnessark.ui.dashboard.DashboardScreen
import com.fitnessark.ui.measurements.MeasurementsScreen
import com.fitnessark.ui.photos.PhotoDetailScreen
import com.fitnessark.ui.photos.PhotoTimelineScreen
import com.fitnessark.ui.settings.SettingsScreen

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Dashboard    : Screen("dashboard",    "Dashboard", Icons.Default.Home)
    object Measurements : Screen("measurements", "Progress",  Icons.Default.TrendingUp)
    object Photos       : Screen("photos",       "Photos",    Icons.Default.Image)
    object Settings     : Screen("settings",     "Settings",  Icons.Default.Settings)
}

val bottomNavScreens = listOf(
    Screen.Dashboard,
    Screen.Measurements,
    Screen.Photos,
    Screen.Settings
)

@Composable
fun FitnessArkNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = bottomNavScreens.any { currentRoute == it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavScreens.forEach { screen ->
                        NavigationBarItem(
                            icon  = { Icon(screen.icon, contentDescription = screen.label) },
                            label = { Text(screen.label) },
                            selected = currentRoute == screen.route,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState    = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController    = navController,
            startDestination = Screen.Dashboard.route,
            modifier         = Modifier.padding(paddingValues)
        ) {

            // ── Bottom nav destinations ────────────────────────────────
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    onNavigateToCheckin  = { navController.navigate("checkin?date=${System.currentTimeMillis()}") },
                    onNavigateToProgress = { navController.navigate(Screen.Measurements.route) },
                    onNavigateToPhotos   = { navController.navigate(Screen.Photos.route) }
                )
            }

            composable(Screen.Measurements.route) {
                MeasurementsScreen()
            }

            composable(Screen.Photos.route) {
                PhotoTimelineScreen(
                    onNavigateToDetail = { id -> navController.navigate("photo/$id") }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen()
            }

            // ── Full check-in — single route with optional date arg ────
            // Navigate with "checkin" (uses defaultValue = now) or
            // "checkin?date=<timestamp>" for a specific date.
            composable(
                route     = "checkin?date={date}",
                arguments = listOf(
                    navArgument("date") {
                        type         = NavType.LongType
                        defaultValue = System.currentTimeMillis()
                    }
                )
            ) { backStackEntry ->
                val date = backStackEntry.arguments?.getLong("date")
                    ?: System.currentTimeMillis()
                CheckinScreen(
                    date           = date,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // ── Photo detail ───────────────────────────────────────────
            composable(
                route     = "photo/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getString("id") ?: return@composable
                PhotoDetailScreen(
                    photoId        = id,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
