package com.fitnessark.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.fitnessark.util.DateUtils
import java.util.UUID

/**
 * One row per check-in photo set. A day can hold several (retaking later in the day adds another
 * rather than replacing); [localDate] is indexed for lookups but not unique.
 */
@Entity(
    tableName = "photos",
    indices = [Index(value = ["localDate"])]
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
