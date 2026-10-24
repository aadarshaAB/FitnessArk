package com.fitnessark.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessark.data.local.entity.MeasurementEntity
import com.fitnessark.data.local.entity.PhotoEntity
import com.fitnessark.data.repository.MeasurementRepository
import com.fitnessark.data.repository.PhotoRepository
import com.fitnessark.util.DateUtils
import com.fitnessark.util.TrendLine
import com.fitnessark.util.WidgetUpdater
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

data class DashboardUiState(
    val todayWeight: Float? = null,
    val streakDays: Int = 0,
    val latestPhoto: PhotoEntity? = null,
    val measurementCount: Int = 0,
    val weightChangeLast7Days: Float? = null,
    /** Mean of the weights logged in the last 7 days (today included), or null if none. */
    val weeklyAverageWeight: Float? = null,
    /** Whether each of the last 7 days (oldest first, today last) has an entry. */
    val recentLogged: List<Boolean> = List(7) { false },
    /** Weights logged in the last 7 days, oldest first, for the hero sparkline. */
    val recentWeights: List<Float> = emptyList(),
    val isLoading: Boolean = true
)

class DashboardViewModel(
    private val measurementRepo: MeasurementRepository,
    private val photoRepo: PhotoRepository,
    private val widgetUpdater: WidgetUpdater
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        // Re-derives everything whenever measurements or photos change, and when the calendar day
        // rolls over while the app stays open (so "today" and the streak don't go stale).
        viewModelScope.launch {
            combine(
                measurementRepo.getAllMeasurements(),
                photoRepo.getPhotos(),
                currentDay()
            ) { measurements, photos, _ -> buildState(measurements, photos.firstOrNull()) }
                .collect { _uiState.value = it }
        }
    }

    private fun currentDay(): Flow<String> = flow {
        while (true) {
            emit(DateUtils.localDateKey(System.currentTimeMillis()))
            delay(DAY_CHECK_INTERVAL_MS)
        }
    }.distinctUntilChanged()

    /** [measurements] arrive newest-first. */
    private fun buildState(measurements: List<MeasurementEntity>, latestPhoto: PhotoEntity?): DashboardUiState {
        val now = System.currentTimeMillis()

        // The same 7 calendar days (today and the 6 before) as the day chips and the weekly average.
        val weighed = measurements.filter {
            it.weight != null && DateUtils.daysBefore(it.date, now) in 0 until TrendLine.WINDOW_DAYS
        }
        val weightChange = weighed.firstOrNull()?.weight?.let { newest ->
            weighed.lastOrNull()?.weight?.takeIf { weighed.size >= 2 }?.let { newest - it }   // newest - oldest
        }

        val todayKey = DateUtils.localDateKey(now)
        val loggedDays = measurements.map { it.localDate }.toSet()
        return DashboardUiState(
            recentLogged = (6 downTo 0).map { DateUtils.localDateKeyDaysBefore(now, it) in loggedDays },
            recentWeights = weighed.reversed().mapNotNull { it.weight },
            todayWeight = measurements.firstOrNull { it.localDate == todayKey }?.weight,
            streakDays = measurementRepo.streakOf(measurements),
            latestPhoto = latestPhoto,
            measurementCount = measurements.size,
            weightChangeLast7Days = weightChange,
            weeklyAverageWeight = TrendLine.recentAverage(
                measurements.mapNotNull { m -> m.weight?.let { TrendLine.Point(m.date, it) } }, now
            ),
            isLoading = false
        )
    }

    fun updateWeight(weight: Float) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val today = measurementRepo.getMeasurementForDay(now)
            // The dashboard observes the database, so it refreshes itself after this save.
            measurementRepo.saveMeasurement(
                today?.copy(weight = weight) ?: MeasurementEntity(date = now, weight = weight)
            )
            widgetUpdater.refresh()
        }
    }

    private companion object {
        const val DAY_CHECK_INTERVAL_MS = 60_000L
    }
}
