package com.fitnessark.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessark.data.repository.MeasurementRepository
import com.fitnessark.data.repository.PhotoRepository
import com.fitnessark.data.repository.PreferencesRepository
import com.fitnessark.data.repository.ThemeMode
import com.fitnessark.util.FileUtils
import com.fitnessark.util.ZipUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val appSizeBytes: Long = 0L,
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    val entryCount: Int = 0,
    val message: String? = null
)

class SettingsViewModel(
    private val context: Context,
    private val measurementRepo: MeasurementRepository,
    private val photoRepo: PhotoRepository,
    private val zipUtils: ZipUtils,
    private val preferencesRepo: PreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        refreshStats()
        viewModelScope.launch {
            preferencesRepo.themeMode.collect { mode ->
                _uiState.update { it.copy(themeMode = mode) }
            }
        }
    }

    fun refreshStats() {
        viewModelScope.launch {
            val size = withContext(Dispatchers.IO) { FileUtils.calculateAppSize(context) }
            val count = measurementRepo.getMeasurementCount()
            _uiState.update { it.copy(appSizeBytes = size, entryCount = count) }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferencesRepo.setThemeMode(mode) }
    }

    suspend fun exportData(): Result<File> {
        _uiState.update { it.copy(isExporting = true) }
        return try {
            val zipFile = withContext(Dispatchers.IO) {
                val measurements = measurementRepo.getMeasurementsBetween(0L, Long.MAX_VALUE)
                val photos = photoRepo.getAllPhotosList()
                zipUtils.exportData(context, measurements, photos)
            }
            _uiState.update { it.copy(isExporting = false, message = "Export ready") }
            Result.success(zipFile)
        } catch (e: Exception) {
            _uiState.update { it.copy(isExporting = false, message = "Export failed: ${e.message}") }
            Result.failure(e)
        }
    }

    fun writeExportToUri(zipFile: File, uri: Uri) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        zipFile.inputStream().use { it.copyTo(out) }
                    }
                }
                _uiState.update { it.copy(message = "Export saved successfully") }
            } catch (e: Exception) {
                _uiState.update { it.copy(message = "Failed to save export: ${e.message}") }
            }
        }
    }

    fun importData(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true) }
            try {
                val result = withContext(Dispatchers.IO) {
                    val cacheFile = File(context.cacheDir, "import_${System.currentTimeMillis()}.zip")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        cacheFile.outputStream().use { input.copyTo(it) }
                    }

                    val importResult = zipUtils.importData(context, cacheFile)
                    cacheFile.delete()
                    importResult
                }

                if (result.success) {
                    result.measurements.forEach { measurementRepo.saveMeasurement(it) }
                    result.photos.forEach { photoRepo.insertPhotoEntity(it) }
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            message = "Import successful: ${result.measurements.size} measurements, ${result.photos.size} photos"
                        )
                    }
                    refreshStats()
                } else {
                    _uiState.update { it.copy(isImporting = false, message = "Import failed: ${result.error}") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isImporting = false, message = "Import error: ${e.message}") }
            }
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            try {
                measurementRepo.deleteAllMeasurements()
                photoRepo.deleteAllPhotos()
                withContext(Dispatchers.IO) {
                    FileUtils.deleteDirectoryRecursively(File(context.filesDir, "photos"))
                }
                _uiState.update { it.copy(message = "All data cleared", entryCount = 0, appSizeBytes = 0L) }
            } catch (e: Exception) {
                _uiState.update { it.copy(message = "Clear failed: ${e.message}") }
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
