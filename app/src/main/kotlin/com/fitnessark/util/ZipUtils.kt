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

data class ImportResult(
    val measurements: List<MeasurementEntity>,
    val photos: List<PhotoEntity>,
    val photoFiles: Map<String, ByteArray>,
    val success: Boolean,
    val error: String? = null
)

class ZipUtils {

    fun exportData(
        context: Context,
        measurements: List<MeasurementEntity>,
        photos: List<PhotoEntity>
    ): File {
        val exportDir = File(context.filesDir, "exports").apply { mkdirs() }
        val zipFile = File(exportDir, "fitness_ark_backup_${System.currentTimeMillis()}.zip")

        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
            // Write measurements JSON
            val measurementsJson = JSONArray().apply {
                measurements.forEach { m ->
                    put(JSONObject().apply {
                        put("id", m.id)
                        put("date", m.date)
                        put("weight", m.weight)
                        put("chest", m.chest)
                        put("waist", m.waist)
                        put("hips", m.hips)
                        put("biceps", m.biceps)
                        put("thighs", m.thighs)
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
                put("version", 1)
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

    fun importData(context: Context, zipFile: File): ImportResult {
        return try {
            val measurements = mutableListOf<MeasurementEntity>()
            val photos = mutableListOf<PhotoEntity>()
            val photoFiles = mutableMapOf<String, ByteArray>()

            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    when {
                        entry.name == "data.json" -> {
                            val json = JSONObject(zis.readBytes().toString(Charsets.UTF_8))
                            val mArray = json.getJSONArray("measurements")
                            for (i in 0 until mArray.length()) {
                                val obj = mArray.getJSONObject(i)
                                measurements.add(
                                    MeasurementEntity(
                                        id = obj.getString("id"),
                                        date = obj.getLong("date"),
                                        weight = obj.getDouble("weight").toFloat(),
                                        chest = obj.getDouble("chest").toFloat(),
                                        waist = obj.getDouble("waist").toFloat(),
                                        hips = obj.getDouble("hips").toFloat(),
                                        biceps = obj.getDouble("biceps").toFloat(),
                                        thighs = obj.getDouble("thighs").toFloat(),
                                        notes = obj.getString("notes").ifEmpty { null }
                                    )
                                )
                            }
                            val pArray = json.getJSONArray("photos")
                            for (i in 0 until pArray.length()) {
                                val obj = pArray.getJSONObject(i)
                                photos.add(
                                    PhotoEntity(
                                        id = obj.getString("id"),
                                        date = obj.getLong("date"),
                                        frontPhotoPath = obj.getString("frontPhotoPath").ifEmpty { null },
                                        sidePhotoPath = obj.getString("sidePhotoPath").ifEmpty { null },
                                        backPhotoPath = obj.getString("backPhotoPath").ifEmpty { null },
                                        thumbnailPath = obj.getString("thumbnailPath").ifEmpty { null }
                                    )
                                )
                            }
                        }
                        entry.name.startsWith("photos/") -> {
                            val fileName = entry.name.removePrefix("photos/")
                            if (fileName.isNotEmpty()) {
                                photoFiles[fileName] = zis.readBytes()
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            // Restore photo files to internal storage
            val photosDir = File(context.filesDir, "photos").apply { mkdirs() }
            photoFiles.forEach { (name, bytes) ->
                File(photosDir, name).writeBytes(bytes)
            }

            // Update paths in photo entities
            val updatedPhotos = photos.map { photo ->
                photo.copy(
                    frontPhotoPath = photo.frontPhotoPath?.let { p ->
                        val name = File(p).name
                        if (photoFiles.containsKey(name)) File(photosDir, name).absolutePath else null
                    },
                    sidePhotoPath = photo.sidePhotoPath?.let { p ->
                        val name = File(p).name
                        if (photoFiles.containsKey(name)) File(photosDir, name).absolutePath else null
                    },
                    backPhotoPath = photo.backPhotoPath?.let { p ->
                        val name = File(p).name
                        if (photoFiles.containsKey(name)) File(photosDir, name).absolutePath else null
                    },
                    thumbnailPath = photo.thumbnailPath?.let { p ->
                        val name = File(p).name
                        if (photoFiles.containsKey(name)) File(photosDir, name).absolutePath else null
                    }
                )
            }

            ImportResult(measurements, updatedPhotos, photoFiles, success = true)
        } catch (e: Exception) {
            ImportResult(emptyList(), emptyList(), emptyMap(), success = false, error = e.message)
        }
    }
}
