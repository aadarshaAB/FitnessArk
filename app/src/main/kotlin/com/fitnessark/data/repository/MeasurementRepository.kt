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

    /**
     * Saves [measurement] as the entry for its calendar day: if that day already has a row, that
     * row is replaced (keeping its id) instead of a second one being added.
     */
    suspend fun saveMeasurement(measurement: MeasurementEntity) {
        val day = DateUtils.localDateKey(measurement.date)
        val existing = dao.getMeasurementByLocalDate(day)
        dao.insertMeasurement(measurement.copy(id = existing?.id ?: measurement.id, localDate = day))
    }

    suspend fun deleteMeasurement(id: String) = dao.deleteMeasurement(id)

    suspend fun getLatestMeasurement(): MeasurementEntity? = dao.getLatestMeasurement()

    suspend fun getMeasurementForDay(date: Long): MeasurementEntity? =
        dao.getMeasurementByLocalDate(DateUtils.localDateKey(date))

    suspend fun getMeasurementCount(): Int = dao.getMeasurementCount()

    suspend fun calculateStreak(): Int = streakOf(dao.getAllMeasurementsList())

    /** Consecutive-day streak ending today (or yesterday, if today isn't logged yet). */
    fun streakOf(measurements: List<MeasurementEntity>): Int {
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
