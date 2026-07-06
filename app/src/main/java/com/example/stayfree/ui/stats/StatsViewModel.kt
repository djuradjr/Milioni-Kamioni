package com.example.stayfree.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stayfree.data.local.preferences.AppPreferences
import com.example.stayfree.data.repository.UsageRepository
import com.example.stayfree.domain.model.AppUsage
import com.example.stayfree.util.TimeUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

enum class StatsPeriod { DAILY, WEEKLY, MONTHLY }

/** Current vs previous period totals for the trend badge. */
data class PeriodComparison(val currentMs: Long, val previousMs: Long)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val usageRepository: UsageRepository,
    private val prefs: AppPreferences
) : ViewModel() {

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
     * Per-app usage summed over the whole period. Multi-day periods aggregate
     * the per-day rows by package (the single-date query would otherwise show
     * just one old day for Weekly/Monthly).
     */
    val appUsageList: StateFlow<List<AppUsage>> = _period.flatMapLatest { period ->
        if (period == StatsPeriod.DAILY) {
            usageRepository.getUsageForDate(TimeUtils.getTodayString())
        } else {
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
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalScreenTime: StateFlow<Long> = _period.flatMapLatest { period ->
        usageRepository.getTotalScreenTimeBetween(currentFrom(period), TimeUtils.getTodayString())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val totalUnlocks: StateFlow<Int> = _period.flatMapLatest { period ->
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
}
