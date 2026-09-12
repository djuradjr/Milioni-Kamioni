package com.example.stayfree.domain.score

import com.example.stayfree.domain.model.AppUsage
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The daily focus score, 0-100. Local and deterministic: the same day always
 * produces the same number, and nothing leaves the device.
 *
 * Starts at 100 and spends points on distraction. Neutral and productive apps
 * cost nothing — this scores *what* the time went to, not how much screen time
 * there was.
 */
object FocusScore {

    /** Points per minute on a DISTRACTION app while still inside the daily goal. */
    private const val COST_PER_MIN = 0.30

    /** Same, once the daily goal is already blown — overshoot costs more. */
    private const val COST_PER_MIN_OVER = 0.50

    /** Points per unlock above the user's own recent average. */
    private const val COST_PER_EXTRA_UNLOCK = 0.30
    private const val MAX_UNLOCK_PENALTY = 15.0

    /** Points earned per content block that fired. */
    private const val BONUS_PER_INTERCEPT = 0.50
    private const val MAX_INTERCEPT_BONUS = 15.0

    data class Breakdown(
        val score: Int,
        val distractionMs: Long,
        val neutralMs: Long,
        val pointsLostToDistraction: Double,
        val pointsLostToUnlocks: Double,
        val pointsGainedFromBlocks: Double,
        /** Points lost per package, biggest first — drives the "where it went" sheet. */
        val perApp: List<Pair<String, Double>>
    )

    /**
     * @param usage today's per-app usage
     * @param categoryOf resolved category per package (user override or default)
     * @param goalMinutes the user's daily screen-time goal
     * @param unlocks today's unlock count
     * @param averageUnlocks the user's recent daily average; pass [unlocks] when
     *        there is no history yet, which zeroes the unlock penalty
     * @param interceptedCount content blocks fired today. Every block counts as a
     *        win for now; telling "honored" blocks apart needs the a11y service to
     *        record whether the user came straight back, which it does not yet.
     */
    fun compute(
        usage: List<AppUsage>,
        categoryOf: (String) -> AppCategory,
        goalMinutes: Int,
        unlocks: Int,
        averageUnlocks: Int,
        interceptedCount: Int
    ): Breakdown {
        var distractionMs = 0L
        var neutralMs = 0L
        var totalMs = 0L
        val perAppMs = mutableListOf<Pair<String, Long>>()

        usage.forEach { app ->
            totalMs += app.totalTimeMs
            when (categoryOf(app.packageName)) {
                AppCategory.DISTRACTION -> {
                    distractionMs += app.totalTimeMs
                    perAppMs += app.packageName to app.totalTimeMs
                }
                AppCategory.NEUTRAL -> neutralMs += app.totalTimeMs
                AppCategory.PRODUCTIVE -> Unit
            }
        }

        // Points that fall past the goal cost more. We don't know which minutes
        // came after the goal was blown — only how far past it the day went — so
        // we treat the overshoot as distraction, which is what it almost always is.
        val goalMs = max(1, goalMinutes) * 60_000L
        val overshootMs = max(0L, totalMs - goalMs).coerceAtMost(distractionMs)
        val normalMs = distractionMs - overshootMs

        val lostToDistraction =
            normalMs.toMinutes() * COST_PER_MIN + overshootMs.toMinutes() * COST_PER_MIN_OVER

        val extraUnlocks = max(0, unlocks - averageUnlocks)
        val lostToUnlocks =
            (extraUnlocks * COST_PER_EXTRA_UNLOCK).coerceAtMost(MAX_UNLOCK_PENALTY)

        val gained =
            (interceptedCount * BONUS_PER_INTERCEPT).coerceAtMost(MAX_INTERCEPT_BONUS)

        val raw = 100.0 - lostToDistraction - lostToUnlocks + gained

        // Per-app cost uses the blended rate so the parts add up to the whole.
        val blendedRate = if (distractionMs > 0) lostToDistraction / distractionMs.toMinutes() else 0.0

        return Breakdown(
            score = raw.roundToInt().coerceIn(0, 100),
            distractionMs = distractionMs,
            neutralMs = neutralMs,
            pointsLostToDistraction = lostToDistraction,
            pointsLostToUnlocks = lostToUnlocks,
            pointsGainedFromBlocks = gained,
            perApp = perAppMs
                .map { (pkg, ms) -> pkg to ms.toMinutes() * blendedRate }
                .sortedByDescending { it.second }
        )
    }

    private fun Long.toMinutes(): Double = this / 60_000.0

    /** Score band, for colouring the ring and picking the caption. */
    fun bandOf(score: Int): Band = when {
        score >= 80 -> Band.GOOD
        score >= 55 -> Band.MIXED
        else -> Band.POOR
    }

    enum class Band { GOOD, MIXED, POOR }
}
