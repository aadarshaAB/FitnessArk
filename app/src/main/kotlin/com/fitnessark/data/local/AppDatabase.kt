package com.fitnessark.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import com.fitnessark.data.local.dao.MeasurementDao
import com.fitnessark.data.local.dao.PhotoDao
import com.fitnessark.data.local.entity.Converters
import com.fitnessark.data.local.entity.MeasurementEntity
import com.fitnessark.data.local.entity.PhotoEntity

@Database(
    entities = [MeasurementEntity::class, PhotoEntity::class],
    version = 1,
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
        val MIGRATIONS: Array<Migration> = emptyArray()
    }
}
