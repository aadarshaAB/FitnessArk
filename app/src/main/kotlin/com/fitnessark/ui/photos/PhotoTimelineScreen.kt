package com.fitnessark.ui.photos

import android.os.Build
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.fitnessark.data.local.entity.PhotoEntity
import com.fitnessark.data.model.PhotoAngle
import com.fitnessark.data.model.pathFor
import com.fitnessark.util.DateUtils
import org.koin.androidx.compose.koinViewModel
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoTimelineScreen(
    onNavigateToDetail: (String) -> Unit,
    viewModel: PhotoTimelineViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf<String?>(null) }
    // Photos start hidden on every visit (plain remember, not saved), in case someone else opens the
    // app, matching the dashboard preview. One toggle covers the big photo, the grid and the comparison.
    var revealed by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Photos") },
                actions = {
                    if (state.photos.isNotEmpty()) {
                        IconButton(onClick = { revealed = !revealed }) {
                            Icon(
                                if (revealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (revealed) "Hide photos" else "Show photos"
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.toggleBeforeAfter() }) {
                        Icon(
                            Icons.Default.CompareArrows,
                            contentDescription = "Before/After",
                            tint = if (state.beforeAfterMode) MaterialTheme.colorScheme.primary
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
                CircularProgressIndicator()
            }
        } else if (state.photos.isEmpty()) {
            EmptyPhotosState(modifier = Modifier.padding(padding))
        } else if (state.beforeAfterMode) {
            BeforeAfterView(
                photos      = state.photos,
                beforePhoto = state.beforePhoto,
                afterPhoto  = state.afterPhoto,
                angle       = state.compareAngle,
                layout      = state.compareLayout,
                hidden      = !revealed,
                onAngleChange  = viewModel::setCompareAngle,
                onLayoutChange = viewModel::setCompareLayout,
                onSetBefore = { id ->
                    viewModel.setBeforeAfterPhotos(id, state.afterPhoto?.id ?: "")
                },
                onSetAfter  = { id ->
                    viewModel.setBeforeAfterPhotos(state.beforePhoto?.id ?: "", id)
                },
                modifier    = Modifier.padding(padding).fillMaxSize()
            )
        } else {
            TimelineGrid(
                state    = state,
                hidden   = !revealed,
                onReveal = { revealed = true },
                onSelect = viewModel::selectPhoto,
                onAngle  = viewModel::setViewAngle,
                onOpen   = onNavigateToDetail,
                onDelete = { showDeleteDialog = it },
                modifier = Modifier.padding(padding)
            )
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

/** A progress photo that is blurred (or, below API 31 where blur is a no-op, not drawn) while [hidden]. */
@Composable
private fun ProtectedPhoto(
    model:              Any?,
    hidden:             Boolean,
    contentDescription: String?,
    contentScale:       ContentScale,
    modifier:           Modifier = Modifier
) {
    if (hidden && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
        return
    }
    AsyncImage(
        model              = model,
        contentDescription = if (hidden) null else contentDescription,
        contentScale       = contentScale,
        modifier           = modifier.then(if (hidden) Modifier.blur(24.dp) else Modifier)
    )
}

private val monthFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")

/** Single scrolling screen: the selected photo and pose switcher on top, then every day grouped by month. */
@Composable
private fun TimelineGrid(
    state:    PhotoTimelineUiState,
    hidden:   Boolean,
    onReveal: () -> Unit,
    onSelect: (String) -> Unit,
    onAngle:  (PhotoAngle) -> Unit,
    onOpen:   (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val byMonth = remember(state.photos) {
        state.photos.groupBy { YearMonth.parse(it.localDate.take(7)) }
    }
    val selected = state.selectedPhoto
    val angle = state.viewAngle

    LazyVerticalGrid(
        columns               = GridCells.Fixed(4),
        state                 = gridState,
        modifier              = modifier.fillMaxSize(),
        contentPadding        = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement   = Arrangement.spacedBy(8.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val path = selected?.pathFor(angle)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(
                            onClickLabel = if (hidden) "Show photos" else "Open photo details"
                        ) {
                            // While hidden the tap only reveals; details are one more tap away
                            if (hidden) onReveal() else selected?.let { onOpen(it.id) }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (path != null) {
                        ProtectedPhoto(
                            model = path, hidden = hidden, contentDescription = "Progress photo",
                            contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            "No ${angle.label.lowercase()} photo for this day",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PhotoAngle.entries.forEach { a ->
                        FilterChip(
                            selected = angle == a,
                            onClick  = { onAngle(a) },
                            label    = { Text(a.label) }
                        )
                    }
                }
                selected?.let { photo ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            DateUtils.formatDate(photo.date),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { if (hidden) onReveal() else onOpen(photo.id) }) { Text("Details") }
                        IconButton(onClick = { onDelete(photo.id) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete day",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        byMonth.forEach { (month, photos) ->
            item(key = "month-$month", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    month.format(monthFormat),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(photos, key = { it.id }) { photo ->
                val isSelected = photo.id == selected?.id
                val thumbPath = photo.thumbnailPath
                    ?: photo.frontPhotoPath ?: photo.sidePhotoPath ?: photo.backPhotoPath
                Card(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clickable(onClickLabel = "Select ${DateUtils.formatDate(photo.date)}") {
                            onSelect(photo.id)
                            scope.launch { gridState.animateScrollToItem(0) }
                        },
                    border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                    shape  = RoundedCornerShape(8.dp)
                ) {
                    Box(Modifier.fillMaxSize()) {
                        if (thumbPath != null) {
                            ProtectedPhoto(
                                model = thumbPath, hidden = hidden,
                                contentDescription = DateUtils.formatDate(photo.date),
                                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                            )
                        }
                        Text(
                            DateUtils.formatDateShort(photo.date),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(topEnd = 6.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }
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
    hidden:      Boolean,
    onAngleChange:  (PhotoAngle) -> Unit,
    onLayoutChange: (ComparisonLayout) -> Unit,
    modifier:    Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Which pose to compare, then (separately) how to lay the two photos out
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PhotoAngle.entries.forEach { a ->
                FilterChip(
                    selected = angle == a,
                    onClick  = { onAngleChange(a) },
                    label    = { Text(a.label) }
                )
            }
        }
        SingleChoiceSegmentedButtonRow(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            ComparisonLayout.entries.forEachIndexed { index, l ->
                SegmentedButton(
                    selected = layout == l,
                    onClick  = { onLayoutChange(l) },
                    shape    = SegmentedButtonDefaults.itemShape(index, ComparisonLayout.entries.size),
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
                var picking by remember { mutableStateOf(false) }
                Column(Modifier.weight(1f)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick  = { picking = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(photo?.let { DateUtils.formatDateShort(it.date) } ?: "Select")
                    }
                }
                if (picking) {
                    PhotoPickerDialog(
                        title    = "Select $label photo",
                        photos   = photos,
                        angle    = angle,
                        hidden   = hidden,
                        selected = photo?.id,
                        onPick   = { action(it); picking = false },
                        onDismiss = { picking = false }
                    )
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
                    before = beforePhoto, after = afterPhoto, angle = angle, hidden = hidden, modifier = comparisonModifier
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
                    beforePath = beforePath, afterPath = afterPath, hidden = hidden, modifier = comparisonModifier
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
    hidden:     Boolean,
    modifier:   Modifier = Modifier
) {
    var sliderFraction by remember { mutableFloatStateOf(0.5f) }
    var containerWidthPx by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            // Drag isn't the only way: a screen reader (or switch) can step the divider instead
            .semantics {
                contentDescription = "Before and after comparison slider"
                stateDescription = "${(sliderFraction * 100).roundToInt()} percent before photo"
                progressBarRangeInfo = ProgressBarRangeInfo(sliderFraction, 0f..1f, steps = 19)
                setProgress { target ->
                    sliderFraction = target.coerceIn(0f, 1f)
                    true
                }
            }
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
        ProtectedPhoto(
            model              = afterPath,
            hidden             = hidden,
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
            ProtectedPhoto(
                model              = beforePath,
                hidden             = hidden,
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
            Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = Color.Black)
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
    hidden:   Boolean,
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
                    ProtectedPhoto(
                        model              = path,
                        hidden             = hidden,
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

/** Thumbnail chooser for a before/after day; days without the chosen pose are dimmed and say so. */
@Composable
private fun PhotoPickerDialog(
    title:     String,
    photos:    List<PhotoEntity>,
    angle:     PhotoAngle,
    hidden:    Boolean,
    selected:  String?,
    onPick:    (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyVerticalGrid(
                columns               = GridCells.Fixed(3),
                modifier              = Modifier.heightIn(max = 420.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement   = Arrangement.spacedBy(8.dp)
            ) {
                items(photos, key = { it.id }) { p ->
                    val hasAngle = p.pathFor(angle) != null
                    val thumb = p.pathFor(angle) ?: p.thumbnailPath
                        ?: p.frontPhotoPath ?: p.sidePhotoPath ?: p.backPhotoPath
                    Column(
                        Modifier.clickable(onClickLabel = "Select ${DateUtils.formatDate(p.date)}") { onPick(p.id) },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Card(
                            modifier = Modifier.aspectRatio(0.75f).alpha(if (hasAngle) 1f else 0.4f),
                            border   = if (p.id == selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                            shape    = RoundedCornerShape(8.dp)
                        ) {
                            if (thumb != null) {
                                ProtectedPhoto(
                                    model = thumb, hidden = hidden, contentDescription = null,
                                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        Text(
                            DateUtils.formatDateShort(p.date),
                            style = MaterialTheme.typography.labelSmall
                        )
                        if (!hasAngle) {
                            Text(
                                "no ${angle.label.lowercase()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
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
