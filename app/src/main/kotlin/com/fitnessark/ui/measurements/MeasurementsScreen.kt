package com.fitnessark.ui.measurements

import android.graphics.Color as AndroidColor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.fitnessark.data.local.entity.MeasurementEntity
import com.fitnessark.data.model.Metric
import com.fitnessark.ui.theme.CyanPrimary
import com.fitnessark.ui.theme.LocalUnitSystem
import com.fitnessark.util.DateUtils
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import org.koin.androidx.compose.koinViewModel

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasurementsScreen(viewModel: MeasurementsViewModel = koinViewModel()) {
    val state           by viewModel.uiState.collectAsState()
    val snackbarHost    = remember { SnackbarHostState() }

    // Keyed on the deleted entry too, so deleting a second entry replaces the first snackbar.
    LaunchedEffect(state.snackbarMessage, state.recentlyDeleted?.id) {
        state.snackbarMessage?.let { message ->
            val result = snackbarHost.showSnackbar(
                message     = message,
                actionLabel = if (state.recentlyDeleted != null) "Undo" else null,
                duration    = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Progress") },
                actions = {
                    IconButton(onClick = { viewModel.toggleView() }) {
                        Icon(
                            if (state.showTableView) Icons.Default.ShowChart
                            else Icons.Default.TableChart,
                            contentDescription = "Toggle view"
                        )
                    }
                    if (!state.showTableView) {
                        IconButton(onClick = { viewModel.toggleComparisonMode() }) {
                            Icon(
                                if (state.comparisonMode) Icons.Default.Close
                                else Icons.Default.ShowChart,
                                contentDescription = "Compare",
                                tint = if (state.comparisonMode) CyanPrimary
                                       else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Metric chips ──────────────────────────────────────────────
            LazyRow(
                contentPadding        = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(Metric.entries.toTypedArray()) { metric ->
                    FilterChip(
                        selected = state.selectedMetric == metric,
                        onClick  = { viewModel.changeMetric(metric) },
                        label    = { Text(metric.label) }
                    )
                }
            }

            // ── Date range chips ──────────────────────────────────────────
            LazyRow(
                contentPadding        = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(DateRange.entries.toTypedArray()) { range ->
                    FilterChip(
                        selected = state.dateRange == range,
                        onClick  = { viewModel.changeDateRange(range) },
                        label    = { Text(range.label) }
                    )
                }
            }

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CyanPrimary)
                }
                return@Scaffold
            }

            if (state.showTableView) {
                MeasurementsTable(
                    measurements = state.measurements,
                    onDelete     = { viewModel.deleteMeasurement(it) }
                )
            } else {
                ChartView(state = state, viewModel = viewModel)
            }
        }
    }
}

// ─── Chart view (chart + day-detail card + comparison panel) ──────────────────

@Composable
private fun ChartView(
    state:     MeasurementsUiState,
    viewModel: MeasurementsViewModel
) {
    val filtered    = viewModel.filteredMeasurements()
    val chartData   = viewModel.getChartData()
    val chartLabels = viewModel.getChartLabels()
    val unitSystem  = LocalUnitSystem.current

    if (filtered.isEmpty() || chartData.isEmpty()) {
        EmptyChartState(hasEntries = filtered.isNotEmpty(), metric = state.selectedMetric)
        return
    }

    Column(Modifier.fillMaxSize()) {

        // ── Line chart ────────────────────────────────────────────────────
        MeasurementLineChart(
            entries     = chartData.map { Entry(it.x, state.selectedMetric.toDisplay(it.y, unitSystem), it.data) },
            labels      = chartLabels,
            metricLabel = "${state.selectedMetric.label} (${state.selectedMetric.unit(unitSystem)})",
            onValueSelected = { entryIndex ->
                viewModel.selectChartEntry(entryIndex)
            },
            onNothingSelected = { viewModel.clearSelectedDay() },
            modifier    = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )

        // ── Day detail card (slides in when a dot is tapped) ──────────────
        AnimatedVisibility(
            visible = state.selectedDayMeasurement != null,
            enter   = fadeIn() + slideInVertically(initialOffsetY = { -it / 2 }),
            exit    = fadeOut() + slideOutVertically(targetOffsetY = { -it / 2 })
        ) {
            state.selectedDayMeasurement?.let { m ->
                DayDetailCard(
                    measurement    = m,
                    selectedMetric = state.selectedMetric,
                    onDismiss      = { viewModel.clearSelectedDay() },
                    onDelete       = {
                        viewModel.deleteMeasurement(m.id)
                        viewModel.clearSelectedDay()
                    },
                    getMetricValue = { metric -> viewModel.getMetricValue(m, metric) }
                )
            }
        }

        // ── Comparison panel ──────────────────────────────────────────────
        if (state.comparisonMode) {
            ComparisonModePanel(
                measurements = viewModel.filteredMeasurementsWithValue(state.selectedMetric),
                selected     = state.selectedComparisonPoints,
                result       = state.comparisonResult,
                onSelect     = { viewModel.selectComparisonPoint(it) }
            )
        }
    }
}

// ─── Day detail card ──────────────────────────────────────────────────────────

@Composable
fun DayDetailCard(
    measurement:    MeasurementEntity,
    selectedMetric: Metric,
    onDismiss:      () -> Unit,
    onDelete:       () -> Unit,
    getMetricValue: (Metric) -> Float?
) {
    val unitSystem = LocalUnitSystem.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Header row: date + dismiss button
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "📅  ${DateUtils.formatDate(measurement.date)}",
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        "Tap another dot to compare",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Delete entry
                    IconButton(
                        onClick  = { showDeleteConfirm = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete entry",
                            tint     = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    // Dismiss card
                    IconButton(
                        onClick  = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint     = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Big highlight — the metric that was tapped
            val highlightValue = getMetricValue(selectedMetric)
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier          = Modifier.padding(bottom = 10.dp)
            ) {
                Text(
                    highlightValue?.let { "%.1f".format(selectedMetric.toDisplay(it, unitSystem)) } ?: "—",
                    style      = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color      = CyanPrimary
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    selectedMetric.unit(unitSystem),
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = CyanPrimary.copy(alpha = 0.8f),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    selectedMetric.label,
                    style  = MaterialTheme.typography.bodySmall,
                    color  = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            // All other metrics in a 3-column grid
            val otherMetrics = Metric.entries.filter { it != selectedMetric }
            val rows = otherMetrics.chunked(3)
            rows.forEach { row ->
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { metric ->
                        val value = getMetricValue(metric)
                        MetricCell(
                            label  = metric.label,
                            value  = metric.format(value, unitSystem),
                            muted  = value == null,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // fill trailing gap if row has < 3 items
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(6.dp))
            }

            // Notes if present
            if (!measurement.notes.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                Spacer(Modifier.height(6.dp))
                Text(
                    "📝  ${measurement.notes}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }

    // ── Delete confirmation ───────────────────────────────────────────────────
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon  = { Icon(Icons.Default.Warning, null,
                tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete This Entry?") },
            text  = {
                Text(
                    "This will permanently remove the measurement entry for " +
                    "${DateUtils.formatDate(measurement.date)}. This cannot be undone."
                )
            },
            confirmButton = {
                Button(
                    onClick = { showDeleteConfirm = false; onDelete() },
                    colors  = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun MetricCell(
    label:    String,
    value:    String,
    muted:    Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier              = modifier,
        horizontalAlignment   = Alignment.Start
    ) {
        Text(
            label,
            style  = MaterialTheme.typography.labelSmall,
            color  = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.55f)
        )
        Text(
            value,
            style      = MaterialTheme.typography.bodySmall,
            fontWeight = if (muted) FontWeight.Normal else FontWeight.SemiBold,
            color      = if (muted)
                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.35f)
            else
                MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

// ─── Line chart (AndroidView wrapper) ────────────────────────────────────────

@Composable
fun MeasurementLineChart(
    entries:          List<Entry>,
    labels:           Map<Float, String>,
    metricLabel:      String,
    onValueSelected:  (Int) -> Unit,
    onNothingSelected: () -> Unit,
    modifier:         Modifier = Modifier
) {
    val primaryColor      = CyanPrimary.toArgb()
    val onSurfaceColor    = MaterialTheme.colorScheme.onSurface.toArgb()
    val highlightColor    = MaterialTheme.colorScheme.tertiary.toArgb()
    val axisGridColor     = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f).toArgb()
    val dotHoleColor      = MaterialTheme.colorScheme.surface.toArgb()

    AndroidView(
        factory = { ctx ->
            LineChart(ctx).apply {
                description.isEnabled  = false
                legend.isEnabled       = false
                setTouchEnabled(true)
                setPinchZoom(true)
                isDoubleTapToZoomEnabled = false
                setBackgroundColor(AndroidColor.TRANSPARENT)
                setDrawGridBackground(false)

                xAxis.apply {
                    position        = XAxis.XAxisPosition.BOTTOM
                    granularity     = 1f
                    textColor       = onSurfaceColor
                    gridColor       = axisGridColor
                    setDrawAxisLine(false)
                }
                axisLeft.apply {
                    textColor       = onSurfaceColor
                    gridColor       = axisGridColor
                    setDrawAxisLine(false)
                }
                axisRight.isEnabled = false

                // Forward tap events to Compose via lambda
                setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                    override fun onValueSelected(e: Entry?, h: Highlight?) {
                        // Entry.data holds the sequential index we set in getChartData()
                        val idx = (e?.data as? Int) ?: return
                        onValueSelected(idx)
                    }
                    override fun onNothingSelected() = onNothingSelected()
                })
            }
        },
        update = { chart ->
            if (entries.isEmpty()) return@AndroidView

            val dataSet = LineDataSet(entries, metricLabel).apply {
                color                   = primaryColor
                setCircleColor(primaryColor)
                lineWidth               = 2.5f
                circleRadius            = 5f
                circleHoleRadius        = 2.5f
                circleHoleColor         = dotHoleColor
                setDrawValues(false)
                mode                    = LineDataSet.Mode.CUBIC_BEZIER
                cubicIntensity          = 0.2f
                setDrawFilled(true)
                fillAlpha               = 35
                fillColor               = primaryColor
                highLightColor          = highlightColor
                highlightLineWidth      = 1.5f
            }

            chart.xAxis.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String =
                    labels[value] ?: ""
            }
            chart.data                 = LineData(dataSet)
            chart.animateX(400)
            chart.invalidate()
        },
        modifier = modifier
    )
}

// ─── Table view ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasurementsTable(
    measurements: List<MeasurementEntity>,
    onDelete:     (String) -> Unit
) {
    if (measurements.isEmpty()) {
        EmptyMeasurementsState()
        return
    }
    LazyColumn(
        contentPadding        = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement   = Arrangement.spacedBy(8.dp)
    ) {
        items(measurements, key = { it.id }) { measurement ->
            val dismissState = rememberSwipeToDismissBoxState(
                confirmValueChange = { value ->
                    if (value == SwipeToDismissBoxValue.EndToStart) {
                        onDelete(measurement.id); true
                    } else false
                }
            )
            SwipeToDismissBox(
                state             = dismissState,
                backgroundContent = {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(end = 16.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Icon(Icons.Default.Delete, "Delete",
                            tint = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            ) {
                MeasurementTableRow(measurement)
            }
        }
    }
}

@Composable
fun MeasurementTableRow(m: MeasurementEntity) {
    val unitSystem = LocalUnitSystem.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                DateUtils.formatDate(m.date),
                style      = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color      = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(Metric.WEIGHT, Metric.CHEST, Metric.WAIST)
                    .forEach { metric ->
                        val label = metric.label
                        val value = metric.format(metric.valueIn(m), unitSystem, separator = "")
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(label, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(value, style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium)
                        }
                    }
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(Metric.HIPS, Metric.BICEPS, Metric.THIGHS)
                    .forEach { metric ->
                        val label = metric.label
                        val value = metric.format(metric.valueIn(m), unitSystem, separator = "")
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(label, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(value, style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium)
                        }
                    }
            }
            if (!m.notes.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(m.notes, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ─── Comparison panel ─────────────────────────────────────────────────────────

@Composable
fun ComparisonModePanel(
    measurements: List<MeasurementEntity>,
    selected:     Pair<String?, String?>,
    result:       ComparisonResult?,
    onSelect:     (String) -> Unit
) {
    val unitSystem = LocalUnitSystem.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("Comparison Mode", style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold)
            Text("Select two data points",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(measurements) { m ->
                    FilterChip(
                        selected = m.id == selected.first || m.id == selected.second,
                        onClick  = { onSelect(m.id) },
                        label    = { Text(DateUtils.formatDateShort(m.date)) }
                    )
                }
            }
            AnimatedVisibility(visible = result != null) {
                result?.let {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Text("${it.daysBetween} days apart",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    listOf(
                        Metric.WEIGHT to it.weightDiff, Metric.CHEST  to it.chestDiff,
                        Metric.WAIST  to it.waistDiff,  Metric.HIPS   to it.hipsDiff,
                        Metric.BICEPS to it.bicepsDiff, Metric.THIGHS to it.thighsDiff
                    ).forEach { (metric, diff) ->
                        if (diff == null || diff == 0f) return@forEach
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(metric.label, style = MaterialTheme.typography.bodySmall)
                            // Neutral color + arrow: a change isn't "good" or "bad" without a goal.
                            Text("${if (diff < 0) "↓" else "↑"} %+.1f ${metric.unit(unitSystem)}"
                                .format(metric.toDisplay(diff, unitSystem)),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
}

// ─── Empty states ─────────────────────────────────────────────────────────────

@Composable
fun EmptyChartState(hasEntries: Boolean, metric: Metric) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier            = Modifier.padding(32.dp)
        ) {
            Text("📊", style = MaterialTheme.typography.displayMedium)
            Text(
                if (hasEntries) "No ${metric.label} data yet"
                else "No measurements in this range",
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign  = TextAlign.Center
            )
            Text(
                if (hasEntries) "Log your ${metric.label.lowercase()} during a check-in"
                else "Start a check-in from the Dashboard",
                style    = MaterialTheme.typography.bodyMedium,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun EmptyMeasurementsState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("📊", style = MaterialTheme.typography.displayMedium)
            Text("No measurements logged yet",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center)
            Text("Start a check-in from the Dashboard",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}
