package com.fitnessark.ui.checkin

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fitnessark.data.model.Metric
import com.fitnessark.data.model.PhotoAngle
import com.fitnessark.ui.theme.CyanPrimary
import com.fitnessark.util.CameraUtils
import com.fitnessark.util.DateUtils
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

// ─── Pose slot data ──────────────────────────────────────────────────────────

private data class PoseSlot(
    val angle: PhotoAngle,
    val emoji: String,
    val instruction: String,
    val hint: String
)

private val poseSlots = listOf(
    PoseSlot(PhotoAngle.FRONT,
        "🧍",
        "Stand straight, arms slightly away from sides, facing the camera",
        "Keep your feet shoulder-width apart"),
    PoseSlot(PhotoAngle.SIDE,
        "🧍",
        "Turn 90° to your left, arms relaxed at sides",
        "Look straight ahead, chin parallel to the floor"),
    PoseSlot(PhotoAngle.BACK,
        "🧍",
        "Turn around completely, arms slightly away from sides",
        "Feet shoulder-width apart, head straight")
)

// ─── Screen ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckinScreen(
    date: Long,
    onNavigateBack: () -> Unit,
    viewModel: CheckinViewModel = koinViewModel(parameters = { parametersOf(date) })
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.saved) { if (state.saved) onNavigateBack() }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // ── Camera URIs (one per pose, created fresh each time camera opens) ──
    // rememberSaveable: the camera app often gets us killed in the background; these must survive that
    var pendingCameraSlot by rememberSaveable { mutableStateOf<PhotoAngle?>(null) }
    var currentCameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && currentCameraUri != null) {
            pendingCameraSlot?.let { slot ->
                viewModel.setPhotoUri(slot, currentCameraUri)
            }
        }
        pendingCameraSlot = null
        currentCameraUri = null
    }

    // Gallery fallback launchers (one per slot)
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { pendingCameraSlot?.let { slot -> viewModel.setPhotoUri(slot, uri) } }
        pendingCameraSlot = null
    }

    // Photo source dialog state
    var showSourceDialog by rememberSaveable { mutableStateOf(false) }
    var dialogTargetSlot by rememberSaveable { mutableStateOf<PhotoAngle?>(null) }

    fun openSource(slot: PhotoAngle) {
        dialogTargetSlot = slot
        showSourceDialog = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Full Check-in") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Date badge
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    DateUtils.formatDate(date),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // ── Measurements ────────────────────────────────────────────
            SectionHeader(icon = Icons.Default.Straighten, title = "Measurements")

            // Two-column grid for measurements
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric.entries.chunked(2).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        row.forEach { metric ->
                            val unit = metric.unit
                            val fieldError = viewModel.fieldError(metric, state)
                            OutlinedTextField(
                                value = state.text(metric),
                                onValueChange = { viewModel.update(metric, it) },
                                label = { Text("${metric.label} ($unit)") },
                                isError = fieldError != null,
                                supportingText = fieldError?.let { { Text(it) } },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                trailingIcon = {
                                    Text(unit,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(end = 4.dp))
                                }
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            OutlinedTextField(
                value = state.notes,
                onValueChange = { viewModel.updateNotes(it) },
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                leadingIcon = { Icon(Icons.Default.Notes, null) }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

            // ── Progress Photos ─────────────────────────────────────────
            SectionHeader(icon = Icons.Default.CameraAlt, title = "Progress Photos")

            Text(
                "Tap each pose to take a photo with your camera. Stand about 2–3 metres from a wall-mounted phone or have someone assist.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Three pose cards
            poseSlots.forEach { slot ->
                PoseCaptureCard(
                    slot      = slot,
                    capturedUri = state.photoUris[slot.angle],
                    onCapture = { openSource(slot.angle) },
                    onRetake  = { openSource(slot.angle) }
                )
            }

            Spacer(Modifier.height(4.dp))

            // ── Save button ─────────────────────────────────────────────
            Button(
                onClick = { viewModel.save() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = !state.isSaving,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor   = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Saving…", color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.SemiBold)
                } else {
                    Icon(Icons.Default.Check, null,
                        tint = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Save Check-in", color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // ── Photo source bottom-sheet dialog ──────────────────────────────────
    if (showSourceDialog) {
        AlertDialog(
            onDismissRequest = { showSourceDialog = false; pendingCameraSlot = null },
            title = {
                val slotLabel = dialogTargetSlot?.label.orEmpty()
                Text("Add $slotLabel Photo")
            },
            text = { Text("Choose how you'd like to add this photo.") },
            confirmButton = {
                // Camera option
                Button(
                    onClick = {
                        showSourceDialog = false
                        pendingCameraSlot = dialogTargetSlot
                        val uri = CameraUtils.createTempCameraUri(context, "pose_${dialogTargetSlot?.fileKey}")
                        currentCameraUri = uri
                        cameraLauncher.launch(uri)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor   = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Default.CameraAlt, null,
                        tint = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(6.dp))
                    Text("Take Photo", color = MaterialTheme.colorScheme.onPrimary)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showSourceDialog = false
                        pendingCameraSlot = dialogTargetSlot
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                ) {
                    Icon(Icons.Default.PhotoLibrary, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Choose from Gallery")
                }
            },
            icon = {
                Icon(Icons.Default.AddAPhoto,
                    contentDescription = null,
                    tint = CyanPrimary,
                    modifier = Modifier.size(32.dp))
            }
        )
    }
}

// ─── Pose Capture Card ────────────────────────────────────────────────────────

@Composable
private fun PoseCaptureCard(
    slot: PoseSlot,
    capturedUri: Uri?,
    onCapture: () -> Unit,
    onRetake: () -> Unit
) {
    val captured = capturedUri != null

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (captured)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Thumbnail / placeholder
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .border(
                        width = if (captured) 2.dp else 1.dp,
                        color = if (captured) CyanPrimary
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (captured) {
                    AsyncImage(
                        model = capturedUri,
                        contentDescription = "${slot.angle.label} photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                    )
                    // Green tick overlay
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .align(Alignment.TopEnd)
                            .offset(x = 6.dp, y = (-6).dp)
                            .background(Color(0xFF4CAF50), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Check, null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp))
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(slot.emoji, fontSize = 28.sp)
                        Text(slot.angle.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Info + button
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        slot.angle.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (captured) {
                        Surface(
                            color = Color(0xFF4CAF50).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text("✓ Done",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF4CAF50),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
                Text(
                    slot.instruction,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
                if (!captured) {
                    Text(
                        "💡 ${slot.hint}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                }

                Spacer(Modifier.height(4.dp))

                if (captured) {
                    OutlinedButton(
                        onClick = onRetake,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Retake", style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    Button(
                        onClick = onCapture,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor   = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Capture ${slot.angle.label}",
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

// ─── Section header helper ────────────────────────────────────────────────────

@Composable
private fun SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String
) {
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null,
            tint = CyanPrimary,
            modifier = Modifier.size(20.dp))
        Text(title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold)
    }
}
