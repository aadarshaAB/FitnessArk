package com.fitnessark.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class ReminderSchedulerTest {

    @Test fun schedules_for_later_today_when_the_time_has_not_passed() {
        val now = LocalDateTime.of(2026, 1, 1, 10, 0)
        val delay = ReminderScheduler.delayUntilNext(20, 0, now)
        assertEquals(10, ChronoUnit.HOURS.between(now, now.plus(delay, ChronoUnit.MILLIS)))
    }

    @Test fun schedules_for_tomorrow_when_the_time_has_already_passed_today() {
        val now = LocalDateTime.of(2026, 1, 1, 21, 0)
        val delay = ReminderScheduler.delayUntilNext(20, 0, now)
        val fired = now.plus(delay, ChronoUnit.MILLIS)
        assertEquals(LocalDateTime.of(2026, 1, 2, 20, 0), fired)
    }

    @Test fun schedules_for_tomorrow_when_the_time_is_exactly_now() {
        val now = LocalDateTime.of(2026, 1, 1, 20, 0)
        val delay = ReminderScheduler.delayUntilNext(20, 0, now)
        val fired = now.plus(delay, ChronoUnit.MILLIS)
        assertEquals(LocalDateTime.of(2026, 1, 2, 20, 0), fired)
    }
}
