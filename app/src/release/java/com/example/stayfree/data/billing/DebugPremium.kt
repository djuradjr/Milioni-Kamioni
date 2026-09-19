package com.example.stayfree.data.billing

import android.content.Context

@Suppress("UNUSED_PARAMETER")
internal object DebugPremium {
    fun override(context: Context): Boolean? = null
    fun fakePlans(context: Context): List<PremiumPlan>? = null
    fun fakePurchase(context: Context): Boolean = false
}
