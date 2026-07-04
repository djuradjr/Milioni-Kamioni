package com.example.stayfree.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "stayfree_prefs")

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    companion object {
        val DAILY_RESET_TIME_MINUTES = intPreferencesKey("daily_reset_time_minutes")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_ENABLED = booleanPreferencesKey("pin_enabled")
        val SYNC_ENABLED = booleanPreferencesKey("sync_enabled")
        val USER_ID = stringPreferencesKey("user_id")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val DASHBOARD_CARD_ORDER = stringPreferencesKey("dashboard_card_order")
        val FOCUS_ACTIVE = booleanPreferencesKey("focus_active")
        val FOCUS_END_TIME = longPreferencesKey("focus_end_time")
        val FOCUS_IS_WHITELIST = booleanPreferencesKey("focus_is_whitelist")
        val ACCESSIBILITY_DISCLOSURE_ACCEPTED = booleanPreferencesKey("accessibility_disclosure_accepted")
        val CONTENT_BLOCK_ENABLED = stringSetPreferencesKey("content_block_enabled_ids")
        // Whole-app block toggles (Block Apps screen). Stores package names whose
        // slider is ON. Enforcement is added later; for now this only persists UI state.
        val BLOCK_APPS_ENABLED = stringSetPreferencesKey("block_apps_enabled_pkgs")
        // Rewarded unlock (reward-mode content like Instagram Stories)
        val CONTENT_UNLOCK_UNTIL = longPreferencesKey("content_unlock_until")
        val CONTENT_UNLOCKS_USED = intPreferencesKey("content_unlocks_used_today")
        val CONTENT_UNLOCK_DATE = stringPreferencesKey("content_unlock_date")
    }

    val dailyResetTimeMinutes: Flow<Int> = dataStore.data.map { it[DAILY_RESET_TIME_MINUTES] ?: 0 }
    val pinHash: Flow<String?> = dataStore.data.map { it[PIN_HASH] }
    val pinEnabled: Flow<Boolean> = dataStore.data.map { it[PIN_ENABLED] ?: false }
    val syncEnabled: Flow<Boolean> = dataStore.data.map { it[SYNC_ENABLED] ?: false }
    val userId: Flow<String?> = dataStore.data.map { it[USER_ID] }
    val onboardingComplete: Flow<Boolean> = dataStore.data.map { it[ONBOARDING_COMPLETE] ?: false }
    val dashboardCardOrder: Flow<String?> = dataStore.data.map { it[DASHBOARD_CARD_ORDER] }
    val focusActive: Flow<Boolean> = dataStore.data.map { it[FOCUS_ACTIVE] ?: false }
    val focusEndTime: Flow<Long> = dataStore.data.map { it[FOCUS_END_TIME] ?: 0L }
    val focusIsWhitelist: Flow<Boolean> = dataStore.data.map { it[FOCUS_IS_WHITELIST] ?: true }
    val accessibilityDisclosureAccepted: Flow<Boolean> =
        dataStore.data.map { it[ACCESSIBILITY_DISCLOSURE_ACCEPTED] ?: false }
    val contentBlockEnabledIds: Flow<Set<String>> =
        dataStore.data.map { it[CONTENT_BLOCK_ENABLED] ?: emptySet() }
    /** Package names whose whole-app block slider is ON (Block Apps screen). */
    val blockAppsEnabledPkgs: Flow<Set<String>> =
        dataStore.data.map { it[BLOCK_APPS_ENABLED] ?: emptySet() }
    /** Epoch ms until which reward-mode content is unlocked (0 = locked). */
    val contentUnlockUntil: Flow<Long> = dataStore.data.map { it[CONTENT_UNLOCK_UNTIL] ?: 0L }

    suspend fun setDailyResetTime(minutes: Int) {
        dataStore.edit { it[DAILY_RESET_TIME_MINUTES] = minutes }
    }

    suspend fun setPinHash(hash: String?) {
        dataStore.edit {
            if (hash != null) it[PIN_HASH] = hash else it.remove(PIN_HASH)
        }
    }

    suspend fun setPinEnabled(enabled: Boolean) {
        dataStore.edit { it[PIN_ENABLED] = enabled }
    }

    suspend fun setSyncEnabled(enabled: Boolean) {
        dataStore.edit { it[SYNC_ENABLED] = enabled }
    }

    suspend fun setUserId(uid: String?) {
        dataStore.edit {
            if (uid != null) it[USER_ID] = uid else it.remove(USER_ID)
        }
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        dataStore.edit { it[ONBOARDING_COMPLETE] = complete }
    }

    suspend fun setDashboardCardOrder(order: String) {
        dataStore.edit { it[DASHBOARD_CARD_ORDER] = order }
    }

    suspend fun setFocusState(active: Boolean, endTime: Long, isWhitelist: Boolean) {
        dataStore.edit {
            it[FOCUS_ACTIVE] = active
            it[FOCUS_END_TIME] = endTime
            it[FOCUS_IS_WHITELIST] = isWhitelist
        }
    }

    suspend fun setAccessibilityDisclosureAccepted(accepted: Boolean) {
        dataStore.edit { it[ACCESSIBILITY_DISCLOSURE_ACCEPTED] = accepted }
    }

    suspend fun setContentBlockEnabled(id: String, enabled: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[CONTENT_BLOCK_ENABLED] ?: emptySet()
            prefs[CONTENT_BLOCK_ENABLED] = if (enabled) current + id else current - id
        }
    }

    suspend fun setBlockAppEnabled(packageName: String, enabled: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[BLOCK_APPS_ENABLED] ?: emptySet()
            prefs[BLOCK_APPS_ENABLED] = if (enabled) current + packageName else current - packageName
        }
    }

    /** Unlocks used so far for [today] (resets implicitly when the date changes). */
    suspend fun unlocksUsedToday(today: String): Int {
        val prefs = dataStore.data.first()
        return if (prefs[CONTENT_UNLOCK_DATE] == today) (prefs[CONTENT_UNLOCKS_USED] ?: 0) else 0
    }

    /**
     * Grants a timed reward unlock if the daily cap hasn't been reached.
     * @return true if granted, false when [dailyCap] is already used up.
     */
    suspend fun grantContentUnlock(durationMs: Long, dailyCap: Int, today: String): Boolean {
        val current = dataStore.data.first()
        val used = if (current[CONTENT_UNLOCK_DATE] == today) (current[CONTENT_UNLOCKS_USED] ?: 0) else 0
        if (used >= dailyCap) return false
        dataStore.edit {
            it[CONTENT_UNLOCK_UNTIL] = System.currentTimeMillis() + durationMs
            it[CONTENT_UNLOCKS_USED] = used + 1
            it[CONTENT_UNLOCK_DATE] = today
        }
        return true
    }
}
