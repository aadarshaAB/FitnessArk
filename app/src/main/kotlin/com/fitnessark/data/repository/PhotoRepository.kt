package com.fitnessark.data.repository

import android.content.Context
import android.graphics.Bitmap
import com.fitnessark.data.local.dao.PhotoDao
import com.fitnessark.data.local.entity.PhotoEntity
import com.fitnessark.util.ImageCompressor
import kotlinx.coroutines.flow.Flow
import java.io.File

class PhotoRepository(
    private val dao: PhotoDao,
    private val imageCompressor: ImageCompressor,
    private val context: Context
) {

    fun getPhotos(): Flow<List<PhotoEntity>> = dao.getAllPhotos()

    suspend fun savePhoto(
        photo: PhotoEntity,
        front: Bitmap?,
        side: Bitmap?,
        back: Bitmap?
    ) {
        // Check if there's already a photo for this day and delete it first if so
        val startOfDay = com.fitnessark.util.DateUtils.getStartOfDay(photo.date)
        val endOfDay = com.fitnessark.util.DateUtils.getEndOfDay(photo.date)
        val existing = dao.getPhotoByDate(startOfDay, endOfDay)
        
        if (existing != null) {
            deletePhoto(existing.id)
        }

        val baseId = photo.id

        val frontPath = front?.let {
            val compressed = imageCompressor.compress(it, 1024, 80)
            imageCompressor.saveToInternalStorage(context, compressed, "front_${baseId}.jpg")
        }
        val sidePath = side?.let {
            val compressed = imageCompressor.compress(it, 1024, 80)
            imageCompressor.saveToInternalStorage(context, compressed, "side_${baseId}.jpg")
        }
        val backPath = back?.let {
            val compressed = imageCompressor.compress(it, 1024, 80)
            imageCompressor.saveToInternalStorage(context, compressed, "back_${baseId}.jpg")
        }

        val sourceBitmap = front ?: side ?: back
        val thumbnailPath = sourceBitmap?.let {
            val thumb = imageCompressor.createThumbnail(it, 200)
            imageCompressor.saveToInternalStorage(context, thumb, "thumb_${baseId}.jpg")
        }

        dao.insertPhoto(
            photo.copy(
                frontPhotoPath = frontPath,
                sidePhotoPath = sidePath,
                backPhotoPath = backPath,
                thumbnailPath = thumbnailPath
            )
        )
    }

    suspend fun deletePhoto(id: String) {
        val photo = dao.getPhotoById(id) ?: return
        listOfNotNull(
            photo.frontPhotoPath,
            photo.sidePhotoPath,
            photo.backPhotoPath,
            photo.thumbnailPath
        ).forEach { path -> File(path).delete() }
        dao.deletePhoto(id)
    }

    fun getPhotoFile(path: String): File? = File(path).takeIf { it.exists() }

    suspend fun getLatestPhoto(): PhotoEntity? = dao.getLatestPhoto()

    suspend fun getAllPhotosList(): List<PhotoEntity> = dao.getAllPhotosList()

    suspend fun insertPhotoEntity(photo: PhotoEntity) = dao.insertPhoto(photo)

    suspend fun getPhotoByDate(start: Long, end: Long): PhotoEntity? = dao.getPhotoByDate(start, end)

    suspend fun deleteAllPhotos() {
        val photos = dao.getAllPhotosList()
        photos.forEach { photo ->
            listOfNotNull(
                photo.frontPhotoPath,
                photo.sidePhotoPath,
                photo.backPhotoPath,
                photo.thumbnailPath
            ).forEach { path -> File(path).delete() }
        }
        dao.deleteAllPhotos()
    }

    /**
     * Replace a single angle (front/side/back/thumbnail) on an existing photo entry.
     * [angle] must be one of "front", "side", "back".
     */
    suspend fun updatePhotoAngle(
        photoId: String,
        angle: String,
        newBitmap: android.graphics.Bitmap
    ) {
        val existing = dao.getPhotoById(photoId) ?: return

        // Save new compressed image
        val fileName = "${angle}_${photoId}.jpg"
        val newPath = imageCompressor.saveToInternalStorage(
            context, imageCompressor.compress(newBitmap, 1024, 80), fileName
        )

        // If this is front (or first available), also regenerate thumbnail
        val thumbPath = if (angle == "front" || (angle == "side" && existing.frontPhotoPath == null)) {
            val thumb = imageCompressor.createThumbnail(newBitmap, 200)
            imageCompressor.saveToInternalStorage(context, thumb, "thumb_${photoId}.jpg")
        } else existing.thumbnailPath

        val updated = when (angle) {
            "front" -> existing.copy(frontPhotoPath = newPath, thumbnailPath = thumbPath)
            "side"  -> existing.copy(sidePhotoPath  = newPath, thumbnailPath = thumbPath ?: existing.thumbnailPath)
            "back"  -> existing.copy(backPhotoPath  = newPath)
            else    -> existing
        }
        dao.insertPhoto(updated)
    }
}
