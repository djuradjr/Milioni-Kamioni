package com.example.stayfree.data.billing

import android.content.Context
import java.io.File
import java.text.NumberFormat
import java.util.Currency

/**
 * Debug builds only; the release source set ships no-ops, so none of this exists there.
 * - `files/premium_override` holding 1 or 0 forces the premium state without Play:
 *   `adb shell run-as <pkg> sh -c 'echo 1 > files/premium_override'`.
 * - `files/fake_plans` (any content) makes the paywall show sample plans, and "buying"
 *   one writes the override — the paywall can be exercised before Play has products.
 */
internal object DebugPremium {

    private const val OVERRIDE = "premium_override"
    private const val FAKE_PLANS = "fake_plans"

    fun override(context: Context): Boolean? =
        File(context.filesDir, OVERRIDE).takeIf { it.isFile }
            ?.readText()?.trim()?.let { it == "1" }

    fun fakePlans(context: Context): List<PremiumPlan>? =
        if (!File(context.filesDir, FAKE_PLANS).isFile) null
        else listOf(
            fakePlan(1, 3_990_000),
            fakePlan(3, 9_990_000),
            fakePlan(6, 16_990_000)
        )

    // Formatted like Play does, in the device locale.
    private fun fakePlan(months: Int, micros: Long) = PremiumPlan(
        months = months,
        price = NumberFormat.getCurrencyInstance()
            .apply { currency = Currency.getInstance("EUR") }
            .format(micros / 1_000_000.0),
        priceMicros = micros,
        currencyCode = "EUR",
        trialDays = 7,
        offerToken = "fake-${months}m"
    )

    fun fakePurchase(context: Context): Boolean {
        if (!File(context.filesDir, FAKE_PLANS).isFile) return false
        File(context.filesDir, OVERRIDE).writeText("1")
        return true
    }
}
