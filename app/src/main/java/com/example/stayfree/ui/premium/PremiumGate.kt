package com.example.stayfree.ui.premium

import androidx.fragment.app.Fragment
import com.example.stayfree.data.billing.PremiumRepository
import com.example.stayfree.ui.onboarding.OnboardingActivity
import com.example.stayfree.util.PermissionUtils

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

/**
 * [requirePremium] for turning a block on. The block is kept either way; when Android's
 * accessibility access is still missing, its setup screen opens so the block actually fires.
 */
fun Fragment.requireBlocking(premium: PremiumRepository): Boolean {
    if (!requirePremium(premium)) return false
    ensureProtection()
    return true
}

fun Fragment.ensureProtection() {
    if (!PermissionUtils.hasAccessibilityServiceEnabled(requireContext())) {
        startActivity(OnboardingActivity.protectionIntent(requireContext()))
    }
}
