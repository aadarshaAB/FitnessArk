package com.fitnessark.data.repository

import android.content.Context
import android.graphics.Bitmap
import com.fitnessark.data.local.dao.PhotoDao
import com.fitnessark.data.local.entity.PhotoEntity
import com.fitnessark.util.DateUtils
import com.fitnessark.util.ImageCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
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
    ) = withContext(Dispatchers.IO) {
        // Merge into the day's existing entry (if any) instead of replacing it,
        // so angles not passed in this call are preserved rather than wiped.
        val day = DateUtils.localDateKey(photo.date)
        val existing = dao.getPhotoByLocalDate(day)

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
                localDate = day,
                frontPhotoPath = frontPath,
                sidePhotoPath = sidePath,
                backPhotoPath = backPath,
                thumbnailPath = thumbnailPath
            )
        )
    }

    suspend fun deletePhoto(id: String) = withContext(Dispatchers.IO) {
        val photo = dao.getPhotoById(id) ?: return@withContext
        listOfNotNull(
            photo.frontPhotoPath,
            photo.sidePhotoPath,
            photo.backPhotoPath,
            photo.thumbnailPath
        ).forEach { path -> File(path).delete() }
        dao.deletePhoto(id)
    }

    /**
     * Remove one angle ("front" | "side" | "back") from an entry, deleting its image file.
     * The thumbnail is rebuilt from the next remaining angle if it came from the removed one;
     * if no angle remains the whole entry (and its files) is deleted.
     */
    suspend fun deletePhotoAngle(photoId: String, angle: String) = withContext(Dispatchers.IO) {
        val existing = dao.getPhotoById(photoId) ?: return@withContext

        val removedPath = when (angle) {
            "front" -> existing.frontPhotoPath
            "side"  -> existing.sidePhotoPath
            "back"  -> existing.backPhotoPath
            else    -> return@withContext
        }
        val updated = when (angle) {
            "front" -> existing.copy(frontPhotoPath = null)
            "side"  -> existing.copy(sidePhotoPath  = null)
            else    -> existing.copy(backPhotoPath  = null)
        }

        val remainingPath = updated.frontPhotoPath ?: updated.sidePhotoPath ?: updated.backPhotoPath
        if (remainingPath == null) {
            deletePhoto(photoId)
            return@withContext
        }

        removedPath?.let { File(it).delete() }

        // Thumbnail is derived from front, else side, else back — rebuild it from the new first angle.
        val thumbSourceBefore = existing.frontPhotoPath ?: existing.sidePhotoPath ?: existing.backPhotoPath
        val thumbnailPath = if (thumbSourceBefore == removedPath) {
            val bitmap = imageCompressor.loadFromInternalStorage(remainingPath)
            if (bitmap != null) {
                val newPath = imageCompressor.saveToInternalStorage(
                    context, imageCompressor.createThumbnail(bitmap, 200), "thumb_${photoId}.jpg"
                )
                existing.thumbnailPath?.takeIf { it != newPath }?.let { File(it).delete() }
                newPath
            } else {
                existing.thumbnailPath?.let { File(it).delete() }
                null
            }
        } else existing.thumbnailPath

        dao.insertPhoto(updated.copy(thumbnailPath = thumbnailPath))
    }

    fun getPhotoFile(path: String): File? = File(path).takeIf { it.exists() }

    suspend fun getLatestPhoto(): PhotoEntity? = dao.getLatestPhoto()

    suspend fun getAllPhotosList(): List<PhotoEntity> = dao.getAllPhotosList()

    /**
     * Inserts [photo] as the entry for its calendar day (used by backup import). If the day already
     * has a different entry, that one is replaced and its image files deleted, unless the incoming
     * entry points at the same files.
     */
    suspend fun insertPhotoEntity(photo: PhotoEntity) = withContext(Dispatchers.IO) {
        val day = DateUtils.localDateKey(photo.date)
        val existing = dao.getPhotoByLocalDate(day)
        if (existing != null && existing.id != photo.id) {
            val keep = listOfNotNull(
                photo.frontPhotoPath, photo.sidePhotoPath, photo.backPhotoPath, photo.thumbnailPath
            ).toSet()
            listOfNotNull(
                existing.frontPhotoPath, existing.sidePhotoPath, existing.backPhotoPath, existing.thumbnailPath
            ).filter { it !in keep }.forEach { File(it).delete() }
            dao.deletePhoto(existing.id)
        }
        dao.insertPhoto(photo.copy(localDate = day))
    }

    suspend fun getPhotoForDay(date: Long): PhotoEntity? =
        dao.getPhotoByLocalDate(DateUtils.localDateKey(date))

    suspend fun deleteAllPhotos() = withContext(Dispatchers.IO) {
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
    ) = withContext(Dispatchers.IO) {
        val existing = dao.getPhotoById(photoId) ?: return@withContext

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
