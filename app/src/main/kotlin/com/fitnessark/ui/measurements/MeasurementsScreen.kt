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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.StickyNote2
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.fitnessark.data.local.entity.MeasurementEntity
import com.fitnessark.data.model.Metric
import com.fitnessark.ui.theme.IconSize
import com.fitnessark.ui.theme.LocalUnitSystem
import com.fitnessark.util.DateUtils
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import org.koin.androidx.compose.koinViewModel
import com.fitnessark.ui.theme.Spacing

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
                                else Icons.AutoMirrored.Filled.CompareArrows,
                                contentDescription = if (state.comparisonMode) "Exit compare" else "Compare two days",
                                tint = if (state.comparisonMode) MaterialTheme.colorScheme.primary
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
            // ── Metric dropdown + 7-day average toggle ────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                MetricDropdown(
                    selected = state.selectedMetric,
                    onSelect = { viewModel.changeMetric(it) },
                    modifier = Modifier.weight(1f)
                )
                if (!state.showTableView) {
                    FilterChip(
                        selected = state.showTrend,
                        onClick  = { viewModel.toggleTrend() },
                        label    = { Text("7-day avg") }
                    )
                }
            }

            // ── Date range ────────────────────────────────────────────────
            SingleChoiceSegmentedButtonRow(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)
            ) {
                DateRange.entries.forEachIndexed { index, range ->
                    SegmentedButton(
                        selected = state.dateRange == range,
                        onClick  = { viewModel.changeDateRange(range) },
                        shape    = SegmentedButtonDefaults.itemShape(index, DateRange.entries.size),
                        label    = { Text(range.label) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
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

@Composable
private fun MetricDropdown(selected: Metric, onSelect: (Metric) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selected.label, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ArrowDropDown, contentDescription = "Choose measurement")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Metric.entries.forEach { metric ->
                DropdownMenuItem(
                    text    = { Text(metric.label) },
                    onClick = { expanded = false; onSelect(metric) }
                )
            }
        }
    }
}

/** Start / latest / change / average over the visible range, in words as well as on the chart. */
@Composable
private fun RangeSummary(metric: Metric, values: List<Float>, unitSystem: com.fitnessark.data.model.UnitSystem) {
    val change = values.last() - values.first()
    val changeText = if (change == 0f) "No change"
        else "%+.1f %s".format(metric.toDisplay(change, unitSystem), metric.unit(unitSystem))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs)
            .semantics(mergeDescendants = true) { },
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        SummaryCell("Start",   metric.format(values.first(), unitSystem))
        SummaryCell("Latest",  metric.format(values.last(), unitSystem))
        SummaryCell("Change",  changeText)
        SummaryCell("Average", metric.format(values.average().toFloat(), unitSystem))
    }
}

@Composable
private fun SummaryCell(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
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
    val trendData   = if (state.showTrend) viewModel.getTrendData() else emptyList()

    if (filtered.isEmpty() || chartData.isEmpty()) {
        EmptyChartState(hasEntries = filtered.isNotEmpty(), metric = state.selectedMetric)
        return
    }

    // Spoken summary: a screen reader can't read the drawn chart, so give the range in words
    val first = chartData.first()
    val last  = chartData.last()
    val chartSummary = "${state.selectedMetric.label} chart, ${chartData.size} entries, " +
        "from ${state.selectedMetric.format(first.y, unitSystem)} on ${chartLabels[first.x].orEmpty()} " +
        "to ${state.selectedMetric.format(last.y, unitSystem)} on ${chartLabels[last.x].orEmpty()}. " +
        "Switch to the table view to hear every value."

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {

        RangeSummary(state.selectedMetric, chartData.map { it.y }, unitSystem)

        // ── Line chart ────────────────────────────────────────────────────
        MeasurementLineChart(
            animateKey  = "${state.selectedMetric}-${state.dateRange}",
            entries     = chartData.map { Entry(it.x, state.selectedMetric.toDisplay(it.y, unitSystem), it.data) },
            trendEntries = trendData.map { Entry(it.x, state.selectedMetric.toDisplay(it.y, unitSystem)) },
            labels      = chartLabels,
            metricLabel = "${state.selectedMetric.label} (${state.selectedMetric.unit(unitSystem)})",
            onValueSelected = { entryIndex ->
                viewModel.selectChartEntry(entryIndex)
            },
            onNothingSelected = { viewModel.clearSelectedDay() },
            modifier    = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                .semantics { contentDescription = chartSummary }
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
                measurements = state.measurements,
                selected     = state.selectedComparisonPoints,
                result       = state.comparisonResult
            )
        }
        Spacer(Modifier.height(16.dp))
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
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {

            // Header row: date + dismiss button
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CalendarMonth, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(IconSize.sm)
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        DateUtils.formatDate(measurement.date),
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    // Delete entry
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete entry",
                            tint     = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    // Dismiss card
                    IconButton(onClick = onDismiss) {
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
                    color      = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    selectedMetric.unit(unitSystem),
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(bottom = Spacing.xs)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    selectedMetric.label,
                    style  = MaterialTheme.typography.bodySmall,
                    color  = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    modifier = Modifier.padding(bottom = Spacing.xs)
                )
            }

            // All other metrics in a 3-column grid
            val otherMetrics = Metric.entries.filter { it != selectedMetric }
            val rows = otherMetrics.chunked(3)
            rows.forEach { row ->
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
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
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Default.StickyNote2, contentDescription = "Note",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(IconSize.sm)
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        measurement.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
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
            color  = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
        )
        Text(
            value,
            style      = MaterialTheme.typography.bodySmall,
            fontWeight = if (muted) FontWeight.Normal else FontWeight.SemiBold,
            color      = if (muted)
                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
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
    trendEntries:     List<Entry>,
    metricLabel:      String,
    /** The chart animates in only when this changes (metric or range), not on every selection. */
    animateKey:       String,
    onValueSelected:  (Int) -> Unit,
    onNothingSelected: () -> Unit,
    modifier:         Modifier = Modifier
) {
    val primaryColor      = MaterialTheme.colorScheme.primary.toArgb()
    val trendColor        = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f).toArgb()
    val onSurfaceColor    = MaterialTheme.colorScheme.onSurface.toArgb()
    val highlightColor    = MaterialTheme.colorScheme.tertiary.toArgb()
    val axisGridColor     = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f).toArgb()
    val dotHoleColor      = MaterialTheme.colorScheme.surface.toArgb()

    AndroidView(
        factory = { ctx ->
            LineChart(ctx).apply {
                description.isEnabled  = false
                // Names the solid line and the dashed 7-day average, so they differ by more than colour
                legend.apply {
                    isEnabled           = true
                    textColor           = onSurfaceColor
                    form                = Legend.LegendForm.LINE
                    isWordWrapEnabled   = true
                }
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

            // Rebuilding the data clears the tapped-dot highlight and replays the animation, so skip
            // it when nothing the chart draws has changed (e.g. recomposition after a dot is selected).
            val signature = listOf(
                entries.map { it.x to it.y }, trendEntries.map { it.x to it.y }, labels, metricLabel,
                primaryColor, trendColor, highlightColor, dotHoleColor
            )
            val previous = chart.tag as? Pair<*, *>
            if (previous?.first == signature) return@AndroidView
            val animate = previous?.second != animateKey
            chart.tag = signature to animateKey

            val dataSet = LineDataSet(entries, metricLabel).apply {
                color                   = primaryColor
                setCircleColor(primaryColor)
                lineWidth               = 2.5f
                circleRadius            = 6.5f
                circleHoleRadius        = 3f
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
            val dataSets = mutableListOf<ILineDataSet>(dataSet)
            if (trendEntries.size >= 2) {
                dataSets += LineDataSet(trendEntries, "7-day average").apply {
                    color                   = trendColor
                    lineWidth               = 2f
                    enableDashedLine(14f, 8f, 0f)
                    setDrawCircles(false)
                    setDrawValues(false)
                    mode                    = LineDataSet.Mode.CUBIC_BEZIER
                    cubicIntensity          = 0.2f
                    isHighlightEnabled      = false
                }
            }
            chart.data                 = LineData(dataSets)
            if (animate) chart.animateX(400)
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
        contentPadding        = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalArrangement   = Arrangement.spacedBy(Spacing.sm)
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
                            .padding(end = Spacing.lg),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Icon(Icons.Default.Delete, "Delete",
                            tint = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            ) {
                // The button is the non-gesture way to delete; both go through the same Undo snackbar
                MeasurementTableRow(measurement, onDelete = { onDelete(measurement.id) })
            }
        }
    }
}

@Composable
fun MeasurementTableRow(m: MeasurementEntity, onDelete: () -> Unit = {}) {
    val unitSystem = LocalUnitSystem.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    DateUtils.formatDate(m.date),
                    style      = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.primary
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete entry for ${DateUtils.formatDate(m.date)}",
                        tint     = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
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
    result:       ComparisonResult?
) {
    val unitSystem = LocalUnitSystem.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Text("Compare two days", style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold)
            val dates = listOf(selected.first, selected.second).mapNotNull { id ->
                measurements.find { it.id == id }?.let { DateUtils.formatDateShort(it.date) }
            }
            Text(
                when (dates.size) {
                    0    -> "Tap two dots on the chart"
                    1    -> "${dates[0]} selected. Tap a second dot."
                    else -> "${dates[0]} and ${dates[1]}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (diff < 0) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                    contentDescription = if (diff < 0) "Down" else "Up",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text("%+.1f ${metric.unit(unitSystem)}"
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
}

// ─── Empty states ─────────────────────────────────────────────────────────────

@Composable
fun EmptyChartState(hasEntries: Boolean, metric: Metric) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier            = Modifier.padding(32.dp)
        ) {
            Icon(Icons.Default.BarChart, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(IconSize.xl))
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
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Icon(Icons.Default.BarChart, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(IconSize.xl))
            Text("No measurements logged yet",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center)
            Text("Start a check-in from the Dashboard",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}
