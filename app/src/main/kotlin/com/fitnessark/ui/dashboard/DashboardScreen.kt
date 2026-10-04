package com.fitnessark.ui.dashboard

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fitnessark.data.model.Metric
import com.fitnessark.data.model.UnitSystem
import com.fitnessark.ui.theme.LocalUnitSystem
import com.fitnessark.ui.theme.CyanPrimary
import com.fitnessark.util.DateUtils
import com.fitnessark.util.MeasurementInput
import org.koin.androidx.compose.koinViewModel

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
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CyanPrimary)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Streak Card
            StreakCard(streak = state.streakDays)

            // Today's Weight Card
            TodayWeightCard(
                weight = state.todayWeight,
                unitSystem = unitSystem,
                onEditClick = { openWeightDialog() }
            )

            // Progress Overview
            ProgressOverviewCard(
                weightChange = state.weightChangeLast7Days,
                weeklyAverage = state.weeklyAverageWeight,
                unitSystem = unitSystem,
                onClick = onNavigateToProgress
            )

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

            // Quick Actions
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onNavigateToCheckin,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor   = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Full Check-in", color = MaterialTheme.colorScheme.onPrimary)
                }
                OutlinedButton(
                    onClick = { openWeightDialog() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Log Weight Only")
                }
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
fun StreakCard(streak: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.LocalFireDepartment,
                contentDescription = "Streak",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "$streak Day Streak",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (streak > 0) "Keep it up!" else "Start logging to build your streak",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun TodayWeightCard(weight: Float?, unitSystem: UnitSystem, onEditClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "Today's Weight",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = weight?.let { Metric.WEIGHT.format(it, unitSystem) } ?: "Not logged",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (weight != null) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledIconButton(onClick = onEditClick) {
                Icon(Icons.Default.Edit, contentDescription = "Edit weight")
            }
        }
    }
}

@Composable
fun ProgressOverviewCard(weightChange: Float?, weeklyAverage: Float?, unitSystem: UnitSystem, onClick: () -> Unit = {}) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Neutral on purpose: a change isn't "good" or "bad", so no red/green.
            val trendColor = MaterialTheme.colorScheme.onSurface
            val trendIcon = when {
                weightChange == null || weightChange == 0f -> Icons.Default.Remove
                weightChange > 0f                          -> Icons.Default.ArrowUpward
                else                                       -> Icons.Default.ArrowDownward
            }

            Icon(
                imageVector = trendIcon,
                contentDescription = "Trend",
                tint = trendColor,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "Last 7 Days",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val changeText = when {
                    weightChange == null -> "—"
                    weightChange == 0f   -> "No change"
                    else                 -> "%+.1f %s".format(
                        Metric.WEIGHT.toDisplay(weightChange, unitSystem), Metric.WEIGHT.unit(unitSystem))
                }
                Text(
                    text = changeText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = trendColor
                )
                if (weeklyAverage != null) {
                    Text(
                        text = "7-day average ${Metric.WEIGHT.format(weeklyAverage, unitSystem)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
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
        shape = RoundedCornerShape(16.dp),
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
                        .clip(RoundedCornerShape(16.dp))
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
                        .padding(12.dp)
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
