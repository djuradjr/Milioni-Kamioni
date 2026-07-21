package com.example.stayfree.data.repository

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.example.stayfree.data.local.db.dao.AppUsageDao
import com.example.stayfree.data.local.entity.AppUsageEntity
import com.example.stayfree.domain.model.AppUsage
import com.example.stayfree.util.AppInfoUtils
import com.example.stayfree.util.TimeUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsageRepositoryImpl @Inject constructor(
    private val dao: AppUsageDao,
    private val usageStatsManager: UsageStatsManager,
    @ApplicationContext private val context: Context
) : UsageRepository {

    override fun getUsageForDate(date: String): Flow<List<AppUsage>> =
        dao.getUsageForDate(date).map { list -> list.map { it.toDomain() } }

    override fun getUsageFromDate(fromDate: String): Flow<List<AppUsage>> =
        dao.getUsageFromDate(fromDate).map { list -> list.map { it.toDomain() } }

    override fun getTotalScreenTimeForDate(date: String): Flow<Long> =
        dao.getTotalScreenTimeForDate(date).map { it ?: 0L }

    override fun getTotalUnlocksForDate(date: String): Flow<Int> =
        dao.getTotalUnlocksForDate(date).map { it ?: 0 }

    override fun getTotalScreenTimeBetween(fromDate: String, toDate: String): Flow<Long> =
        dao.getTotalScreenTimeBetween(fromDate, toDate).map { it ?: 0L }

    override fun getTotalUnlocksBetween(fromDate: String, toDate: String): Flow<Int> =
        dao.getTotalUnlocksBetween(fromDate, toDate).map { it ?: 0 }

    override fun getUsageForPackage(packageName: String, fromDate: String): Flow<List<AppUsage>> =
        dao.getUsageForPackage(packageName, fromDate).map { list -> list.map { it.toDomain() } }

    override fun getScreenTimeForPackageOnDate(packageName: String, date: String): Flow<Long> =
        dao.getUsageForPackageOnDate(packageName, date).map { it?.totalTimeMs ?: 0L }

    override fun getUnlocksForPackageOnDate(packageName: String, date: String): Flow<Int> =
        dao.getUsageForPackageOnDate(packageName, date).map { it?.unlockCount ?: 0 }

    /**
     * Recomputes per-app foreground time for the effective day by folding raw
     * UsageEvents into sessions. Unlike queryUsageStats(INTERVAL_DAILY) — which
     * is aligned to the system day — this respects a custom daily reset time.
     */
    override suspend fun syncFromUsageStats(date: String, resetTimeMinutes: Int) {
        val startMs = effectiveDayStartMs(resetTimeMinutes)
        val endMs = System.currentTimeMillis()
        if (endMs <= startMs) return

        // Never track ourselves — a screen-time tracker topping its own chart with
        // hours spent on the block/config screens is meaningless and alarming.
        dao.deleteForPackage(context.packageName)
        // One-shot healing of days corrupted by the old double-counting fold.
        dao.deleteCorruptDays(DAY_MS)

        val totals = mutableMapOf<String, Long>()
        foldForegroundSessions(startMs, endMs) { pkg, from, to ->
            totals[pkg] = (totals[pkg] ?: 0L) + (to - from)
        }

        for ((pkg, totalMs) in totals) {
            if (totalMs <= 0 || pkg == AppUsageEntity.DEVICE_ROW || pkg == context.packageName) continue
            val existing = dao.getUsageForPackageAndDate(pkg, date)
            dao.upsert(
                AppUsageEntity(
                    id = existing?.id ?: 0,
                    packageName = pkg,
                    appName = AppInfoUtils.getAppName(context, pkg),
                    date = date,
                    totalTimeMs = totalMs,
                    unlockCount = existing?.unlockCount ?: 0,
                    screenOnCount = existing?.screenOnCount ?: 0
                )
            )
        }
    }

    /**
     * Splits foreground sessions from raw UsageEvents into 24 clock-hour buckets.
     * Only works within the system's event retention window (~last 7 days);
     * older dates return all zeros.
     */
    override suspend fun getHourlyUsage(date: String): List<Long> {
        val buckets = LongArray(24)
        val dayStart = TimeUtils.getDayStartMs(date) ?: return buckets.toList()
        val dayEnd = minOf(dayStart + 24 * 3_600_000L, System.currentTimeMillis())
        if (dayEnd <= dayStart) return buckets.toList()

        foldForegroundSessions(dayStart, dayEnd) { pkg, from, to ->
            // Excluded from totals, so exclude here too — otherwise the peak chart
            // disagrees with the daily total.
            if (pkg == context.packageName) return@foldForegroundSessions
            var cursor = from
            while (cursor < to) {
                val hour = ((cursor - dayStart) / 3_600_000L).toInt().coerceIn(0, 23)
                val hourEnd = dayStart + (hour + 1) * 3_600_000L
                buckets[hour] += minOf(to, hourEnd) - cursor
                cursor = hourEnd
            }
        }
        return buckets.toList()
    }

    /**
     * Folds raw UsageEvents into non-overlapping foreground sessions clamped to
     * [windowStartMs, windowEndMs] using a single-current-foreground model: only
     * one app counts at a time; a RESUMED of another package, a PAUSED/STOPPED of
     * the current activity, or screen-off/keyguard/shutdown ends the session.
     * (A per-package map double-counted apps with several activities: the second
     * RESUMED was swallowed, so its PAUSED fell back to the window start and a
     * day could sum to 20h+.) Activity identity (pkg+class) guards against a
     * stale PAUSED arriving after a same-package activity switch.
     */
    private fun foldForegroundSessions(
        windowStartMs: Long,
        windowEndMs: Long,
        onSession: (pkg: String, fromMs: Long, toMs: Long) -> Unit
    ) {
        // Look back so a session straddling the window start still counts from it.
        val events = usageStatsManager.queryEvents(windowStartMs - LOOKBACK_MS, windowEndMs) ?: return
        val event = UsageEvents.Event()
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
    }

    override suspend fun incrementUnlock(date: String) {
        ensureDeviceRow(date)
        dao.incrementUnlockCount(AppUsageEntity.DEVICE_ROW, date)
    }

    override suspend fun incrementScreenOn(date: String) {
        ensureDeviceRow(date)
        dao.incrementScreenOnCount(AppUsageEntity.DEVICE_ROW, date)
    }

    private suspend fun ensureDeviceRow(date: String) {
        dao.insertIgnore(
            AppUsageEntity(
                packageName = AppUsageEntity.DEVICE_ROW,
                appName = "Device",
                date = date
            )
        )
    }

    private companion object {
        const val LOOKBACK_MS = 6 * 3_600_000L
        const val DAY_MS = 24 * 3_600_000L
    }

    private fun effectiveDayStartMs(resetTimeMinutes: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, resetTimeMinutes / 60)
            set(Calendar.MINUTE, resetTimeMinutes % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis > System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, -1)
            }
        }
        return cal.timeInMillis
    }

    private fun AppUsageEntity.toDomain() = AppUsage(
        packageName = packageName,
        appName = appName,
        date = date,
        totalTimeMs = totalTimeMs,
        unlockCount = unlockCount,
        screenOnCount = screenOnCount
    )
}
