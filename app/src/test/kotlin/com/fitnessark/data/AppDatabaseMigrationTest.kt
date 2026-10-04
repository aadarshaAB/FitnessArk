package com.fitnessark.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.fitnessark.TestSupport
import com.fitnessark.data.local.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

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

    @Test
    fun version1_data_survives_opening_with_current_database() {
        helper.createDatabase(dbName, 1).apply {
            execSQL(
                "INSERT INTO measurements (id, date, weight, chest, waist, hips, biceps, thighs, notes) " +
                    "VALUES ('m1', 1700000000000, 72.5, 0, 80.0, 0, 0, 0, 'hello')"
            )
            execSQL(
                "INSERT INTO photos (id, date, frontPhotoPath, sidePhotoPath, backPhotoPath, thumbnailPath) " +
                    "VALUES ('p1', 1700000000000, '/f.jpg', NULL, NULL, '/t.jpg')"
            )
            close()
        }

        val db = Room.databaseBuilder(TestSupport.context(), AppDatabase::class.java, dbName)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            runBlocking {
                val m = db.measurementDao().getAllMeasurementsList().single()
                assertEquals("m1", m.id)
                assertEquals(72.5f, m.weight, 0.001f)
                assertEquals("hello", m.notes)
                val p = db.photoDao().getAllPhotosList().single()
                assertEquals("/f.jpg", p.frontPhotoPath)
                assertEquals("/t.jpg", p.thumbnailPath)
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun every_registered_migration_matches_the_exported_schema() {
        for (migration in AppDatabase.MIGRATIONS) {
            helper.createDatabase(dbName, migration.startVersion).close()
            helper.runMigrationsAndValidate(dbName, migration.endVersion, true, *AppDatabase.MIGRATIONS)
        }
    }
}
