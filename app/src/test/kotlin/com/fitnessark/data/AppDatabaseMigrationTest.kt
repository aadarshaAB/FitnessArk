package com.fitnessark.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.fitnessark.TestSupport
import com.fitnessark.data.local.entity.PhotoEntity
import com.fitnessark.data.local.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.TimeZone

/**
 * Guards the user's on-device data across schema changes.
 *
 * When you bump AppDatabase.version: add the Migration to AppDatabase.MIGRATIONS and add a test
 * here that seeds data at the old version, migrates, and checks the data survived. The generic
 * test below already validates every registered migration against the exported schema JSON.
 */
@RunWith(RobolectricTestRunner::class)
class AppDatabaseMigrationTest {

    private val dbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    private val originalZone = TimeZone.getDefault()

    @Before fun pinZone() = TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

    @After fun restoreZone() = TimeZone.setDefault(originalZone)

    private fun openCurrent(): AppDatabase =
        Room.databaseBuilder(TestSupport.context(), AppDatabase::class.java, dbName)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()

    // 2023-11-14 22:13:20 UTC, and one hour / one day later.
    private val t0 = 1_700_000_000_000L
    private val hour = 3_600_000L

    @Test
    fun version1_data_survives_the_v2_migration_with_blanks_becoming_null() {
        helper.createDatabase(dbName, 1).apply {
            execSQL(
                "INSERT INTO measurements (id, date, weight, chest, waist, hips, biceps, thighs, notes) " +
                    "VALUES ('m1', $t0, 72.5, 0, 80.0, 0, 0, 0, 'hello')"
            )
            execSQL(
                "INSERT INTO photos (id, date, frontPhotoPath, sidePhotoPath, backPhotoPath, thumbnailPath) " +
                    "VALUES ('p1', $t0, '/f.jpg', NULL, NULL, '/t.jpg')"
            )
            close()
        }

        val db = openCurrent()
        try {
            runBlocking {
                val m = db.measurementDao().getAllMeasurementsList().single()
                assertEquals("m1", m.id)
                assertEquals(72.5f, m.weight!!, 0.001f)
                assertEquals(80f, m.waist!!, 0.001f)
                assertNull("0 meant not logged", m.chest)
                assertNull(m.hips)
                assertEquals("hello", m.notes)
                assertEquals("2023-11-14", m.localDate)
                val p = db.photoDao().getAllPhotosList().single()
                assertEquals("/f.jpg", p.frontPhotoPath)
                assertEquals("/t.jpg", p.thumbnailPath)
                assertEquals("2023-11-14", p.localDate)
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun duplicate_days_from_older_versions_are_merged_not_lost() {
        helper.createDatabase(dbName, 1).apply {
            // Two rows on one day: the later one has no waist, the earlier one does.
            execSQL(
                "INSERT INTO measurements (id, date, weight, chest, waist, hips, biceps, thighs, notes) " +
                    "VALUES ('early', $t0, 70, 0, 80, 0, 0, 0, 'morning')"
            )
            execSQL(
                "INSERT INTO measurements (id, date, weight, chest, waist, hips, biceps, thighs, notes) " +
                    "VALUES ('late', ${t0 + hour}, 71, 100, 0, 0, 0, 0, NULL)"
            )
            // A different day is untouched.
            execSQL(
                "INSERT INTO measurements (id, date, weight, chest, waist, hips, biceps, thighs, notes) " +
                    "VALUES ('other', ${t0 + 24 * hour}, 69, 0, 0, 0, 0, 0, NULL)"
            )
            execSQL(
                "INSERT INTO photos (id, date, frontPhotoPath, sidePhotoPath, backPhotoPath, thumbnailPath) " +
                    "VALUES ('p-early', $t0, '/front.jpg', NULL, NULL, '/thumb1.jpg')"
            )
            execSQL(
                "INSERT INTO photos (id, date, frontPhotoPath, sidePhotoPath, backPhotoPath, thumbnailPath) " +
                    "VALUES ('p-late', ${t0 + hour}, NULL, '/side.jpg', NULL, '/thumb2.jpg')"
            )
            close()
        }

        val db = openCurrent()
        try {
            runBlocking {
                val ms = db.measurementDao().getAllMeasurementsList()
                assertEquals(2, ms.size)
                val merged = ms.single { it.localDate == "2023-11-14" }
                assertEquals("the latest row is kept", "late", merged.id)
                assertEquals(71f, merged.weight!!, 0.001f)
                assertEquals(100f, merged.chest!!, 0.001f)
                assertEquals("blank filled from the earlier row", 80f, merged.waist!!, 0.001f)
                assertEquals("morning", merged.notes)

                val p = db.photoDao().getAllPhotosList().single()
                assertEquals("p-late", p.id)
                assertEquals("angle kept from the earlier row", "/front.jpg", p.frontPhotoPath)
                assertEquals("/side.jpg", p.sidePhotoPath)
                assertEquals("/thumb2.jpg", p.thumbnailPath)
            }
        } finally {
            db.close()
        }
    }


    @Test
    fun version2_photos_survive_the_v3_migration_and_a_day_can_hold_several() {
        helper.createDatabase(dbName, 2).apply {
            execSQL(
                "INSERT INTO photos (id, date, frontPhotoPath, sidePhotoPath, backPhotoPath, thumbnailPath, localDate) " +
                    "VALUES ('p1', $t0, '/front.jpg', NULL, NULL, '/thumb1.jpg', '2023-11-14')"
            )
            close()
        }

        val db = openCurrent()
        try {
            runBlocking {
                assertEquals("/front.jpg", db.photoDao().getPhotoById("p1")!!.frontPhotoPath)
                db.photoDao().insertPhoto(PhotoEntity(id = "p2", date = t0 + hour))
                assertEquals(2, db.photoDao().getAllPhotosList().size)
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun every_registered_migration_matches_the_exported_schema() {
        for (migration in AppDatabase.MIGRATIONS) {
            val name = "$dbName-${migration.startVersion}"
            helper.createDatabase(name, migration.startVersion).close()
            helper.runMigrationsAndValidate(name, migration.endVersion, true, *AppDatabase.MIGRATIONS).close()
        }
    }
}
