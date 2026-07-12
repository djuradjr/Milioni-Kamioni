package com.example.stayfree.service

import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.example.stayfree.data.local.preferences.AppPreferences
import com.example.stayfree.data.repository.UsageRepository
import com.example.stayfree.receiver.ScreenStateReceiver
import com.example.stayfree.util.AppInfoUtils
import com.example.stayfree.util.NotificationUtils
import com.example.stayfree.util.TimeUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class UsageTrackingService : LifecycleService() {

    @Inject lateinit var usageRepository: UsageRepository
    @Inject lateinit var prefs: AppPreferences

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var screenStateReceiver: ScreenStateReceiver
    private var syncJob: Job? = null

    // One warning per app per day ("pkg|date"). In-memory on purpose: a service
    // restart may repeat a warning once, which beats persisting churn every 60s.
    private val warnedLimitKeys = mutableSetOf<String>()

    override fun onCreate() {
        super.onCreate()
        startForeground(
            NotificationUtils.NOTIFICATION_ID_FOREGROUND,
            NotificationUtils.buildForegroundNotification(this)
        )
        registerScreenStateReceiver()
        startPeriodicSync()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(screenStateReceiver)
        } catch (e: Exception) {
            // Receiver may not be registered if onCreate failed midway.
        }
        syncJob?.cancel()
        serviceScope.cancel()
    }

    private fun registerScreenStateReceiver() {
        screenStateReceiver = ScreenStateReceiver(usageRepository, prefs, serviceScope)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        ContextCompat.registerReceiver(
            this, screenStateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun startPeriodicSync() {
        syncJob = serviceScope.launch {
            while (isActive) {
                try {
                    val resetTime = prefs.dailyResetTimeMinutes.first()
                    val date = TimeUtils.getEffectiveDate(resetTime)
                    usageRepository.syncFromUsageStats(date, resetTime)
                    checkBeforeTimeoutWarnings(date)
                } catch (e: Exception) {
                    // continue on error
                }
                delay(60_000L)
            }
        }
    }

    private suspend fun checkBeforeTimeoutWarnings(date: String) {
        if (!prefs.notificationsMaster.first() || !prefs.notifBeforeTimeout.first()) return
        val enabledPkgs = prefs.blockAppsEnabledPkgs.first()
        if (enabledPkgs.isEmpty()) return
        val limits = prefs.blockAppLimitsMinutes.first()
        for (pkg in enabledPkgs) {
            val limitMin = limits[pkg] ?: AppPreferences.DEFAULT_BLOCK_APP_LIMIT_MINUTES
            if (limitMin <= 0) continue // block-now targets have no countdown
            val key = "$pkg|$date"
            if (key in warnedLimitKeys) continue
            val usedMs = usageRepository.getScreenTimeForPackageOnDate(pkg, date).first()
            val remainingMs = limitMin * 60_000L - usedMs
            if (remainingMs in 1..WARN_BEFORE_MS) {
                warnedLimitKeys.add(key)
                NotificationUtils.sendBeforeTimeoutAlert(
                    this, pkg,
                    AppInfoUtils.getAppName(this, pkg),
                    ((remainingMs + 59_999) / 60_000).toInt()
                )
            }
        }
    }

    private companion object {
        const val WARN_BEFORE_MS = 5 * 60_000L
    }
}
