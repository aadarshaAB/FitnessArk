package com.fitnessark.util

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Schedules the daily check-in reminder (F5) as a chain of one-shot [ReminderWorker] runs rather
 * than a single periodic request, so a change to the reminder time takes effect on the very next
 * firing instead of waiting out the previous period.
 */
object ReminderScheduler {

    const val UNIQUE_WORK_NAME = "daily_checkin_reminder"

    fun schedule(context: Context, hour: Int, minute: Int) {
        val delay = delayUntilNext(hour, minute)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(ReminderWorker.KEY_HOUR to hour, ReminderWorker.KEY_MINUTE to minute))
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    /** Milliseconds from now until the next [hour]:[minute], today if that time hasn't passed yet. */
    fun delayUntilNext(hour: Int, minute: Int, now: LocalDateTime = LocalDateTime.now()): Long {
        var next = LocalDateTime.of(now.toLocalDate(), LocalTime.of(hour, minute))
        if (!next.isAfter(now)) next = next.plusDays(1)
        val zone = ZoneId.systemDefault()
        return next.atZone(zone).toInstant().toEpochMilli() - now.atZone(zone).toInstant().toEpochMilli()
    }
}
