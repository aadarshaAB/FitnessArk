package com.fitnessark.util

import com.fitnessark.TestSupport
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

    @Test fun picked_day_other_than_today_is_logged_at_local_noon_of_that_day() {
        val now = TestSupport.noon(0)
        val pickedUtc = DateUtils.pickerMillisOf(TestSupport.noon(5))
        val ts = DateUtils.timestampForPickedDay(pickedUtc, now)
        assertEquals(DateUtils.localDateKey(TestSupport.noon(5)), DateUtils.localDateKey(ts))
        assertEquals(TestSupport.noon(5), ts)
    }

    @Test fun picking_today_keeps_the_current_time() {
        val now = TestSupport.noon(0) + 3_600_000L        // 13:00 today
        val ts = DateUtils.timestampForPickedDay(DateUtils.pickerMillisOf(now), now)
        assertEquals(now, ts)
    }

    @Test fun formats_time_of_day_in_the_device_locale() {
        Locale.setDefault(Locale.US)
        // The JDK's SHORT time format can separate the hour and AM/PM with a narrow no-break
        // space rather than a plain one, so compare case-insensitively on the normalized text.
        fun normalize(s: String) = s.replace(' ', ' ').uppercase()
        assertEquals("8:00 PM", normalize(DateUtils.formatTimeOfDay(20, 0)))
        assertEquals("12:00 AM", normalize(DateUtils.formatTimeOfDay(0, 0)))
    }
}
