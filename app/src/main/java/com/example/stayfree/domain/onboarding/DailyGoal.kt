package com.example.stayfree.domain.onboarding

import kotlin.math.roundToInt

/** The onboarding goal stepper's arithmetic, in whole minutes throughout. */
object DailyGoal {
    const val STEP_MINUTES = 10
    const val MIN_MINUTES = 30
    const val MAX_MINUTES = 12 * 60
    private const val SUGGESTED_CUT = 0.25

    /** How far a goal sits under the measured average. */
    data class Cut(val minutes: Int, val percent: Int)

    /** A quarter under the average, on the stepper's grid. */
    fun suggest(averageMinutes: Int): Int =
        snap((averageMinutes * (1 - SUGGESTED_CUT)).roundToInt())

    fun nudge(goalMinutes: Int, steps: Int): Int =
        (goalMinutes + steps * STEP_MINUTES).coerceIn(MIN_MINUTES, MAX_MINUTES)

    /** null when the goal already allows the average or more. */
    fun cut(averageMinutes: Int, goalMinutes: Int): Cut? {
        if (averageMinutes <= goalMinutes) return null
        val minutes = averageMinutes - goalMinutes
        return Cut(minutes, (minutes * 100f / averageMinutes).roundToInt())
    }

    private fun snap(minutes: Int): Int =
        (((minutes + STEP_MINUTES / 2) / STEP_MINUTES) * STEP_MINUTES)
            .coerceIn(MIN_MINUTES, MAX_MINUTES)
}
