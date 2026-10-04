package com.fitnessark.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fitnessark.MainActivity
import com.fitnessark.R
import com.fitnessark.data.repository.MeasurementRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Fires once a day at the time set in Settings. Skips the notification if today is already
 * logged, then reschedules itself for the next day (see [ReminderScheduler]).
 */
class ReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val measurementRepo: MeasurementRepository by inject()

    companion object {
        const val KEY_HOUR = "hour"
        const val KEY_MINUTE = "minute"
        const val CHANNEL_ID = "daily_reminder"
        const val NOTIFICATION_ID = 1001
    }

    override suspend fun doWork(): Result {
        val hour = inputData.getInt(KEY_HOUR, 20)
        val minute = inputData.getInt(KEY_MINUTE, 0)

        val alreadyLogged = measurementRepo.getMeasurementForDay(System.currentTimeMillis()) != null
        if (!alreadyLogged) showNotification()

        // Always reschedule, win or lose, so a missed/denied notification doesn't end the reminder.
        ReminderScheduler.schedule(applicationContext, hour, minute)
        return Result.success()
    }

    private fun showNotification() {
        val context = applicationContext
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Daily reminder", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }

        val openApp = PendingIntent.getActivity(
            context, 0,
            android.content.Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Time for your check-in")
            .setContentText("Log today's weight or measurements to keep your streak going.")
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }
}
