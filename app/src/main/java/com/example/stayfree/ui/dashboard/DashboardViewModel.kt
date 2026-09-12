package com.example.stayfree.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stayfree.data.local.preferences.AppPreferences
import com.example.stayfree.data.repository.UsageRepository
import com.example.stayfree.domain.model.AppUsage
import com.example.stayfree.domain.score.AppCategory
import com.example.stayfree.domain.score.FocusScore
import com.example.stayfree.util.TimeUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val AVERAGE_WINDOW_DAYS = 7

enum class StatsPeriod { DAILY, WEEKLY, MONTHLY }

/** Current vs previous period totals for the trend badge. */
data class PeriodComparison(val currentMs: Long, val previousMs: Long)

/** Daily-goal card state; [percent] is uncapped (113% = goal exceeded). */
data class GoalUi(val goalMinutes: Int, val percent: Int, val remainingMs: Long)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val usageRepository: UsageRepository,
    private val prefs: AppPreferences
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(TimeUtils.getTodayString())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    /** True while the selected date is today — drives the TODAY label and disables the forward arrow. */
    val isToday: StateFlow<Boolean> = selectedDate
        .map { it == TimeUtils.getTodayString() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val totalScreenTime: StateFlow<Long> = selectedDate
        .flatMapLatest { date -> usageRepository.getTotalScreenTimeForDate(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val totalUnlocks: StateFlow<Int> = selectedDate
        .flatMapLatest { date -> usageRepository.getTotalUnlocksForDate(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val topApps: StateFlow<List<AppUsage>> = selectedDate
        .flatMapLatest { date -> usageRepository.getUsageForDate(date) }
        .map { it.take(5) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val goalUi: StateFlow<GoalUi?> = combine(totalScreenTime, prefs.dailyGoalMinutes) { totalMs, raw ->
        val goalMin = raw.coerceAtLeast(1)
        val goalMs = goalMin * 60_000L
        GoalUi(
            goalMinutes = goalMin,
            percent = (totalMs * 100 / goalMs).toInt(),
            remainingMs = goalMs - totalMs
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Content blocks fired on the selected date (persisted for today only). */
    val interceptedCount: StateFlow<Int> =
        combine(prefs.contentBlockCount, selectedDate) { (date, count), selected ->
            if (date == selected) count else 0
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun setDailyGoal(minutes: Int) {
        viewModelScope.launch { prefs.setDailyGoalMinutes(minutes) }
    }

    /** Foreground ms per clock hour (24 buckets) for the hourly chart. */
    val hourlyUsage: StateFlow<List<Long>> = selectedDate
        .mapLatest { date -> usageRepository.getHourlyUsage(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), List(24) { 0L })

    /** Hour (0-23) with the most usage, or null when there is no usage at all. */
    val peakHour: StateFlow<Int?> = hourlyUsage
        .map { buckets ->
            val max = buckets.maxOrNull() ?: 0L
            if (max > 0L) buckets.indexOf(max) else null
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Every app for the selected date — the score weighs all of them, not just the top 5. */
    private val dailyUsage: StateFlow<List<AppUsage>> = selectedDate
        .flatMapLatest { date -> usageRepository.getUsageForDate(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** User's override wins; unknown packages fall back to the seed guess. */
    private val categoryResolver: StateFlow<(String) -> AppCategory> =
        prefs.appCategoryOverrides
            .map { overrides -> { pkg: String -> overrides[pkg] ?: AppCategory.defaultFor(pkg) } }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                { pkg: String -> AppCategory.defaultFor(pkg) }
            )

    /**
     * Unlocks per day over the previous week, today excluded — today is what we
     * are judging, so it must not move its own baseline. With no history this is
     * 0, and [FocusScore] then charges no unlock penalty at all.
     */
    private val averageUnlocks: StateFlow<Int> = usageRepository
        .getTotalUnlocksBetween(
            TimeUtils.getDateStringDaysAgo(AVERAGE_WINDOW_DAYS),
            TimeUtils.getDateStringDaysAgo(1)
        )
        .map { total -> total / AVERAGE_WINDOW_DAYS }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val scorePending = combine(
        dailyUsage,
        categoryResolver,
        prefs.dailyGoalMinutes,
        totalUnlocks,
        averageUnlocks
    ) { usage, categoryOf, goal, unlocks, avgUnlocks ->
        { intercepted: Int ->
            FocusScore.compute(usage, categoryOf, goal, unlocks, avgUnlocks, intercepted)
        }
    }

    /** Null until there is any usage at all — the first-day state shows a dash, never 0. */
    val focusScore: StateFlow<FocusScore.Breakdown?> =
        combine(scorePending, interceptedCount, dailyUsage) { compute, intercepted, usage ->
            if (usage.isEmpty()) null else compute(intercepted)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Distraction vs. everything else per clock hour — the two-colour bar chart. */
    val hourlyBreakdown: StateFlow<List<Pair<Long, Long>>> =
        combine(hourlyUsage, dailyUsage, categoryResolver) { hours, usage, categoryOf ->
            val total = usage.sumOf { it.totalTimeMs }.coerceAtLeast(1L)
            val distraction = usage
                .filter { categoryOf(it.packageName) == AppCategory.DISTRACTION }
                .sumOf { it.totalTimeMs }
            // Per-hour category split isn't recorded, so each hour is divided by
            // the day's own distraction share. Totals stay exact; single hours are
            // an estimate — which is why no hour is ever labelled as pure.
            val share = distraction.toDouble() / total
            hours.map { ms -> (ms * share).toLong() to (ms - (ms * share).toLong()) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _period = MutableStateFlow(StatsPeriod.DAILY)
    val period: StateFlow<StatsPeriod> = _period.asStateFlow()

    /** Number of days a period spans (today inclusive). */
    private fun periodDays(p: StatsPeriod) = when (p) {
        StatsPeriod.DAILY -> 1
        StatsPeriod.WEEKLY -> 7
        StatsPeriod.MONTHLY -> 30
    }

    private fun currentFrom(p: StatsPeriod) = TimeUtils.getDateStringDaysAgo(periodDays(p) - 1)

    /**
     * Per-app usage summed over the rolling period: per-day rows aggregated by
     * package (the single-date query would otherwise show just one old day).
     */
    val periodAppUsage: StateFlow<List<AppUsage>> = _period.flatMapLatest { period ->
        usageRepository.getUsageFromDate(currentFrom(period)).map { rows ->
            rows.groupBy { it.packageName }
                .map { (_, days) ->
                    days.first().copy(
                        totalTimeMs = days.sumOf { it.totalTimeMs },
                        unlockCount = days.sumOf { it.unlockCount },
                        screenOnCount = days.sumOf { it.screenOnCount }
                    )
                }
                .sortedByDescending { it.totalTimeMs }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val periodTotalScreenTime: StateFlow<Long> = _period.flatMapLatest { period ->
        usageRepository.getTotalScreenTimeBetween(currentFrom(period), TimeUtils.getTodayString())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    /** Per-day total screen time (ms) across the rolling window, oldest→today,
     *  zero-filled for days without data — the line chart series. */
    val periodDailyUsage: StateFlow<List<Long>> = _period.flatMapLatest { period ->
        usageRepository.getUsageFromDate(currentFrom(period)).map { rows ->
            val byDate = rows.groupBy { it.date }
                .mapValues { (_, dayRows) -> dayRows.sumOf { it.totalTimeMs } }
            (periodDays(period) - 1 downTo 0).map { ago ->
                byDate[TimeUtils.getDateStringDaysAgo(ago)] ?: 0L
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Average daily screen time — the hero number for Weekly/Monthly, where a raw
     * sum would read as misleadingly huge. Divides by the days that actually have
     * data, not by the nominal 7/30: a week-old install would otherwise report a
     * 30-day average of a third of the real number.
     */
    val periodAverageScreenTime: StateFlow<Long> = periodDailyUsage.map { daily ->
        val trackedDays = daily.count { it > 0L }
        if (trackedDays == 0) 0L else daily.sum() / trackedDays
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val periodTotalUnlocks: StateFlow<Int> = _period.flatMapLatest { period ->
        usageRepository.getTotalUnlocksBetween(currentFrom(period), TimeUtils.getTodayString())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Screen time of this period vs the equally long period right before it. */
    val screenTimeComparison: StateFlow<PeriodComparison?> = _period.flatMapLatest { period ->
        val days = periodDays(period)
        val prevFrom = TimeUtils.getDateStringDaysAgo(days * 2 - 1)
        val prevTo = TimeUtils.getDateStringDaysAgo(days)
        combine(
            usageRepository.getTotalScreenTimeBetween(currentFrom(period), TimeUtils.getTodayString()),
            usageRepository.getTotalScreenTimeBetween(prevFrom, prevTo)
        ) { current, previous -> PeriodComparison(current, previous) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setPeriod(p: StatsPeriod) { _period.value = p }

    fun selectDate(date: String) {
        _selectedDate.value = date
    }

    fun goToPreviousDay() {
        _selectedDate.value = TimeUtils.addDays(_selectedDate.value, -1)
    }

    fun goToNextDay() {
        if (_selectedDate.value == TimeUtils.getTodayString()) return
        _selectedDate.value = TimeUtils.addDays(_selectedDate.value, 1)
    }

    fun selectToday() {
        viewModelScope.launch {
            val resetTime = prefs.dailyResetTimeMinutes.first()
            _selectedDate.value = TimeUtils.getEffectiveDate(resetTime)
        }
    }
}
