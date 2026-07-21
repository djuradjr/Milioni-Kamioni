package com.example.stayfree.ui.blocking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stayfree.data.local.entity.BlockRuleEntity
import com.example.stayfree.data.local.preferences.AppPreferences
import com.example.stayfree.data.repository.BlockingRepository
import com.example.stayfree.data.repository.WebsiteBlockRepository
import com.example.stayfree.domain.BlockRuleEvaluator
import com.example.stayfree.domain.content.ContentSignatures
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One row in the "App block" list: a classic rule or any other switched-on block. */
sealed class ActiveBlockItem {
    abstract val key: String

    data class Rule(val rule: BlockRuleEntity) : ActiveBlockItem() {
        override val key get() = "rule:${rule.id}"
    }

    /** Whole-app block from the Block Apps screen (slider ON). */
    data class App(val packageName: String, val limitMinutes: Int) : ActiveBlockItem() {
        override val key get() = "app:$packageName"
    }

    /** Enabled content target (Reels/Shorts/Stories/TikTok). */
    data class Content(
        val targetId: String,
        val displayName: String,
        val packageName: String,
        val limitMinutes: Int
    ) : ActiveBlockItem() {
        override val key get() = "content:$targetId"
    }

    /** Active website block. */
    data class Site(val id: Long, val domain: String, val dailyCapMs: Long?) : ActiveBlockItem() {
        override val key get() = "site:$id"
    }
}

@HiltViewModel
class BlockingViewModel @Inject constructor(
    private val blockingRepository: BlockingRepository,
    websiteBlockRepository: WebsiteBlockRepository,
    prefs: AppPreferences
) : ViewModel() {

    private val allRules: StateFlow<List<BlockRuleEntity>> = blockingRepository.getAllRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val appBlocks = combine(
        prefs.blockAppsEnabledPkgs, prefs.blockAppLimitsMinutes
    ) { pkgs, limits ->
        pkgs.map { it to (limits[it] ?: AppPreferences.DEFAULT_BLOCK_APP_LIMIT_MINUTES) }
    }

    private val contentBlocks = combine(
        prefs.contentBlockEnabledIds, prefs.contentTargetLimitsMinutes
    ) { enabled, limits ->
        ContentSignatures.ALL.filter { it.id in enabled }
            .map { ActiveBlockItem.Content(it.id, it.displayName, it.packageName, limits[it.id] ?: 0) }
    }

    /** Every currently-ON block: whole-app sliders, enabled content targets,
     *  active site blocks and active classic rules — only what's actually
     *  blocking right now (the screen is a live "what am I blocking" list). */
    val activeBlocks: StateFlow<List<ActiveBlockItem>> = combine(
        allRules,
        appBlocks,
        contentBlocks,
        websiteBlockRepository.getAll()
    ) { rules, apps, content, sites ->
        buildList {
            apps.sortedBy { it.first }.forEach { (pkg, limit) -> add(ActiveBlockItem.App(pkg, limit)) }
            addAll(content)
            sites.filter { it.isActive }.forEach { add(ActiveBlockItem.Site(it.id, it.domain, it.dailyCapMs)) }
            rules.filter { it.isActive }.forEach { add(ActiveBlockItem.Rule(it)) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
