package com.fitnessark.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessark.data.repository.BackupRepository
import com.fitnessark.data.repository.ImportMode
import com.fitnessark.data.repository.MeasurementRepository
import com.fitnessark.data.repository.PhotoRepository
import com.fitnessark.data.repository.PreferencesRepository
import com.fitnessark.data.repository.ThemeMode
import com.fitnessark.util.FileUtils
import com.fitnessark.util.ZipUtils
import kotlinx.coroutines.CoroutineDispatcher
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
    private val backupRepo: BackupRepository,
    private val zipUtils: ZipUtils,
    private val preferencesRepo: PreferencesRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
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
            val size = withContext(ioDispatcher) { FileUtils.calculateAppSize(context) }
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
            val zipFile = withContext(ioDispatcher) {
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
                withContext(ioDispatcher) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        zipFile.inputStream().use { it.copyTo(out) }
                    }
                }
                _uiState.update { it.copy(message = "Export saved successfully") }
            } catch (e: Exception) {
                _uiState.update { it.copy(message = "Failed to save export: ${e.message}") }
            } finally {
                // The copy in the user's chosen location is the real backup; drop our temp one.
                withContext(ioDispatcher) { zipFile.delete() }
                refreshStats()
            }
        }
    }

    fun importData(uri: Uri, mode: ImportMode) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true) }
            try {
                val staged = withContext(ioDispatcher) {
                    val cacheFile = File(context.cacheDir, "import_${System.currentTimeMillis()}.zip")
                    try {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            cacheFile.outputStream().use { input.copyTo(it) }
                        }
                        zipUtils.stageBackup(context, cacheFile)
                    } finally {
                        cacheFile.delete()
                    }
                }

                val backup = staged.backup
                if (backup == null) {
                    _uiState.update { it.copy(isImporting = false, message = "Import failed: ${staged.error}") }
                    return@launch
                }
                val summary = backupRepo.restore(backup, mode)
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        message = "Import successful: ${summary.measurements} measurements, ${summary.photos} photos"
                    )
                }
                refreshStats()
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
                withContext(ioDispatcher) {
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
