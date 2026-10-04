package com.fitnessark.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.fitnessark.data.local.dao.PhotoDao
import com.fitnessark.data.local.entity.PhotoEntity
import com.fitnessark.data.model.PhotoAngle
import com.fitnessark.data.model.filePaths
import com.fitnessark.data.model.pathFor
import com.fitnessark.data.model.thumbnailAngle
import com.fitnessark.data.model.withPath
import com.fitnessark.util.BitmapUtils
import com.fitnessark.util.DateUtils
import com.fitnessark.util.ImageCompressor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Photo entries and their image files. Decoding, compression and file work run on [ioDispatcher]
 * (injectable so tests can control it); [context] is the application context, used for file
 * locations and for reading picked/captured photo Uris.
 */
class PhotoRepository(
    private val dao: PhotoDao,
    private val imageCompressor: ImageCompressor,
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    fun getPhotos(): Flow<List<PhotoEntity>> = dao.getAllPhotos()

    /**
     * Saves the photos at [uris] into the entry for the day of [date], merging with that day's
     * existing entry (see the bitmap overload). A photo that can't be decoded is skipped.
     */
    suspend fun savePhoto(date: Long, uris: Map<PhotoAngle, Uri>) {
        val bitmaps = decodeAll(uris)
        if (bitmaps.isNotEmpty()) savePhoto(PhotoEntity(date = date), bitmaps)
    }

    /** Positional convenience for [savePhoto]; a null angle is left as it was. */
    suspend fun savePhoto(photo: PhotoEntity, front: Bitmap?, side: Bitmap?, back: Bitmap?) =
        savePhoto(
            photo,
            listOfNotNull(
                front?.let { PhotoAngle.FRONT to it },
                side?.let { PhotoAngle.SIDE to it },
                back?.let { PhotoAngle.BACK to it }
            ).toMap()
        )

    /**
     * Merges [bitmaps] into the day's existing entry (if any) instead of replacing it, so angles
     * not passed in are preserved rather than wiped.
     */
    suspend fun savePhoto(photo: PhotoEntity, bitmaps: Map<PhotoAngle, Bitmap>) =
        withContext(ioDispatcher) {
            if (bitmaps.isEmpty()) return@withContext
            val day = DateUtils.localDateKey(photo.date)
            val existing = dao.getPhotoByLocalDate(day)
            val baseId = existing?.id ?: photo.id

            var entry = photo.copy(
                id = baseId,
                localDate = day,
                frontPhotoPath = existing?.frontPhotoPath,
                sidePhotoPath = existing?.sidePhotoPath,
                backPhotoPath = existing?.backPhotoPath,
                thumbnailPath = existing?.thumbnailPath
            )

            for ((angle, bitmap) in bitmaps) {
                val compressed = imageCompressor.compress(bitmap, 1024, 80)
                val newPath = imageCompressor.saveToInternalStorage(
                    context, compressed, "${angle.fileKey}_${baseId}.jpg"
                )
                val oldPath = entry.pathFor(angle)
                if (oldPath != null && oldPath != newPath) File(oldPath).delete()
                entry = entry.withPath(angle, newPath)
            }

            // The thumbnail comes from the first angle that has a photo. Rebuild it only when that
            // angle was just replaced (or there is no thumbnail yet).
            val thumbAngle = entry.thumbnailAngle()
            val thumbSource = thumbAngle?.let { bitmaps[it] }
                ?: if (entry.thumbnailPath == null) bitmaps.values.firstOrNull() else null
            if (thumbSource != null) {
                entry = entry.copy(thumbnailPath = writeThumbnail(baseId, thumbSource, entry.thumbnailPath))
            }

            dao.insertPhoto(entry)
        }

    suspend fun deletePhoto(id: String) = withContext(ioDispatcher) {
        val photo = dao.getPhotoById(id) ?: return@withContext
        photo.filePaths().forEach { path -> File(path).delete() }
        dao.deletePhoto(id)
    }

    /**
     * Remove one [angle] from an entry, deleting its image file.
     * The thumbnail is rebuilt from the next remaining angle if it came from the removed one;
     * if no angle remains the whole entry (and its files) is deleted.
     */
    suspend fun deletePhotoAngle(photoId: String, angle: PhotoAngle) = withContext(ioDispatcher) {
        val existing = dao.getPhotoById(photoId) ?: return@withContext

        val removedPath = existing.pathFor(angle)
        val updated = existing.withPath(angle, null)

        val remainingAngle = updated.thumbnailAngle()
        if (remainingAngle == null) {
            deletePhoto(photoId)
            return@withContext
        }

        removedPath?.let { File(it).delete() }

        // Thumbnail is derived from the first angle with a photo; rebuild it if that angle was removed.
        val thumbnailPath = if (existing.thumbnailAngle() == angle) {
            val bitmap = imageCompressor.loadFromInternalStorage(updated.pathFor(remainingAngle)!!)
            if (bitmap != null) {
                writeThumbnail(photoId, bitmap, existing.thumbnailPath)
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
    suspend fun insertPhotoEntity(photo: PhotoEntity) = withContext(ioDispatcher) {
        val day = DateUtils.localDateKey(photo.date)
        val existing = dao.getPhotoByLocalDate(day)
        if (existing != null && existing.id != photo.id) {
            val keep = photo.filePaths().toSet()
            existing.filePaths().filter { it !in keep }.forEach { File(it).delete() }
            dao.deletePhoto(existing.id)
        }
        dao.insertPhoto(photo.copy(localDate = day))
    }

    suspend fun getPhotoForDay(date: Long): PhotoEntity? =
        dao.getPhotoByLocalDate(DateUtils.localDateKey(date))

    suspend fun deleteAllPhotos() = withContext(ioDispatcher) {
        dao.getAllPhotosList().forEach { photo -> photo.filePaths().forEach { File(it).delete() } }
        dao.deleteAllPhotos()
    }

    /**
     * Replaces [angle] on an existing entry with the photo at [uri]. Returns false (and changes
     * nothing) if the photo can't be read.
     */
    suspend fun updatePhotoAngle(photoId: String, angle: PhotoAngle, uri: Uri): Boolean {
        val bitmap = withContext(ioDispatcher) { BitmapUtils.decodeUriToBitmap(context, uri) }
            ?: return false
        updatePhotoAngle(photoId, angle, bitmap)
        return true
    }

    /** Replaces a single [angle] (and the thumbnail, if it came from that angle) on an existing entry. */
    suspend fun updatePhotoAngle(
        photoId: String,
        angle: PhotoAngle,
        newBitmap: Bitmap
    ) = withContext(ioDispatcher) {
        val existing = dao.getPhotoById(photoId) ?: return@withContext

        val newPath = imageCompressor.saveToInternalStorage(
            context, imageCompressor.compress(newBitmap, 1024, 80), "${angle.fileKey}_${photoId}.jpg"
        )
        var updated = existing.withPath(angle, newPath)
        if (updated.thumbnailAngle() == angle) {
            updated = updated.copy(thumbnailPath = writeThumbnail(photoId, newBitmap, existing.thumbnailPath))
        }
        dao.insertPhoto(updated)
    }

    /** Decodes every uri in parallel; angles whose photo can't be read are left out. */
    private suspend fun decodeAll(uris: Map<PhotoAngle, Uri>): Map<PhotoAngle, Bitmap> = coroutineScope {
        uris.map { (angle, uri) ->
            async(ioDispatcher) { BitmapUtils.decodeUriToBitmap(context, uri)?.let { angle to it } }
        }.awaitAll().filterNotNull().toMap()
    }

    /** Writes the 200px thumbnail of [source] for [photoId], deleting [oldPath] if it was a different file. */
    private fun writeThumbnail(photoId: String, source: Bitmap, oldPath: String?): String {
        val newPath = imageCompressor.saveToInternalStorage(
            context, imageCompressor.createThumbnail(source, 200), "thumb_${photoId}.jpg"
        )
        if (oldPath != null && oldPath != newPath) File(oldPath).delete()
        return newPath
    }
}
