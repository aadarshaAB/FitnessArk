package com.fitnessark.data.local.entity

import androidx.room.TypeConverter
import java.util.Date

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? = value?.let { Date(it) }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? = date?.time

    @TypeConverter
    fun fromFloat(value: Float?): Float? = value?.let { Math.round(it * 100f) / 100f }
}
