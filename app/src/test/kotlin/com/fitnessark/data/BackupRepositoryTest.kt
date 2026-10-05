package com.fitnessark.data

import com.fitnessark.TestSupport
import com.fitnessark.TestSupport.noon
import com.fitnessark.data.local.AppDatabase
import com.fitnessark.data.local.entity.MeasurementEntity
import com.fitnessark.data.local.entity.PhotoEntity
import com.fitnessark.data.repository.BackupRepository
import com.fitnessark.data.repository.ImportMode
import com.fitnessark.util.StagedBackup
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class BackupRepositoryTest {

    private val context = TestSupport.context()
    private lateinit var db: AppDatabase
    private lateinit var repo: BackupRepository
    private val photosDir get() = File(context.filesDir, "photos").apply { mkdirs() }
    private val staging get() = File(context.filesDir, "import_staging").apply { mkdirs() }

    @Before fun setUp() {
        db = TestSupport.inMemoryDb(context)
        repo = BackupRepository(db, context)
    }

    @After fun tearDown() = db.close()

    /** A file already on the device, in the live photo folder. */
    private fun live(name: String, content: String = name): String =
        File(photosDir, name).apply { writeText(content) }.absolutePath

    /** A file that came out of a backup ZIP, in the staging folder. */
    private fun staged(name: String, content: String = name): String =
        File(staging, name).apply { writeText(content) }.absolutePath

    private fun backup(
        measurements: List<MeasurementEntity> = emptyList(),
        photos: List<PhotoEntity> = emptyList()
    ) = StagedBackup(measurements, photos, staging)

    private fun measurements() = runBlocking { db.measurementDao().getAllMeasurementsList() }
    private fun photos() = runBlocking { db.photoDao().getAllPhotosList() }
    private fun text(path: String?) = File(path!!).readText()

    @Test fun replace_swaps_everything_for_the_backup_and_deletes_the_old_photo_files() = runBlocking {
        db.measurementDao().insertMeasurement(MeasurementEntity("old-m", noon(3), weight = 90f))
        val oldFront = live("front_old.jpg")
        db.photoDao().insertPhoto(PhotoEntity("old-p", noon(3), frontPhotoPath = oldFront))

        val summary = repo.restore(
            backup(
                listOf(MeasurementEntity("new-m", noon(1), weight = 70f)),
                listOf(PhotoEntity("new-p", noon(1), frontPhotoPath = staged("front_new.jpg")))
            ),
            ImportMode.REPLACE
        )

        assertEquals(1, summary.measurements)
        assertEquals(listOf("new-m"), measurements().map { it.id })
        assertEquals(listOf("new-p"), photos().map { it.id })
        assertFalse("old photo file removed", File(oldFront).exists())
        assertEquals("front_new.jpg", text(photos().single().frontPhotoPath))
        assertEquals("restored into the live folder", photosDir, File(photos().single().frontPhotoPath!!).parentFile)
        assertFalse("staging folder is cleaned up", File(context.filesDir, "import_staging").exists())
    }

    @Test fun merge_keeps_current_data_and_adds_days_only_in_the_backup() = runBlocking {
        db.measurementDao().insertMeasurement(MeasurementEntity("here", noon(5), weight = 80f))

        repo.restore(backup(listOf(MeasurementEntity("there", noon(2), weight = 75f))), ImportMode.MERGE)

        assertEquals(setOf("here", "there"), measurements().map { it.id }.toSet())
    }

    @Test fun merge_on_a_shared_day_takes_backup_values_but_keeps_what_the_backup_lacks() = runBlocking {
        db.measurementDao().insertMeasurement(
            MeasurementEntity("here", noon(2), weight = 80f, waist = 90f, notes = "mine")
        )

        repo.restore(
            backup(listOf(MeasurementEntity("there", noon(2), weight = 75f, chest = 100f))),
            ImportMode.MERGE
        )

        val day = measurements().single()
        assertEquals("the day keeps its existing id", "here", day.id)
        assertEquals("backup wins", 75f, day.weight!!, 0.001f)
        assertEquals("backup-only value added", 100f, day.chest!!, 0.001f)
        assertEquals("device-only value kept", 90f, day.waist!!, 0.001f)
        assertEquals("device-only note kept", "mine", day.notes)
    }

    @Test fun merge_combines_photo_angles_on_a_shared_day() = runBlocking {
        val deviceFront = live("front_here.jpg")
        val deviceSide = live("side_here.jpg")
        val deviceThumb = live("thumb_here.jpg")
        db.photoDao().insertPhoto(
            PhotoEntity("here", noon(2), frontPhotoPath = deviceFront, sidePhotoPath = deviceSide,
                thumbnailPath = deviceThumb)
        )

        repo.restore(
            backup(
                photos = listOf(
                    PhotoEntity("here", noon(2), sidePhotoPath = staged("side_there.jpg"),
                        backPhotoPath = staged("back_there.jpg"), thumbnailPath = staged("thumb_there.jpg"))
                )
            ),
            ImportMode.MERGE
        )

        val day = photos().single()
        assertEquals("the day keeps its existing id", "here", day.id)
        assertEquals("device-only angle kept", deviceFront, day.frontPhotoPath)
        assertEquals("backup wins on a shared angle", "side_there.jpg", text(day.sidePhotoPath))
        assertEquals("backup-only angle added", "back_there.jpg", text(day.backPhotoPath))
        assertEquals("thumbnail follows the front photo, which is the device's", deviceThumb, day.thumbnailPath)
        assertFalse("replaced side photo deleted", File(deviceSide).exists())
        assertFalse("unused backup thumbnail deleted", File(photosDir, "thumb_there.jpg").exists())
        assertTrue(File(deviceFront).exists() && File(deviceThumb).exists())
    }

    @Test fun merge_uses_the_backup_thumbnail_when_the_backup_supplies_the_front_photo() = runBlocking {
        db.photoDao().insertPhoto(
            PhotoEntity("here", noon(2), sidePhotoPath = live("side_here.jpg"), thumbnailPath = live("thumb_here.jpg"))
        )

        repo.restore(
            backup(
                photos = listOf(
                    PhotoEntity("here", noon(2), frontPhotoPath = staged("front_there.jpg"),
                        thumbnailPath = staged("thumb_there.jpg"))
                )
            ),
            ImportMode.MERGE
        )

        val day = photos().single()
        assertEquals("thumb_there.jpg", text(day.thumbnailPath))
        assertFalse("old thumbnail deleted", File(photosDir, "thumb_here.jpg").exists())
    }

    @Test fun a_failed_restore_changes_nothing() = runBlocking {
        db.measurementDao().insertMeasurement(MeasurementEntity("keep", noon(4), weight = 80f))
        val keptFile = live("front_keep.jpg")
        db.photoDao().insertPhoto(PhotoEntity("keep-p", noon(4), frontPhotoPath = keptFile))
        val goodStaged = staged("front_good.jpg")
        val missing = File(staging, "never_extracted.jpg").absolutePath   // a path with no file behind it

        val failure = runCatching {
            repo.restore(
                backup(
                    listOf(MeasurementEntity("new", noon(1), weight = 70f)),
                    listOf(
                        PhotoEntity("good", noon(1), frontPhotoPath = goodStaged),
                        PhotoEntity("bad", noon(2), frontPhotoPath = missing)
                    )
                ),
                ImportMode.REPLACE
            )
        }

        assertTrue(failure.isFailure)
        assertEquals(listOf("keep"), measurements().map { it.id })
        assertEquals(listOf("keep-p"), photos().map { it.id })
        assertTrue("existing photo file untouched", File(keptFile).exists())
        assertFalse("photo placed before the failure is removed again", File(photosDir, "front_good.jpg").exists())
        assertFalse("staging folder is cleaned up", File(context.filesDir, "import_staging").exists())
        assertNull(photos().single().sidePhotoPath)
    }
}
