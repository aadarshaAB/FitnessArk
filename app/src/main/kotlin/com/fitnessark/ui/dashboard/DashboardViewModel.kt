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
import java.util.concurrent.TimeUnit

data class DashboardUiState(
    val todayWeight: Float? = null,
    val streakDays: Int = 0,
    val latestPhoto: PhotoEntity? = null,
    val measurementCount: Int = 0,
    val weightChangeLast7Days: Float? = null,
    /** Mean of the weights logged in the last 7 days (today included), or null if none. */
    val weeklyAverageWeight: Float? = null,
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
        val today = DateUtils.getStartOfDay(now)
        val sevenDaysAgo = today - TimeUnit.DAYS.toMillis(7)

        val weighed = measurements.filter { it.weight != null && it.date >= sevenDaysAgo }
        val weightChange = if (weighed.size >= 2) {
            weighed.first().weight!! - weighed.last().weight!!   // newest - oldest
        } else null

        val todayKey = DateUtils.localDateKey(now)
        return DashboardUiState(
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
