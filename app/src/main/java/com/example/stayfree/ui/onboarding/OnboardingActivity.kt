package com.example.stayfree.ui.onboarding

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.stayfree.R
import com.example.stayfree.data.local.preferences.AppPreferences
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.example.stayfree.databinding.ActivityOnboardingBinding
import com.example.stayfree.service.StayFreeAccessibilityService
import com.example.stayfree.service.TrackingScheduler
import com.example.stayfree.ui.MainActivity
import com.example.stayfree.util.PermissionUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class OnboardingActivity : AppCompatActivity() {

    @Inject lateinit var prefs: AppPreferences

    private lateinit var binding: ActivityOnboardingBinding

    private val steps = listOf(
        OnboardingStep.USAGE_ACCESS,
        OnboardingStep.ACCESSIBILITY,
        OnboardingStep.OVERLAY,
        OnboardingStep.NOTIFICATIONS,
        OnboardingStep.BATTERY
    )
    private var currentStep = 0
    private var awaitingGrant = false
    private var finishing = false
    private var pollJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        currentStep = firstUngrantedStep()
        showStep(currentStep)

        binding.btnGrant.setOnClickListener { requestCurrentPermission() }
        binding.btnNext.setOnClickListener { advance() }
    }

    /** Resumes where the user stopped: a cold start must not rewind a granted step. */
    private fun firstUngrantedStep(): Int =
        steps.indexOfFirst { !isStepGranted(it) }.let { if (it == -1) steps.lastIndex else it }

    override fun onResume() {
        super.onResume()
        refresh()
        pollJob?.cancel()
        pollJob = lifecycleScope.launch {
            repeat(POLL_ATTEMPTS) {
                delay(POLL_INTERVAL_MS)
                if (refresh()) return@launch
            }
        }
    }

    override fun onPause() {
        super.onPause()
        pollJob?.cancel()
    }

    private fun showStep(index: Int) {
        val step = steps[index]
        binding.tvTitle.setText(step.titleRes)
        binding.tvDescription.setText(step.descriptionRes)
        binding.tvStepIndicator.text = "${index + 1} / ${steps.size}"
        refresh()
    }

    /**
     * Returns whether the current step is granted. The system toggle can flip while we
     * are in the background *and* seconds after we come back, so onResume polls this
     * instead of checking once.
     */
    private fun refresh(): Boolean {
        if (finishing) return true
        val granted = isStepGranted(steps[currentStep])
        binding.btnGrant.isEnabled = !granted
        binding.btnGrant.setText(
            if (granted) R.string.btn_permission_granted else R.string.btn_grant_permission
        )
        binding.btnNext.isEnabled = granted
        if (granted && awaitingGrant) {
            awaitingGrant = false
            advance()
        }
        return granted
    }

    private fun isStepGranted(step: OnboardingStep): Boolean = when (step) {
        OnboardingStep.USAGE_ACCESS -> PermissionUtils.hasUsageStatsPermission(this)
        OnboardingStep.ACCESSIBILITY -> PermissionUtils.hasAccessibilityServiceEnabled(this)
        OnboardingStep.OVERLAY -> PermissionUtils.hasOverlayPermission(this)
        OnboardingStep.NOTIFICATIONS -> PermissionUtils.hasNotificationPermission(this)
        OnboardingStep.BATTERY -> PermissionUtils.isIgnoringBatteryOptimizations(this)
    }

    private fun requestCurrentPermission() {
        awaitingGrant = true
        when (steps[currentStep]) {
            OnboardingStep.USAGE_ACCESS ->
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            OnboardingStep.ACCESSIBILITY ->
                showAccessibilityDisclosure()
            OnboardingStep.OVERLAY ->
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")))
            OnboardingStep.NOTIFICATIONS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100)
                }
            }
            OnboardingStep.BATTERY ->
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")))
        }
    }

    /**
     * Play policy: AccessibilityService usage requires prominent disclosure
     * (what is read, why, and where it goes) BEFORE the system settings open.
     */
    private fun showAccessibilityDisclosure() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.disclosure_title)
            .setMessage(R.string.disclosure_message)
            .setPositiveButton(R.string.disclosure_accept) { _, _ ->
                lifecycleScope.launch { prefs.setAccessibilityDisclosureAccepted(true) }
                openAccessibilitySettings()
            }
            .setNegativeButton(R.string.disclosure_decline) { _, _ -> awaitingGrant = false }
            .setOnCancelListener { awaitingGrant = false }
            .show()
    }

    /**
     * The details page (ACTION_ACCESSIBILITY_DETAILS_SETTINGS) is not public API, so this
     * opens the public list with the undocumented Settings extras that scroll to and
     * highlight our row; ROMs that ignore them just show the plain list.
     */
    private fun openAccessibilitySettings() {
        val component = ComponentName(this, StayFreeAccessibilityService::class.java)
            .flattenToString()
        val args = Bundle().apply { putString(SETTINGS_ARGS_KEY, component) }
        startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .putExtra(SETTINGS_ARGS_KEY, component)
                .putExtra(":settings:show_fragment_args", args)
        )
    }

    private fun advance() {
        if (finishing) return
        if (currentStep >= steps.lastIndex) {
            complete()
        } else {
            currentStep++
            showStep(currentStep)
        }
    }

    private fun complete() {
        finishing = true
        pollJob?.cancel()
        lifecycleScope.launch {
            prefs.setOnboardingComplete(true)
            val resetTime = prefs.dailyResetTimeMinutes.first()
            TrackingScheduler.ensureWorkScheduled(this@OnboardingActivity, resetTime)
            TrackingScheduler.ensureStarted(this@OnboardingActivity)
            startActivity(Intent(this@OnboardingActivity, MainActivity::class.java))
            finish()
        }
    }

    private companion object {
        const val POLL_ATTEMPTS = 12
        const val POLL_INTERVAL_MS = 500L
        const val SETTINGS_ARGS_KEY = ":settings:fragment_args_key"
    }
}
