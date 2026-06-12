package com.example.stayfree.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.stayfree.data.local.preferences.AppPreferences
import com.example.stayfree.data.repository.UsageRepository
import com.example.stayfree.util.TimeUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject

// Registered dynamically in UsageTrackingService (not in manifest — ACTION_SCREEN_ON cannot be static)
class ScreenStateReceiver(
    private val usageRepository: UsageRepository,
    private val prefs: AppPreferences,
    private val scope: CoroutineScope
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        scope.launch {
            val resetTime = prefs.dailyResetTimeMinutes.first()
            val date = TimeUtils.getEffectiveDate(resetTime)
            when (intent.action) {
                Intent.ACTION_USER_PRESENT -> usageRepository.incrementUnlock(date)
                Intent.ACTION_SCREEN_ON -> usageRepository.incrementScreenOn(date)
            }
        }
    }
}
