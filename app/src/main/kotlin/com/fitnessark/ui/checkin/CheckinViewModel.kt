package com.fitnessark.ui.checkin

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessark.data.local.entity.MeasurementEntity
import com.fitnessark.data.model.Metric
import com.fitnessark.data.model.PhotoAngle
import com.fitnessark.data.repository.MeasurementRepository
import com.fitnessark.data.repository.PhotoRepository
import com.fitnessark.util.MeasurementInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CheckinUiState(
    /** What's typed in each measurement field; blank means "not logged". */
    val measurements: Map<Metric, String> = emptyMap(),
    val notes: String = "",
    val photoUris: Map<PhotoAngle, Uri> = emptyMap(),
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val errorMessage: String? = null
) {
    fun text(metric: Metric): String = measurements[metric].orEmpty()
}

class CheckinViewModel(
    private val measurementRepo: MeasurementRepository,
    private val photoRepo: PhotoRepository,
    private val date: Long
) : ViewModel() {

    private val _uiState = MutableStateFlow(CheckinUiState())
    val uiState: StateFlow<CheckinUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            measurementRepo.getMeasurementForDay(date)?.let { m ->
                _uiState.update { s ->
                    s.copy(
                        measurements = Metric.entries.associateWith { metric ->
                            metric.valueIn(m)?.toString() ?: ""
                        },
                        notes = m.notes ?: ""
                    )
                }
            }
        }
    }

    fun update(metric: Metric, value: String) {
        _uiState.update { it.copy(measurements = it.measurements + (metric to value)) }
    }

    fun updateNotes(value: String) {
        _uiState.update { it.copy(notes = value) }
    }

    fun setPhotoUri(angle: PhotoAngle, uri: Uri?) {
        _uiState.update {
            it.copy(photoUris = if (uri == null) it.photoUris - angle else it.photoUris + (angle to uri))
        }
    }

    /** Error text for a measurement field, or null when it is blank or valid. */
    fun fieldError(metric: Metric, state: CheckinUiState): String? =
        MeasurementInput.validate(metric, state.text(metric))

    fun save() {
        val current = _uiState.value
        if (Metric.entries.any { fieldError(it, current) != null }) {
            _uiState.update { it.copy(errorMessage = "Please fix the highlighted fields") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            try {
                val s = _uiState.value
                fun value(metric: Metric) = MeasurementInput.parse(s.text(metric))
                // The repository keeps one row per day, reusing that day's existing id.
                measurementRepo.saveMeasurement(
                    MeasurementEntity(
                        date   = date,
                        weight = value(Metric.WEIGHT),
                        chest  = value(Metric.CHEST),
                        waist  = value(Metric.WAIST),
                        hips   = value(Metric.HIPS),
                        biceps = value(Metric.BICEPS),
                        thighs = value(Metric.THIGHS),
                        notes  = s.notes.ifEmpty { null }
                    )
                )
                if (s.photoUris.isNotEmpty()) photoRepo.savePhoto(date, s.photoUris)
                _uiState.update { it.copy(isSaving = false, saved = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, errorMessage = e.message) }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }
}
