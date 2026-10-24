package com.fitnessark.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fitnessark.data.model.Metric
import com.fitnessark.data.model.UnitSystem
import com.fitnessark.ui.theme.DashboardSkeleton
import com.fitnessark.ui.theme.LocalUnitSystem
import com.fitnessark.util.DateUtils
import com.fitnessark.util.MeasurementInput
import org.koin.androidx.compose.koinViewModel
import com.fitnessark.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToCheckin: () -> Unit,
    onNavigateToProgress: () -> Unit,
    onNavigateToPhotos: () -> Unit,
    openWeightDialogOnStart: Boolean = false,
    viewModel: DashboardViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showWeightDialog by remember { mutableStateOf(false) }
    var weightInput by remember { mutableStateOf("") }
    // What the dialog was opened with, so an unchanged value isn't re-saved through a rounded lb round trip
    var weightInitial by remember { mutableStateOf("") }
    val unitSystem = LocalUnitSystem.current
    val weightUnit = Metric.WEIGHT.unit(unitSystem)
    fun openWeightDialog() {
        weightInput = state.todayWeight?.let { Metric.WEIGHT.toInputText(it, unitSystem) } ?: ""
        weightInitial = weightInput
        showWeightDialog = true
    }
    // The widget's "Log weight" button opens straight to this dialog (it has no number of its own to save).
    LaunchedEffect(openWeightDialogOnStart) { if (openWeightDialogOnStart) openWeightDialog() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Fitness Ark",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        if (state.isLoading) {
            DashboardSkeleton(Modifier.padding(padding))
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            if (state.measurementCount == 0) {
                FirstRunCard(onCheckin = onNavigateToCheckin, onLogWeight = { openWeightDialog() })
            } else {
                HeroCard(
                    state = state,
                    unitSystem = unitSystem,
                    onEditWeight = { openWeightDialog() },
                    onCheckin = onNavigateToCheckin,
                    onProgress = onNavigateToProgress
                )
            }

            // Latest Photo Preview
            if (state.latestPhoto != null) {
                LatestPhotoCard(
                    // The pre-generated thumbnail, not a full-res angle path: this preview is
                    // blurred by default (see LatestPhotoCard), so there's no reason to decode
                    // the larger image just to immediately obscure it.
                    photoPath = state.latestPhoto!!.thumbnailPath,
                    date = state.latestPhoto!!.date,
                    onClick = onNavigateToPhotos
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    if (showWeightDialog) {
        val parsedWeight = MeasurementInput.parseMetric(Metric.WEIGHT, weightInput, unitSystem)
        val weightError = MeasurementInput.validate(Metric.WEIGHT, weightInput, unitSystem)
        AlertDialog(
            onDismissRequest = { showWeightDialog = false },
            title = { Text("Log Weight") },
            text = {
                OutlinedTextField(
                    value = weightInput,
                    onValueChange = { weightInput = it },
                    label = { Text("Weight ($weightUnit)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = weightError != null,
                    supportingText = weightError?.let { { Text(it) } },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = parsedWeight != null && weightError == null,
                    onClick = {
                        if (weightInput != weightInitial) parsedWeight?.let { viewModel.updateWeight(it) }
                        showWeightDialog = false
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showWeightDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun HeroCard(
    state: DashboardUiState,
    unitSystem: UnitSystem,
    onEditWeight: () -> Unit,
    onCheckin: () -> Unit,
    onProgress: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .clickable(onClickLabel = "Edit today's weight", onClick = onEditWeight)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.LocalFireDepartment,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "${state.streakDays} day streak",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onProgress) { Text("Progress") }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Today's weight",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = state.todayWeight?.let { Metric.WEIGHT.format(it, unitSystem) } ?: "Not logged",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (state.todayWeight != null) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (state.recentWeights.size >= 2) {
                    Sparkline(
                        values = state.recentWeights,
                        modifier = Modifier.width(96.dp).height(40.dp)
                    )
                }
            }

            WeekChange(state.weightChangeLast7Days, state.weeklyAverageWeight, unitSystem)
            WeekStrip(state.recentLogged)

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Button(onClick = onCheckin, modifier = Modifier.weight(1f)) { Text("Full check-in") }
                OutlinedButton(onClick = onEditWeight, modifier = Modifier.weight(1f)) { Text("Log weight only") }
            }
        }
    }
}

@Composable
private fun WeekChange(weightChange: Float?, weeklyAverage: Float?, unitSystem: UnitSystem) {
    // Neutral on purpose: a change isn't "good" or "bad", so no red/green.
    val icon = when {
        weightChange == null || weightChange == 0f -> Icons.Default.Remove
        weightChange > 0f                          -> Icons.Default.ArrowUpward
        else                                       -> Icons.Default.ArrowDownward
    }
    val changeText = when {
        weightChange == null -> "—"
        weightChange == 0f   -> "No change"
        else                 -> "%+.1f %s".format(
            Metric.WEIGHT.toDisplay(weightChange, unitSystem), Metric.WEIGHT.unit(unitSystem))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = "$changeText in 7 days",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
        if (weeklyAverage != null) {
            Text(
                text = "  ·  avg ${Metric.WEIGHT.format(weeklyAverage, unitSystem)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Seven dots, oldest to today: filled = logged, outlined = not. The caption carries the same facts as text. */
@Composable
private fun WeekStrip(logged: List<Boolean>) {
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = "Logged ${logged.count { it }} of the last 7 days"
        }
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            logged.forEach { done ->
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .then(
                            if (done) Modifier.background(primary)
                            else Modifier.border(1.5.dp, outline, CircleShape)
                        )
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = "${logged.count { it }}/7 days",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun Sparkline(values: List<Float>, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier.clearAndSetSemantics { }) {
        val min = values.min()
        val range = (values.max() - min).takeIf { it > 0f } ?: 1f
        val pad = 4.dp.toPx()
        fun point(i: Int) = Offset(
            pad + (size.width - 2 * pad) * i / (values.size - 1),
            size.height - pad - (size.height - 2 * pad) * (values[i] - min) / range
        )
        val path = Path().apply {
            moveTo(point(0).x, point(0).y)
            for (i in 1 until values.size) lineTo(point(i).x, point(i).y)
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(color, radius = 3.5.dp.toPx(), center = point(values.lastIndex))
    }
}

@Composable
private fun FirstRunCard(onCheckin: () -> Unit, onLogWeight: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Icon(
                Icons.Default.LocalFireDepartment,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Text(
                "Nothing logged yet",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Log your weight, measurements and photos to start your streak and see your progress.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Button(onClick = onCheckin, modifier = Modifier.fillMaxWidth()) { Text("Start your first check-in") }
            TextButton(onClick = onLogWeight) { Text("Log weight only") }
        }
    }
}

@Composable
fun LatestPhotoCard(photoPath: String?, date: Long, onClick: () -> Unit) {
    // Resets to blurred whenever the photo being shown changes, as well as on first composition
    // — a body photo on the dashboard should never default to visible, in case someone else is
    // the one opening the app. Keyed on photoPath (not just composition lifetime) so a newer
    // check-in photo replacing this one re-blurs even if a future change keeps this composable
    // alive across the swap instead of disposing it.
    var revealed by remember(photoPath) { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                // While blurred, the first tap only reveals — it must not also hand the viewer
                // the full, unblurred photo one screen away in the Photos tab. Only a tap after
                // it's already revealed navigates there.
                if (revealed) onClick() else revealed = true
            },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(180.dp)) {
            if (photoPath != null) {
                AsyncImage(
                    model = photoPath,
                    // Doesn't announce "Latest photo" while blurred: a screen reader shouldn't
                    // confirm a body photo is there when sighted users can't make it out either.
                    contentDescription = if (revealed) "Latest photo" else "Hidden photo, double tap to show",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(MaterialTheme.shapes.large)
                        .then(if (!revealed) Modifier.blur(24.dp) else Modifier)
                )
                // Date overlay
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent,
                                    Color.Black.copy(alpha = 0.6f))
                            )
                        )
                        .padding(Spacing.md)
                ) {
                    Text(
                        text = DateUtils.formatDate(date),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.align(Alignment.BottomStart)
                    )
                }
                FilledIconButton(
                    onClick = { revealed = !revealed },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(48.dp)
                        .clip(CircleShape),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.5f),
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = if (revealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (revealed) "Hide photo" else "Show photo"
                    )
                }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Latest Progress Photo",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
