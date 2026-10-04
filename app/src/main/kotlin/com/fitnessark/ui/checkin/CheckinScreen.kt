package com.fitnessark.ui.checkin

import android.content.Context
import android.graphics.BitmapFactory
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.fitnessark.data.local.entity.MeasurementEntity
import com.fitnessark.data.local.entity.PhotoEntity
import com.fitnessark.data.repository.MeasurementRepository
import com.fitnessark.data.repository.PhotoRepository
import com.fitnessark.ui.theme.CyanPrimary
import com.fitnessark.util.BitmapUtils
import com.fitnessark.util.CameraUtils
import com.fitnessark.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

// ─── UI State ────────────────────────────────────────────────────────────────

data class CheckinUiState(
    val weight: String = "",
    val chest: String = "",
    val waist: String = "",
    val hips: String = "",
    val biceps: String = "",
    val thighs: String = "",
    val notes: String = "",
    val frontPhotoUri: Uri? = null,
    val sidePhotoUri: Uri? = null,
    val backPhotoUri: Uri? = null,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val errorMessage: String? = null
)

// ─── ViewModel ───────────────────────────────────────────────────────────────

class CheckinViewModel(
    private val measurementRepo: MeasurementRepository,
    private val photoRepo: PhotoRepository,
    private val date: Long
) : ViewModel() {

    private val _uiState = MutableStateFlow(CheckinUiState())
    val uiState: StateFlow<CheckinUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            measurementRepo.getMeasurementForDay(date)?.let { m ->
                _uiState.update { s ->
                    s.copy(
                        weight = if (m.weight > 0) m.weight.toString() else "",
                        chest  = if (m.chest  > 0) m.chest.toString()  else "",
                        waist  = if (m.waist  > 0) m.waist.toString()  else "",
                        hips   = if (m.hips   > 0) m.hips.toString()   else "",
                        biceps = if (m.biceps > 0) m.biceps.toString() else "",
                        thighs = if (m.thighs > 0) m.thighs.toString() else "",
                        notes  = m.notes ?: ""
                    )
                }
            }
        }
    }

    fun update(field: String, value: String) {
        _uiState.update {
            when (field) {
                "weight" -> it.copy(weight = value)
                "chest"  -> it.copy(chest  = value)
                "waist"  -> it.copy(waist  = value)
                "hips"   -> it.copy(hips   = value)
                "biceps" -> it.copy(biceps = value)
                "thighs" -> it.copy(thighs = value)
                "notes"  -> it.copy(notes  = value)
                else     -> it
            }
        }
    }

    fun setPhotoUri(type: String, uri: Uri?) {
        _uiState.update {
            when (type) {
                "front" -> it.copy(frontPhotoUri = uri)
                "side"  -> it.copy(sidePhotoUri  = uri)
                "back"  -> it.copy(backPhotoUri  = uri)
                else    -> it
            }
        }
    }

    fun save(context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            try {
                val s = _uiState.value
                val existingId = measurementRepo.getMeasurementForDay(date)?.id
                measurementRepo.saveMeasurement(
                    MeasurementEntity(
                        id     = existingId ?: java.util.UUID.randomUUID().toString(),
                        date   = date,
                        weight = s.weight.toFloatOrNull() ?: 0f,
                        chest  = s.chest.toFloatOrNull()  ?: 0f,
                        waist  = s.waist.toFloatOrNull()  ?: 0f,
                        hips   = s.hips.toFloatOrNull()   ?: 0f,
                        biceps = s.biceps.toFloatOrNull() ?: 0f,
                        thighs = s.thighs.toFloatOrNull() ?: 0f,
                        notes  = s.notes.ifEmpty { null }
                    )
                )
                if (s.frontPhotoUri != null || s.sidePhotoUri != null || s.backPhotoUri != null) {
                    coroutineScope {
                        fun loadBitmapAsync(uri: Uri?) = uri?.let {
                            async(Dispatchers.IO) { BitmapUtils.decodeUriToBitmap(context, it) }
                        }

                        val frontDeferred = loadBitmapAsync(s.frontPhotoUri)
                        val sideDeferred = loadBitmapAsync(s.sidePhotoUri)
                        val backDeferred = loadBitmapAsync(s.backPhotoUri)

                        photoRepo.savePhoto(
                            PhotoEntity(date = date),
                            frontDeferred?.await(),
                            sideDeferred?.await(),
                            backDeferred?.await()
                        )
                    }
                }
                _uiState.update { it.copy(isSaving = false, saved = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, errorMessage = e.message) }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }
}

// ─── Pose slot data ──────────────────────────────────────────────────────────

private data class PoseSlot(
    val key: String,
    val label: String,
    val emoji: String,
    val instruction: String,
    val hint: String
)

private val poseSlots = listOf(
    PoseSlot("front", "Front",
        "🧍",
        "Stand straight, arms slightly away from sides, facing the camera",
        "Keep your feet shoulder-width apart"),
    PoseSlot("side", "Side",
        "🧍",
        "Turn 90° to your left, arms relaxed at sides",
        "Look straight ahead, chin parallel to the floor"),
    PoseSlot("back", "Back",
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
    var pendingCameraSlot by rememberSaveable { mutableStateOf<String?>(null) }
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
    var dialogTargetSlot by rememberSaveable { mutableStateOf("") }

    fun openSource(slot: String) {
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

            val fields = listOf(
                Triple("weight", "Weight", "kg"),
                Triple("chest",  "Chest",  "cm"),
                Triple("waist",  "Waist",  "cm"),
                Triple("hips",   "Hips",   "cm"),
                Triple("biceps", "Biceps", "cm"),
                Triple("thighs", "Thighs", "cm")
            )
            // Two-column grid for measurements
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                fields.chunked(2).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        row.forEach { (key, label, unit) ->
                            val value = when (key) {
                                "weight" -> state.weight; "chest"  -> state.chest
                                "waist"  -> state.waist;  "hips"   -> state.hips
                                "biceps" -> state.biceps; else     -> state.thighs
                            }
                            OutlinedTextField(
                                value = value,
                                onValueChange = { viewModel.update(key, it) },
                                label = { Text("$label ($unit)") },
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
                onValueChange = { viewModel.update("notes", it) },
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
                val uri = when (slot.key) {
                    "front" -> state.frontPhotoUri
                    "side"  -> state.sidePhotoUri
                    else    -> state.backPhotoUri
                }
                PoseCaptureCard(
                    slot      = slot,
                    capturedUri = uri,
                    onCapture = { openSource(slot.key) },
                    onRetake  = { openSource(slot.key) }
                )
            }

            Spacer(Modifier.height(4.dp))

            // ── Save button ─────────────────────────────────────────────
            Button(
                onClick = { viewModel.save(context) },
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
                val slotLabel = poseSlots.find { it.key == dialogTargetSlot }?.label ?: dialogTargetSlot
                Text("Add $slotLabel Photo")
            },
            text = { Text("Choose how you'd like to add this photo.") },
            confirmButton = {
                // Camera option
                Button(
                    onClick = {
                        showSourceDialog = false
                        pendingCameraSlot = dialogTargetSlot
                        val uri = CameraUtils.createTempCameraUri(context, "pose_${dialogTargetSlot}")
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
                        contentDescription = "${slot.label} photo",
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
                        Text(slot.label,
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
                        slot.label,
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
                        Text("Capture ${slot.label}",
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
