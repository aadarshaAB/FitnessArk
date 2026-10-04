package com.fitnessark.ui.measurements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessark.data.local.entity.MeasurementEntity
import com.fitnessark.data.model.Metric
import com.fitnessark.data.repository.MeasurementRepository
import com.fitnessark.util.DateUtils
import com.fitnessark.util.TrendLine
import com.github.mikephil.charting.data.Entry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

enum class DateRange(val label: String, val days: Int?) {
    DAYS_7("7d",   7),
    DAYS_30("30d", 30),
    DAYS_90("90d", 90),
    ALL("All",     null)
}

data class ComparisonResult(
    val measurement1: MeasurementEntity,
    val measurement2: MeasurementEntity,
    // null when either entry didn't log that measurement
    val weightDiff:  Float?,
    val chestDiff:   Float?,
    val waistDiff:   Float?,
    val hipsDiff:    Float?,
    val bicepsDiff:  Float?,
    val thighsDiff:  Float?,
    val daysBetween: Int
)

data class MeasurementsUiState(
    val measurements:              List<MeasurementEntity> = emptyList(),
    val selectedMetric:            Metric                  = Metric.WEIGHT,
    val dateRange:                 DateRange               = DateRange.DAYS_30,
    val comparisonMode:            Boolean                 = false,
    val selectedComparisonPoints:  Pair<String?, String?>  = Pair(null, null),
    val comparisonResult:          ComparisonResult?       = null,
    val showTableView:             Boolean                 = false,
    val showTrend:                 Boolean                 = true,
    val isLoading:                 Boolean                 = true,
    val snackbarMessage:           String?                 = null,
    // last deleted entry, kept so the snackbar's "Undo" can put it back
    val recentlyDeleted:           MeasurementEntity?      = null,
    // dot-tap: measurement whose chart dot the user tapped
    val selectedDayMeasurement:    MeasurementEntity?      = null
)

class MeasurementsViewModel(
    private val repo: MeasurementRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MeasurementsUiState())
    val uiState: StateFlow<MeasurementsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repo.getAllMeasurements().collect { list ->
                _uiState.update { it.copy(measurements = list, isLoading = false) }
            }
        }
    }

    // ── Chart data ────────────────────────────────────────────────────────────

    /**
     * One Entry per measurement that has a logged (non-null) value for the selected
     * metric. X = day-offset from first entry so gaps reflect real time.
     */
    fun getChartData(): List<Entry> {
        val metric   = _uiState.value.selectedMetric
        val filtered = filteredMeasurementsWithValue(metric)
        if (filtered.isEmpty()) return emptyList()
        val firstDate = filtered.first().date
        return filtered.mapIndexed { index, m ->
            // Use sequential index as X so MPAndroidChart spacing is uniform,
            // but tag each Entry with its list index so we can recover the
            // MeasurementEntity on tap (via Entry.data).
            val dayOffset = DateUtils.getDaysBetween(firstDate, m.date).toFloat()
            Entry(dayOffset, getMetricValue(m, metric)!!, index)   // data = index
        }
    }

    /**
     * Maps each chart Entry's X-value (day-offset from the first entry, as used
     * in getChartData()) to its formatted date, so the chart's axis formatter
     * looks up labels by day-offset rather than by list position. This stays
     * correct even when days are skipped between entries.
     */
    fun getChartLabels(): Map<Float, String> {
        val filtered = filteredMeasurementsWithValue(_uiState.value.selectedMetric)
        if (filtered.isEmpty()) return emptyMap()
        val firstDate = filtered.first().date
        return filtered.associate { m ->
            val dayOffset = DateUtils.getDaysBetween(firstDate, m.date).toFloat()
            dayOffset to DateUtils.formatDateShort(m.date)
        }
    }

    /**
     * The 7-day moving average of the selected metric, as chart Entries on the same x-axis as
     * [getChartData]. It is averaged over all logged days (not just the visible range), so the
     * left edge of a short range is still a true average.
     */
    fun getTrendData(): List<Entry> {
        val metric  = _uiState.value.selectedMetric
        val visible = filteredMeasurementsWithValue(metric)
        if (visible.size < 2) return emptyList()
        val all = _uiState.value.measurements.sortedBy { it.date }
            .mapNotNull { m -> getMetricValue(m, metric)?.let { TrendLine.Point(m.date, it) } }
        val averages = TrendLine.movingAverage(all)
        val averageByDate = all.indices.associate { all[it].date to averages[it] }
        val firstDate = visible.first().date
        return visible.map { m ->
            Entry(DateUtils.getDaysBetween(firstDate, m.date).toFloat(), averageByDate.getValue(m.date))
        }
    }

    // ── Dot-tap ───────────────────────────────────────────────────────────────

    /**
     * Called when the user taps a dot on the chart. [entryIndex] is the
     * sequential index into filteredMeasurementsWithValue for the active metric.
     */
    fun selectChartEntry(entryIndex: Int) {
        val list = filteredMeasurementsWithValue(_uiState.value.selectedMetric)
        val measurement = list.getOrNull(entryIndex)
        _uiState.update { it.copy(selectedDayMeasurement = measurement) }
    }

    fun clearSelectedDay() {
        _uiState.update { it.copy(selectedDayMeasurement = null) }
    }

    // ── Filtered lists ────────────────────────────────────────────────────────

    fun filteredMeasurements(): List<MeasurementEntity> {
        val sorted = _uiState.value.measurements.sortedBy { it.date }
        val days   = _uiState.value.dateRange.days ?: return sorted
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong())
        return sorted.filter { it.date >= cutoff }
    }

    fun filteredMeasurementsWithValue(metric: Metric): List<MeasurementEntity> =
        filteredMeasurements().filter { getMetricValue(it, metric) != null }

    // ── Other actions ─────────────────────────────────────────────────────────

    fun deleteMeasurement(id: String) {
        viewModelScope.launch {
            val entry = _uiState.value.measurements.find { it.id == id }
            repo.deleteMeasurement(id)
            _uiState.update {
                it.copy(snackbarMessage = "Measurement deleted", recentlyDeleted = entry)
            }
        }
    }

    fun undoDelete() {
        val entry = _uiState.value.recentlyDeleted ?: return
        viewModelScope.launch {
            repo.saveMeasurement(entry)
            _uiState.update { it.copy(recentlyDeleted = null) }
        }
    }

    fun comparePoints(id1: String, id2: String): ComparisonResult? {
        val m1 = _uiState.value.measurements.find { it.id == id1 } ?: return null
        val m2 = _uiState.value.measurements.find { it.id == id2 } ?: return null
        return ComparisonResult(
            measurement1 = m1, measurement2 = m2,
            weightDiff  = diff(m1.weight, m2.weight),
            chestDiff   = diff(m1.chest,  m2.chest),
            waistDiff   = diff(m1.waist,  m2.waist),
            hipsDiff    = diff(m1.hips,   m2.hips),
            bicepsDiff  = diff(m1.biceps, m2.biceps),
            thighsDiff  = diff(m1.thighs, m2.thighs),
            daysBetween = DateUtils.getDaysBetween(m1.date, m2.date)
        ).also { result -> _uiState.update { it.copy(comparisonResult = result) } }
    }

    fun changeMetric(metric: Metric) {
        _uiState.update { it.copy(selectedMetric = metric, selectedDayMeasurement = null) }
    }

    fun changeDateRange(range: DateRange) {
        _uiState.update { it.copy(dateRange = range, selectedDayMeasurement = null) }
    }

    fun toggleView() = _uiState.update { it.copy(showTableView = !it.showTableView) }

    fun toggleTrend() = _uiState.update { it.copy(showTrend = !it.showTrend) }

    fun toggleComparisonMode() = _uiState.update {
        it.copy(
            comparisonMode               = !it.comparisonMode,
            selectedComparisonPoints     = Pair(null, null),
            comparisonResult             = null,
            selectedDayMeasurement       = null
        )
    }

    fun selectComparisonPoint(id: String) {
        val current = _uiState.value.selectedComparisonPoints
        val updated = when {
            current.first == null                          -> Pair(id, null)
            current.second == null && current.first != id -> Pair(current.first, id)
            else                                           -> Pair(id, null)
        }
        _uiState.update { it.copy(selectedComparisonPoints = updated) }
        if (updated.first != null && updated.second != null) {
            comparePoints(updated.first!!, updated.second!!)
        }
    }

    fun clearSnackbar() = _uiState.update { it.copy(snackbarMessage = null, recentlyDeleted = null) }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun diff(from: Float?, to: Float?): Float? =
        if (from != null && to != null) to - from else null

    /** The logged value for [metric], or null if it wasn't logged that day. */
    fun getMetricValue(m: MeasurementEntity, metric: Metric): Float? = metric.valueIn(m)
}
