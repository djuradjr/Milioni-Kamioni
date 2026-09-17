package com.example.stayfree.data.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.example.stayfree.data.local.entity.AppUsageEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one definition of "screen time" in the app: which packages count, and how raw
 * UsageEvents become foreground sessions. The dashboard and the onboarding report
 * both read through this, so the two can never show different numbers for a day.
 *
 * queryUsageStats(INTERVAL_DAILY) is deliberately not used: the system's daily
 * buckets don't start at midnight, so a midnight-to-midnight query returns two
 * overlapping buckets and roughly doubles the day.
 */
@Singleton
class ForegroundSessions @Inject constructor(
    private val usageStatsManager: UsageStatsManager,
    @ApplicationContext private val context: Context
) {

    /**
     * Packages that count as screen time: launchable apps only, minus the home app
     * and ourselves. Without this the launcher is the #1 "app" of the day and
     * system dialogs (permission controller, installers) show up as apps.
     */
    fun trackablePackages(): Set<String> {
        val pm = context.packageManager
        val launchable = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).mapTo(mutableSetOf()) { it.activityInfo.packageName }
        homePackage()?.let { launchable -= it }
        launchable -= context.packageName
        launchable -= AppUsageEntity.DEVICE_ROW
        return launchable
    }

    /**
     * The current default launcher only. Matching every package that declares a
     * CATEGORY_HOME activity would also swallow Settings, which ships the boot-time
     * FallbackHome and is a perfectly normal app to track.
     */
    fun homePackage(): String? =
        context.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY
        )?.activityInfo?.packageName

    /**
     * Folds raw UsageEvents into non-overlapping foreground sessions clamped to
     * [windowStartMs, windowEndMs] using a single-current-foreground model: only
     * one app counts at a time; a RESUMED of another package, a PAUSED/STOPPED of
     * the current activity, or screen-off/keyguard/shutdown ends the session.
     * (A per-package map double-counted apps with several activities: the second
     * RESUMED was swallowed, so its PAUSED fell back to the window start and a
     * day could sum to 20h+.) Activity identity (pkg+class) guards against a
     * stale PAUSED arriving after a same-package activity switch.
     *
     * @return timestamp of the oldest event the system still had, or null when the
     *         log is empty — tells callers how far back the history really reaches.
     */
    fun fold(
        windowStartMs: Long,
        windowEndMs: Long,
        onSession: (pkg: String, fromMs: Long, toMs: Long) -> Unit
    ): Long? {
        // Look back so a session straddling the window start still counts from it.
        val events = usageStatsManager.queryEvents(windowStartMs - LOOKBACK_MS, windowEndMs)
            ?: return null
        val event = UsageEvents.Event()
        var oldestEventMs: Long? = null
        var currentPkg: String? = null
        var currentClass: String? = null
        var currentSince = 0L

        fun close(endMs: Long) {
            val pkg = currentPkg ?: return
            currentPkg = null
            val from = maxOf(currentSince, windowStartMs)
            val to = minOf(endMs, windowEndMs)
            if (to > from) onSession(pkg, from, to)
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (oldestEventMs == null) oldestEventMs = event.timeStamp
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    val pkg = event.packageName ?: continue
                    if (pkg != currentPkg) {
                        close(event.timeStamp)
                        currentPkg = pkg
                        currentSince = event.timeStamp
                    }
                    currentClass = event.className
                }
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED ->
                    if (event.packageName == currentPkg && event.className == currentClass) {
                        close(event.timeStamp)
                    }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                UsageEvents.Event.KEYGUARD_SHOWN,
                UsageEvents.Event.DEVICE_SHUTDOWN -> close(event.timeStamp)
            }
        }
        close(windowEndMs)
        return oldestEventMs
    }

    private companion object {
        const val LOOKBACK_MS = 6 * 3_600_000L
    }
}
