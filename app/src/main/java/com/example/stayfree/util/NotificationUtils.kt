package com.example.stayfree.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.stayfree.R
import com.example.stayfree.ui.MainActivity

object NotificationUtils {

    const val CHANNEL_FOREGROUND = "channel_foreground_service"
    const val CHANNEL_USAGE_ALERTS = "channel_usage_alerts"
    const val CHANNEL_DAILY_SUMMARY = "channel_daily_summary"

    const val NOTIFICATION_ID_FOREGROUND = 1
    const val NOTIFICATION_ID_USAGE_ALERT = 2
    const val NOTIFICATION_ID_DAILY_SUMMARY = 3

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannels(
                listOf(
                    NotificationChannel(
                        CHANNEL_FOREGROUND,
                        "Usage Tracking",
                        NotificationManager.IMPORTANCE_MIN
                    ).apply { description = "Block Brainrot is tracking your screen time" },
                    NotificationChannel(
                        CHANNEL_USAGE_ALERTS,
                        "Usage Alerts",
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply { description = "Alerts when you reach app usage limits" },
                    NotificationChannel(
                        CHANNEL_DAILY_SUMMARY,
                        "Daily Summary",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply { description = "Your daily screen time summary" }
                )
            )
        }
    }

    fun buildForegroundNotification(context: Context): Notification {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_FOREGROUND)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText("Tracking your screen time")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    fun sendUsageAlert(context: Context, appName: String, percent: Int) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, CHANNEL_USAGE_ALERTS)
            .setContentTitle("Usage Alert — $appName")
            .setContentText("You've used $percent% of your daily limit for $appName")
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_ID_USAGE_ALERT, notification)
    }

    fun sendDailySummary(context: Context, totalScreenTime: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, CHANNEL_DAILY_SUMMARY)
            .setContentTitle("Daily Screen Time Summary")
            .setContentText("Total screen time yesterday: $totalScreenTime")
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_ID_DAILY_SUMMARY, notification)
    }
}
