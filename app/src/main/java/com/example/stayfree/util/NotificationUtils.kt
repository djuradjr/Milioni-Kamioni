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
                        context.getString(R.string.notif_channel_foreground_name),
                        NotificationManager.IMPORTANCE_MIN
                    ).apply { description = context.getString(R.string.notif_channel_foreground_desc) },
                    NotificationChannel(
                        CHANNEL_USAGE_ALERTS,
                        context.getString(R.string.notif_channel_alerts_name),
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply { description = context.getString(R.string.notif_channel_alerts_desc) },
                    NotificationChannel(
                        CHANNEL_DAILY_SUMMARY,
                        context.getString(R.string.notif_channel_summary_name),
                        NotificationManager.IMPORTANCE_LOW
                    ).apply { description = context.getString(R.string.notif_channel_summary_desc) }
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
            .setContentText(context.getString(R.string.notif_foreground_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    /** One notification per app (id = package hash) so alerts don't overwrite each other. */
    fun sendBeforeTimeoutAlert(context: Context, packageName: String, appName: String, minutesLeft: Int) {
        if (!PermissionUtils.hasNotificationPermission(context)) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, CHANNEL_USAGE_ALERTS)
            .setContentTitle(context.getString(R.string.notif_before_timeout_title, appName, minutesLeft))
            .setContentText(context.getString(R.string.notif_before_timeout_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .build()
        nm.notify(packageName.hashCode(), notification)
    }

    fun sendDailySummary(context: Context, totalScreenTime: String) {
        if (!PermissionUtils.hasNotificationPermission(context)) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, CHANNEL_DAILY_SUMMARY)
            .setContentTitle(context.getString(R.string.notif_daily_summary_title))
            .setContentText(context.getString(R.string.notif_daily_summary_text, totalScreenTime))
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_ID_DAILY_SUMMARY, notification)
    }
}
