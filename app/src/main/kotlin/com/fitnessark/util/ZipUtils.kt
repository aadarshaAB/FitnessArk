package com.fitnessark.util

import android.content.Context
import com.fitnessark.data.local.entity.MeasurementEntity
import com.fitnessark.data.local.entity.PhotoEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** A backup read into [stagingDir]; its photo paths point at the staged files. */
class StagedBackup(
    val measurements: List<MeasurementEntity>,
    val photos: List<PhotoEntity>,
    val stagingDir: File
)

/** Either a [backup] ready to restore, or an [error] explaining why it can't be. */
data class ImportResult(val backup: StagedBackup?, val error: String? = null) {
    val success: Boolean get() = backup != null
}

class ZipUtils {

    /** Rejects entry names that could traverse outside the target folder (zip slip). */
    private fun isSafeEntryName(name: String): Boolean =
        name != "." && name != ".." &&
            !name.contains("/") && !name.contains("\\")

    /** A measurement value, or null if absent/null — or 0, which version-1 backups used for "not logged". */
    private fun JSONObject.loggedValue(key: String): Float? =
        if (isNull(key)) null else getDouble(key).toFloat().takeIf { it > 0f }

    fun exportData(
        context: Context,
        measurements: List<MeasurementEntity>,
        photos: List<PhotoEntity>
    ): File {
        val exportDir = File(context.filesDir, "exports").apply { mkdirs() }
        // Backups left over from earlier exports (e.g. a failed or cancelled save) are dead weight.
        exportDir.listFiles()?.forEach { it.delete() }
        val zipFile = File(exportDir, "fitness_ark_backup_${System.currentTimeMillis()}.zip")

        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
            // Write measurements JSON
            val measurementsJson = JSONArray().apply {
                measurements.forEach { m ->
                    put(JSONObject().apply {
                        put("id", m.id)
                        put("date", m.date)
                        put("weight", m.weight ?: JSONObject.NULL)
                        put("chest", m.chest ?: JSONObject.NULL)
                        put("waist", m.waist ?: JSONObject.NULL)
                        put("hips", m.hips ?: JSONObject.NULL)
                        put("biceps", m.biceps ?: JSONObject.NULL)
                        put("thighs", m.thighs ?: JSONObject.NULL)
                        put("notes", m.notes ?: "")
                    })
                }
            }
            val photosJson = JSONArray().apply {
                photos.forEach { p ->
                    put(JSONObject().apply {
                        put("id", p.id)
                        put("date", p.date)
                        put("frontPhotoPath", p.frontPhotoPath ?: "")
                        put("sidePhotoPath", p.sidePhotoPath ?: "")
                        put("backPhotoPath", p.backPhotoPath ?: "")
                        put("thumbnailPath", p.thumbnailPath ?: "")
                    })
                }
            }
            val dataJson = JSONObject().apply {
                put("measurements", measurementsJson)
                put("photos", photosJson)
                put("exportedAt", System.currentTimeMillis())
                put("version", BACKUP_VERSION)   // 2: blank measurements are null (1 used 0)
            }

            zos.putNextEntry(ZipEntry("data.json"))
            zos.write(dataJson.toString(2).toByteArray())
            zos.closeEntry()

            // Write photo files
            photos.forEach { photo ->
                listOfNotNull(
                    photo.frontPhotoPath,
                    photo.sidePhotoPath,
                    photo.backPhotoPath,
                    photo.thumbnailPath
                ).forEach { path ->
                    val file = File(path)
                    if (file.exists()) {
                        zos.putNextEntry(ZipEntry("photos/${file.name}"))
                        file.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }
        }

        return zipFile
    }

    /**
     * Reads a backup ZIP into a staging folder without touching the live photos or database.
     * Photo entries are streamed to disk one at a time (never held in memory); [StagedBackup]
     * carries the parsed rows, whose photo paths point into the staging folder. Hand it to
     * `BackupRepository.restore`, which moves the files into place and writes the rows in one
     * transaction. On failure nothing is left behind and [ImportResult.error] says why.
     */
    fun stageBackup(context: Context, zipFile: File): ImportResult {
        val staging = File(context.filesDir, STAGING_DIR)
        FileUtils.deleteDirectoryRecursively(staging)
        staging.mkdirs()

        fun fail(message: String): ImportResult {
            FileUtils.deleteDirectoryRecursively(staging)
            return ImportResult(null, message)
        }

        return try {
            var dataJson: String? = null
            val stagedNames = mutableSetOf<String>()

            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    when {
                        entry.name == "data.json" -> dataJson = zis.readBytes().toString(Charsets.UTF_8)
                        entry.name.startsWith("photos/") && !entry.isDirectory -> {
                            val fileName = entry.name.removePrefix("photos/")
                            if (fileName.isNotEmpty() && isSafeEntryName(fileName)) {
                                File(staging, fileName).outputStream().use { zis.copyTo(it) }
                                stagedNames += fileName
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            val json = JSONObject(dataJson ?: return fail("Not a Fitness Ark backup (data.json is missing)"))
            val version = json.optInt("version", 1)
            if (version > BACKUP_VERSION) {
                return fail(
                    "This backup was made by a newer version of Fitness Ark " +
                        "(backup format $version). Update the app to restore it."
                )
            }

            /** The staged copy of the photo file a backup path refers to, or null if the ZIP lacked it. */
            fun staged(path: String): String? =
                File(path).name.takeIf { it in stagedNames }?.let { File(staging, it).absolutePath }

            val measurements = mutableListOf<MeasurementEntity>()
            val mArray = json.getJSONArray("measurements")
            for (i in 0 until mArray.length()) {
                val obj = mArray.getJSONObject(i)
                measurements.add(
                    MeasurementEntity(
                        id = obj.getString("id"),
                        date = obj.getLong("date"),
                        weight = obj.loggedValue("weight"),
                        chest = obj.loggedValue("chest"),
                        waist = obj.loggedValue("waist"),
                        hips = obj.loggedValue("hips"),
                        biceps = obj.loggedValue("biceps"),
                        thighs = obj.loggedValue("thighs"),
                        notes = obj.getString("notes").ifEmpty { null }
                    )
                )
            }
            val photos = mutableListOf<PhotoEntity>()
            val pArray = json.getJSONArray("photos")
            for (i in 0 until pArray.length()) {
                val obj = pArray.getJSONObject(i)
                fun path(key: String) = obj.getString(key).ifEmpty { null }?.let(::staged)
                photos.add(
                    PhotoEntity(
                        id = obj.getString("id"),
                        date = obj.getLong("date"),
                        frontPhotoPath = path("frontPhotoPath"),
                        sidePhotoPath = path("sidePhotoPath"),
                        backPhotoPath = path("backPhotoPath"),
                        thumbnailPath = path("thumbnailPath")
                    )
                )
            }

            ImportResult(StagedBackup(measurements, photos, staging))
        } catch (e: Exception) {
            fail(e.message ?: "Couldn't read the backup")
        }
    }

    companion object {
        /** Format written by [exportData]; a backup with a higher version is refused. */
        const val BACKUP_VERSION = 2
        private const val STAGING_DIR = "import_staging"
    }
}
