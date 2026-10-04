package com.fitnessark.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone

class DateUtilsTest {

    private val originalZone = TimeZone.getDefault()
    private val originalLocale = Locale.getDefault()

    @Before fun pinZoneAndLocale() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))   // has DST
        Locale.setDefault(Locale.US)
    }

    @After fun restore() {
        TimeZone.setDefault(originalZone)
        Locale.setDefault(originalLocale)
    }

    private fun millis(date: String, hour: Int, minute: Int = 0): Long =
        LocalDate.parse(date).atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test fun formats_dates_in_the_default_locale() {
        val t = millis("2025-03-07", 12)
        assertEquals("Mar 07, 2025", DateUtils.formatDate(t))
        assertEquals("03/07/25", DateUtils.formatDateShort(t))
    }

    @Test fun formats_follow_a_locale_change_while_the_app_runs() {
        val t = millis("2025-03-07", 12)
        Locale.setDefault(Locale.GERMANY)
        assertEquals("07/03/25".length, DateUtils.formatDateShort(t).length)
        assertEquals("Mär", DateUtils.formatDate(t).take(3).removeSuffix("."))
    }

    @Test fun local_date_key_uses_the_device_zone() {
        // 23:30 in New York is already the next day in UTC.
        assertEquals("2025-03-07", DateUtils.localDateKey(millis("2025-03-07", 23, 30)))
    }

    @Test fun start_and_end_of_day_bound_the_local_day() {
        val t = millis("2025-03-07", 15)
        assertEquals(millis("2025-03-07", 0), DateUtils.getStartOfDay(t))
        assertEquals(millis("2025-03-08", 0) - 1, DateUtils.getEndOfDay(t))
    }

    @Test fun start_and_end_of_day_are_correct_on_a_dst_day() {
        // 2025-03-09 is 23 hours long in New York (clocks go forward).
        val t = millis("2025-03-09", 12)
        val length = DateUtils.getEndOfDay(t) + 1 - DateUtils.getStartOfDay(t)
        assertEquals(23L * 60 * 60 * 1000, length)
    }

    @Test fun days_between_counts_calendar_days_not_24_hour_spans() {
        // Late evening to early morning is one calendar day apart, though only a few hours.
        assertEquals(1, DateUtils.getDaysBetween(millis("2025-03-07", 22), millis("2025-03-08", 1)))
        // Across the spring-forward day, a 23-hour "day" still counts as one.
        assertEquals(1, DateUtils.getDaysBetween(millis("2025-03-09", 0, 30), millis("2025-03-10", 0, 30)))
        assertEquals(0, DateUtils.getDaysBetween(millis("2025-03-07", 1), millis("2025-03-07", 23)))
    }

    @Test fun days_between_ignores_argument_order() {
        val a = millis("2025-03-01", 12)
        val b = millis("2025-03-11", 12)
        assertEquals(10, DateUtils.getDaysBetween(a, b))
        assertEquals(10, DateUtils.getDaysBetween(b, a))
        assertTrue(DateUtils.getDaysBetween(a, a) == 0)
    }
}
