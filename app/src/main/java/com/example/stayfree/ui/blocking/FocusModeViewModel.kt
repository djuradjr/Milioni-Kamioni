package com.example.stayfree.ui.blocking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stayfree.data.local.preferences.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val DEFAULT_DURATION_MINUTES = 25
private const val MIN_DURATION_MINUTES = 5
private const val MAX_DURATION_MINUTES = 120

@HiltViewModel
class FocusModeViewModel @Inject constructor(
    private val prefs: AppPreferences
) : ViewModel() {

    private val _isFocusActive = MutableStateFlow(false)
    val isFocusActive: StateFlow<Boolean> = _isFocusActive.asStateFlow()

    private val _remainingMs = MutableStateFlow(0L)
    val remainingMs: StateFlow<Long> = _remainingMs.asStateFlow()

    private val _durationMinutes = MutableStateFlow(DEFAULT_DURATION_MINUTES)
    val durationMinutes: StateFlow<Int> = _durationMinutes.asStateFlow()
    private var isWhitelistMode = true
    private var countdownJob: Job? = null

    init {
        // Resume a persisted focus session after process restart.
        viewModelScope.launch {
            val active = prefs.focusActive.first()
            val endTime = prefs.focusEndTime.first()
            val isWhitelist = prefs.focusIsWhitelist.first()
            if (active && endTime > System.currentTimeMillis()) {
                isWhitelistMode = isWhitelist
                FocusModeState.update(true, endTime, emptySet(), isWhitelist)
                resumeCountdown(endTime)
            }
        }
    }

    fun setDurationMinutes(minutes: Int) {
        _durationMinutes.value = minutes.coerceIn(MIN_DURATION_MINUTES, MAX_DURATION_MINUTES)
    }
    fun setWhitelistMode(whitelist: Boolean) { isWhitelistMode = whitelist }

    fun startFocusMode() {
        val durationMs = _durationMinutes.value * 60_000L
        val endTime = System.currentTimeMillis() + durationMs

        FocusModeState.update(
            active = true,
            endTime = endTime,
            whitelist = emptySet(),
            isWhitelist = isWhitelistMode
        )
        viewModelScope.launch { prefs.setFocusState(true, endTime, isWhitelistMode) }
        resumeCountdown(endTime)
    }

    fun stopFocusMode() {
        countdownJob?.cancel()
        _isFocusActive.value = false
        _remainingMs.value = 0L
        FocusModeState.update(active = false, endTime = 0L, whitelist = emptySet(), isWhitelist = true)
        viewModelScope.launch { prefs.setFocusState(false, 0L, true) }
    }

    private fun resumeCountdown(endTime: Long) {
        countdownJob?.cancel()
        _isFocusActive.value = true
        _remainingMs.value = endTime - System.currentTimeMillis()
        countdownJob = viewModelScope.launch {
            while (true) {
                val remaining = endTime - System.currentTimeMillis()
                if (remaining <= 0) break
                _remainingMs.value = remaining
                delay(1000L)
            }
            stopFocusMode()
        }
    }
}
