package com.example.stayfree.ui.blocking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stayfree.data.local.entity.BlockRuleEntity
import com.example.stayfree.data.local.preferences.AppPreferences
import com.example.stayfree.data.repository.BlockingRepository
import com.example.stayfree.data.repository.WebsiteBlockRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    // Live hub-card statuses
    val focusActive: StateFlow<Boolean> = prefs.focusActive
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val sleepConfigured: StateFlow<Boolean> = allRules
        .map { rules -> rules.any { it.packageName == "__sleep_mode__" && it.isActive } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val activeWebsiteCount: StateFlow<Int> = websiteBlockRepository.getAll()
        .map { sites -> sites.count { it.isActive } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val blockedAppCount: StateFlow<Int> = prefs.blockAppsEnabledPkgs
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun toggleRule(id: Long, active: Boolean) {
        viewModelScope.launch { blockingRepository.setRuleActive(id, active) }
    }

    fun deleteRule(id: Long) {
        viewModelScope.launch { blockingRepository.deleteRule(id) }
    }
}
