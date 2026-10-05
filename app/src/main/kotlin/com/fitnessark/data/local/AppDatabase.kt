package com.fitnessark.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.fitnessark.data.local.dao.MeasurementDao
import com.fitnessark.data.local.dao.PhotoDao
import com.fitnessark.data.local.entity.Converters
import com.fitnessark.data.local.entity.MeasurementEntity
import com.fitnessark.data.local.entity.PhotoEntity
import java.time.Instant
import java.time.ZoneId

@Database(
    entities = [MeasurementEntity::class, PhotoEntity::class],
    version = 3,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun measurementDao(): MeasurementDao
    abstract fun photoDao(): PhotoDao

    companion object {
        const val DATABASE_NAME = "fitness_ark.db"

        /**
         * Every schema change must bump `version` above and add a Migration here
         * (plus a test in AppDatabaseMigrationTest). There is deliberately no
         * destructive fallback: a missing migration crashes loudly instead of
         * silently wiping the user's data.
         */
        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
    }
}

private const val MEASUREMENT_FIELDS = "weight, chest, waist, hips, biceps, thighs"

/**
 * v1 -> v2 (S3 + S4):
 *  - measurement fields become nullable; the old "0 means not logged" values become NULL
 *  - both tables get a `localDate` (yyyy-MM-dd, in the device zone) with a unique index, so a
 *    day can hold only one row. Days that already have several rows (possible from before the
 *    Q2/Q3 fixes) are merged into one: the latest row wins, older rows only fill its blanks.
 */
internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val zone = ZoneId.systemDefault()
        fun dayOf(millis: Long) = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toString()

        migrateMeasurements(db, ::dayOf)
        migratePhotos(db, ::dayOf)
    }

    private fun migrateMeasurements(db: SupportSQLiteDatabase, dayOf: (Long) -> String) {
        class Row(val id: String, val date: Long, val values: List<Float?>, val notes: String?)

        val rows = mutableListOf<Row>()
        db.query("SELECT id, date, $MEASUREMENT_FIELDS, notes FROM measurements ORDER BY date ASC, id ASC").use { c ->
            while (c.moveToNext()) {
                rows += Row(
                    id = c.getString(0),
                    date = c.getLong(1),
                    values = (2..7).map { i -> c.getFloat(i).takeIf { it > 0f } },
                    notes = if (c.isNull(8)) null else c.getString(8)
                )
            }
        }

        db.execSQL(
            "CREATE TABLE measurements_new (id TEXT NOT NULL, date INTEGER NOT NULL, weight REAL, chest REAL, " +
                "waist REAL, hips REAL, biceps REAL, thighs REAL, notes TEXT, localDate TEXT NOT NULL, " +
                "PRIMARY KEY(id))"
        )
        // Rows are oldest-first, so the last row of a day is the one we keep.
        rows.groupBy { dayOf(it.date) }.forEach { (day, sameDay) ->
            val keep = sameDay.last()
            val merged = (0..5).map { i -> sameDay.asReversed().firstNotNullOfOrNull { it.values[i] } }
            val notes = sameDay.asReversed().firstNotNullOfOrNull { it.notes?.takeIf(String::isNotBlank) }
            db.execSQL(
                "INSERT INTO measurements_new (id, date, $MEASUREMENT_FIELDS, notes, localDate) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(keep.id, keep.date, *merged.toTypedArray(), notes, day)
            )
        }
        db.execSQL("DROP TABLE measurements")
        db.execSQL("ALTER TABLE measurements_new RENAME TO measurements")
        db.execSQL("CREATE UNIQUE INDEX `index_measurements_localDate` ON measurements (localDate)")
    }

    private fun migratePhotos(db: SupportSQLiteDatabase, dayOf: (Long) -> String) {
        class Row(val id: String, val date: Long, val paths: List<String?>)

        val rows = mutableListOf<Row>()
        db.query("SELECT id, date, frontPhotoPath, sidePhotoPath, backPhotoPath, thumbnailPath FROM photos ORDER BY date ASC, id ASC").use { c ->
            while (c.moveToNext()) {
                rows += Row(c.getString(0), c.getLong(1), (2..5).map { i -> if (c.isNull(i)) null else c.getString(i) })
            }
        }

        db.execSQL(
            "CREATE TABLE photos_new (id TEXT NOT NULL, date INTEGER NOT NULL, frontPhotoPath TEXT, sidePhotoPath TEXT, " +
                "backPhotoPath TEXT, thumbnailPath TEXT, localDate TEXT NOT NULL, PRIMARY KEY(id))"
        )
        rows.groupBy { dayOf(it.date) }.forEach { (day, sameDay) ->
            val keep = sameDay.last()
            val merged = (0..3).map { i -> sameDay.asReversed().firstNotNullOfOrNull { it.paths[i] } }
            db.execSQL(
                "INSERT INTO photos_new (id, date, frontPhotoPath, sidePhotoPath, backPhotoPath, thumbnailPath, localDate) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(keep.id, keep.date, *merged.toTypedArray(), day)
            )
        }
        db.execSQL("DROP TABLE photos")
        db.execSQL("ALTER TABLE photos_new RENAME TO photos")
        db.execSQL("CREATE UNIQUE INDEX `index_photos_localDate` ON photos (localDate)")
    }
}

/**
 * v2 -> v3: a day may now hold several photo entries (retaking adds one instead of replacing), so
 * the unique index on `photos.localDate` becomes a plain index. No rows change.
 */
internal val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP INDEX IF EXISTS `index_photos_localDate`")
        db.execSQL("CREATE INDEX `index_photos_localDate` ON photos (localDate)")
    }
}
