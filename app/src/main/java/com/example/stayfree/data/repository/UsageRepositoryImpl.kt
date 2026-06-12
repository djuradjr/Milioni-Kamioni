package com.example.stayfree.data.repository

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.example.stayfree.data.local.db.dao.AppUsageDao
import com.example.stayfree.data.local.entity.AppUsageEntity
import com.example.stayfree.domain.model.AppUsage
import com.example.stayfree.util.AppInfoUtils
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

    override fun getTotalScreenTimeForDate(date: String): Flow<Long> =
        dao.getTotalScreenTimeForDate(date).map { it ?: 0L }

    override fun getTotalUnlocksForDate(date: String): Flow<Int> =
        dao.getTotalUnlocksForDate(date).map { it ?: 0 }

    override fun getUsageForPackage(packageName: String, fromDate: String): Flow<List<AppUsage>> =
        dao.getUsageForPackage(packageName, fromDate).map { list -> list.map { it.toDomain() } }

    override fun getScreenTimeForPackageOnDate(packageName: String, date: String): Flow<Long> =
        dao.getUsageForPackageOnDate(packageName, date).map { it?.totalTimeMs ?: 0L }

    override fun getUnlocksForPackageOnDate(packageName: String, date: String): Flow<Int> =
        dao.getUsageForPackageOnDate(packageName, date).map { it?.unlockCount ?: 0 }

    /**
     * Recomputes per-app foreground time for the effective day by iterating
     * raw UsageEvents (RESUMED = session start, PAUSED = session end). Unlike
     * queryUsageStats(INTERVAL_DAILY) — which is aligned to the system day —
     * this respects a custom daily reset time.
     */
    override suspend fun syncFromUsageStats(date: String, resetTimeMinutes: Int) {
        val startMs = effectiveDayStartMs(resetTimeMinutes)
        val endMs = System.currentTimeMillis()
        if (endMs <= startMs) return

        val events = usageStatsManager.queryEvents(startMs, endMs) ?: return
        val totals = mutableMapOf<String, Long>()
        val foregroundSince = mutableMapOf<String, Long>()
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            when (event.eventType) {
                // ACTIVITY_RESUMED/PAUSED (API 29+) share int values with the
                // older MOVE_TO_FOREGROUND/BACKGROUND, so this matches both.
                UsageEvents.Event.ACTIVITY_RESUMED ->
                    if (pkg !in foregroundSince) foregroundSince[pkg] = event.timeStamp
                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    // PAUSE without a seen RESUME: session started before the window.
                    val sessionStart = foregroundSince.remove(pkg) ?: startMs
                    val elapsed = event.timeStamp - sessionStart
                    if (elapsed > 0) totals[pkg] = (totals[pkg] ?: 0L) + elapsed
                }
            }
        }
        // Apps still in the foreground count up to the end of the window.
        for ((pkg, since) in foregroundSince) {
            val elapsed = endMs - since
            if (elapsed > 0) totals[pkg] = (totals[pkg] ?: 0L) + elapsed
        }

        for ((pkg, totalMs) in totals) {
            if (totalMs <= 0 || pkg == AppUsageEntity.DEVICE_ROW) continue
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
