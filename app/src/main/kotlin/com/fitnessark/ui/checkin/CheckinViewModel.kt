package com.fitnessark.ui.checkin

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessark.data.local.entity.MeasurementEntity
import com.fitnessark.data.model.Metric
import com.fitnessark.data.model.PhotoAngle
import com.fitnessark.data.model.UnitSystem
import com.fitnessark.data.repository.MeasurementRepository
import com.fitnessark.data.repository.PhotoRepository
import com.fitnessark.util.DateUtils
import com.fitnessark.util.MeasurementInput
import com.fitnessark.util.WidgetUpdater
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CheckinUiState(
    /** The day being logged (a timestamp within that day). */
    val date: Long,
    /** The units the fields are typed and shown in. */
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    /** What's typed in each measurement field, in [unitSystem]; blank means "not logged". */
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
    private val unitSystemFlow: Flow<UnitSystem>,
    private val widgetUpdater: WidgetUpdater,
    date: Long
) : ViewModel() {

    private val _uiState = MutableStateFlow(CheckinUiState(date = date))
    val uiState: StateFlow<CheckinUiState> = _uiState.asStateFlow()

    /** The saved entry the form was filled from, and the text it was filled with. */
    private var loaded: MeasurementEntity? = null
    private var loadedText: Map<Metric, String> = emptyMap()
    private var loadJob: Job? = null

    init {
        loadDay(date)
    }

    /** Fills the form from [day]'s saved entry, or blanks it if that day has none. */
    private fun loadDay(day: Long) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val system = unitSystemFlow.first()
            val entry = measurementRepo.getMeasurementForDay(day)
            loaded = entry
            loadedText = Metric.entries.associateWith { metric ->
                entry?.let { metric.valueIn(it) }?.let { metric.toInputText(it, system) } ?: ""
            }
            _uiState.update {
                it.copy(
                    unitSystem = system,
                    measurements = if (entry != null) loadedText else it.measurements,
                    notes = if (entry != null) entry.notes ?: "" else it.notes
                )
            }
        }
    }

    /**
     * Switches the form to another day (the one picked in the date picker), loading that day's
     * saved values. Anything typed for the previous day is replaced; picked photos are kept.
     */
    fun setDate(date: Long) {
        if (DateUtils.localDateKey(date) == DateUtils.localDateKey(_uiState.value.date)) return
        _uiState.update { it.copy(date = date, measurements = emptyMap(), notes = "") }
        loadDay(date)
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
        MeasurementInput.validate(metric, state.text(metric), state.unitSystem)

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
                // A field left as it was filled in keeps its stored value, so re-saving a day in
                // imperial doesn't nudge kg/cm values through a rounded lb/in round trip.
                fun value(metric: Metric): Float? {
                    val text = s.text(metric)
                    if (text == loadedText[metric]) loaded?.let { return metric.valueIn(it) }
                    return MeasurementInput.parseMetric(metric, text, s.unitSystem)
                }
                // The repository keeps one row per day, reusing that day's existing id.
                measurementRepo.saveMeasurement(
                    MeasurementEntity(
                        date   = s.date,
                        weight = value(Metric.WEIGHT),
                        chest  = value(Metric.CHEST),
                        waist  = value(Metric.WAIST),
                        hips   = value(Metric.HIPS),
                        biceps = value(Metric.BICEPS),
                        thighs = value(Metric.THIGHS),
                        notes  = s.notes.ifEmpty { null }
                    )
                )
                if (s.photoUris.isNotEmpty()) photoRepo.savePhoto(s.date, s.photoUris)
                widgetUpdater.refresh()
                _uiState.update { it.copy(isSaving = false, saved = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, errorMessage = e.message) }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }
}
