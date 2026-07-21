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
import kotlinx.coroutines.flow.MutableStateFlow
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

    private val query = MutableStateFlow("")

    private val allItems = combine(
        installedApps,
        prefs.blockAppsEnabledPkgs,
        prefs.blockAppLimitsMinutes,
        prefs.contentBlockEnabledIds,
        prefs.contentTargetLimitsMinutes
    ) { apps, enabled, limits, contentEnabled, contentLimits ->
        apps
            .sortedWith(compareBy({ blockPriority(it) }, { it.appName.lowercase() }))
            .map { app ->
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
    }

    /** null = still loading (installed-app query hasn't finished yet). */
    val items: StateFlow<List<BlockAppItem>?> = combine(allItems, query) { list, q ->
        if (q.isBlank()) list
        else list.filter { it.appName.contains(q.trim(), ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setQuery(q: String) {
        query.value = q
    }

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

    companion object {
        /** Apps people most often block, pinned to the top in this order;
         *  games follow, everything else stays alphabetical below. */
        private val PRIORITY_PKGS = listOf(
            "com.instagram.android",
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
            "com.google.android.youtube",
            "com.snapchat.android",
            "com.facebook.katana",
            "com.twitter.android",
            "com.reddit.frontpage",
            "com.facebook.orca",
            "com.whatsapp",
            "org.telegram.messenger",
            "com.discord",
            "com.pinterest",
            "tv.twitch.android.app",
            "com.netflix.mediaclient",
            "com.roblox.client"
        )

        private fun blockPriority(app: AppInfoUtils.InstalledApp): Int {
            val idx = PRIORITY_PKGS.indexOf(app.packageName)
            return when {
                idx >= 0 -> idx
                app.isGame -> PRIORITY_PKGS.size
                else -> PRIORITY_PKGS.size + 1
            }
        }
    }
}
