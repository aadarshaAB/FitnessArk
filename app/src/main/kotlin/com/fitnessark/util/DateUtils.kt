package com.fitnessark.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

object DateUtils {

    /**
     * A pattern's formatter for the current default locale. DateTimeFormatter is immutable and
     * thread-safe, so it is cached, but rebuilt if the device language changes while the app runs.
     */
    private class LocaleFormatter(private val pattern: String) {
        @Volatile private var cached: Pair<Locale, DateTimeFormatter>? = null

        fun get(): DateTimeFormatter {
            val locale = Locale.getDefault()
            cached?.let { if (it.first == locale) return it.second }
            return DateTimeFormatter.ofPattern(pattern, locale).also { cached = locale to it }
        }
    }

    private val fullFormat = LocaleFormatter("MMM dd, yyyy")
    private val shortFormat = LocaleFormatter("MM/dd/yy")

    private fun localDate(timestamp: Long): LocalDate =
        Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()

    fun formatDate(timestamp: Long): String = fullFormat.get().format(localDate(timestamp))

    fun formatDateShort(timestamp: Long): String = shortFormat.get().format(localDate(timestamp))

    /** ISO local date (yyyy-MM-dd) of [timestamp] in the device's current time zone. */
    fun localDateKey(timestamp: Long): String = localDate(timestamp).toString()

    fun getStartOfDay(timestamp: Long): Long =
        localDate(timestamp).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun getEndOfDay(timestamp: Long): Long =
        localDate(timestamp).plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1

    /**
     * The timestamp to log a check-in under for the day picked in a date picker, which reports that
     * day as UTC midnight. Today keeps the current time; any other day is logged at local noon.
     */
    fun timestampForPickedDay(pickerUtcMillis: Long, now: Long = System.currentTimeMillis()): Long {
        val day = Instant.ofEpochMilli(pickerUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()
        return if (day == localDate(now)) now
        else day.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    /** [timestamp]'s day as a date picker expects it (UTC midnight). */
    fun pickerMillisOf(timestamp: Long): Long =
        localDate(timestamp).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    /** Whole calendar days between the days of [start] and [end] (order doesn't matter). */
    fun getDaysBetween(start: Long, end: Long): Int =
        kotlin.math.abs(ChronoUnit.DAYS.between(localDate(start), localDate(end))).toInt()
}
