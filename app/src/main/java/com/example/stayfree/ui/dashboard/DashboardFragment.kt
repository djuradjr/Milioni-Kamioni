package com.example.stayfree.ui.dashboard

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.stayfree.R
import com.example.stayfree.databinding.DialogDailyGoalBinding
import com.example.stayfree.databinding.FragmentDashboardBinding
import com.example.stayfree.domain.model.AppUsage
import com.example.stayfree.ui.common.CountUp
import com.example.stayfree.util.TimeUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.transition.MaterialFadeThrough
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

@AndroidEntryPoint
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DashboardViewModel by viewModels()
    private lateinit var topAppsAdapter: AppUsageListAdapter
    private lateinit var periodAppsAdapter: AppUsageListAdapter

    // Tracks the previously shown date so day changes can slide in from the right side.
    private var lastShownDate: String? = null
    private var lastPeriod: StatsPeriod? = null

    /** Length of the current per-day chart series (7 or 30) — maps a chart index
     *  back to its calendar date for the x-axis and tooltip. */
    private var periodDayCount: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialFadeThrough()
        exitTransition = MaterialFadeThrough()
        reenterTransition = MaterialFadeThrough()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupSegments()
        setupRecyclerViews()
        setupDateNav()
        setupGoalCard()
        setupCharts()
        observeData()
    }

    private fun segmentViews(): Map<StatsPeriod, TextView> = mapOf(
        StatsPeriod.DAILY to binding.segDay,
        StatsPeriod.WEEKLY to binding.segWeek,
        StatsPeriod.MONTHLY to binding.segMonth
    )

    private fun setupSegments() {
        segmentViews().forEach { (period, segment) ->
            segment.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                viewModel.setPeriod(period)
            }
        }
    }

    /** Amber active segment on the navy track; the rest stay transparent. */
    private fun applySegmentState(selected: StatsPeriod) {
        segmentViews().forEach { (period, segment) ->
            val active = period == selected
            segment.setBackgroundResource(if (active) R.drawable.bg_seg_active else 0)
            segment.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (active) R.color.dash_navy_card else R.color.dash_seg_inactive
                )
            )
            segment.translationZ = if (active) 2f * resources.displayMetrics.density else 0f
        }
    }

    private fun setupGoalCard() {
        binding.cardGoal.setOnClickListener {
            val goal = viewModel.goalUi.value ?: return@setOnClickListener
            showGoalDialog(goal.goalMinutes)
        }
    }

    private fun showGoalDialog(goalMinutes: Int) {
        val dialogBinding = DialogDailyGoalBinding.inflate(layoutInflater)
        dialogBinding.etGoalHours.setText((goalMinutes / 60).toString())
        dialogBinding.etGoalMinutes.setText((goalMinutes % 60).toString())
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dashboard_goal_dialog_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.btn_save) { _, _ ->
                val hours = dialogBinding.etGoalHours.text.toString().toIntOrNull() ?: 0
                val minutes = dialogBinding.etGoalMinutes.text.toString().toIntOrNull() ?: 0
                val total = hours * 60 + minutes
                if (total > 0) viewModel.setDailyGoal(total)
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    /** "4h", "4h 30m" or "45m" — goal copy without the seconds noise. */
    private fun formatMinutesCompact(totalMinutes: Int): String {
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return when {
            h > 0 && m == 0 -> "${h}h"
            h > 0 -> "${h}h ${m}m"
            else -> "${m}m"
        }
    }

    private fun setupRecyclerViews() {
        val onAppClick: (AppUsage) -> Unit = { appUsage ->
            findNavController().navigate(
                R.id.action_dashboard_to_appDetail,
                bundleOf("packageName" to appUsage.packageName)
            )
        }
        topAppsAdapter = AppUsageListAdapter(onAppClick)
        binding.rvTopApps.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = topAppsAdapter
        }
        periodAppsAdapter = AppUsageListAdapter(onAppClick)
        binding.rvPeriodApps.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = periodAppsAdapter
        }
    }

    private fun setupDateNav() {
        binding.btnPrevDay.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            viewModel.goToPreviousDay()
        }
        binding.btnNextDay.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            viewModel.goToNextDay()
        }
    }

    private fun setupCharts() {
        val minutesToDuration: (Float) -> String = { minutes ->
            TimeUtils.formatDuration((minutes * 60_000f).toLong())
        }
        // Daily: 24 hourly points, clock ticks at 0/6/12/18, tooltip caption = hour.
        binding.chartHourly.apply {
            tooltipValueFormatter = minutesToDuration
            tooltipCaptionFormatter = { hour -> String.format(Locale.US, "%02d:00", hour) }
            xLabelFormatter = { hour -> if (hour % 6 == 0) hour.toString() else "" }
        }
        // Weekly/Monthly: per-day points; ticks are day initials (7d) or dates (30d).
        binding.chartPeriod.apply {
            tooltipValueFormatter = minutesToDuration
            tooltipCaptionFormatter = { index -> TimeUtils.formatDisplayDate(dateForPeriodIndex(index)) }
            xLabelFormatter = { index ->
                val date = dateForPeriodIndex(index)
                if (viewModel.period.value == StatsPeriod.WEEKLY) {
                    TimeUtils.dayInitial(date)
                } else if (index % 5 == 0 || index == periodDayCount - 1) {
                    TimeUtils.dayOfMonth(date)
                } else ""
            }
        }
    }

    /** Maps a per-day chart index (oldest→today) back to its "yyyy-MM-dd" date. */
    private fun dateForPeriodIndex(index: Int): String {
        val daysAgo = (periodDayCount - 1 - index).coerceAtLeast(0)
        return TimeUtils.getDateStringDaysAgo(daysAgo)
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            launch {
                viewModel.period.collectLatest { period ->
                    applySegmentState(period)
                    val daily = period == StatsPeriod.DAILY
                    binding.dailyContent.visibility = if (daily) View.VISIBLE else View.GONE
                    binding.periodContent.visibility = if (daily) View.GONE else View.VISIBLE
                    if (!daily) {
                        binding.tvPeriodPill.setText(
                            if (period == StatsPeriod.WEEKLY) R.string.dashboard_period_last_week
                            else R.string.dashboard_period_last_month
                        )
                    }
                    animatePeriodChange(period)
                }
            }
            launch {
                viewModel.totalScreenTime.collectLatest { ms ->
                    CountUp.animate(binding.tvTotalScreenTime, ms) { TimeUtils.formatDuration(it) }
                }
            }
            launch {
                viewModel.totalUnlocks.collectLatest { count ->
                    CountUp.animate(binding.tvUnlocks, count.toLong()) { it.toString() }
                }
            }
            launch {
                viewModel.topApps.collectLatest { apps ->
                    topAppsAdapter.submitList(apps)
                }
            }
            launch {
                viewModel.interceptedCount.collectLatest { count ->
                    CountUp.animate(binding.tvIntercepted, count.toLong()) { it.toString() }
                }
            }
            launch {
                viewModel.goalUi.collectLatest { goal ->
                    if (goal == null) return@collectLatest
                    binding.tvGoalTitle.text =
                        getString(R.string.dashboard_goal_title, formatMinutesCompact(goal.goalMinutes))
                    binding.tvGoalPct.text = getString(R.string.dashboard_goal_percent, goal.percent)
                    ObjectAnimator.ofInt(
                        binding.progressGoal, "progress", goal.percent.coerceIn(0, 100)
                    ).apply {
                        duration = 700
                        interpolator = DecelerateInterpolator()
                    }.start()
                    val remainingMin = (goal.remainingMs / 60_000L).toInt()
                    binding.tvGoalSub.text = if (goal.remainingMs > 0) {
                        getString(
                            R.string.dashboard_goal_remaining,
                            formatMinutesCompact(remainingMin.coerceAtLeast(1))
                        )
                    } else {
                        getString(
                            R.string.dashboard_goal_over,
                            formatMinutesCompact((-remainingMin).coerceAtLeast(1))
                        )
                    }
                }
            }
            launch {
                viewModel.selectedDate.collectLatest { date ->
                    val today = date == TimeUtils.getTodayString()
                    binding.tvHeroLabel.text =
                        getString(R.string.dashboard_hero_label, TimeUtils.formatHeroDate(date))
                    binding.btnNextDay.isEnabled = !today
                    binding.btnNextDay.alpha = if (today) 0.3f else 1f
                    animateDayChange(date)
                }
            }
            launch {
                viewModel.hourlyUsage.collectLatest { buckets ->
                    binding.chartHourly.setData(buckets.map { it / 60_000f })
                }
            }
            launch {
                viewModel.peakHour.collectLatest { hour ->
                    binding.tvPeakTime.text = if (hour == null) {
                        getString(R.string.dashboard_no_peak)
                    } else {
                        String.format(Locale.US, "%02d:00 – %02d:00", hour, (hour + 1) % 24)
                    }
                }
            }
            launch {
                viewModel.periodAverageScreenTime.collectLatest { ms ->
                    CountUp.animate(binding.tvPeriodTotalTime, ms) { TimeUtils.formatDuration(it) }
                }
            }
            launch {
                viewModel.periodTotalScreenTime.collectLatest { ms ->
                    binding.tvPeriodTotalSum.text =
                        getString(R.string.dashboard_period_total, TimeUtils.formatDuration(ms))
                }
            }
            launch {
                viewModel.periodTotalUnlocks.collectLatest { count ->
                    binding.tvPeriodUnlocks.text = getString(R.string.stats_unlocks_count, count)
                }
            }
            launch {
                viewModel.screenTimeComparison.collectLatest { comparison ->
                    bindTrendBadge(comparison)
                }
            }
            launch {
                viewModel.periodAppUsage.collectLatest { list ->
                    periodAppsAdapter.submitList(list)
                }
            }
            launch {
                viewModel.periodDailyUsage.collectLatest { daily ->
                    periodDayCount = daily.size
                    binding.chartPeriod.markersAllPoints = viewModel.period.value == StatsPeriod.WEEKLY
                    binding.chartPeriod.setData(daily.map { it / 60_000f })
                }
            }
        }
    }

    private fun bindTrendBadge(comparison: PeriodComparison?) {
        val badge = binding.tvTrend
        if (comparison == null || comparison.previousMs <= 0L) {
            badge.visibility = View.GONE
            return
        }
        val deltaPct = ((comparison.currentMs - comparison.previousMs) * 100 / comparison.previousMs).toInt()
        if (deltaPct == 0) {
            badge.visibility = View.GONE
            return
        }
        badge.visibility = View.VISIBLE
        badge.text = if (deltaPct < 0) {
            getString(R.string.stats_trend_down, -deltaPct)
        } else {
            getString(R.string.stats_trend_up, deltaPct)
        }
        // Less screen time = calming teal; more = glass white (no alarm, just a nudge).
        badge.setBackgroundResource(
            if (deltaPct < 0) R.drawable.bg_badge_teal else R.drawable.bg_badge_glass
        )
    }

    /** Crossfades the content when switching Daily/Weekly/Monthly. */
    private fun animatePeriodChange(period: StatsPeriod) {
        val previous = lastPeriod
        lastPeriod = period
        if (previous == null || previous == period) return
        binding.dashboardContent.alpha = 0f
        binding.dashboardContent.translationY = 24f
        binding.dashboardContent.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(250)
            .setInterpolator(DecelerateInterpolator())
            .start()
        val visibleList = if (period == StatsPeriod.DAILY) binding.rvTopApps else binding.rvPeriodApps
        visibleList.scheduleLayoutAnimation()
    }

    /** Slides the whole content sideways when the user browses to another day. */
    private fun animateDayChange(newDate: String) {
        val previous = lastShownDate
        lastShownDate = newDate
        if (previous == null || previous == newDate) return
        // ISO yyyy-MM-dd strings compare chronologically.
        val direction = if (newDate > previous) 1f else -1f
        binding.dashboardContent.translationX = 48f * direction
        binding.dashboardContent.alpha = 0.4f
        binding.dashboardContent.animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(240)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    // The dashboard is the only full-bleed orange screen, so the status bar is
    // recolored while it is visible and restored for the light screens on leave.
    override fun onStart() {
        super.onStart()
        setStatusBar(R.color.dash_bg_top, lightIcons = false)
    }

    override fun onStop() {
        setStatusBar(R.color.surface_container_low, lightIcons = true)
        super.onStop()
    }

    private fun setStatusBar(colorRes: Int, lightIcons: Boolean) {
        val window = requireActivity().window
        window.statusBarColor = ContextCompat.getColor(requireContext(), colorRes)
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = lightIcons
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
