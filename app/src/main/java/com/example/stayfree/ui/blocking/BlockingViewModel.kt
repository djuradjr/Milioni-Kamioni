package com.example.stayfree.ui.blocking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stayfree.data.local.entity.BlockRuleEntity
import com.example.stayfree.data.local.preferences.AppPreferences
import com.example.stayfree.data.repository.BlockingRepository
import com.example.stayfree.data.repository.WebsiteBlockRepository
import com.example.stayfree.domain.BlockRuleEvaluator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BlockingViewModel @Inject constructor(
    private val blockingRepository: BlockingRepository,
    websiteBlockRepository: WebsiteBlockRepository,
    prefs: AppPreferences
) : ViewModel() {

    val allRules: StateFlow<List<BlockRuleEntity>> = blockingRepository.getAllRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Live tool-card statuses
    val focusActive: StateFlow<Boolean> = prefs.focusActive
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** End of the active sleep window in minutes from midnight, or null when off. */
    val sleepEndMinutes: StateFlow<Int?> = allRules
        .map { rules ->
            rules.firstOrNull {
                it.packageName == BlockRuleEvaluator.SLEEP_MODE_PACKAGE && it.isActive
            }?.id
        }
        .distinctUntilChanged()
        .map { id -> id?.let { blockingRepository.getSchedulesForRule(it).firstOrNull()?.endTimeMinutes } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val activeWebsiteCount: StateFlow<Int> = websiteBlockRepository.getAll()
        .map { sites -> sites.count { it.isActive } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val contentTargetCount: StateFlow<Int> = prefs.contentBlockEnabledIds
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun toggleRule(id: Long, active: Boolean) {
        viewModelScope.launch { blockingRepository.setRuleActive(id, active) }
    }

    fun deleteRule(id: Long) {
        viewModelScope.launch { blockingRepository.deleteRule(id) }
    }
}
