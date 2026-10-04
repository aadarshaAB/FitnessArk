package com.fitnessark.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.fitnessark.util.DateUtils
import java.util.UUID

/** One row per calendar day (unique [localDate], see [MeasurementEntity]). */
@Entity(
    tableName = "photos",
    indices = [Index(value = ["localDate"], unique = true)]
)
data class PhotoEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val date: Long = System.currentTimeMillis(),
    val frontPhotoPath: String? = null,
    val sidePhotoPath: String? = null,
    val backPhotoPath: String? = null,
    val thumbnailPath: String? = null,
    val localDate: String = DateUtils.localDateKey(date)
)
