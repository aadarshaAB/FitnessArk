package com.fitnessark.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.fitnessark.util.DateUtils
import java.util.UUID

/**
 * One row per calendar day (unique [localDate]). A null measurement means "not logged".
 * [localDate] is the ISO local date (yyyy-MM-dd) the entry was logged for, fixed at write time so
 * it doesn't shift if the device later changes time zone; the repository keeps it in sync with [date].
 */
@Entity(
    tableName = "measurements",
    indices = [Index(value = ["localDate"], unique = true)]
)
data class MeasurementEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val date: Long = System.currentTimeMillis(),
    val weight: Float? = null,
    val chest: Float? = null,
    val waist: Float? = null,
    val hips: Float? = null,
    val biceps: Float? = null,
    val thighs: Float? = null,
    val notes: String? = null,
    val localDate: String = DateUtils.localDateKey(date)
)
