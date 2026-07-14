package com.example.stayfree.ui.blockapps

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stayfree.data.local.preferences.AppPreferences
import com.example.stayfree.domain.content.ContentSignatures
import com.example.stayfree.util.AppInfoUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One content-target row inside an expandable app row (data class for DiffUtil). */
data class ContentTargetRow(
    val id: String,
    val displayName: String,
    val enabled: Boolean,
    val limitMinutes: Int
)

/** One row on the Block Apps screen. Icon is loaded lazily in the adapter. */
data class BlockAppItem(
    val packageName: String,
    val appName: String,
    val isBlocked: Boolean,
    val limitMinutes: Int,
    val contentTargets: List<ContentTargetRow> = emptyList()
)

@HiltViewModel
class BlockAppsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: AppPreferences
) : ViewModel() {

    // Installed apps we're allowed to see (per manifest <queries>). Loaded once, off the main thread.
    private val installedApps = flow {
        emit(AppInfoUtils.getInstalledApps(context))
    }.flowOn(Dispatchers.IO)

    val items: StateFlow<List<BlockAppItem>> = combine(
        installedApps,
        prefs.blockAppsEnabledPkgs,
        prefs.blockAppLimitsMinutes,
        prefs.contentBlockEnabledIds,
        prefs.contentTargetLimitsMinutes
    ) { apps, enabled, limits, contentEnabled, contentLimits ->
        apps.map { app ->
            BlockAppItem(
                packageName = app.packageName,
                appName = app.appName,
                isBlocked = app.packageName in enabled,
                limitMinutes = limits[app.packageName]
                    ?: AppPreferences.DEFAULT_BLOCK_APP_LIMIT_MINUTES,
                contentTargets = ContentSignatures.allByPackage(app.packageName).map { target ->
                    ContentTargetRow(
                        id = target.id,
                        displayName = target.displayName,
                        enabled = target.id in contentEnabled,
                        limitMinutes = contentLimits[target.id] ?: 0
                    )
                }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setBlocked(packageName: String, blocked: Boolean) {
        viewModelScope.launch { prefs.setBlockAppEnabled(packageName, blocked) }
    }

    fun setLimit(packageName: String, minutes: Int) {
        viewModelScope.launch { prefs.setBlockAppLimitMinutes(packageName, minutes) }
    }

    fun setContentEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch { prefs.setContentBlockEnabled(id, enabled) }
    }

    fun setContentLimit(id: String, minutes: Int) {
        viewModelScope.launch { prefs.setContentTargetLimitMinutes(id, minutes) }
    }
}
