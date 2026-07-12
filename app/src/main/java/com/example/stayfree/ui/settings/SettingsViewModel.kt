package com.example.stayfree.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stayfree.data.local.preferences.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: AppPreferences
) : ViewModel() {

    val pinEnabled: StateFlow<Boolean> = prefs.pinEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val dailyResetTime: StateFlow<Int> = prefs.dailyResetTimeMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val appearanceMode: StateFlow<String> = prefs.appearanceMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppPreferences.APPEARANCE_LIGHT)

    val profileUsername: StateFlow<String> = prefs.profileUsername
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val profileEmail: StateFlow<String> = prefs.profileEmail
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val notificationsMaster: StateFlow<Boolean> = prefs.notificationsMaster
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val notifBeforeTimeout: StateFlow<Boolean> = prefs.notifBeforeTimeout
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val notifDailySummary: StateFlow<Boolean> = prefs.notifDailySummary
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setDailyResetTime(minutes: Int) {
        viewModelScope.launch { prefs.setDailyResetTime(minutes) }
    }

    fun setAppearanceMode(mode: String) {
        viewModelScope.launch { prefs.setAppearanceMode(mode) }
    }

    fun setProfile(username: String, email: String) {
        viewModelScope.launch { prefs.setProfile(username, email) }
    }

    fun clearPin() {
        viewModelScope.launch { prefs.clearPin() }
    }

    fun setNotificationsMaster(enabled: Boolean) {
        viewModelScope.launch { prefs.setNotificationsMaster(enabled) }
    }

    fun setNotifBeforeTimeout(enabled: Boolean) {
        viewModelScope.launch { prefs.setNotifBeforeTimeout(enabled) }
    }

    fun setNotifDailySummary(enabled: Boolean) {
        viewModelScope.launch { prefs.setNotifDailySummary(enabled) }
    }
}
