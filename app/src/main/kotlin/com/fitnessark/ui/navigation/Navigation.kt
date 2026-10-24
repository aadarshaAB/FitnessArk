package com.fitnessark.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
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
import com.fitnessark.ui.theme.rememberReducedMotion

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

    // Every jump to a top-level tab goes through here (the bottom bar and the Dashboard's links alike),
    // so the back stack always has the same shape. A plain navigate() from the Dashboard left a
    // Progress entry above it that the Dashboard tab's single-top navigation then could not leave.
    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState    = true
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavScreens.forEach { screen ->
                        NavigationBarItem(
                            icon  = { Icon(screen.icon, contentDescription = null) },
                            label = { Text(screen.label) },
                            selected = currentRoute == screen.route,
                            onClick = { navigateToTab(screen.route) }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        // Tabs cross-fade; detail screens (check-in, photo, camera) slide in from the right. With
        // system animations off, every transition is skipped.
        val reducedMotion = rememberReducedMotion()
        val tabEnter: EnterTransition = if (reducedMotion) EnterTransition.None else fadeIn(tween(150))
        val tabExit: ExitTransition = if (reducedMotion) ExitTransition.None else fadeOut(tween(150))
        val detailEnter: EnterTransition = if (reducedMotion) EnterTransition.None
            else slideInHorizontally(tween(250)) { it / 4 } + fadeIn(tween(250))
        val detailExit: ExitTransition = if (reducedMotion) ExitTransition.None
            else slideOutHorizontally(tween(250)) { it / 4 } + fadeOut(tween(250))
        val isDetail: (String?) -> Boolean = { route ->
            route != null && bottomNavScreens.none { it.route == route }
        }

        // Wide screens (tablet, landscape): content keeps a readable width and is centred
        Box(Modifier.padding(paddingValues).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            NavHost(
                navController    = navController,
                startDestination = Screen.Dashboard.route,
                modifier         = Modifier.widthIn(max = ContentMaxWidth).fillMaxSize(),
                enterTransition    = { if (isDetail(targetState.destination.route)) detailEnter else tabEnter },
                exitTransition     = { tabExit },
                popEnterTransition = { tabEnter },
                popExitTransition  = { if (isDetail(initialState.destination.route)) detailExit else tabExit }
            ) {

                // ── Bottom nav destinations ────────────────────────────────
                composable(Screen.Dashboard.route) {
                    DashboardScreen(
                        onNavigateToCheckin  = { navController.navigate("checkin?date=${System.currentTimeMillis()}") { launchSingleTop = true } },
                        onNavigateToProgress = { navigateToTab(Screen.Measurements.route) },
                        onNavigateToPhotos   = { navigateToTab(Screen.Photos.route) },
                        openWeightDialogOnStart = openWeightDialogOnStart
                    )
                }

                composable(Screen.Measurements.route) {
                    MeasurementsScreen()
                }

                composable(Screen.Photos.route) {
                    PhotoTimelineScreen(
                        onNavigateToDetail = { id -> navController.navigate("photo/$id") { launchSingleTop = true } }
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
                            navController.navigate("camera/${angle.name}") { launchSingleTop = true }
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
                            navController.navigate("camera/${angle.name}") { launchSingleTop = true }
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
}

private val ContentMaxWidth = 720.dp

/** Key the in-app camera's result Uri is stored under in the caller's SavedStateHandle. */
const val CAMERA_RESULT_KEY = "camera_captured_uri"

/** Key set (to true) in the caller's SavedStateHandle when a capture attempt failed. */
const val CAMERA_FAILURE_KEY = "camera_capture_failed"
