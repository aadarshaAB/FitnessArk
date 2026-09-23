package com.fitnessark.data.repository

import com.fitnessark.data.local.dao.MeasurementDao
import com.fitnessark.data.local.entity.MeasurementEntity
import com.fitnessark.util.DateUtils
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit

class MeasurementRepository(private val dao: MeasurementDao) {

    fun getAllMeasurements(): Flow<List<MeasurementEntity>> = dao.getAllMeasurements()

    suspend fun getMeasurementsBetween(startDate: Long, endDate: Long): List<MeasurementEntity> =
        dao.getMeasurementsBetween(startDate, endDate)

    suspend fun saveMeasurement(measurement: MeasurementEntity) =
        dao.insertMeasurement(measurement)

    suspend fun deleteMeasurement(id: String) = dao.deleteMeasurement(id)

    suspend fun getLatestMeasurement(): MeasurementEntity? = dao.getLatestMeasurement()

    suspend fun getMeasurementCount(): Int = dao.getMeasurementCount()

    suspend fun calculateStreak(): Int {
        val measurements = dao.getAllMeasurementsList()
        if (measurements.isEmpty()) return 0

        val today = DateUtils.getStartOfDay(System.currentTimeMillis())
        val sortedDays = measurements
            .map { DateUtils.getStartOfDay(it.date) }
            .distinct()
            .sortedDescending()

        if (sortedDays.first() < today - TimeUnit.DAYS.toMillis(1)) return 0

        var streak = 1
        for (i in 0 until sortedDays.size - 1) {
            val diff = sortedDays[i] - sortedDays[i + 1]
            if (diff <= TimeUnit.DAYS.toMillis(1) + TimeUnit.HOURS.toMillis(1)) {
                streak++
            } else {
                break
            }
        }
        return streak
    }

    suspend fun deleteAllMeasurements() = dao.deleteAllMeasurements()
}
