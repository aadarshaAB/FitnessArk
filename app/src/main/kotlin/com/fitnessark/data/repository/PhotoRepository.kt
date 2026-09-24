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
        // Merge into the day's existing entry (if any) instead of replacing it,
        // so angles not passed in this call are preserved rather than wiped.
        val startOfDay = com.fitnessark.util.DateUtils.getStartOfDay(photo.date)
        val endOfDay = com.fitnessark.util.DateUtils.getEndOfDay(photo.date)
        val existing = dao.getPhotoByDate(startOfDay, endOfDay)

        val baseId = existing?.id ?: photo.id

        fun saveAngle(bitmap: Bitmap?, existingPath: String?, fileName: String): String? =
            bitmap?.let {
                val compressed = imageCompressor.compress(it, 1024, 80)
                val newPath = imageCompressor.saveToInternalStorage(context, compressed, fileName)
                if (existingPath != null && existingPath != newPath) File(existingPath).delete()
                newPath
            } ?: existingPath

        val frontPath = saveAngle(front, existing?.frontPhotoPath, "front_${baseId}.jpg")
        val sidePath = saveAngle(side, existing?.sidePhotoPath, "side_${baseId}.jpg")
        val backPath = saveAngle(back, existing?.backPhotoPath, "back_${baseId}.jpg")

        // Regenerate the thumbnail only when the angle it's derived from changed
        // (front takes priority, matching updatePhotoAngle), or none exists yet.
        val thumbnailSource = when {
            front != null -> front
            side != null && existing?.frontPhotoPath == null -> side
            back != null && existing?.frontPhotoPath == null && existing?.sidePhotoPath == null -> back
            existing?.thumbnailPath == null -> front ?: side ?: back
            else -> null
        }
        val thumbnailPath = thumbnailSource?.let {
            val thumb = imageCompressor.createThumbnail(it, 200)
            val newPath = imageCompressor.saveToInternalStorage(context, thumb, "thumb_${baseId}.jpg")
            val oldPath = existing?.thumbnailPath
            if (oldPath != null && oldPath != newPath) File(oldPath).delete()
            newPath
        } ?: existing?.thumbnailPath

        dao.insertPhoto(
            photo.copy(
                id = baseId,
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
