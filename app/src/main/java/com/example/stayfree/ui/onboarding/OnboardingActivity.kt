package com.example.stayfree.ui.onboarding

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.stayfree.R
import com.example.stayfree.data.billing.PremiumRepository
import com.example.stayfree.data.local.preferences.AppPreferences
import com.example.stayfree.domain.content.ContentBlockTarget
import com.example.stayfree.domain.content.ContentSignatures
import com.example.stayfree.domain.onboarding.DailyGoal
import com.example.stayfree.domain.onboarding.UsageHistory
import com.example.stayfree.ui.premium.PaywallActivity
import com.example.stayfree.util.PermissionUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.example.stayfree.databinding.ActivityOnboardingBinding
import com.example.stayfree.service.StayFreeAccessibilityService
import com.example.stayfree.service.TrackingScheduler
import com.example.stayfree.ui.MainActivity
import com.example.stayfree.util.AppInfoUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class OnboardingActivity : AppCompatActivity() {

    @Inject lateinit var prefs: AppPreferences
    @Inject lateinit var usageHistory: UsageHistory
    @Inject lateinit var premium: PremiumRepository

    private lateinit var binding: ActivityOnboardingBinding

    // Tracking is free and needs only usage access. The blocking permissions come after the
    // paywall, so nobody grants accessibility to an app that then can't use it.
    private val trackingSteps = listOf(OnboardingStep.USAGE_ACCESS)
    private val protectionSteps = listOf(
        OnboardingStep.ACCESSIBILITY,
        OnboardingStep.OVERLAY,
        OnboardingStep.NOTIFICATIONS,
        OnboardingStep.BATTERY
    )
    private var steps = trackingSteps
    /** Tracking permission, report, target picks, then — with premium — the blocking permissions. */
    private enum class Phase { PERMISSIONS, REPORT, TARGETS, PROTECTION }

    /** Opened from the app (premium bought later, or a permission revoked): only the protection run. */
    private val protectionOnly by lazy { intent.getBooleanExtra(EXTRA_PROTECTION_ONLY, false) }

    private val paywall = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        // Closing the paywall finishes onboarding as a tracker; the picked blocks stay saved.
        if (result.resultCode == RESULT_OK || premium.isPremium.value) enterProtection() else complete()
    }

    private var phase = Phase.PERMISSIONS
    private var report: UsageHistory.Report? = null
    private var goalMinutes = AppPreferences.DEFAULT_DAILY_GOAL_MINUTES
    /** Target id → chosen daily limit in minutes; absent means the switch is off. */
    private val chosenTargets = linkedMapOf<String, Int>()

    private var currentStep = 0
    private var awaitingGrant = false
    private var finishing = false
    private var pollJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        buildProgress()
        // Resumes from the real permission state: a cold start must not rewind a granted step.
        val usageGranted = isStepGranted(OnboardingStep.USAGE_ACCESS)
        when {
            protectionOnly || (usageGranted && premium.isPremium.value) -> enterProtection()
            usageGranted -> enterReport()
            else -> showStep(0)
        }

        binding.btnGrant.setOnClickListener { onPrimaryClick() }
        binding.btnNext.setOnClickListener { advance() }
        binding.btnSkip.setOnClickListener { advance() }
        binding.btnGoalMinus.setOnClickListener { nudgeGoal(-1) }
        binding.btnGoalPlus.setOnClickListener { nudgeGoal(1) }
    }

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
        val position = if (phase == Phase.PROTECTION) trackingSteps.size + 2 + index else index
        binding.tvStepIndicator.text = getString(
            R.string.onboarding_step_indicator, position + 1, totalSteps()
        )
        updateProgress(position)
        refresh()
    }

    /**
     * Returns whether the current step is granted. The system toggle can flip while we
     * are in the background *and* seconds after we come back, so onResume polls this
     * instead of checking once.
     */
    private fun refresh(): Boolean {
        if (finishing) return true
        // Only the permission phases depend on system toggles.
        if (phase != Phase.PERMISSIONS && phase != Phase.PROTECTION) return true
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
        when (phase) {
            Phase.PERMISSIONS ->
                if (currentStep >= steps.lastIndex) enterReport() else {
                    currentStep++
                    showStep(currentStep)
                }
            Phase.REPORT -> enterTargets()
            Phase.TARGETS -> complete()
            Phase.PROTECTION ->
                if (currentStep >= steps.lastIndex) complete() else {
                    currentStep++
                    showStep(currentStep)
                }
        }
    }

    /** The primary button means something different in each phase. */
    private fun onPrimaryClick() {
        when (phase) {
            Phase.PERMISSIONS, Phase.PROTECTION -> requestCurrentPermission()
            Phase.REPORT -> {
                lifecycleScope.launch { prefs.setDailyGoalMinutes(goalMinutes) }
                enterTargets()
            }
            Phase.TARGETS -> saveTargetsAndProtect()
        }
    }

    // ---------------------------------------------------------------- phase 2

    private fun enterReport() {
        phase = Phase.REPORT
        pollJob?.cancel()
        binding.groupPermission.visibility = View.GONE
        binding.groupTargets.visibility = View.GONE
        binding.groupReport.visibility = View.VISIBLE
        binding.btnNext.visibility = View.GONE
        binding.btnSkip.visibility = View.VISIBLE
        binding.btnGrant.isEnabled = true
        binding.btnGrant.setText(R.string.onboarding_set_goal)
        updateProgress(trackingSteps.size)

        lifecycleScope.launch {
            val result = usageHistory.lastWeek()
            report = result
            bindReport(result)
        }
    }

    private fun bindReport(result: UsageHistory.Report) {
        if (!result.hasData) {
            // No history on this device: say so plainly and start from the default.
            binding.tvReportLabel.setText(R.string.onboarding_first_run_title)
            binding.reportMetrics.visibility = View.GONE
            binding.chartReport.visibility = View.GONE
            binding.reportTopApps.visibility = View.GONE
            binding.tvReportSub.setText(R.string.onboarding_first_run_desc)
            binding.tvReportSub.visibility = View.VISIBLE
            binding.tvGoalHint.setText(R.string.onboarding_goal_hint_start)
            goalMinutes = AppPreferences.DEFAULT_DAILY_GOAL_MINUTES
            renderGoal()
            return
        }

        // Both in whole minutes, so total, average and the goal hint below add up exactly.
        binding.tvReportTotal.text = formatMinutesCompact((result.totalMs / 60_000L).toInt())
        binding.tvReportDaily.text = formatMinutesCompact(averageMinutes())
        if (result.measuredDays < result.dailyTotals.size) {
            binding.tvReportSub.text = resources.getQuantityString(
                R.plurals.onboarding_report_days_measured, result.measuredDays, result.measuredDays
            )
            binding.tvReportSub.visibility = View.VISIBLE
        }
        binding.chartReport.setData(result.dailyTotals.map { it / 60_000f })
        bindTopApps(result.topApps)

        goalMinutes = DailyGoal.suggest(averageMinutes())
        renderGoal()
    }

    private fun bindTopApps(apps: List<UsageHistory.AppTotal>) {
        val host = binding.reportTopApps
        host.removeAllViews()
        apps.take(TOP_APPS_SHOWN).forEach { app ->
            val row = layoutInflater.inflate(R.layout.item_onboarding_app, host, false)
            row.findViewById<TextView>(R.id.tv_name).text = app.label
            row.findViewById<TextView>(R.id.tv_time).text =
                formatMinutesCompact((app.totalMs / 60_000L).toInt())
            AppInfoUtils.getAppIcon(this, app.packageName)?.let {
                row.findViewById<ImageView>(R.id.iv_icon).setImageDrawable(it)
            }
            host.addView(row)
        }
    }

    private fun nudgeGoal(steps: Int) {
        goalMinutes = DailyGoal.nudge(goalMinutes, steps)
        renderGoal()
    }

    private fun renderGoal() {
        binding.tvGoalValue.text = formatMinutesCompact(goalMinutes)
        if (report?.hasData != true) {
            binding.tvGoalHint.setText(R.string.onboarding_goal_hint_start)
            return
        }
        val cut = DailyGoal.cut(averageMinutes(), goalMinutes)
        if (cut == null) {
            binding.tvGoalHint.setText(R.string.onboarding_goal_hint_already)
        } else {
            binding.tvGoalHint.text = getString(
                R.string.onboarding_goal_hint_less, formatMinutesCompact(cut.minutes), cut.percent
            )
        }
    }

    private fun averageMinutes(): Int = ((report?.dailyAverageMs ?: 0L) / 60_000L).toInt()

    // ---------------------------------------------------------------- phase 3

    private fun enterTargets() {
        phase = Phase.TARGETS
        binding.groupReport.visibility = View.GONE
        binding.groupTargets.visibility = View.VISIBLE
        binding.btnGrant.isEnabled = true
        binding.btnGrant.setText(R.string.onboarding_turn_on_protection)
        binding.tvTargetsSub.setText(
            if (report?.hasData == true) R.string.onboarding_targets_desc
            else R.string.onboarding_targets_desc_blind
        )
        updateProgress(trackingSteps.size + 1)
        buildTargetRows()
    }

    /**
     * One row per detectable surface whose app is actually installed. Rows start
     * on when the app showed up in the user's own week — confirming a pick beats
     * configuring from scratch.
     */
    private fun buildTargetRows() {
        val host = binding.targetsList
        host.removeAllViews()
        chosenTargets.clear()

        val weekPackages = report?.topApps?.map { it.packageName }?.toSet().orEmpty()
        val installed = ContentSignatures.ALL.filter {
            AppInfoUtils.getAppIcon(this, it.packageName) != null
        }

        installed.forEachIndexed { index, target ->
            val on = weekPackages.isEmpty() || target.packageName in weekPackages
            val limit = suggestedLimit(target)
            if (on) chosenTargets[target.id] = limit

            val row = layoutInflater.inflate(R.layout.item_onboarding_target, host, false)
            row.findViewById<View>(R.id.divider).visibility =
                if (index == 0) View.GONE else View.VISIBLE
            val appName = AppInfoUtils.getAppName(this, target.packageName)
            row.findViewById<TextView>(R.id.tv_name).text =
                if (target.matchWholeApp || target.displayName.equals(appName, ignoreCase = true)) {
                    getString(R.string.onboarding_target_name_whole, appName)
                } else {
                    getString(R.string.onboarding_target_name, appName, target.displayName)
                }
            row.findViewById<TextView>(R.id.tv_limit).text = limitLabel(limit)
            AppInfoUtils.getAppIcon(this, target.packageName)?.let {
                row.findViewById<ImageView>(R.id.iv_icon).setImageDrawable(it)
            }
            row.findViewById<SwitchCompat>(R.id.switch_target).apply {
                isChecked = on
                setOnCheckedChangeListener { _, checked ->
                    if (checked) chosenTargets[target.id] = limit else chosenTargets.remove(target.id)
                }
            }
            host.addView(row)
        }
    }

    private fun suggestedLimit(target: ContentBlockTarget): Int =
        if (target.matchWholeApp) WHOLE_APP_LIMIT_MINUTES else SURFACE_LIMIT_MINUTES

    private fun limitLabel(minutes: Int): String =
        if (minutes <= 0) getString(R.string.onboarding_target_limit_off)
        else getString(R.string.onboarding_target_limit_minutes, minutes)

    private fun saveTargetsAndProtect() {
        finishing = true
        lifecycleScope.launch {
            chosenTargets.forEach { (id, minutes) ->
                prefs.setContentBlockEnabled(id, true)
                prefs.setContentTargetLimitMinutes(id, minutes)
            }
            finishing = false
            if (premium.isPremium.value) enterProtection()
            else paywall.launch(PaywallActivity.newIntent(this@OnboardingActivity))
        }
    }

    // ---------------------------------------------------------------- phase 4

    private fun enterProtection() {
        val first = protectionSteps.indexOfFirst { !isStepGranted(it) }
        if (first == -1) {
            complete()
            return
        }
        phase = Phase.PROTECTION
        steps = protectionSteps
        currentStep = first
        binding.groupReport.visibility = View.GONE
        binding.groupTargets.visibility = View.GONE
        binding.groupPermission.visibility = View.VISIBLE
        binding.btnNext.visibility = View.VISIBLE
        binding.btnSkip.visibility = View.GONE
        showStep(currentStep)
    }

    // ---------------------------------------------------------------- progress

    /** One segment per step across all four phases. */
    private fun buildProgress() {
        val host = binding.progressRow
        host.removeAllViews()
        repeat(totalSteps()) { index ->
            val segment = View(this)
            val params = LinearLayout.LayoutParams(0, dp(3), 1f)
            if (index > 0) params.marginStart = dp(4)
            segment.layoutParams = params
            segment.setBackgroundResource(R.drawable.bg_skor_dot)
            host.addView(segment)
        }
        updateProgress(currentStep)
    }

    private fun updateProgress(reached: Int) {
        val host = binding.progressRow
        for (i in 0 until host.childCount) {
            host.getChildAt(i).backgroundTintList = ContextCompat.getColorStateList(
                this, if (i <= reached) R.color.skor_focus else R.color.skor_line
            )
        }
    }

    private fun totalSteps() = trackingSteps.size + 2 + protectionSteps.size

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    /** "4h", "4h 30m" or "45m" — a goal has no business showing seconds. */
    private fun formatMinutesCompact(totalMinutes: Int): String {
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return when {
            h > 0 && m == 0 -> getString(R.string.duration_hours, h)
            h > 0 -> getString(R.string.duration_hours_minutes, h, m)
            else -> getString(R.string.duration_minutes, m)
        }
    }

    private fun complete() {
        finishing = true
        pollJob?.cancel()
        if (protectionOnly) {
            finish()
            return
        }
        lifecycleScope.launch {
            prefs.setOnboardingComplete(true)
            val resetTime = prefs.dailyResetTimeMinutes.first()
            TrackingScheduler.ensureWorkScheduled(this@OnboardingActivity, resetTime)
            TrackingScheduler.ensureStarted(this@OnboardingActivity)
            startActivity(Intent(this@OnboardingActivity, MainActivity::class.java))
            finish()
        }
    }

    companion object {
        private const val TOP_APPS_SHOWN = 3
        private const val SURFACE_LIMIT_MINUTES = 15
        private const val WHOLE_APP_LIMIT_MINUTES = 15
        private const val POLL_ATTEMPTS = 12
        private const val POLL_INTERVAL_MS = 500L
        private const val SETTINGS_ARGS_KEY = ":settings:fragment_args_key"
        private const val EXTRA_PROTECTION_ONLY = "protection_only"

        fun protectionIntent(context: Context) =
            Intent(context, OnboardingActivity::class.java).putExtra(EXTRA_PROTECTION_ONLY, true)
    }
}
