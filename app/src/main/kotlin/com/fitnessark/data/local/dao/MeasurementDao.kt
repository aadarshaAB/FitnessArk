package com.fitnessark.data.local.dao

import androidx.room.*
import com.fitnessark.data.local.entity.MeasurementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MeasurementDao {

    @Query("SELECT * FROM measurements ORDER BY date DESC")
    fun getAllMeasurements(): Flow<List<MeasurementEntity>>

    @Query("SELECT * FROM measurements WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    suspend fun getMeasurementsBetween(startDate: Long, endDate: Long): List<MeasurementEntity>

    @Query("SELECT * FROM measurements ORDER BY date DESC LIMIT 1")
    suspend fun getLatestMeasurement(): MeasurementEntity?

    @Query("SELECT COUNT(*) FROM measurements")
    suspend fun getMeasurementCount(): Int

    @Query("SELECT * FROM measurements ORDER BY date DESC")
    suspend fun getAllMeasurementsList(): List<MeasurementEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeasurement(measurement: MeasurementEntity)

    @Query("DELETE FROM measurements WHERE id = :id")
    suspend fun deleteMeasurement(id: String)

    @Query("DELETE FROM measurements")
    suspend fun deleteAllMeasurements()
}
