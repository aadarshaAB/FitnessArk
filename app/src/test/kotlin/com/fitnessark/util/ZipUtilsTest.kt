package com.fitnessark.util

import com.fitnessark.TestSupport
import com.fitnessark.data.local.entity.MeasurementEntity
import com.fitnessark.data.local.entity.PhotoEntity
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class ZipUtilsTest {

    private val context = TestSupport.context()
    private val zipUtils = ZipUtils()
    private val photosDir get() = File(context.filesDir, "photos").apply { mkdirs() }

    private fun photoFile(name: String, content: String): File =
        File(photosDir, name).apply { writeText(content) }

    private fun zipWith(name: String, data: String?, vararg extra: Pair<String, String>): File {
        val zip = File(context.cacheDir, name)
        ZipOutputStream(zip.outputStream()).use { zos ->
            if (data != null) {
                zos.putNextEntry(ZipEntry("data.json"))
                zos.write(data.toByteArray())
                zos.closeEntry()
            }
            extra.forEach { (entryName, content) ->
                zos.putNextEntry(ZipEntry(entryName))
                zos.write(content.toByteArray())
                zos.closeEntry()
            }
        }
        return zip
    }

    @Test fun backup_round_trip_restores_measurements_and_photo_files() {
        val front = photoFile("front_p1.jpg", "front-bytes")
        val thumb = photoFile("thumb_p1.jpg", "thumb-bytes")
        val measurements = listOf(
            MeasurementEntity("m1", 1_700_000_000_000, 72.5f, 100f, 80f, 95f, 35f, 55f, "note one"),
            MeasurementEntity("m2", 1_700_086_400_000, 72f, null, null, null, null, null, null)
        )
        val photos = listOf(
            PhotoEntity("p1", 1_700_000_000_000, front.absolutePath, null, null, thumb.absolutePath)
        )

        val zip = zipUtils.exportData(context, measurements, photos)

        // Simulate a fresh install: the photo files are gone.
        photosDir.listFiles()!!.forEach { it.delete() }

        val result = zipUtils.stageBackup(context, zip)

        assertTrue(result.error, result.success)
        val backup = result.backup!!
        assertEquals(measurements, backup.measurements)
        val restored = backup.photos.single()
        assertEquals("p1", restored.id)
        assertArrayEquals("front-bytes".toByteArray(), File(restored.frontPhotoPath!!).readBytes())
        assertArrayEquals("thumb-bytes".toByteArray(), File(restored.thumbnailPath!!).readBytes())
        assertNull("angle with no file in the backup", restored.sidePhotoPath)
        assertEquals("nothing touches the live photo folder until restore", 0, photosDir.list()!!.size)
    }

    @Test fun photo_paths_in_a_backup_are_rebased_onto_the_staging_folder() {
        val front = photoFile("front_p1.jpg", "x")
        val zip = zipUtils.exportData(
            context, emptyList(),
            listOf(PhotoEntity("p1", 1L, "/some/other/phone/photos/front_p1.jpg", null, null, null))
                .map { it.copy(frontPhotoPath = front.absolutePath) }
        )
        val backup = zipUtils.stageBackup(context, zip).backup!!

        assertEquals(File(backup.stagingDir, "front_p1.jpg").absolutePath, backup.photos.single().frontPhotoPath)
    }

    @Test fun import_rejects_zip_slip_entry_names() {
        val zip = zipWith(
            "evil.zip", """{"measurements":[],"photos":[],"version":1}""",
            "photos/../zip_slip_1.txt" to "pwned",
            "photos/..\\zip_slip_2.txt" to "pwned",
            "photos/sub/zip_slip_3.jpg" to "pwned",
            "photos/../../zip_slip_4.txt" to "pwned",
            "photos/ok.jpg" to "fine"
        )

        val result = zipUtils.stageBackup(context, zip)

        assertTrue(result.error, result.success)
        val staging = result.backup!!.stagingDir
        assertEquals("only the well-formed name is accepted", listOf("ok.jpg"), staging.list()!!.toList())
        // Nothing escaped the staging folder (or landed inside it under a bad name).
        val everywhere = listOf(context.filesDir, context.filesDir.parentFile!!)
        everywhere.forEach { dir ->
            assertFalse(dir.walkTopDown().any { it.name.startsWith("zip_slip_") })
        }
    }

    @Test fun a_corrupt_backup_reports_failure_and_leaves_nothing_behind() {
        val bad = File(context.cacheDir, "bad.zip").apply { writeText("not a zip at all") }

        val result = zipUtils.stageBackup(context, bad)

        assertFalse(result.success)
        assertNotNull(result.error)
        assertFalse(File(context.filesDir, "import_staging").exists())
    }

    @Test fun a_zip_without_data_json_is_refused() {
        val result = zipUtils.stageBackup(context, zipWith("nodata.zip", null, "photos/a.jpg" to "x"))

        assertFalse(result.success)
        assertFalse(File(context.filesDir, "import_staging").exists())
    }

    @Test fun a_backup_from_a_newer_app_version_is_refused() {
        val newer = zipWith(
            "newer.zip", """{"measurements":[],"photos":[],"version":${ZipUtils.BACKUP_VERSION + 1}}""",
            "photos/a.jpg" to "x"
        )

        val result = zipUtils.stageBackup(context, newer)

        assertFalse(result.success)
        assertTrue(result.error!!.contains("newer version"))
        assertFalse("staged files are cleaned up", File(context.filesDir, "import_staging").exists())
    }

    @Test fun a_backup_without_a_version_is_read_as_version_1() {
        val old = zipWith(
            "v1.zip",
            """{"measurements":[{"id":"m","date":1700000000000,"weight":0,"chest":0,"waist":0,"hips":0,"biceps":0,"thighs":0,"notes":""}],"photos":[]}"""
        )

        val backup = zipUtils.stageBackup(context, old).backup!!

        assertNull("0 meant 'not logged' in version 1", backup.measurements.single().weight)
    }

    @Test fun staging_starts_clean_each_time() {
        val staging = File(context.filesDir, "import_staging").apply { mkdirs() }
        File(staging, "left_over.jpg").writeText("old")

        val backup = zipUtils.stageBackup(context, zipUtils.exportData(context, emptyList(), emptyList())).backup!!

        assertEquals(emptyList<String>(), backup.stagingDir.list()!!.toList())
    }

    @Test fun exporting_removes_leftover_backups_from_earlier_exports() {
        val exports = File(context.filesDir, "exports").apply { mkdirs() }
        val stale = File(exports, "fitness_ark_backup_1.zip").apply { writeText("old") }

        val fresh = zipUtils.exportData(context, emptyList(), emptyList())

        assertFalse(stale.exists())
        assertTrue(fresh.exists())
        assertEquals(listOf(fresh.name), exports.list()!!.toList())
    }
}
