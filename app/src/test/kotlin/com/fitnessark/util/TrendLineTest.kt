package com.fitnessark.util

import com.fitnessark.util.TrendLine.Point
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

class TrendLineTest {

    private val originalZone = TimeZone.getDefault()

    @Before fun pinZone() = TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    @After fun restoreZone() = TimeZone.setDefault(originalZone)

    private fun day(n: Long): Long =
        LocalDate.of(2026, 1, 1).plusDays(n).atTime(12, 0).atZone(ZoneId.of("UTC")).toInstant().toEpochMilli()

    private fun point(n: Long, v: Float) = Point(day(n), v)

    @Test fun first_point_is_its_own_average() {
        assertEquals(listOf(70f), TrendLine.movingAverage(listOf(point(0, 70f))))
    }

    @Test fun averages_the_values_within_the_window_ending_on_each_day() {
        val avg = TrendLine.movingAverage(listOf(point(0, 70f), point(1, 72f), point(2, 74f)))
        assertEquals(70f, avg[0], 0.001f)
        assertEquals(71f, avg[1], 0.001f)
        assertEquals(72f, avg[2], 0.001f)
    }

    @Test fun values_older_than_the_window_drop_out() {
        // Day 0 is 7 days before day 7, so a 7-day window ending day 7 (days 1..7) excludes it.
        val avg = TrendLine.movingAverage(listOf(point(0, 100f), point(7, 80f)))
        assertEquals(80f, avg[1], 0.001f)
        val sixApart = TrendLine.movingAverage(listOf(point(0, 100f), point(6, 80f)))
        assertEquals(90f, sixApart[1], 0.001f)
    }

    @Test fun days_with_nothing_logged_do_not_count_as_zero() {
        val avg = TrendLine.movingAverage(listOf(point(0, 70f), point(4, 74f)))
        assertEquals(72f, avg[1], 0.001f)
    }

    @Test fun recent_average_covers_the_last_seven_days_including_today() {
        val points = listOf(point(0, 100f), point(3, 70f), point(9, 74f), point(10, 76f))
        // "now" = day 10: window is days 4..10 -> 74 and 76
        assertEquals(75f, TrendLine.recentAverage(points, day(10))!!, 0.001f)
    }

    @Test fun recent_average_is_null_when_nothing_is_recent() {
        assertNull(TrendLine.recentAverage(listOf(point(0, 70f)), day(30)))
        assertNull(TrendLine.recentAverage(emptyList(), day(0)))
    }
}
