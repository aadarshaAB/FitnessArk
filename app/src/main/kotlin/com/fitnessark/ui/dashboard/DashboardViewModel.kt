package com.fitnessark.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessark.data.local.entity.MeasurementEntity
import com.fitnessark.data.local.entity.PhotoEntity
import com.fitnessark.data.repository.MeasurementRepository
import com.fitnessark.data.repository.PhotoRepository
import com.fitnessark.util.DateUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

data class DashboardUiState(
    val todayWeight: Float? = null,
    val streakDays: Int = 0,
    val latestPhoto: PhotoEntity? = null,
    val measurementCount: Int = 0,
    val weightChangeLast7Days: Float = 0f,
    val isLoading: Boolean = true
)

class DashboardViewModel(
    private val measurementRepo: MeasurementRepository,
    private val photoRepo: PhotoRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadDashboardData()
    }

    fun loadDashboardData() {
        viewModelScope.launch {
            val latest = measurementRepo.getLatestMeasurement()
            val streak = measurementRepo.calculateStreak()
            val count = measurementRepo.getMeasurementCount()
            val latestPhoto = photoRepo.getLatestPhoto()

            val today = DateUtils.getStartOfDay(System.currentTimeMillis())
            val sevenDaysAgo = today - TimeUnit.DAYS.toMillis(7)
            val recentMeasurements = measurementRepo.getMeasurementsBetween(sevenDaysAgo, today + TimeUnit.DAYS.toMillis(1))

            val weightChange = if (recentMeasurements.size >= 2) {
                val oldest = recentMeasurements.minByOrNull { it.date }?.weight ?: 0f
                val newest = recentMeasurements.maxByOrNull { it.date }?.weight ?: 0f
                newest - oldest
            } else 0f

            val todayStart = DateUtils.getStartOfDay(System.currentTimeMillis())
            val todayWeight = recentMeasurements.firstOrNull { it.date >= todayStart }?.weight

            _uiState.update {
                it.copy(
                    todayWeight = todayWeight,
                    streakDays = streak,
                    latestPhoto = latestPhoto,
                    measurementCount = count,
                    weightChangeLast7Days = weightChange,
                    isLoading = false
                )
            }
        }
    }

    fun updateWeight(weight: Float) {
        viewModelScope.launch {
            val existing = measurementRepo.getLatestMeasurement()
            val todayStart = DateUtils.getStartOfDay(System.currentTimeMillis())

            if (existing != null && existing.date >= todayStart) {
                measurementRepo.saveMeasurement(existing.copy(weight = weight))
            } else {
                measurementRepo.saveMeasurement(
                    MeasurementEntity(weight = weight)
                )
            }
            loadDashboardData()
        }
    }
}
