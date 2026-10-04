package com.fitnessark.util

import com.fitnessark.TestSupport
import com.fitnessark.data.local.entity.MeasurementEntity
import com.fitnessark.data.local.entity.PhotoEntity
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

        val result = zipUtils.importData(context, zip)

        assertTrue(result.error, result.success)
        assertEquals(measurements, result.measurements)
        val restored = result.photos.single()
        assertEquals("p1", restored.id)
        assertArrayEquals("front-bytes".toByteArray(), File(restored.frontPhotoPath!!).readBytes())
        assertArrayEquals("thumb-bytes".toByteArray(), File(restored.thumbnailPath!!).readBytes())
        assertNull("angle with no file in the backup", restored.sidePhotoPath)
    }

    @Test fun photo_paths_in_a_backup_are_rebased_onto_this_devices_photo_folder() {
        val front = photoFile("front_p1.jpg", "x")
        val zip = zipUtils.exportData(
            context, emptyList(),
            listOf(PhotoEntity("p1", 1L, "/some/other/phone/photos/front_p1.jpg", null, null, null))
                .map { it.copy(frontPhotoPath = front.absolutePath) }
        )
        val result = zipUtils.importData(context, zip)

        assertEquals(File(photosDir, "front_p1.jpg").absolutePath, result.photos.single().frontPhotoPath)
    }

    @Test fun import_rejects_zip_slip_entry_names() {
        val evil = listOf(
            "photos/../zip_slip_1.txt",
            "photos/..\\zip_slip_2.txt",
            "photos/sub/zip_slip_3.jpg",
            "photos/../../zip_slip_4.txt"
        )
        val zip = File(context.cacheDir, "evil.zip")
        ZipOutputStream(zip.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("data.json"))
            zos.write("""{"measurements":[],"photos":[],"version":1}""".toByteArray())
            zos.closeEntry()
            evil.forEach { name ->
                zos.putNextEntry(ZipEntry(name))
                zos.write("pwned".toByteArray())
                zos.closeEntry()
            }
            zos.putNextEntry(ZipEntry("photos/ok.jpg"))
            zos.write("fine".toByteArray())
            zos.closeEntry()
        }

        val result = zipUtils.importData(context, zip)

        assertTrue(result.error, result.success)
        assertEquals("only the well-formed name is accepted", setOf("ok.jpg"), result.photoFiles.keys)
        // Nothing escaped the photos folder (or landed inside it under a bad name).
        val everywhere = listOf(context.filesDir, context.filesDir.parentFile!!, photosDir)
        everywhere.forEach { dir ->
            assertFalse(dir.walkTopDown().any { it.name.startsWith("zip_slip_") })
        }
        assertTrue(File(photosDir, "ok.jpg").exists())
    }

    @Test fun a_corrupt_backup_reports_failure_instead_of_throwing() {
        val bad = File(context.cacheDir, "bad.zip").apply { writeText("not a zip at all") }

        val result = zipUtils.importData(context, bad)

        // Either an explicit failure or an empty result; it must not crash or import anything.
        assertTrue(result.measurements.isEmpty())
        assertTrue(result.photos.isEmpty())
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
