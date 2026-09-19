package com.example.stayfree.ui.premium

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stayfree.data.billing.PremiumPlan
import com.example.stayfree.data.billing.PremiumRepository
import com.example.stayfree.data.local.preferences.AppPreferences
import com.example.stayfree.domain.onboarding.UsageHistory
import com.example.stayfree.domain.score.AppCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PaywallViewModel @Inject constructor(
    private val premium: PremiumRepository,
    private val usageHistory: UsageHistory,
    private val prefs: AppPreferences
) : ViewModel() {

    sealed interface Plans {
        data object Loading : Plans
        data object Unavailable : Plans
        data class Ready(val plans: List<PremiumPlan>) : Plans
    }

    private val _plans = MutableStateFlow<Plans>(Plans.Loading)
    val plans: StateFlow<Plans> = _plans

    // Plans are sorted shortest first, so index 0 is the monthly plan: nothing pricier is
    // pre-selected for the user.
    private val _selected = MutableStateFlow(0)
    val selected: StateFlow<Int> = _selected

    private val _distractionMs = MutableStateFlow<Long?>(null)
    /** Last week's time in distracting apps; null when there is nothing worth showing. */
    val distractionMs: StateFlow<Long?> = _distractionMs

    val purchasePending: StateFlow<Boolean> = premium.purchasePending
    val premiumActive: Flow<Boolean> = prefs.premiumActive

    init {
        loadPlans()
        viewModelScope.launch {
            val overrides = prefs.appCategoryOverrides.first()
            _distractionMs.value = usageHistory.lastWeek().topApps
                .filter { (overrides[it.packageName] ?: AppCategory.defaultFor(it.packageName)) == AppCategory.DISTRACTION }
                .sumOf { it.totalMs }
                .takeIf { it >= MIN_STAT_MS }
        }
    }

    fun loadPlans() {
        viewModelScope.launch {
            _plans.value = Plans.Loading
            _plans.value = premium.loadPlans()?.let { Plans.Ready(it) } ?: Plans.Unavailable
        }
    }

    fun select(index: Int) {
        _selected.value = index
    }

    fun selectedPlan(): PremiumPlan? =
        (_plans.value as? Plans.Ready)?.plans?.getOrNull(_selected.value)

    suspend fun restore(): Boolean = premium.restore()

    private companion object {
        const val MIN_STAT_MS = 15 * 60_000L
    }
}
