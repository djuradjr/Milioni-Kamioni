package com.example.stayfree

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.stayfree.data.local.preferences.AppPreferences
import com.example.stayfree.data.repository.BlockingRepository
import com.example.stayfree.service.TrackingScheduler
import com.example.stayfree.util.AppearanceModes
import com.example.stayfree.util.NotificationUtils
import com.example.stayfree.util.PinHasher
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltAndroidApp
class StayFreeApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var prefs: AppPreferences
    @Inject lateinit var blockingRepository: BlockingRepository

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Synchronous read on purpose: an async apply would flash the wrong
        // theme on cold start, then visibly recreate the first activity.
        val appearance = runBlocking { prefs.appearanceMode.first() }
        AppCompatDelegate.setDefaultNightMode(AppearanceModes.toNightMode(appearance))
        NotificationUtils.createChannels(this)
        applicationScope.launch {
            val resetTime = prefs.dailyResetTimeMinutes.first()
            TrackingScheduler.ensureWorkScheduled(this@StayFreeApp, resetTime)
        }
        applicationScope.launch {
            try {
                blockingRepository.deactivateRulesForUninstalledApps()
            } catch (e: Exception) {
                // Non-critical cleanup; stale rules simply never match a foreground app.
            }
        }
        applicationScope.launch {
            // Migration from the unsalted SHA-256 PIN format (pre-release dev
            // installs only): drop the PIN; the user sets it again in Settings.
            val storedHash = prefs.pinHash.first()
            if (storedHash != null && !PinHasher.isModernFormat(storedHash)) {
                prefs.setPinHash(null)
                prefs.setPinEnabled(false)
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
