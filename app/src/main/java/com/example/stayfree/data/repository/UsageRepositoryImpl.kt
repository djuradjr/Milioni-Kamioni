package com.example.stayfree.data.repository

import android.content.Context
import com.example.stayfree.data.local.db.dao.AppUsageDao
import com.example.stayfree.data.local.entity.AppUsageEntity
import com.example.stayfree.data.usage.ForegroundSessions
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
    private val sessions: ForegroundSessions,
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

        // The home screen used to top the chart as if it were an app.
        sessions.homePackage()?.let { dao.deleteForPackage(it) }

        val trackable = sessions.trackablePackages()
        if (trackable.isNotEmpty()) dao.deleteUntrackedForDate(date, trackable.toList())

        val totals = mutableMapOf<String, Long>()
        val opens = mutableMapOf<String, Int>()
        // Screen changes inside an app, and system dialogs on top of it, each end one
        // foreground session and start another; only a real absence counts as an open.
        val lastEndMs = mutableMapOf<String, Long>()
        sessions.fold(startMs, endMs) { pkg, from, to ->
            if (pkg !in trackable) return@fold
            totals[pkg] = (totals[pkg] ?: 0L) + (to - from)
            val previousEnd = lastEndMs[pkg]
            if (previousEnd == null || from - previousEnd > OPEN_GAP_MS) {
                opens[pkg] = (opens[pkg] ?: 0) + 1
            }
            lastEndMs[pkg] = to
        }

        for ((pkg, totalMs) in totals) {
            if (totalMs <= 0) continue
            val existing = dao.getUsageForPackageAndDate(pkg, date)
            dao.upsert(
                AppUsageEntity(
                    id = existing?.id ?: 0,
                    packageName = pkg,
                    appName = AppInfoUtils.getAppName(context, pkg),
                    date = date,
                    totalTimeMs = totalMs,
                    unlockCount = opens[pkg] ?: 0,
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

        val trackable = sessions.trackablePackages()
        sessions.fold(dayStart, dayEnd) { pkg, from, to ->
            // Same filter as the totals, or the peak chart disagrees with the daily total.
            if (pkg !in trackable) return@fold
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
        const val OPEN_GAP_MS = 5_000L
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
