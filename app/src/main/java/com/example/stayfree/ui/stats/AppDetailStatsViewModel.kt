package com.example.stayfree.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stayfree.data.repository.UsageRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import com.example.stayfree.domain.score.AppCategory
import com.example.stayfree.data.local.preferences.AppPreferences
import com.example.stayfree.domain.model.AppUsage
import com.example.stayfree.util.TimeUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

/** Matches FocusScore's inside-the-goal rate; the detail view shows the common case. */
private const val POINTS_PER_MINUTE = 0.30
private const val WEEK_DAYS = 7

@HiltViewModel
class AppDetailStatsViewModel @Inject constructor(
    private val usageRepository: UsageRepository,
    private val prefs: AppPreferences
) : ViewModel() {

    private val _packageName = MutableStateFlow("")

    val todayUsage: StateFlow<Long> = _packageName.flatMapLatest { pkg ->
        if (pkg.isEmpty()) flowOf(0L)
        else usageRepository.getScreenTimeForPackageOnDate(pkg, TimeUtils.getTodayString())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val todayUnlocks: StateFlow<Int> = _packageName.flatMapLatest { pkg ->
        if (pkg.isEmpty()) flowOf(0)
        else usageRepository.getUnlocksForPackageOnDate(pkg, TimeUtils.getTodayString())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val weeklyUsage: StateFlow<List<AppUsage>> = _packageName.flatMapLatest { pkg ->
        if (pkg.isEmpty()) flowOf(emptyList())
        else usageRepository.getUsageForPackage(pkg, TimeUtils.getDateStringDaysAgo(7))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Per-day minutes over the last week, oldest→today, zero-filled. Without the
     * fill, two days of data render as two bars filling the whole chart, which
     * reads as "every day is like this".
     */
    val weeklyMinutes: StateFlow<List<Float>> = weeklyUsage.map { rows ->
        val byDate = rows.groupBy { it.date }
            .mapValues { (_, day) -> day.sumOf { it.totalTimeMs } }
        (WEEK_DAYS - 1 downTo 0).map { ago ->
            (byDate[TimeUtils.getDateStringDaysAgo(ago)] ?: 0L) / 60_000f
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), List(WEEK_DAYS) { 0f })

    /** This app's category: the user's override, else the seed guess. */
    val category: StateFlow<AppCategory> = combine(
        _packageName, prefs.appCategoryOverrides
    ) { pkg, overrides ->
        if (pkg.isEmpty()) AppCategory.NEUTRAL
        else overrides[pkg] ?: AppCategory.defaultFor(pkg)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppCategory.NEUTRAL)

    /**
     * Points this app took off today's score. Only DISTRACTION costs anything,
     * which is exactly what makes the category worth changing.
     */
    val scoreImpact: StateFlow<Double> = combine(todayUsage, category) { ms, cat ->
        if (cat == AppCategory.DISTRACTION) ms / 60_000.0 * POINTS_PER_MINUTE else 0.0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    /** Distraction → Neutral → Productive → Distraction. */
    fun cycleCategory() {
        val pkg = _packageName.value
        if (pkg.isEmpty()) return
        val next = when (category.value) {
            AppCategory.DISTRACTION -> AppCategory.NEUTRAL
            AppCategory.NEUTRAL -> AppCategory.PRODUCTIVE
            AppCategory.PRODUCTIVE -> AppCategory.DISTRACTION
        }
        viewModelScope.launch { prefs.setAppCategory(pkg, next) }
    }

    fun loadApp(packageName: String) {
        _packageName.value = packageName
    }
}
