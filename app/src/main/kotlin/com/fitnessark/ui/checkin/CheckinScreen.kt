package com.fitnessark.ui.checkin

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.fitnessark.data.model.Metric
import com.fitnessark.data.model.PhotoAngle
import com.fitnessark.ui.theme.successColor
import com.fitnessark.util.DateUtils
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import com.fitnessark.ui.theme.Spacing

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
        "🚶",
        "Turn 90° to your left, arms relaxed at sides",
        "Look straight ahead, chin parallel to the floor"),
    PoseSlot(PhotoAngle.BACK,
        "🔙",
        "Turn around completely, arms slightly away from sides",
        "Feet shoulder-width apart, head straight")
)

// ─── Screen ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckinScreen(
    date: Long,
    onNavigateBack: () -> Unit,
    onOpenInAppCamera: (PhotoAngle) -> Unit = {},
    cameraResult: State<String?> = mutableStateOf(null),
    onCameraResultConsumed: () -> Unit = {},
    cameraFailed: State<Boolean> = mutableStateOf(false),
    onCameraFailureConsumed: () -> Unit = {},
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

    // R10: leaving with anything typed or picked asks first. Nothing typed leaves straight away.
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    val hasUnsaved = viewModel.hasUnsavedInput(state)
    val requestBack = { if (hasUnsaved && !state.isSaving) showDiscardDialog = true else onNavigateBack() }
    BackHandler(enabled = hasUnsaved && !state.isSaving) { showDiscardDialog = true }

    // dialogTargetSlot is "which pose a pending in-app camera / gallery result belongs to", since
    // the camera route itself carries the PhotoAngle (there's no separate "pending slot" to let
    // drift from what was actually opened).
    var dialogTargetSlot by rememberSaveable { mutableStateOf<PhotoAngle?>(null) }

    LaunchedEffect(cameraResult.value) {
        val uriString = cameraResult.value ?: return@LaunchedEffect
        dialogTargetSlot?.let { slot -> viewModel.setPhotoUri(slot, Uri.parse(uriString)) }
        dialogTargetSlot = null
        // Clears the backing SavedStateHandle entry so a later attempt doesn't replay this result.
        onCameraResultConsumed()
    }
    LaunchedEffect(cameraFailed.value) {
        if (!cameraFailed.value) return@LaunchedEffect
        dialogTargetSlot = null
        viewModel.cameraCaptureFailed()
        onCameraFailureConsumed()
    }

    // F11: CAMERA is requested at runtime the first time "Take photo" is tapped, not declared and
    // left unrequested (that was the old Q1 bug). A denial falls back to the gallery button for
    // that attempt instead of silently doing nothing.
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val slot = dialogTargetSlot
        if (granted && slot != null) {
            onOpenInAppCamera(slot)
        } else {
            dialogTargetSlot = null
            viewModel.cameraPermissionDenied()
        }
    }

    // Gallery launcher (one for all poses; dialogTargetSlot says which)
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { dialogTargetSlot?.let { slot -> viewModel.setPhotoUri(slot, uri) } }
        dialogTargetSlot = null
    }

    // R9: "Take photo" goes straight to the camera (asking for permission first if needed).
    fun takePhoto(slot: PhotoAngle) {
        dialogTargetSlot = slot
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) onOpenInAppCamera(slot)
        else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    fun pickFromGallery(slot: PhotoAngle) {
        dialogTargetSlot = slot
        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    // R7: only weight is shown up front; the other measurements fold away until asked for.
    val otherMetrics = Metric.entries.filter { it != Metric.WEIGHT }
    var moreExpanded by rememberSaveable { mutableStateOf(false) }
    val othersFilled = otherMetrics.any { state.text(it).isNotBlank() }
    // A day that already has other measurements opens the section by itself
    LaunchedEffect(othersFilled) { if (othersFilled) moreExpanded = true }
    // ...and a field with an error never stays hidden
    val showMore = moreExpanded || otherMetrics.any { viewModel.fieldError(it, state) != null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Full Check-in") },
                navigationIcon = {
                    IconButton(onClick = requestBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        // R8: Save stays on screen however far the form is scrolled
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = { viewModel.save() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.md)
                        .height(52.dp),
                    enabled = !state.isSaving,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor   = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = MaterialTheme.shapes.medium
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
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            // Date badge — tap to log or edit another day
            Surface(
                onClick = { showDatePicker = true },
                // The badge stays compact, but its tap area is the full 48dp
                modifier = Modifier.minimumInteractiveComponentSize(),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        DateUtils.formatDate(state.date),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(Icons.Default.Edit, "Change date",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(14.dp))
                }
            }

            // ── Measurements ────────────────────────────────────────────
            SectionHeader(icon = Icons.Default.Straighten, title = "Measurements")

            MeasurementField(Metric.WEIGHT, state, viewModel, Modifier.fillMaxWidth())

            TextButton(
                onClick = { moreExpanded = !showMore },
                modifier = Modifier.semantics {
                    stateDescription = if (showMore) "Expanded" else "Collapsed"
                }
            ) {
                Text("More measurements")
                Spacer(Modifier.width(4.dp))
                Icon(
                    if (showMore) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }

            AnimatedVisibility(visible = showMore) {
                // Two-column grid; every field reserves its error line, so an error doesn't shift
                // the rows below it
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    otherMetrics.chunked(2).forEach { row ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            row.forEach { metric ->
                                MeasurementField(metric, state, viewModel, Modifier.weight(1f))
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
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
                "Prop your phone against a wall or use a tripod about 2–3 m away, or ask someone to hold it. Tap Take photo on each pose.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Three pose cards
            poseSlots.forEach { slot ->
                PoseCaptureCard(
                    slot        = slot,
                    capturedUri = state.photoUris[slot.angle],
                    onTakePhoto = { takePhoto(slot.angle) },
                    onPickFromGallery = { pickFromGallery(slot.angle) }
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = DateUtils.pickerMillisOf(state.date),
            selectableDates = object : SelectableDates {
                // No future days: a check-in records what you measured
                override fun isSelectableDate(utcTimeMillis: Long) =
                    utcTimeMillis <= DateUtils.pickerMillisOf(System.currentTimeMillis())
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    enabled = pickerState.selectedDateMillis != null,
                    onClick = {
                        pickerState.selectedDateMillis?.let { viewModel.setDate(DateUtils.timestampForPickedDay(it)) }
                        showDatePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = pickerState) }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard this check-in?") },
            text = { Text("What you've entered hasn't been saved.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("Keep editing") }
            }
        )
    }
}

// ─── Measurement field ────────────────────────────────────────────────────────

@Composable
private fun MeasurementField(
    metric: Metric,
    state: CheckinUiState,
    viewModel: CheckinViewModel,
    modifier: Modifier = Modifier
) {
    val fieldError = viewModel.fieldError(metric, state)
    OutlinedTextField(
        value = state.text(metric),
        onValueChange = { viewModel.update(metric, it) },
        // The unit appears once, in the label
        label = { Text("${metric.label} (${metric.unit(state.unitSystem)})") },
        isError = fieldError != null,
        // Always one line tall, so an error doesn't push the fields below it down
        supportingText = { Text(fieldError ?: " ") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
        singleLine = true
    )
}

// ─── Pose Capture Card ────────────────────────────────────────────────────────

@Composable
private fun PoseCaptureCard(
    slot: PoseSlot,
    capturedUri: Uri?,
    onTakePhoto: () -> Unit,
    onPickFromGallery: () -> Unit
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
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // Thumbnail / placeholder
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.background)
                    .border(
                        width = if (captured) 2.dp else 1.dp,
                        color = if (captured) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        shape = MaterialTheme.shapes.medium
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (captured) {
                    AsyncImage(
                        model = capturedUri,
                        contentDescription = "${slot.angle.label} photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium)
                    )
                    // Done tick overlay
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .align(Alignment.TopEnd)
                            .offset(x = 6.dp, y = (-6).dp)
                            .background(successColor(), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Check, null,
                            tint = MaterialTheme.colorScheme.background,
                            modifier = Modifier.size(14.dp))
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Decorative: the label right below names the pose
                        Text(slot.emoji, fontSize = 28.sp,
                            modifier = Modifier.clearAndSetSemantics { })
                        Text(slot.angle.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Info + buttons
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
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
                            color = successColor().copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text("✓ Done",
                                style = MaterialTheme.typography.labelSmall,
                                color = successColor(),
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
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        Icon(Icons.Default.Lightbulb, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp))
                        Text(
                            slot.hint,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    if (captured) {
                        OutlinedButton(
                            onClick = onTakePhoto,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Retake", style = MaterialTheme.typography.labelMedium)
                        }
                    } else {
                        Button(
                            onClick = onTakePhoto,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor   = MaterialTheme.colorScheme.onPrimary
                            ),
                            contentPadding = PaddingValues(vertical = Spacing.sm),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Icon(Icons.Default.CameraAlt, null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Take photo",
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    OutlinedIconButton(onClick = onPickFromGallery) {
                        Icon(Icons.Default.PhotoLibrary,
                            contentDescription = "Choose ${slot.angle.label.lowercase()} photo from gallery")
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
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Icon(icon, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp))
        Text(title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold)
    }
}
