package com.example.stayfree.ui.inapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stayfree.data.local.entity.InAppBlockEntity
import com.example.stayfree.data.local.preferences.AppPreferences
import com.example.stayfree.data.repository.InAppBlockRepository
import com.example.stayfree.domain.content.ContentSignatures
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InAppBlockViewModel @Inject constructor(
    private val repository: InAppBlockRepository,
    private val prefs: AppPreferences
) : ViewModel() {

    val allTargets: StateFlow<List<InAppBlockEntity>> = repository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val contentBlockEnabledIds: StateFlow<Set<String>> = prefs.contentBlockEnabledIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun setContentEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch { prefs.setContentBlockEnabled(id, enabled) }
    }

    fun initDefaultTargets() {
        viewModelScope.launch {
            // Detection targets the on-screen *player container* (matched by a
            // substring of its resource-id) rather than the always-present bottom
            // nav tab — otherwise the whole app would be blocked from its home
            // screen. resource-ids are app-version specific, so each target lists
            // several historical candidates via "anyOf".
            // Instagram Reels + YouTube Shorts moved to the overlay-based content
            // blocker (Phase F); remove any leftover rows so they aren't double-blocked.
            repository.getAllOnce()
                .filter { it.targetApp in ContentSignatures.targetPackages }
                .forEach { repository.deleteById(it.id) }

            val defaults = listOf(
                "com.snapchat.android" to ("Snapchat Spotlight" to
                    """{"type":"anyOf","strategies":[{"type":"viewIdContains","value":"spotlight"},{"type":"viewIdContains","value":"discover_feed"}]}"""),
                "com.facebook.katana" to ("Facebook Reels" to
                    """{"type":"viewIdContains","value":"reels"}"""),
                "com.twitter.android" to ("Twitter Trending" to
                    """{"type":"anyOf","strategies":[{"type":"viewIdContains","value":"explore"},{"type":"contentDescription","value":"Trending"}]}""")
            )

            val existing = repository.getAllOnce()
            for ((targetApp, feature) in defaults) {
                val (featureName, strategy) = feature
                val matches = existing.filter { it.targetApp == targetApp && it.featureName == featureName }
                if (matches.isEmpty()) {
                    repository.insert(
                        InAppBlockEntity(targetApp = targetApp, featureName = featureName, detectionStrategy = strategy)
                    )
                } else {
                    // Keep one row (prefer an already-enabled one), refresh its
                    // detection strategy, and drop any duplicates from older builds.
                    val keep = matches.firstOrNull { it.isActive } ?: matches.first()
                    repository.updateStrategy(keep.id, strategy)
                    matches.filter { it.id != keep.id }.forEach { repository.deleteById(it.id) }
                }
            }
        }
    }

    fun toggleTarget(entity: InAppBlockEntity) {
        viewModelScope.launch {
            repository.setActive(entity.id, !entity.isActive)
        }
    }
}
