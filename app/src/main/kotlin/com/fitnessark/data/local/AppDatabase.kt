package com.fitnessark.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.fitnessark.data.local.dao.MeasurementDao
import com.fitnessark.data.local.dao.PhotoDao
import com.fitnessark.data.local.entity.Converters
import com.fitnessark.data.local.entity.MeasurementEntity
import com.fitnessark.data.local.entity.PhotoEntity

@Database(
    entities = [MeasurementEntity::class, PhotoEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun measurementDao(): MeasurementDao
    abstract fun photoDao(): PhotoDao

    companion object {
        const val DATABASE_NAME = "fitness_ark.db"
    }
}
