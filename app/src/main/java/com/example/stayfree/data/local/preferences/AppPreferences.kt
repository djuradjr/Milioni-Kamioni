package com.example.stayfree.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.stayfree.domain.score.AppCategory
import com.example.stayfree.util.TimeUtils
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
        // Per-content-target daily allowance: JSON map {targetId: minutes}.
        // 0 / missing = block immediately (the original hard-block behavior).
        val CONTENT_TARGET_LIMITS = stringPreferencesKey("content_target_limits_json")
        // Accumulated on-surface time per target for ONE effective day:
        // {"date":"yyyy-MM-dd","usage":{targetId: ms}}. Written by the a11y
        // service; a date mismatch on write/read resets the day transparently.
        val CONTENT_TARGET_USAGE = stringPreferencesKey("content_target_usage_json")
        // Screen-time goal for the dashboard "Daily goal" card, in minutes.
        val DAILY_GOAL_MINUTES = intPreferencesKey("daily_goal_minutes")
        const val DEFAULT_DAILY_GOAL_MINUTES = 240
        // Content blocks fired for ONE effective day: {"date":"yyyy-MM-dd","count":N}.
        // Same rolling-day reset as CONTENT_TARGET_USAGE.
        val CONTENT_BLOCK_COUNT = stringPreferencesKey("content_block_count_json")
        // Focus-score category overrides: JSON map {pkg: AppCategory.name}.
        // Only packages the user actually changed are stored; everything else
        // resolves through AppCategory.defaultFor so the seed list can evolve.
        val APP_CATEGORIES = stringPreferencesKey("app_categories_json")
        // Raises to a limit are parked here until their date arrives:
        // {id: {"minutes": N, "from": "yyyy-MM-dd"}}. Lowering never parks.
        val CONTENT_TARGET_LIMITS_PENDING = stringPreferencesKey("content_target_limits_pending_json")
        val BLOCK_APP_LIMITS_PENDING = stringPreferencesKey("block_app_limits_pending_json")
        const val PENDING_MINUTES = "minutes"
        const val PENDING_FROM = "from"
    }

    val dailyResetTimeMinutes: Flow<Int> = dataStore.data.map { it[DAILY_RESET_TIME_MINUTES] ?: 0 }
    // Skor is designed dark-first; light is a translation of it, not the default.
    val appearanceMode: Flow<String> = dataStore.data.map { it[APPEARANCE_MODE] ?: APPEARANCE_DARK }
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
    val blockAppLimitsMinutes: Flow<Map<String, Int>> = dataStore.data.map {
        parseLimits(it[BLOCK_APP_LIMITS]) + duePending(it[BLOCK_APP_LIMITS_PENDING])
    }
    /** Daily allowance in minutes per content target (0 = block immediately). */
    val contentTargetLimitsMinutes: Flow<Map<String, Int>> = dataStore.data.map {
        parseLimits(it[CONTENT_TARGET_LIMITS]) + duePending(it[CONTENT_TARGET_LIMITS_PENDING])
    }
    /** Only the packages the user re-categorised; resolve through [categoryOf]. */
    val appCategoryOverrides: Flow<Map<String, AppCategory>> = dataStore.data.map { prefs ->
        val json = prefs[APP_CATEGORIES] ?: return@map emptyMap()
        try {
            val obj = JSONObject(json)
            obj.keys().asSequence().mapNotNull { key ->
                runCatching { key to AppCategory.valueOf(obj.getString(key)) }.getOrNull()
            }.toMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    val dailyGoalMinutes: Flow<Int> =
        dataStore.data.map { it[DAILY_GOAL_MINUTES] ?: DEFAULT_DAILY_GOAL_MINUTES }
    /** (effectiveDate, count) of content blocks fired that day. */
    val contentBlockCount: Flow<Pair<String, Int>> = dataStore.data.map {
        val json = it[CONTENT_BLOCK_COUNT] ?: return@map "" to 0
        try {
            val obj = JSONObject(json)
            obj.optString("date") to obj.optInt("count")
        } catch (e: Exception) {
            "" to 0
        }
    }

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
        applyLimit(BLOCK_APP_LIMITS, BLOCK_APP_LIMITS_PENDING, packageName, minutes)
    }

    /** What a raise is waiting on, per key: (minutes, effective date). */
    val blockAppLimitsPending: Flow<Map<String, Pair<Int, String>>> =
        dataStore.data.map { parsePending(it[BLOCK_APP_LIMITS_PENDING]) }

    val contentTargetLimitsPending: Flow<Map<String, Pair<Int, String>>> =
        dataStore.data.map { parsePending(it[CONTENT_TARGET_LIMITS_PENDING]) }

    /** User overrides of the focus-score category: JSON map {pkg: AppCategory.name}. */
    suspend fun setAppCategory(packageName: String, category: AppCategory) {
        dataStore.edit { prefs ->
            val obj = try {
                JSONObject(prefs[APP_CATEGORIES] ?: "{}")
            } catch (e: Exception) {
                JSONObject()
            }
            if (category == AppCategory.defaultFor(packageName)) {
                obj.remove(packageName)
            } else {
                obj.put(packageName, category.name)
            }
            prefs[APP_CATEGORIES] = obj.toString()
        }
    }

    suspend fun setContentTargetLimitMinutes(id: String, minutes: Int) {
        applyLimit(CONTENT_TARGET_LIMITS, CONTENT_TARGET_LIMITS_PENDING, id, minutes)
    }

    /**
     * Tightening protection is immediate; loosening it waits until tomorrow.
     *
     * Without this, a limit is only a suggestion: the moment it bites, the user
     * raises it and the app stops meaning anything. The delay costs nothing to
     * someone who genuinely wants more time tomorrow, and everything to the
     * impulse that wants it right now.
     *
     * A raise that is still parked is replaced, not stacked — the newest wins.
     */
    private suspend fun applyLimit(
        activeKey: Preferences.Key<String>,
        pendingKey: Preferences.Key<String>,
        id: String,
        minutes: Int
    ) {
        val target = minutes.coerceAtLeast(0)
        dataStore.edit { prefs ->
            val active = JSONObject(prefs[activeKey] ?: "{}")
            val pending = JSONObject(prefs[pendingKey] ?: "{}")

            // Promote anything already due, so "current" means what the user sees.
            duePending(prefs[pendingKey]).forEach { (key, value) ->
                active.put(key, value)
                pending.remove(key)
            }

            val current = if (active.has(id)) active.optInt(id) else null
            if (current == null || target <= current) {
                active.put(id, target)
                pending.remove(id)
            } else {
                pending.put(
                    id,
                    JSONObject()
                        .put(PENDING_MINUTES, target)
                        .put(PENDING_FROM, TimeUtils.addDays(TimeUtils.getTodayString(), 1))
                )
            }
            prefs[activeKey] = active.toString()
            prefs[pendingKey] = pending.toString()
        }
    }

    /**
     * Adds [deltaMs] to the target's on-surface time for the effective day [date]
     * and returns the new total. A stored different date means the day rolled
     * over — the whole map is dropped before adding (the daily reset).
     */
    suspend fun addContentTargetUsage(id: String, date: String, deltaMs: Long): Long {
        var newTotal = 0L
        dataStore.edit { prefs ->
            val obj = try {
                JSONObject(prefs[CONTENT_TARGET_USAGE] ?: "{}")
            } catch (e: Exception) {
                JSONObject()
            }
            val usage = if (obj.optString("date") == date) {
                obj.optJSONObject("usage") ?: JSONObject()
            } else JSONObject()
            newTotal = usage.optLong(id) + deltaMs.coerceAtLeast(0L)
            usage.put(id, newTotal)
            prefs[CONTENT_TARGET_USAGE] = JSONObject().put("date", date).put("usage", usage).toString()
        }
        return newTotal
    }

    suspend fun setDailyGoalMinutes(minutes: Int) {
        dataStore.edit { it[DAILY_GOAL_MINUTES] = minutes.coerceAtLeast(1) }
    }

    /** Bumps today's content-block counter; a stored different date resets it first. */
    suspend fun incrementContentBlockCount(date: String) {
        dataStore.edit { prefs ->
            val current = try {
                val obj = JSONObject(prefs[CONTENT_BLOCK_COUNT] ?: "{}")
                if (obj.optString("date") == date) obj.optInt("count") else 0
            } catch (e: Exception) {
                0
            }
            prefs[CONTENT_BLOCK_COUNT] =
                JSONObject().put("date", date).put("count", current + 1).toString()
        }
    }

    suspend fun getContentTargetUsageMs(id: String, date: String): Long {
        val json = dataStore.data.first()[CONTENT_TARGET_USAGE] ?: return 0L
        return try {
            val obj = JSONObject(json)
            if (obj.optString("date") != date) 0L
            else obj.optJSONObject("usage")?.optLong(id) ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    /** Parked raises whose date has arrived, ready to be merged over the active map. */
    private fun duePending(json: String?): Map<String, Int> {
        val today = TimeUtils.getTodayString()
        return parsePending(json)
            .filterValues { (_, from) -> from <= today }
            .mapValues { (_, value) -> value.first }
    }

    private fun parsePending(json: String?): Map<String, Pair<Int, String>> {
        if (json.isNullOrEmpty()) return emptyMap()
        return try {
            val obj = JSONObject(json)
            obj.keys().asSequence().mapNotNull { key ->
                val entry = obj.optJSONObject(key) ?: return@mapNotNull null
                val from = entry.optString(PENDING_FROM)
                if (from.isEmpty()) null
                else key to (entry.optInt(PENDING_MINUTES) to from)
            }.toMap()
        } catch (e: Exception) {
            emptyMap()
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
