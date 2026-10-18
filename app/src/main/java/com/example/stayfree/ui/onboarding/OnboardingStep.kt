package com.example.stayfree.ui.onboarding

import com.example.stayfree.R

enum class OnboardingStep(val titleRes: Int, val descriptionRes: Int) {
    USAGE_ACCESS(R.string.onboarding_usage_title, R.string.onboarding_usage_desc),
    ACCESSIBILITY(R.string.onboarding_accessibility_title, R.string.onboarding_accessibility_desc)
}
