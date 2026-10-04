package com.fitnessark.ui.photos

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.fitnessark.data.model.PhotoAngle
import com.fitnessark.data.model.pathFor
import com.fitnessark.ui.theme.CyanPrimary
import com.fitnessark.util.DateUtils
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoDetailScreen(
    photoId:        String,
    onNavigateBack: () -> Unit,
    onOpenInAppCamera: (PhotoAngle) -> Unit = {},
    cameraResult: State<String?> = mutableStateOf(null),
    onCameraResultConsumed: () -> Unit = {},
    cameraFailed: State<Boolean> = mutableStateOf(false),
    onCameraFailureConsumed: () -> Unit = {},
    viewModel:      PhotoTimelineViewModel = koinViewModel()
) {
    val state          by viewModel.uiState.collectAsState()
    val photo           = state.photos.find { it.id == photoId }
    val context         = LocalContext.current
    val scope           = rememberCoroutineScope()
    val snackbarHost    = remember { SnackbarHostState() }

    var selectedTab     by remember { mutableIntStateOf(0) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Which angle is being retaken (null when none) — also doubles as "which pose a pending
    // in-app camera / gallery result belongs to" (F11), since the camera route itself carries
    // the PhotoAngle rather than a separately-tracked "pending slot" that could drift from it.
    // rememberSaveable: the in-app camera screen / gallery picker can get us killed in the
    // background; this must survive that.
    var retakeAngle     by rememberSaveable { mutableStateOf<PhotoAngle?>(null) }
    var showSourceDialog by rememberSaveable { mutableStateOf(false) }

    fun applyRetake(uri: Uri) {
        val angle = retakeAngle ?: return
        scope.launch {
            val updated = viewModel.retakePhotoAngle(photoId, angle, uri)
            snackbarHost.showSnackbar(
                if (updated) "${angle.label} photo updated" else "Couldn't read the photo. Please try again."
            )
        }
    }

    LaunchedEffect(cameraResult.value) {
        val uriString = cameraResult.value ?: return@LaunchedEffect
        applyRetake(Uri.parse(uriString))
        retakeAngle = null
        onCameraResultConsumed()
    }
    LaunchedEffect(cameraFailed.value) {
        if (!cameraFailed.value) return@LaunchedEffect
        retakeAngle = null
        scope.launch { snackbarHost.showSnackbar("Couldn't take that photo. Please try again.") }
        onCameraFailureConsumed()
    }

    // F11: CAMERA is requested at runtime the first time "Camera" is tapped here, same as on
    // check-in. A denial falls back to "Gallery" for that attempt.
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val angle = retakeAngle
        if (granted && angle != null) {
            onOpenInAppCamera(angle)
        } else {
            retakeAngle = null
            scope.launch {
                snackbarHost.showSnackbar("Camera permission denied. You can still add a photo from your gallery.")
            }
        }
    }

    // ── Gallery launcher ─────────────────────────────────────────────────────
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { applyRetake(it) }
        retakeAngle = null
    }

    // ── Helpers ──────────────────────────────────────────────────────────────
    fun startRetake(angle: PhotoAngle) {
        retakeAngle      = angle
        showSourceDialog = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(photo?.let { DateUtils.formatDate(it.date) } ?: "Photo")
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    // Delete whole entry
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, "Delete entry",
                            tint = MaterialTheme.colorScheme.error)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) }
    ) { padding ->
        if (photo == null) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { Text("Photo not found") }
            return@Scaffold
        }

        val views = buildList {
            PhotoAngle.entries.forEach { angle -> photo.pathFor(angle)?.let { add(angle to it) } }
        }
        val safeTab = selectedTab.coerceAtMost((views.size - 1).coerceAtLeast(0))

        Column(Modifier.fillMaxSize().padding(padding)) {

            // ── Angle tabs ────────────────────────────────────────────────
            if (views.size > 1) {
                TabRow(selectedTabIndex = safeTab) {
                    views.forEachIndexed { i, (angle, _) ->
                        Tab(
                            selected = safeTab == i,
                            onClick  = { selectedTab = i },
                            text     = { Text(angle.label) }
                        )
                    }
                }
            }

            // ── Main photo ────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val currentPath = views.getOrNull(safeTab)?.second

                AnimatedContent(targetState = currentPath, label = "photo") { path ->
                    if (path != null) {
                        AsyncImage(
                            model              = path,
                            contentDescription = "Progress photo",
                            contentScale       = ContentScale.Fit,
                            modifier           = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center) {
                            Text("No photo", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // Angle label badge top-left
                views.getOrNull(safeTab)?.let { (angle, _) ->
                    Surface(
                        modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                        color    = Color.Black.copy(alpha = 0.5f),
                        shape    = RoundedCornerShape(6.dp)
                    ) {
                        Text(angle.label,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style    = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color    = Color.White)
                    }
                }
            }

            // ── Per-angle action bar ───────────────────────────────────────
            views.getOrNull(safeTab)?.let { (angle, _) ->
                Surface(
                    color    = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier              = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        // Retake button
                        Button(
                            onClick = { startRetake(angle) },
                            modifier = Modifier.weight(1f),
                            colors   = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor   = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape    = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, null,
                                tint     = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Retake ${angle.label}",
                                color      = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize   = 14.sp)
                        }

                        // Delete just this angle (if > 1 angle exists)
                        if (views.size > 1) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        viewModel.deletePhotoAngle(photoId, angle)
                                        // Move to the first remaining tab
                                        selectedTab = 0
                                        snackbarHost.showSnackbar("${angle.label} photo removed")
                                    }
                                },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                shape  = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Delete, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Remove", fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Source picker dialog ─────────────────────────────────────────────────
    if (showSourceDialog) {
        val angleLabel = retakeAngle?.label.orEmpty()
        AlertDialog(
            onDismissRequest = { showSourceDialog = false; retakeAngle = null },
            icon  = { Icon(Icons.Default.AddAPhoto, null, tint = CyanPrimary,
                modifier = Modifier.size(30.dp)) },
            title = { Text("Retake $angleLabel Photo") },
            text  = { Text("How would you like to add the new photo?") },
            confirmButton = {
                // F11: opens the in-app camera (front lens + 5s timer by default), same as check-in
                Button(
                    onClick = {
                        showSourceDialog = false
                        val hasPermission = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasPermission) {
                            retakeAngle?.let { onOpenInAppCamera(it) }
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor   = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Default.CameraAlt, null,
                        tint = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(6.dp))
                    Text("Camera", color = MaterialTheme.colorScheme.onPrimary)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    showSourceDialog = false
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }) {
                    Icon(Icons.Default.PhotoLibrary, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Gallery")
                }
            }
        )
    }

    // ── Delete entire entry dialog ───────────────────────────────────────────
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon  = { Icon(Icons.Default.Warning, null,
                tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete All Photos?") },
            text  = {
                Text("This will permanently delete all angles for this date. " +
                     "Use 'Remove' in the action bar to delete a single angle.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePhoto(photoId)
                        showDeleteDialog = false
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Delete All") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}
