package com.example.rentmate.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.rentmate.MainActivity
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

object ReservationNotificationManager {

    private const val CHANNEL_ID = "reservation_channel"
    private const val NOTIFICATION_ID = 1
    private const val REMINDER_WORK_NAME = "reservation_reminder_work"

    fun createNotificationChannel(context: Context) {
        val name = "Reservation Notifications"
        val descriptionText = "Notifications for apartment reservations"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    fun sendNotification(context: Context, title: String, message: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun scheduleReminder(context: Context, startDateStr: String) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val startDate = sdf.parse(startDateStr)?.time ?: return
        val reminderTime = startDate - (24 * 60 * 60 * 1000)
        val currentTime = System.currentTimeMillis()

        if (reminderTime > currentTime) {
            val workRequest = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(reminderTime - currentTime, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    REMINDER_WORK_NAME + "_$startDateStr",
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )
        }
    }
}

class ReminderWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        ReservationNotificationManager.sendNotification(
            applicationContext,
            "Reservation Reminder",
            "Imate rezervaciju sutra! Provjerite detalje."
        )
        return Result.success()
    }
}