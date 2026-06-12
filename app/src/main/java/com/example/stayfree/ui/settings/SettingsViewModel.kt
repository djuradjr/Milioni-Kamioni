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

    fun setDailyResetTime(minutes: Int) {
        viewModelScope.launch { prefs.setDailyResetTime(minutes) }
    }
}
