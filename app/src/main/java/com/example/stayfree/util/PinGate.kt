package com.example.stayfree.util

import com.example.stayfree.data.local.preferences.AppPreferences
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single verification path for every PIN prompt in the app, with persistent
 * brute-force throttling: 5 wrong attempts lock the PIN for 30s, escalating
 * per round (30s, 60s, 90s…). State lives in DataStore so it survives
 * process death and is shared by all entry points.
 */
@Singleton
class PinGate @Inject constructor(private val prefs: AppPreferences) {

    sealed class Result {
        data object Success : Result()
        data object Wrong : Result()
        data class LockedOut(val remainingSeconds: Int) : Result()
        data object NoPin : Result()
    }

    suspend fun isPinSet(): Boolean =
        prefs.pinEnabled.first() && prefs.pinHash.first() != null

    suspend fun verify(pin: String): Result {
        val now = System.currentTimeMillis()
        val lockedUntil = prefs.pinLockoutUntil.first()
        if (now < lockedUntil) {
            return Result.LockedOut(((lockedUntil - now + 999) / 1000).toInt())
        }
        val stored = prefs.pinHash.first() ?: return Result.NoPin
        return if (PinHasher.verify(pin, stored)) {
            prefs.resetPinAttempts()
            Result.Success
        } else {
            val attempts = prefs.registerFailedPinAttempt()
            if (attempts % MAX_ATTEMPTS_PER_ROUND == 0) {
                val lockSeconds = LOCKOUT_BASE_SECONDS * (attempts / MAX_ATTEMPTS_PER_ROUND)
                prefs.setPinLockout(System.currentTimeMillis() + lockSeconds * 1000L)
                Result.LockedOut(lockSeconds)
            } else {
                Result.Wrong
            }
        }
    }

    private companion object {
        const val MAX_ATTEMPTS_PER_ROUND = 5
        const val LOCKOUT_BASE_SECONDS = 30
    }
}
