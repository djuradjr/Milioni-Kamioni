package com.example.stayfree.domain.onboarding

import android.content.Context
import com.example.stayfree.data.usage.ForegroundSessions
import com.example.stayfree.util.AppInfoUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the week *before* install from the system's usage event log.
 *
 * The repository's own sync only ever covers the current effective day, so it
 * can't answer "what did last week look like". This does, through the same
 * [ForegroundSessions] fold the dashboard uses, so the report and the dashboard
 * agree to the minute.
 *
 * How much history exists is up to the device: the event log usually reaches back
 * about a week, some OEMs clear it sooner. An empty result is normal, not an error,
 * and callers must have a first-run state for it.
 */
@Singleton
class UsageHistory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessions: ForegroundSessions
) {

    data class AppTotal(val packageName: String, val label: String, val totalMs: Long)

    data class Session(val packageName: String, val fromMs: Long, val toMs: Long)

    data class Report(
        /** Per-day totals, oldest→yesterday. Empty when the device kept nothing. */
        val dailyTotals: List<Long>,
        val totalMs: Long,
        val topApps: List<AppTotal>,
        /** Days the log fully covers and that have usage — what the average divides by. */
        val measuredDays: Int,
        val measuredMs: Long
    ) {
        val hasData: Boolean get() = totalMs > 0L
        val dailyAverageMs: Long
            get() = if (measuredDays == 0) 0L else measuredMs / measuredDays
    }

    /**
     * @param days how far back to look, not counting today — today is still in
     *        progress and would drag the average down.
     */
    suspend fun lastWeek(days: Int = DEFAULT_DAYS): Report = withContext(Dispatchers.IO) {
        // days+1 boundaries: start of each day, and midnight today closing the last one.
        val boundaries = (days downTo 0).map { startOfDay(it) }
        val trackable = sessions.trackablePackages()
        val found = mutableListOf<Session>()

        val oldestEventMs = runCatching {
            sessions.fold(boundaries.first(), boundaries.last()) { pkg, from, to ->
                if (pkg in trackable) found += Session(pkg, from, to)
            }
        }.getOrNull() ?: return@withContext EMPTY

        buildReport(boundaries, found, oldestEventMs) { AppInfoUtils.getAppName(context, it) }
    }

    /** Local midnight [ago] days before today; the calendar handles DST days. */
    private fun startOfDay(ago: Int): Long =
        Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -ago)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    internal companion object {
        const val DEFAULT_DAYS = 7
        const val TOP_APPS = 5
        /** Below a minute an app is a tap-through, not part of anyone's week. */
        const val MIN_APP_MS = 60_000L
        val EMPTY = Report(emptyList(), 0L, emptyList(), 0, 0L)

        /**
         * @param boundaries start of each day plus the end of the last one, ascending
         * @param oldestEventMs how far back the system log actually reaches
         */
        fun buildReport(
            boundaries: List<Long>,
            sessions: List<Session>,
            oldestEventMs: Long,
            labelOf: (String) -> String
        ): Report {
            val days = boundaries.size - 1
            val dayTotals = LongArray(days)
            val perApp = mutableMapOf<String, Long>()

            sessions.forEach { s ->
                // A session can run across midnight; each day gets its own part.
                for (day in 0 until days) {
                    val overlap = minOf(s.toMs, boundaries[day + 1]) - maxOf(s.fromMs, boundaries[day])
                    if (overlap <= 0) continue
                    dayTotals[day] += overlap
                    perApp[s.packageName] = (perApp[s.packageName] ?: 0L) + overlap
                }
            }

            val total = dayTotals.sum()
            if (total <= 0L) return EMPTY

            // A day that starts before the oldest surviving event is only partly in the
            // log; averaging it in would understate the user's real day.
            val withUsage = (0 until days).filter { dayTotals[it] > 0L }
            val averaged = withUsage.filter { boundaries[it] >= oldestEventMs }.ifEmpty { withUsage }

            val top = perApp.entries
                .filter { it.value >= MIN_APP_MS }
                .sortedByDescending { it.value }
                .take(TOP_APPS)
                .map { (pkg, ms) -> AppTotal(pkg, labelOf(pkg), ms) }

            return Report(
                dailyTotals = dayTotals.toList(),
                totalMs = total,
                topApps = top,
                measuredDays = averaged.size,
                measuredMs = averaged.sumOf { dayTotals[it] }
            )
        }
    }
}
