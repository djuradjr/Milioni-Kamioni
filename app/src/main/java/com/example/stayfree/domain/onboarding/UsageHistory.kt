package com.example.stayfree.domain.onboarding

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the week *before* install straight from UsageStatsManager.
 *
 * The repository's own sync only ever covers the current effective day, so it
 * can't answer "what did last week look like". This does, and it lets the first
 * screen after the permission steps show real numbers instead of an empty state
 * — which is the one moment the user actually wants to see them.
 *
 * How much history exists is up to the device: most keep daily buckets for about
 * a week, some OEMs clear them aggressively. An empty result is normal, not an
 * error, and callers must have a first-run state for it.
 */
@Singleton
class UsageHistory @Inject constructor(
    @ApplicationContext private val context: Context
) {

    data class AppTotal(val packageName: String, val label: String, val totalMs: Long)

    data class Report(
        /** Per-day totals, oldest→yesterday. Empty when the device kept nothing. */
        val dailyTotals: List<Long>,
        val totalMs: Long,
        val topApps: List<AppTotal>
    ) {
        val hasData: Boolean get() = totalMs > 0L
        val dailyAverageMs: Long
            get() = if (dailyTotals.isEmpty()) 0L else totalMs / dailyTotals.size
    }

    /**
     * @param days how far back to look, not counting today — today is still in
     *        progress and would drag the average down.
     */
    suspend fun lastWeek(days: Int = DEFAULT_DAYS): Report = withContext(Dispatchers.IO) {
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return@withContext EMPTY
        val pm = context.packageManager
        val trackable = trackablePackages(pm)

        val dayTotals = mutableListOf<Long>()
        val perApp = mutableMapOf<String, Long>()

        for (ago in days downTo 1) {
            val (start, end) = dayBounds(ago)
            val stats = runCatching {
                manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
            }.getOrNull().orEmpty()

            var dayTotal = 0L
            stats.forEach { entry ->
                val pkg = entry.packageName ?: return@forEach
                if (pkg !in trackable) return@forEach
                // INTERVAL_DAILY buckets can overlap the query window; clamping to
                // the window keeps a long-running app from inflating one day.
                val ms = entry.totalTimeInForeground.coerceAtMost(end - start)
                if (ms <= 0L) return@forEach
                dayTotal += ms
                perApp[pkg] = (perApp[pkg] ?: 0L) + ms
            }
            dayTotals += dayTotal
        }

        val total = dayTotals.sum()
        if (total <= 0L) return@withContext EMPTY

        val top = perApp.entries
            .sortedByDescending { it.value }
            .take(TOP_APPS)
            .map { (pkg, ms) -> AppTotal(pkg, labelOf(pm, pkg), ms) }

        Report(dailyTotals = dayTotals, totalMs = total, topApps = top)
    }

    /** Start/end of the day [ago] days before today, in local time. */
    private fun dayBounds(ago: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -ago)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        return start to (start + DAY_MS)
    }

    /**
     * Launchable, non-system apps only — the same rule the tracker uses, so the
     * report can't contradict what the dashboard shows later. Our own package is
     * excluded: a screen-time app topping its own chart is meaningless.
     */
    private fun trackablePackages(pm: PackageManager): Set<String> =
        runCatching {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .asSequence()
                .filter { it.packageName != context.packageName }
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                .filter { isUpdatedSystemOrUserApp(it) }
                .map { it.packageName }
                .toSet()
        }.getOrDefault(emptySet())

    private fun isUpdatedSystemOrUserApp(info: ApplicationInfo): Boolean =
        info.flags and ApplicationInfo.FLAG_SYSTEM == 0 ||
            info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0

    private fun labelOf(pm: PackageManager, pkg: String): String =
        runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }
            .getOrDefault(pkg)

    private companion object {
        const val DEFAULT_DAYS = 7
        const val TOP_APPS = 5
        const val DAY_MS = 24 * 60 * 60 * 1000L
        val EMPTY = Report(emptyList(), 0L, emptyList())
    }
}
