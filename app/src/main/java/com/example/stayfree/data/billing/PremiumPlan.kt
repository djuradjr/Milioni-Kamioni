package com.example.stayfree.data.billing

import kotlin.math.roundToInt

/** One base plan of the premium subscription, as Play offers it to this user. */
data class PremiumPlan(
    val months: Int,
    /** Recurring price, already formatted by Play in the user's currency. */
    val price: String,
    val priceMicros: Long,
    val currencyCode: String,
    /** 0 when this user can't get a free trial — Play omits offers they aren't eligible for. */
    val trialDays: Int,
    val offerToken: String
) {
    val perMonthMicros: Long get() = priceMicros / months

    companion object {
        private val ISO_PERIOD = Regex("""P(\d+)([DWMY])""")

        /** "P1M", "P3M", "P1Y" → months; null for any other period. */
        fun months(isoPeriod: String): Int? = ISO_PERIOD.matchEntire(isoPeriod)?.let {
            val n = it.groupValues[1].toInt()
            when (it.groupValues[2]) {
                "M" -> n
                "Y" -> n * 12
                else -> null
            }
        }

        /** "P7D", "P1W" → days; null for any other period. */
        fun days(isoPeriod: String): Int? = ISO_PERIOD.matchEntire(isoPeriod)?.let {
            val n = it.groupValues[1].toInt()
            when (it.groupValues[2]) {
                "D" -> n
                "W" -> n * 7
                else -> null
            }
        }

        /** Whole-percent saving per month against [monthly]; 0 when there is none. */
        fun discountPercent(plan: PremiumPlan, monthly: PremiumPlan): Int =
            if (monthly.perMonthMicros <= 0) 0
            else (100 - plan.perMonthMicros * 100.0 / monthly.perMonthMicros)
                .roundToInt().coerceAtLeast(0)
    }
}
