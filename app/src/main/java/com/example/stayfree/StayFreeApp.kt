package com.example.stayfree

import android.app.Application
import android.app.LocaleManager
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppLocalesMetadataHolderService
import androidx.core.os.LocaleListCompat
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.stayfree.data.billing.PremiumRepository
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
    @Inject lateinit var premiumRepository: PremiumRepository

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Synchronous read on purpose: an async apply would flash the wrong
        // theme on cold start, then visibly recreate the first activity.
        val appearance = runBlocking { prefs.appearanceMode.first() }
        AppCompatDelegate.setDefaultNightMode(AppearanceModes.toNightMode(appearance))
        applyEnglishOnFirstInstall()
        NotificationUtils.createChannels(this)
        premiumRepository.start()
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

    /**
     * A fresh install starts in English whatever the phone language; Serbian stays
     * one tap away in Settings. Runs once: below API 33 AppCompat restores a stored
     * choice only when the first activity starts, so a later empty read here would
     * wrongly reset it. Updates are left alone so nobody's language changes.
     * On API 33+ AppCompat's setter is a no-op until an activity exists, so the
     * framework LocaleManager is used directly.
     */
    private fun applyEnglishOnFirstInstall() {
        if (runBlocking { prefs.languageDefaultApplied.first() }) return
        val info = packageManager.getPackageInfo(packageName, 0)
        if (info.firstInstallTime == info.lastUpdateTime) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val localeManager = getSystemService(LocaleManager::class.java)
                if (localeManager.applicationLocales.isEmpty) {
                    localeManager.applicationLocales = LocaleList.forLanguageTags("en")
                    // AppCompat's one-time storage→framework sync runs at the first activity
                    // and can read the framework list as empty, writing its (empty) stored
                    // list over ours. A fresh install has nothing to sync, so mark it done.
                    packageManager.setComponentEnabledSetting(
                        ComponentName(this, AppLocalesMetadataHolderService::class.java),
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP
                    )
                }
            } else if (AppCompatDelegate.getApplicationLocales().isEmpty) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
            }
        }
        applicationScope.launch { prefs.setLanguageDefaultApplied() }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
