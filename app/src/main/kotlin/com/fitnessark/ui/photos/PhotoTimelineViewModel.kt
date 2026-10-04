package com.fitnessark.ui.photos

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessark.data.local.entity.PhotoEntity
import com.fitnessark.data.model.PhotoAngle
import com.fitnessark.data.repository.PhotoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PhotoTimelineUiState(
    val photos:         List<PhotoEntity> = emptyList(),
    val selectedPhoto:  PhotoEntity?      = null,
    val beforeAfterMode: Boolean          = false,
    val beforePhoto:    PhotoEntity?      = null,
    val afterPhoto:     PhotoEntity?      = null,
    val isLoading:      Boolean           = true
)

class PhotoTimelineViewModel(
    private val repo: PhotoRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PhotoTimelineUiState())
    val uiState: StateFlow<PhotoTimelineUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repo.getPhotos().collect { list ->
                _uiState.update {
                    it.copy(
                        photos        = list,
                        selectedPhoto = it.selectedPhoto ?: list.firstOrNull(),
                        isLoading     = false
                    )
                }
            }
        }
    }

    fun selectPhoto(id: String) {
        val photo = _uiState.value.photos.find { it.id == id }
        _uiState.update { it.copy(selectedPhoto = photo) }
    }

    fun toggleBeforeAfter() {
        _uiState.update {
            it.copy(
                beforeAfterMode = !it.beforeAfterMode,
                beforePhoto     = null,
                afterPhoto      = null
            )
        }
    }

    fun setBeforeAfterPhotos(beforeId: String, afterId: String) {
        val before = _uiState.value.photos.find { it.id == beforeId }
        val after  = _uiState.value.photos.find { it.id == afterId }
        _uiState.update { it.copy(beforePhoto = before, afterPhoto = after) }
    }

    fun deletePhoto(id: String) {
        viewModelScope.launch {
            repo.deletePhoto(id)
            val current = _uiState.value
            if (current.selectedPhoto?.id == id) {
                _uiState.update {
                    it.copy(selectedPhoto = it.photos.firstOrNull { p -> p.id != id })
                }
            }
        }
    }

    /**
     * Replaces [angle] on an existing photo entry with the photo at [uri].
     * Returns false if the photo couldn't be read (nothing is changed then).
     */
    suspend fun retakePhotoAngle(photoId: String, angle: PhotoAngle, uri: Uri): Boolean =
        repo.updatePhotoAngle(photoId, angle, uri)

    /**
     * Delete just one angle from a photo entry.
     * If all three angles are gone after removal, deletes the whole entry.
     */
    fun deletePhotoAngle(photoId: String, angle: PhotoAngle) {
        viewModelScope.launch { repo.deletePhotoAngle(photoId, angle) }
    }
}
