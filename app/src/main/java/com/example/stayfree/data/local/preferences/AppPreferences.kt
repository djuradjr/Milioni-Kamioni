package com.example.stayfree.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject
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
        // "light" | "dark" | "system" — the UI exposes only light/dark; "system"
        // is reserved so a future "follow system" option needs no migration.
        val APPEARANCE_MODE = stringPreferencesKey("appearance_mode")
        const val APPEARANCE_LIGHT = "light"
        const val APPEARANCE_DARK = "dark"
        const val APPEARANCE_SYSTEM = "system"
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_ENABLED = booleanPreferencesKey("pin_enabled")
        val PIN_FAILED_ATTEMPTS = intPreferencesKey("pin_failed_attempts")
        val PIN_LOCKOUT_UNTIL = longPreferencesKey("pin_lockout_until")
        // Local-only profile — never leaves the device (no INTERNET permission).
        val PROFILE_USERNAME = stringPreferencesKey("profile_username")
        val PROFILE_EMAIL = stringPreferencesKey("profile_email")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        // Master ON by default — actual delivery is additionally gated by the
        // POST_NOTIFICATIONS permission and the per-type child flag.
        val NOTIFICATIONS_MASTER = booleanPreferencesKey("notifications_master_enabled")
        val NOTIF_BEFORE_TIMEOUT = booleanPreferencesKey("notif_before_timeout")
        val NOTIF_DAILY_SUMMARY = booleanPreferencesKey("notif_daily_summary")
        val DASHBOARD_CARD_ORDER = stringPreferencesKey("dashboard_card_order")
        val FOCUS_ACTIVE = booleanPreferencesKey("focus_active")
        val FOCUS_END_TIME = longPreferencesKey("focus_end_time")
        val FOCUS_IS_WHITELIST = booleanPreferencesKey("focus_is_whitelist")
        val ACCESSIBILITY_DISCLOSURE_ACCEPTED = booleanPreferencesKey("accessibility_disclosure_accepted")
        val CONTENT_BLOCK_ENABLED = stringSetPreferencesKey("content_block_enabled_ids")
        // Whole-app block toggles (Block Apps screen). Stores package names whose
        // slider is ON. Enforcement is added later; for now this only persists UI state.
        val BLOCK_APPS_ENABLED = stringSetPreferencesKey("block_apps_enabled_pkgs")
        // Per-app daily allowance for whole-app blocks: JSON map {pkg: minutes}.
        // 0 minutes = block immediately; missing entry = DEFAULT_BLOCK_APP_LIMIT_MINUTES.
        val BLOCK_APP_LIMITS = stringPreferencesKey("block_app_limits_json")
        const val DEFAULT_BLOCK_APP_LIMIT_MINUTES = 30
    }

    val dailyResetTimeMinutes: Flow<Int> = dataStore.data.map { it[DAILY_RESET_TIME_MINUTES] ?: 0 }
    val appearanceMode: Flow<String> = dataStore.data.map { it[APPEARANCE_MODE] ?: APPEARANCE_LIGHT }
    val pinHash: Flow<String?> = dataStore.data.map { it[PIN_HASH] }
    val pinEnabled: Flow<Boolean> = dataStore.data.map { it[PIN_ENABLED] ?: false }
    val pinLockoutUntil: Flow<Long> = dataStore.data.map { it[PIN_LOCKOUT_UNTIL] ?: 0L }
    val profileUsername: Flow<String> = dataStore.data.map { it[PROFILE_USERNAME] ?: "" }
    val profileEmail: Flow<String> = dataStore.data.map { it[PROFILE_EMAIL] ?: "" }
    val onboardingComplete: Flow<Boolean> = dataStore.data.map { it[ONBOARDING_COMPLETE] ?: false }
    val notificationsMaster: Flow<Boolean> = dataStore.data.map { it[NOTIFICATIONS_MASTER] ?: true }
    val notifBeforeTimeout: Flow<Boolean> = dataStore.data.map { it[NOTIF_BEFORE_TIMEOUT] ?: true }
    val notifDailySummary: Flow<Boolean> = dataStore.data.map { it[NOTIF_DAILY_SUMMARY] ?: true }
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
    /** Daily allowance in minutes per blocked app (0 = block immediately). */
    val blockAppLimitsMinutes: Flow<Map<String, Int>> =
        dataStore.data.map { parseLimits(it[BLOCK_APP_LIMITS]) }

    suspend fun setDailyResetTime(minutes: Int) {
        dataStore.edit { it[DAILY_RESET_TIME_MINUTES] = minutes }
    }

    suspend fun setAppearanceMode(mode: String) {
        dataStore.edit { it[APPEARANCE_MODE] = mode }
    }

    suspend fun setPinHash(hash: String?) {
        dataStore.edit {
            if (hash != null) it[PIN_HASH] = hash else it.remove(PIN_HASH)
        }
    }

    suspend fun setPinEnabled(enabled: Boolean) {
        dataStore.edit { it[PIN_ENABLED] = enabled }
    }

    suspend fun setPin(hash: String) {
        dataStore.edit {
            it[PIN_HASH] = hash
            it[PIN_ENABLED] = true
            it.remove(PIN_FAILED_ATTEMPTS)
            it.remove(PIN_LOCKOUT_UNTIL)
        }
    }

    suspend fun clearPin() {
        dataStore.edit {
            it.remove(PIN_HASH)
            it[PIN_ENABLED] = false
            it.remove(PIN_FAILED_ATTEMPTS)
            it.remove(PIN_LOCKOUT_UNTIL)
        }
    }

    /** Increments the failed-attempt counter and returns the new count. */
    suspend fun registerFailedPinAttempt(): Int {
        val updated = dataStore.edit {
            it[PIN_FAILED_ATTEMPTS] = (it[PIN_FAILED_ATTEMPTS] ?: 0) + 1
        }
        return updated[PIN_FAILED_ATTEMPTS] ?: 0
    }

    suspend fun resetPinAttempts() {
        dataStore.edit {
            it.remove(PIN_FAILED_ATTEMPTS)
            it.remove(PIN_LOCKOUT_UNTIL)
        }
    }

    suspend fun setPinLockout(untilMillis: Long) {
        dataStore.edit { it[PIN_LOCKOUT_UNTIL] = untilMillis }
    }

    suspend fun setProfile(username: String, email: String) {
        dataStore.edit {
            it[PROFILE_USERNAME] = username
            it[PROFILE_EMAIL] = email
        }
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        dataStore.edit { it[ONBOARDING_COMPLETE] = complete }
    }

    suspend fun setNotificationsMaster(enabled: Boolean) {
        dataStore.edit { it[NOTIFICATIONS_MASTER] = enabled }
    }

    suspend fun setNotifBeforeTimeout(enabled: Boolean) {
        dataStore.edit { it[NOTIF_BEFORE_TIMEOUT] = enabled }
    }

    suspend fun setNotifDailySummary(enabled: Boolean) {
        dataStore.edit { it[NOTIF_DAILY_SUMMARY] = enabled }
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

    suspend fun setBlockAppLimitMinutes(packageName: String, minutes: Int) {
        dataStore.edit { prefs ->
            val obj = try {
                JSONObject(prefs[BLOCK_APP_LIMITS] ?: "{}")
            } catch (e: Exception) {
                JSONObject()
            }
            obj.put(packageName, minutes.coerceAtLeast(0))
            prefs[BLOCK_APP_LIMITS] = obj.toString()
        }
    }

    private fun parseLimits(json: String?): Map<String, Int> {
        if (json.isNullOrEmpty()) return emptyMap()
        return try {
            val obj = JSONObject(json)
            buildMap {
                for (key in obj.keys()) put(key, obj.optInt(key))
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

}
