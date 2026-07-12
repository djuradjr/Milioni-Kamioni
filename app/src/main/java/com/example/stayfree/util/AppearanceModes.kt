package com.example.stayfree.util

import androidx.appcompat.app.AppCompatDelegate
import com.example.stayfree.data.local.preferences.AppPreferences

object AppearanceModes {
    fun toNightMode(mode: String): Int = when (mode) {
        AppPreferences.APPEARANCE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
        AppPreferences.APPEARANCE_SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        else -> AppCompatDelegate.MODE_NIGHT_NO
    }
}
