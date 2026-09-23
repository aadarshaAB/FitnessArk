package com.fitnessark.ui.photos

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessark.data.local.entity.PhotoEntity
import com.fitnessark.data.repository.PhotoRepository
import com.fitnessark.util.BitmapUtils
import com.fitnessark.util.DateUtils
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

    fun savePhoto(
        context:   Context,
        photo:     PhotoEntity,
        frontUri:  Uri?,
        sideUri:   Uri?,
        backUri:   Uri?
    ) {
        viewModelScope.launch {
            fun loadBitmap(uri: Uri?) = uri?.let { BitmapUtils.decodeUriToBitmap(context, it) }

            repo.savePhoto(photo, loadBitmap(frontUri), loadBitmap(sideUri), loadBitmap(backUri))
        }
    }

    /** Replace a single angle on an existing photo entry with a new bitmap. */
    fun retakePhotoAngle(context: Context, photoId: String, angle: String, bitmap: android.graphics.Bitmap) {
        viewModelScope.launch {
            repo.updatePhotoAngle(photoId, angle, bitmap)
        }
    }

    /**
     * Delete just one angle (front/side/back) from a photo entry.
     * If all three angles are gone after removal, deletes the whole entry.
     */
    fun deletePhotoAngle(photoId: String, angle: String) {
        viewModelScope.launch {
            val photos = _uiState.value.photos
            val photo  = photos.find { it.id == photoId } ?: return@launch

            val updated = when (angle) {
                "front" -> photo.copy(frontPhotoPath = null)
                "side"  -> photo.copy(sidePhotoPath  = null)
                "back"  -> photo.copy(backPhotoPath  = null)
                else    -> return@launch
            }

            // If no angles remain, delete the whole entry
            if (updated.frontPhotoPath == null &&
                updated.sidePhotoPath  == null &&
                updated.backPhotoPath  == null) {
                repo.deletePhoto(photoId)
            } else {
                repo.insertPhotoEntity(updated)
            }
        }
    }
}
