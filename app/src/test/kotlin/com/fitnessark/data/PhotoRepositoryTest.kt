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

    @Test fun saving_one_angle_keeps_the_angles_already_saved_that_day() = runBlocking {
        repo.savePhoto(PhotoEntity(date = day), bitmap(), null, bitmap())   // front + back
        val before = saved()

        repo.savePhoto(PhotoEntity(date = day), null, bitmap(), null)       // side only
        val after = saved()

        assertEquals("still one entry for the day", before.id, after.id)
        assertEquals(before.frontPhotoPath, after.frontPhotoPath)
        assertEquals(before.backPhotoPath, after.backPhotoPath)
        assertNotNull(after.sidePhotoPath)
        listOf(after.frontPhotoPath, after.sidePhotoPath, after.backPhotoPath, after.thumbnailPath)
            .forEach { assertTrue("missing file $it", exists(it)) }
    }

    @Test fun retaking_an_angle_replaces_its_file_in_place() = runBlocking {
        repo.savePhoto(PhotoEntity(date = day), bitmap(), null, null)
        val first = saved()

        repo.savePhoto(PhotoEntity(date = day), bitmap(), null, null)
        val second = saved()

        assertEquals(first.id, second.id)
        assertTrue(exists(second.frontPhotoPath))
        assertEquals(1, db.photoDao().getAllPhotosList().size)
    }

    @Test fun different_days_get_separate_entries() = runBlocking {
        repo.savePhoto(PhotoEntity(date = noon(2)), bitmap(), null, null)
        repo.savePhoto(PhotoEntity(date = noon(0)), bitmap(), null, null)

        assertEquals(2, db.photoDao().getAllPhotosList().size)
    }

    @Test fun importing_an_entry_for_a_day_that_has_one_replaces_it_and_cleans_up_its_files() = runBlocking {
        repo.savePhoto(PhotoEntity(date = day), bitmap(), null, null)
        val local = saved()

        repo.insertPhotoEntity(PhotoEntity(id = "imported", date = day, frontPhotoPath = "/restored/front.jpg"))

        val only = saved()
        assertEquals("imported", only.id)
        assertFalse("replaced entry's file should be deleted", exists(local.frontPhotoPath))
        assertFalse(exists(local.thumbnailPath))
    }

    @Test fun the_database_itself_rejects_a_second_photo_entry_for_a_day() = runBlocking {
        db.photoDao().insertPhoto(PhotoEntity(id = "a", date = day))
        db.photoDao().insertPhoto(PhotoEntity(id = "b", date = day))

        assertEquals(listOf("b"), db.photoDao().getAllPhotosList().map { it.id })
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
