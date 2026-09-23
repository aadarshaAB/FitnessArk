package com.fitnessark.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "measurements")
data class MeasurementEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val date: Long = System.currentTimeMillis(),
    val weight: Float = 0f,
    val chest: Float = 0f,
    val waist: Float = 0f,
    val hips: Float = 0f,
    val biceps: Float = 0f,
    val thighs: Float = 0f,
    val notes: String? = null
)
