package com.fitnessark.ui.photos

import android.graphics.BitmapFactory
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fitnessark.ui.theme.CyanPrimary
import com.fitnessark.util.BitmapUtils
import com.fitnessark.util.CameraUtils
import com.fitnessark.util.DateUtils
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoDetailScreen(
    photoId:        String,
    onNavigateBack: () -> Unit,
    viewModel:      PhotoTimelineViewModel = koinViewModel()
) {
    val state          by viewModel.uiState.collectAsState()
    val photo           = state.photos.find { it.id == photoId }
    val context         = LocalContext.current
    val scope           = rememberCoroutineScope()
    val snackbarHost    = remember { SnackbarHostState() }

    var selectedTab     by remember { mutableIntStateOf(0) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Which angle is being retaken ("front" | "side" | "back" | null)
    var retakeAngle     by remember { mutableStateOf<String?>(null) }
    var showSourceDialog by remember { mutableStateOf(false) }
    var cameraUri       by remember { mutableStateOf<Uri?>(null) }

    // ── Camera launcher ──────────────────────────────────────────────────────
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraUri != null && retakeAngle != null) {
            scope.launch {
                val bitmap = BitmapUtils.decodeUriToBitmap(context, cameraUri!!)
                if (bitmap != null) {
                    viewModel.retakePhotoAngle(context, photoId, retakeAngle!!, bitmap)
                    snackbarHost.showSnackbar("${retakeAngle!!.replaceFirstChar { it.uppercase() }} photo updated")
                }
            }
        }
        retakeAngle = null
    }

    // ── Gallery launcher ─────────────────────────────────────────────────────
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && retakeAngle != null) {
            scope.launch {
                val bitmap = BitmapUtils.decodeUriToBitmap(context, uri)
                if (bitmap != null) {
                    viewModel.retakePhotoAngle(context, photoId, retakeAngle!!, bitmap)
                    snackbarHost.showSnackbar("${retakeAngle!!.replaceFirstChar { it.uppercase() }} photo updated")
                }
            }
        }
        retakeAngle = null
    }

    // ── Helpers ──────────────────────────────────────────────────────────────
    fun startRetake(angle: String) {
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
            if (photo.frontPhotoPath != null) add(Triple("Front", "front", photo.frontPhotoPath))
            if (photo.sidePhotoPath  != null) add(Triple("Side",  "side",  photo.sidePhotoPath))
            if (photo.backPhotoPath  != null) add(Triple("Back",  "back",  photo.backPhotoPath))
        }
        val safeTab = selectedTab.coerceAtMost((views.size - 1).coerceAtLeast(0))

        Column(Modifier.fillMaxSize().padding(padding)) {

            // ── Angle tabs ────────────────────────────────────────────────
            if (views.size > 1) {
                TabRow(selectedTabIndex = safeTab) {
                    views.forEachIndexed { i, (label, _, _) ->
                        Tab(
                            selected = safeTab == i,
                            onClick  = { selectedTab = i },
                            text     = { Text(label) }
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
                val currentPath = views.getOrNull(safeTab)?.third

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
                views.getOrNull(safeTab)?.let { (label, _, _) ->
                    Surface(
                        modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                        color    = Color.Black.copy(alpha = 0.5f),
                        shape    = RoundedCornerShape(6.dp)
                    ) {
                        Text(label,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style    = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color    = Color.White)
                    }
                }
            }

            // ── Per-angle action bar ───────────────────────────────────────
            views.getOrNull(safeTab)?.let { (label, angleKey, _) ->
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
                            onClick = { startRetake(angleKey) },
                            modifier = Modifier.weight(1f),
                            colors   = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                            shape    = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, null,
                                tint     = MaterialTheme.colorScheme.background,
                                modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Retake $label",
                                color      = MaterialTheme.colorScheme.background,
                                fontWeight = FontWeight.SemiBold,
                                fontSize   = 14.sp)
                        }

                        // Delete just this angle (if > 1 angle exists)
                        if (views.size > 1) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        viewModel.deletePhotoAngle(photoId, angleKey)
                                        // Move to the first remaining tab
                                        selectedTab = 0
                                        snackbarHost.showSnackbar("$label photo removed")
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
        val angleLabel = retakeAngle?.replaceFirstChar { it.uppercase() } ?: ""
        AlertDialog(
            onDismissRequest = { showSourceDialog = false; retakeAngle = null },
            icon  = { Icon(Icons.Default.AddAPhoto, null, tint = CyanPrimary,
                modifier = Modifier.size(30.dp)) },
            title = { Text("Retake $angleLabel Photo") },
            text  = { Text("How would you like to add the new photo?") },
            confirmButton = {
                Button(
                    onClick = {
                        showSourceDialog = false
                        val uri = CameraUtils.createTempCameraUri(context, "retake_${retakeAngle}")
                        cameraUri = uri
                        cameraLauncher.launch(uri)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary)
                ) {
                    Icon(Icons.Default.CameraAlt, null,
                        tint = MaterialTheme.colorScheme.background)
                    Spacer(Modifier.width(6.dp))
                    Text("Camera", color = MaterialTheme.colorScheme.background)
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
