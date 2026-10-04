package com.fitnessark.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.fitnessark.data.local.AppDatabase
import com.fitnessark.data.local.entity.MeasurementEntity
import com.fitnessark.data.local.entity.PhotoEntity
import com.fitnessark.data.model.PhotoAngle
import com.fitnessark.data.model.filePaths
import com.fitnessark.data.model.pathFor
import com.fitnessark.data.model.thumbnailAngle
import com.fitnessark.data.model.withPath
import com.fitnessark.util.DateUtils
import com.fitnessark.util.FileUtils
import com.fitnessark.util.StagedBackup
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** How a backup is applied to the data already on the device. */
enum class ImportMode {
    /**
     * Keep current data and add the backup. When a day is in both, the backup's values win, but
     * anything the backup has no value (or photo angle) for on that day is kept.
     */
    MERGE,

    /** Delete all current measurements and photos first, then load the backup. */
    REPLACE
}

data class RestoreSummary(val measurements: Int, val photos: Int)

/**
 * Applies a [StagedBackup] to the database and photo folder. All database changes happen in one
 * transaction, so a failed restore leaves the rows exactly as they were.
 */
class BackupRepository(
    private val db: AppDatabase,
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val measurementDao = db.measurementDao()
    private val photoDao = db.photoDao()

    suspend fun restore(backup: StagedBackup, mode: ImportMode): RestoreSummary =
        withContext(ioDispatcher) {
            val photosDir = File(context.filesDir, "photos").apply { mkdirs() }
            // Files that no longer belong to any entry once the restore is done; deleted after commit.
            val orphanCandidates = mutableSetOf<String>()
            val placedNew = mutableListOf<File>()
            try {
                // Move the staged photos into place first. If the transaction below fails, the ones
                // that weren't already there are removed again.
                val photos = backup.photos.map { photo ->
                    fun place(path: String?): String? = path?.let {
                        val target = File(photosDir, File(it).name)
                        val existed = target.exists()
                        Files.move(File(it).toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                        if (!existed) placedNew += target
                        target.absolutePath
                    }
                    photo.copy(
                        frontPhotoPath = place(photo.frontPhotoPath),
                        sidePhotoPath = place(photo.sidePhotoPath),
                        backPhotoPath = place(photo.backPhotoPath),
                        thumbnailPath = place(photo.thumbnailPath)
                    )
                }

                db.withTransaction {
                    if (mode == ImportMode.REPLACE) {
                        photoDao.getAllPhotosList().forEach { orphanCandidates += it.filePaths() }
                        measurementDao.deleteAllMeasurements()
                        photoDao.deleteAllPhotos()
                    }
                    backup.measurements.forEach { mergeMeasurement(it) }
                    photos.forEach { orphanCandidates += mergePhoto(it) }
                }

                val inUse = photoDao.getAllPhotosList().flatMap { it.filePaths() }.toSet()
                (orphanCandidates - inUse).forEach { File(it).delete() }
                RestoreSummary(backup.measurements.size, backup.photos.size)
            } catch (e: Exception) {
                placedNew.forEach { it.delete() }
                throw e
            } finally {
                FileUtils.deleteDirectoryRecursively(backup.stagingDir)
            }
        }

    private suspend fun mergeMeasurement(m: MeasurementEntity) {
        val day = DateUtils.localDateKey(m.date)
        val existing = measurementDao.getMeasurementByLocalDate(day)
        val merged = if (existing == null) {
            m.copy(localDate = day)
        } else {
            m.copy(
                id = existing.id,
                localDate = day,
                weight = m.weight ?: existing.weight,
                chest = m.chest ?: existing.chest,
                waist = m.waist ?: existing.waist,
                hips = m.hips ?: existing.hips,
                biceps = m.biceps ?: existing.biceps,
                thighs = m.thighs ?: existing.thighs,
                notes = m.notes ?: existing.notes
            )
        }
        measurementDao.insertMeasurement(merged)
    }

    /**
     * Writes [incoming] as its day's entry, combining it with the entry already there angle by
     * angle (the backup's photo wins; angles only the device has are kept). Returns the file paths
     * of both versions, so the caller can delete whichever ones ended up unused.
     */
    private suspend fun mergePhoto(incoming: PhotoEntity): List<String> {
        val day = DateUtils.localDateKey(incoming.date)
        val existing = photoDao.getPhotoByLocalDate(day)
        if (existing == null) {
            photoDao.insertPhoto(incoming.copy(localDate = day))
            return emptyList()
        }

        var merged: PhotoEntity = existing
        for (angle in PhotoAngle.entries) {
            merged = merged.withPath(angle, incoming.pathFor(angle) ?: existing.pathFor(angle))
        }
        // The thumbnail belongs to the first angle with a photo, so take it from whichever side
        // supplied that angle.
        val thumbAngle = merged.thumbnailAngle()
        val thumbnail = when {
            thumbAngle == null -> null
            incoming.pathFor(thumbAngle) != null -> incoming.thumbnailPath ?: existing.thumbnailPath
            else -> existing.thumbnailPath ?: incoming.thumbnailPath
        }
        photoDao.insertPhoto(merged.copy(thumbnailPath = thumbnail))
        return existing.filePaths() + incoming.filePaths()
    }
}
