package com.fitnessark.data

import android.graphics.Bitmap
import com.fitnessark.TestSupport
import com.fitnessark.TestSupport.noon
import com.fitnessark.data.local.AppDatabase
import com.fitnessark.data.local.entity.PhotoEntity
import com.fitnessark.data.model.PhotoAngle
import com.fitnessark.data.repository.PhotoRepository
import com.fitnessark.util.ImageCompressor
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class PhotoRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: PhotoRepository
    private val day = noon(0)

    @Before fun setUp() {
        db = TestSupport.inMemoryDb()
        repo = PhotoRepository(db.photoDao(), ImageCompressor(), TestSupport.context())
    }

    @After fun tearDown() = db.close()

    private fun bitmap(): Bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)

    private fun saved(): PhotoEntity = runBlocking { db.photoDao().getAllPhotosList().single() }

    private fun exists(path: String?) = path != null && File(path).exists()

    @Test fun saving_into_an_existing_entry_keeps_the_angles_already_saved() = runBlocking {
        val entry = PhotoEntity(date = day)
        repo.savePhoto(entry, bitmap(), null, bitmap())   // front + back
        val before = saved()

        repo.savePhoto(entry, null, bitmap(), null)       // side only
        val after = saved()

        assertEquals("still one entry", before.id, after.id)
        assertEquals(before.frontPhotoPath, after.frontPhotoPath)
        assertEquals(before.backPhotoPath, after.backPhotoPath)
        assertNotNull(after.sidePhotoPath)
        listOf(after.frontPhotoPath, after.sidePhotoPath, after.backPhotoPath, after.thumbnailPath)
            .forEach { assertTrue("missing file $it", exists(it)) }
    }

    @Test fun a_second_photo_the_same_day_is_added_and_the_first_is_kept() = runBlocking {
        repo.savePhoto(PhotoEntity(date = day), bitmap(), null, null)
        val first = saved()

        repo.savePhoto(PhotoEntity(date = day), bitmap(), null, null)

        val all = db.photoDao().getAllPhotosList()
        assertEquals(2, all.size)
        assertTrue("first photo kept", all.any { it.id == first.id && exists(it.frontPhotoPath) })
        assertTrue(all.all { exists(it.frontPhotoPath) && exists(it.thumbnailPath) })
        assertEquals("separate files", 2, all.map { it.frontPhotoPath }.toSet().size)
    }

    @Test fun different_days_get_separate_entries() = runBlocking {
        repo.savePhoto(PhotoEntity(date = noon(2)), bitmap(), null, null)
        repo.savePhoto(PhotoEntity(date = noon(0)), bitmap(), null, null)

        assertEquals(2, db.photoDao().getAllPhotosList().size)
    }

    @Test fun importing_an_entry_for_a_day_that_has_one_keeps_both() = runBlocking {
        repo.savePhoto(PhotoEntity(date = day), bitmap(), null, null)
        val local = saved()

        repo.insertPhotoEntity(PhotoEntity(id = "imported", date = day, frontPhotoPath = "/restored/front.jpg"))

        assertEquals(setOf(local.id, "imported"), db.photoDao().getAllPhotosList().map { it.id }.toSet())
        assertTrue(exists(local.frontPhotoPath))
    }

    @Test fun deleting_an_angle_removes_its_file_and_keeps_the_rest() = runBlocking {
        repo.savePhoto(PhotoEntity(date = day), bitmap(), bitmap(), bitmap())
        val before = saved()

        repo.deletePhotoAngle(before.id, PhotoAngle.SIDE)
        val after = saved()

        assertNull(after.sidePhotoPath)
        assertFalse("side file should be gone", exists(before.sidePhotoPath))
        assertTrue(exists(after.frontPhotoPath))
        assertTrue(exists(after.backPhotoPath))
        assertTrue(exists(after.thumbnailPath))
    }

    @Test fun deleting_the_front_angle_rebuilds_the_thumbnail_from_the_next_angle() = runBlocking {
        repo.savePhoto(PhotoEntity(date = day), bitmap(), bitmap(), null)
        val before = saved()

        repo.deletePhotoAngle(before.id, PhotoAngle.FRONT)
        val after = saved()

        assertNull(after.frontPhotoPath)
        assertFalse(exists(before.frontPhotoPath))
        assertNotNull("thumbnail should be rebuilt from the side photo", after.thumbnailPath)
        assertTrue(exists(after.thumbnailPath))
    }

    @Test fun deleting_the_last_angle_removes_the_whole_entry_and_its_files() = runBlocking {
        repo.savePhoto(PhotoEntity(date = day), bitmap(), null, null)
        val before = saved()

        repo.deletePhotoAngle(before.id, PhotoAngle.FRONT)

        assertTrue(db.photoDao().getAllPhotosList().isEmpty())
        assertFalse(exists(before.frontPhotoPath))
        assertFalse(exists(before.thumbnailPath))
    }

    @Test fun deleting_an_entry_removes_all_its_files() = runBlocking {
        repo.savePhoto(PhotoEntity(date = day), bitmap(), bitmap(), bitmap())
        val before = saved()

        repo.deletePhoto(before.id)

        assertTrue(db.photoDao().getAllPhotosList().isEmpty())
        listOf(before.frontPhotoPath, before.sidePhotoPath, before.backPhotoPath, before.thumbnailPath)
            .forEach { assertFalse("leaked $it", exists(it)) }
    }
}
