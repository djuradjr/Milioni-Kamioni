package com.example.stayfree.ui.premium

import androidx.fragment.app.Fragment
import com.example.stayfree.data.billing.PremiumRepository

/**
 * Blocking is the paid feature. Every path that turns a block ON goes through here; paths
 * that turn one OFF never do — a user whose subscription lapsed must always be able to
 * switch their blocks off. Returns true when premium is active, otherwise opens the paywall.
 */
fun Fragment.requirePremium(premium: PremiumRepository): Boolean {
    if (premium.isPremium.value) return true
    startActivity(PaywallActivity.newIntent(requireContext()))
    return false
}
