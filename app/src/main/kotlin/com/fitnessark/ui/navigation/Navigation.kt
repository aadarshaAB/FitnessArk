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
import com.fitnessark.data.model.PhotoAngle
import com.fitnessark.ui.camera.InAppCameraScreen
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
fun FitnessArkNavHost(openWeightDialogOnStart: Boolean = false) {
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
                    onNavigateToPhotos   = { navController.navigate(Screen.Photos.route) },
                    openWeightDialogOnStart = openWeightDialogOnStart
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
                    onNavigateBack = { navController.popBackStack() },
                    onOpenInAppCamera = { angle ->
                        navController.navigate("camera/${angle.name}")
                    },
                    cameraResult = backStackEntry.savedStateHandle
                        .getStateFlow<String?>(CAMERA_RESULT_KEY, null)
                        .collectAsState(),
                    onCameraResultConsumed = {
                        backStackEntry.savedStateHandle[CAMERA_RESULT_KEY] = null
                    },
                    cameraFailed = backStackEntry.savedStateHandle
                        .getStateFlow(CAMERA_FAILURE_KEY, false)
                        .collectAsState(),
                    onCameraFailureConsumed = {
                        backStackEntry.savedStateHandle[CAMERA_FAILURE_KEY] = false
                    }
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
                    onNavigateBack = { navController.popBackStack() },
                    onOpenInAppCamera = { angle ->
                        navController.navigate("camera/${angle.name}")
                    },
                    cameraResult = backStackEntry.savedStateHandle
                        .getStateFlow<String?>(CAMERA_RESULT_KEY, null)
                        .collectAsState(),
                    onCameraResultConsumed = {
                        backStackEntry.savedStateHandle[CAMERA_RESULT_KEY] = null
                    },
                    cameraFailed = backStackEntry.savedStateHandle
                        .getStateFlow(CAMERA_FAILURE_KEY, false)
                        .collectAsState(),
                    onCameraFailureConsumed = {
                        backStackEntry.savedStateHandle[CAMERA_FAILURE_KEY] = false
                    }
                )
            }

            // ── In-app camera (F11) ─────────────────────────────────────
            // The route arg is the PhotoAngle's own name — the single source of truth for which
            // pose slot the result belongs to — not a separately-tracked "pending slot" in the
            // caller, which could in principle drift from what the route actually opened for.
            // The captured Uri is handed back via the caller's SavedStateHandle (Navigation-
            // Compose's standard "return a result" pattern).
            composable(
                route     = "camera/{angle}",
                arguments = listOf(navArgument("angle") { type = NavType.StringType })
            ) { backStackEntry ->
                val angle = backStackEntry.arguments?.getString("angle")
                    ?.let { PhotoAngle.fromNameOrNull(it) }
                    ?: return@composable
                InAppCameraScreen(
                    poseLabel  = angle.label,
                    poseKey    = angle.fileKey,
                    onCaptured = { uri ->
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set(CAMERA_RESULT_KEY, uri.toString())
                        navController.popBackStack()
                    },
                    onCaptureFailed = {
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set(CAMERA_FAILURE_KEY, true)
                        navController.popBackStack()
                    },
                    onCancel = { navController.popBackStack() }
                )
            }
        }
    }
}

/** Key the in-app camera's result Uri is stored under in the caller's SavedStateHandle. */
const val CAMERA_RESULT_KEY = "camera_captured_uri"

/** Key set (to true) in the caller's SavedStateHandle when a capture attempt failed. */
const val CAMERA_FAILURE_KEY = "camera_capture_failed"
