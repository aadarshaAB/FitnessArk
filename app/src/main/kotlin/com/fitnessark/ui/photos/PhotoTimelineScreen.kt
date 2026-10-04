package com.fitnessark.ui.photos

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.fitnessark.data.local.entity.PhotoEntity
import com.fitnessark.data.model.PhotoAngle
import com.fitnessark.data.model.pathFor
import com.fitnessark.ui.theme.CyanPrimary
import com.fitnessark.util.DateUtils
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoTimelineScreen(
    onNavigateToDetail: (String) -> Unit,
    viewModel: PhotoTimelineViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Photos") },
                actions = {
                    IconButton(onClick = { viewModel.toggleBeforeAfter() }) {
                        Icon(
                            Icons.Default.CompareArrows,
                            contentDescription = "Before/After",
                            tint = if (state.beforeAfterMode) CyanPrimary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CyanPrimary)
            }
        } else if (state.photos.isEmpty()) {
            EmptyPhotosState(modifier = Modifier.padding(padding))
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (state.beforeAfterMode) {
                    BeforeAfterView(
                        photos      = state.photos,
                        beforePhoto = state.beforePhoto,
                        afterPhoto  = state.afterPhoto,
                        angle       = state.compareAngle,
                        layout      = state.compareLayout,
                        onAngleChange  = viewModel::setCompareAngle,
                        onLayoutChange = viewModel::setCompareLayout,
                        onSetBefore = { id ->
                            viewModel.setBeforeAfterPhotos(id, state.afterPhoto?.id ?: "")
                        },
                        onSetAfter  = { id ->
                            viewModel.setBeforeAfterPhotos(state.beforePhoto?.id ?: "", id)
                        },
                        modifier    = Modifier.weight(1f)
                    )
                } else {
                    // Main large photo
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(16.dp)
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        val photoPath = state.selectedPhoto?.let {
                            it.frontPhotoPath ?: it.sidePhotoPath ?: it.backPhotoPath
                        }
                        if (photoPath != null) {
                            AsyncImage(
                                model              = photoPath,
                                contentDescription = "Progress photo",
                                contentScale       = ContentScale.Fit,
                                modifier           = Modifier.fillMaxSize()
                            )
                        }
                        state.selectedPhoto?.let { photo ->
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(8.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        DateUtils.formatDate(photo.date),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    IconButton(
                                        onClick = { showDeleteDialog = photo.id },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete day",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Filmstrip
                LazyRow(
                    contentPadding    = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    state             = rememberLazyListState()
                ) {
                    items(state.photos, key = { it.id }) { photo ->
                        val isSelected = photo.id == state.selectedPhoto?.id
                        val thumbPath  = photo.thumbnailPath
                            ?: photo.frontPhotoPath
                            ?: photo.sidePhotoPath
                            ?: photo.backPhotoPath

                        Card(
                            modifier = Modifier
                                .size(72.dp)
                                .clickable { viewModel.selectPhoto(photo.id) },
                            border = if (isSelected) BorderStroke(2.dp, CyanPrimary) else null,
                            shape  = RoundedCornerShape(8.dp)
                        ) {
                            Box(Modifier.fillMaxSize()) {
                                if (thumbPath != null) {
                                    AsyncImage(
                                        model              = thumbPath,
                                        contentDescription = null,
                                        contentScale       = ContentScale.Crop,
                                        modifier           = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Entry?") },
            text = { Text("This will permanently remove all photos for this day.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePhoto(showDeleteDialog!!)
                        showDeleteDialog = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun BeforeAfterView(
    photos:      List<PhotoEntity>,
    beforePhoto: PhotoEntity?,
    afterPhoto:  PhotoEntity?,
    onSetBefore: (String) -> Unit,
    onSetAfter:  (String) -> Unit,
    angle:       PhotoAngle,
    layout:      ComparisonLayout,
    onAngleChange:  (PhotoAngle) -> Unit,
    onLayoutChange: (ComparisonLayout) -> Unit,
    modifier:    Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Which pose to compare, and how to lay the two photos out
        LazyRow(
            contentPadding        = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(PhotoAngle.entries.toList()) { a ->
                FilterChip(
                    selected = angle == a,
                    onClick  = { onAngleChange(a) },
                    label    = { Text(a.label) }
                )
            }
            items(ComparisonLayout.entries.toList()) { l ->
                FilterChip(
                    selected = layout == l,
                    onClick  = { onLayoutChange(l) },
                    label    = { Text(l.label) }
                )
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "Before" to beforePhoto to onSetBefore,
                "After"  to afterPhoto  to onSetAfter
            ).forEach { (labelPhoto, action) ->
                val (label, photo) = labelPhoto
                Column(Modifier.weight(1f)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    var expanded by remember { mutableStateOf(false) }
                    OutlinedButton(
                        onClick  = { expanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(photo?.let { DateUtils.formatDateShort(it.date) } ?: "Select")
                    }
                    DropdownMenu(
                        expanded          = expanded,
                        onDismissRequest  = { expanded = false }
                    ) {
                        photos.forEach { p ->
                            DropdownMenuItem(
                                text    = {
                                    val hasAngle = p.pathFor(angle) != null
                                    Text(
                                        if (hasAngle) DateUtils.formatDate(p.date)
                                        else "${DateUtils.formatDate(p.date)} — no ${angle.label.lowercase()} photo",
                                        color = if (hasAngle) MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                onClick = { action(p.id); expanded = false }
                            )
                        }
                    }
                }
            }
        }

        if (beforePhoto != null && afterPhoto != null) {
            val comparisonModifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
            val beforePath = beforePhoto.pathFor(angle)
            val afterPath  = afterPhoto.pathFor(angle)
            when {
                layout == ComparisonLayout.SIDE_BY_SIDE -> SideBySideView(
                    before = beforePhoto, after = afterPhoto, angle = angle, modifier = comparisonModifier
                )
                // A slider needs both images; say which one is missing instead of showing another pose
                beforePath == null || afterPath == null -> MissingAngleMessage(
                    missing = listOfNotNull(
                        beforePhoto.takeIf { beforePath == null },
                        afterPhoto.takeIf { afterPath == null }
                    ),
                    angle = angle, modifier = comparisonModifier
                )
                else -> SliderComparisonView(
                    beforePath = beforePath, afterPath = afterPath, modifier = comparisonModifier
                )
            }
        } else {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Select before and after photos to compare",
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp)
                )
            }
        }
    }
}

@Composable
private fun SliderComparisonView(
    beforePath: String,
    afterPath:  String,
    modifier:   Modifier = Modifier
) {
    var sliderFraction by remember { mutableFloatStateOf(0.5f) }
    var containerWidthPx by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(pass = PointerEventPass.Main)
                    sliderFraction = (down.position.x / size.width).coerceIn(0f, 1f)
                    containerWidthPx = size.width.toFloat()
                    do {
                        val event = awaitPointerEvent(pass = PointerEventPass.Main)
                        val drag  = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (drag.pressed) {
                            sliderFraction = (drag.position.x / size.width).coerceIn(0f, 1f)
                            drag.consume()
                        } else break
                    } while (true)
                }
            }
    ) {
        AsyncImage(
            model              = afterPath,
            contentDescription = "After",
            contentScale       = ContentScale.Crop,
            modifier           = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(sliderFraction)
                .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
        ) {
            AsyncImage(
                model              = beforePath,
                contentDescription = "Before",
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize()
            )
        }
        val sliderXPx = containerWidthPx * sliderFraction
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawLine(
                color       = Color.Black.copy(alpha = 0.25f),
                start       = Offset(sliderXPx + 2f, 0f),
                end         = Offset(sliderXPx + 2f, size.height),
                strokeWidth = 4f
            )
            drawLine(
                color       = Color.White,
                start       = Offset(sliderXPx, 0f),
                end         = Offset(sliderXPx, size.height),
                strokeWidth = 3f
            )
        }
        val handleHalfPx = with(LocalDensity.current) { 20.dp.toPx() }
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (sliderXPx - handleHalfPx).roundToInt().coerceAtLeast(0),
                        y = 0
                    )
                }
                .align(Alignment.CenterStart)
                .size(40.dp)
                .background(Color.White, RoundedCornerShape(50))
                .padding(2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("◀▶", style = MaterialTheme.typography.labelSmall, color = Color.Black)
        }
        Surface(
            modifier = Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 8.dp),
            color = Color.Black.copy(alpha = 0.45f),
            shape = RoundedCornerShape(4.dp)
        ) {
            Text("BEFORE", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Color.White)
        }
        Surface(
            modifier = Modifier.align(Alignment.TopEnd).padding(end = 8.dp, top = 8.dp),
            color = Color.Black.copy(alpha = 0.45f),
            shape = RoundedCornerShape(4.dp)
        ) {
            Text("AFTER", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Color.White)
        }
    }
}

@Composable
private fun SideBySideView(
    before:   PhotoEntity,
    after:    PhotoEntity,
    angle:    PhotoAngle,
    modifier: Modifier = Modifier
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("BEFORE" to before, "AFTER" to after).forEach { (label, photo) ->
            val path = photo.pathFor(angle)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (path != null) {
                    AsyncImage(
                        model              = path,
                        contentDescription = "$label ${angle.label.lowercase()} photo",
                        contentScale       = ContentScale.Fit,
                        modifier           = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        "No ${angle.label.lowercase()} photo",
                        style     = MaterialTheme.typography.bodySmall,
                        color     = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier  = Modifier.padding(8.dp)
                    )
                }
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                    color = Color.Black.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        "$label · ${DateUtils.formatDateShort(photo.date)}",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun MissingAngleMessage(
    missing:  List<PhotoEntity>,
    angle:    PhotoAngle,
    modifier: Modifier = Modifier
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(
            "No ${angle.label.lowercase()} photo for " +
                missing.joinToString(" or ") { DateUtils.formatDate(it.date) } +
                ". Pick another pose or day, or use Side by side.",
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier  = Modifier.padding(32.dp)
        )
    }
}

@Composable
fun EmptyPhotosState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment   = Alignment.CenterHorizontally,
            verticalArrangement   = Arrangement.spacedBy(8.dp),
            modifier              = Modifier.padding(32.dp)
        ) {
            Text("📸", style = MaterialTheme.typography.displayMedium)
            Text("No photos yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Start your journey! Add photos during a Full Check-in.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}
