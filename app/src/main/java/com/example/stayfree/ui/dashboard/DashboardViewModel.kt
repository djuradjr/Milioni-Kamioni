package com.example.stayfree.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stayfree.data.local.preferences.AppPreferences
import com.example.stayfree.data.repository.BlockingRepository
import com.example.stayfree.data.repository.UsageRepository
import com.example.stayfree.domain.model.AppUsage
import com.example.stayfree.util.TimeUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class StatsPeriod { DAILY, WEEKLY, MONTHLY }

/** Current vs previous period totals for the trend badge. */
data class PeriodComparison(val currentMs: Long, val previousMs: Long)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val usageRepository: UsageRepository,
    private val blockingRepository: BlockingRepository,
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

    val activeBlockCount: StateFlow<Int> = blockingRepository.getActiveRules()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

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

    /** Average daily screen time over the period (total / days) — the hero number
     *  for Weekly/Monthly, where a raw sum would read as misleadingly huge. */
    val periodAverageScreenTime: StateFlow<Long> = _period.flatMapLatest { period ->
        val days = periodDays(period)
        usageRepository.getTotalScreenTimeBetween(currentFrom(period), TimeUtils.getTodayString())
            .map { it / days }
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
