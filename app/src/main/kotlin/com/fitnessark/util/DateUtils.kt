package com.fitnessark.util

import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.util.*
import java.util.concurrent.TimeUnit

object DateUtils {

    private val fullFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    private val shortFormat = SimpleDateFormat("MM/dd/yy", Locale.getDefault())

    fun formatDate(timestamp: Long): String = fullFormat.format(Date(timestamp))

    fun formatDateShort(timestamp: Long): String = shortFormat.format(Date(timestamp))

    /** ISO local date (yyyy-MM-dd) of [timestamp] in the device's current time zone. */
    fun localDateKey(timestamp: Long): String =
        Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate().toString()

    fun getStartOfDay(timestamp: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun getEndOfDay(timestamp: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return cal.timeInMillis
    }

    fun getDaysBetween(start: Long, end: Long): Int {
        val diff = kotlin.math.abs(end - start)
        return TimeUnit.MILLISECONDS.toDays(diff).toInt()
    }
}
