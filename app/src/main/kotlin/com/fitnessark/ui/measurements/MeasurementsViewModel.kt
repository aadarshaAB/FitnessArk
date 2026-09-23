package com.fitnessark.ui.measurements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessark.data.local.entity.MeasurementEntity
import com.fitnessark.data.repository.MeasurementRepository
import com.fitnessark.util.DateUtils
import com.github.mikephil.charting.data.Entry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

enum class Metric(val label: String, val unit: String) {
    WEIGHT("Weight", "kg"),
    CHEST("Chest",   "cm"),
    WAIST("Waist",   "cm"),
    HIPS("Hips",     "cm"),
    BICEPS("Biceps", "cm"),
    THIGHS("Thighs", "cm")
}

enum class DateRange(val label: String, val days: Int?) {
    DAYS_7("7d",   7),
    DAYS_30("30d", 30),
    DAYS_90("90d", 90),
    ALL("All",     null)
}

data class ComparisonResult(
    val measurement1: MeasurementEntity,
    val measurement2: MeasurementEntity,
    val weightDiff:  Float,
    val chestDiff:   Float,
    val waistDiff:   Float,
    val hipsDiff:    Float,
    val bicepsDiff:  Float,
    val thighsDiff:  Float,
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
    val isLoading:                 Boolean                 = true,
    val snackbarMessage:           String?                 = null,
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
     * One Entry per measurement that has a real (> 0) value for the selected
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
            val dayOffset = TimeUnit.MILLISECONDS.toDays(m.date - firstDate).toFloat()
            Entry(dayOffset, getMetricValue(m, metric), index)   // data = index
        }
    }

    fun getChartLabels(): List<String> =
        filteredMeasurementsWithValue(_uiState.value.selectedMetric)
            .map { DateUtils.formatDateShort(it.date) }

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
        filteredMeasurements().filter { getMetricValue(it, metric) > 0f }

    // ── Other actions ─────────────────────────────────────────────────────────

    fun deleteMeasurement(id: String) {
        viewModelScope.launch {
            repo.deleteMeasurement(id)
            _uiState.update { it.copy(snackbarMessage = "Measurement deleted") }
        }
    }

    fun comparePoints(id1: String, id2: String): ComparisonResult? {
        val m1 = _uiState.value.measurements.find { it.id == id1 } ?: return null
        val m2 = _uiState.value.measurements.find { it.id == id2 } ?: return null
        return ComparisonResult(
            measurement1 = m1, measurement2 = m2,
            weightDiff  = m2.weight - m1.weight,
            chestDiff   = m2.chest  - m1.chest,
            waistDiff   = m2.waist  - m1.waist,
            hipsDiff    = m2.hips   - m1.hips,
            bicepsDiff  = m2.biceps - m1.biceps,
            thighsDiff  = m2.thighs - m1.thighs,
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

    fun clearSnackbar() = _uiState.update { it.copy(snackbarMessage = null) }

    // ── Helpers ───────────────────────────────────────────────────────────────

    fun getMetricValue(m: MeasurementEntity, metric: Metric): Float = when (metric) {
        Metric.WEIGHT -> m.weight
        Metric.CHEST  -> m.chest
        Metric.WAIST  -> m.waist
        Metric.HIPS   -> m.hips
        Metric.BICEPS -> m.biceps
        Metric.THIGHS -> m.thighs
    }
}
