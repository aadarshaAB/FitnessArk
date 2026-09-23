package com.fitnessark.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "photos")
data class PhotoEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val date: Long = System.currentTimeMillis(),
    val frontPhotoPath: String? = null,
    val sidePhotoPath: String? = null,
    val backPhotoPath: String? = null,
    val thumbnailPath: String? = null
)
