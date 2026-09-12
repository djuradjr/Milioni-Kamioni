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
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.stayfree.R
import com.example.stayfree.databinding.FragmentDashboardBinding
import com.example.stayfree.domain.model.AppUsage
import com.example.stayfree.domain.score.FocusScore
import com.example.stayfree.ui.common.CountUp
import com.example.stayfree.util.TimeUtils
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
            segment.setBackgroundResource(if (active) R.drawable.bg_skor_seg_active else 0)
            segment.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (active) R.color.skor_text else R.color.skor_text_dim
                )
            )
            segment.translationZ = if (active) 2f * resources.displayMetrics.density else 0f
        }
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
        // The hourly chart is HourlyBarsView now; dragging it reports an hour
        // instead of showing a tooltip, so the peak caption doubles as the readout.
        binding.chartHourly.onHourSelected = { hour ->
            binding.tvPeakTime.text = if (hour == null) {
                peakCaption(viewModel.peakHour.value)
            } else {
                val (distraction, other) = viewModel.hourlyBreakdown.value.getOrNull(hour)
                    ?: (0L to 0L)
                getString(
                    R.string.dashboard_hour_readout,
                    hour,
                    TimeUtils.formatDuration(distraction + other)
                )
            }
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

    private fun peakCaption(hour: Int?): String =
        if (hour == null) getString(R.string.dashboard_no_peak)
        else getString(R.string.dashboard_peak_prefix, String.format(Locale.US, "%02dh", hour))

    /** Ring and number share the band colour so the state reads without the digits. */
    private fun scoreColor(score: Int): Int = when (FocusScore.bandOf(score)) {
        FocusScore.Band.GOOD -> R.color.skor_focus
        FocusScore.Band.MIXED -> R.color.skor_warn
        FocusScore.Band.POOR -> R.color.skor_distraction
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
                    val remainingMin = (goal.remainingMs / 60_000L).toInt()
                    val over = goal.remainingMs <= 0
                    binding.tvGoalChip.text = if (over) {
                        getString(
                            R.string.dashboard_goal_chip_over,
                            formatMinutesCompact((-remainingMin).coerceAtLeast(1))
                        )
                    } else {
                        getString(
                            R.string.dashboard_goal_chip_left,
                            formatMinutesCompact(remainingMin.coerceAtLeast(1))
                        )
                    }
                    val tint = if (over) R.color.skor_distraction else R.color.skor_focus
                    val fill = if (over) R.color.skor_distraction_soft else R.color.skor_focus_soft
                    binding.tvGoalChip.setTextColor(ContextCompat.getColor(requireContext(), tint))
                    binding.tvGoalChip.backgroundTintList =
                        ContextCompat.getColorStateList(requireContext(), fill)
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
                // collect, not collectLatest: setData animates, and a cancelled
                // block would leave the legend showing the previous (0s) totals.
                viewModel.hourlyBreakdown.collect { hours ->
                    binding.chartHourly.setData(hours)
                    val distraction = hours.sumOf { it.first }
                    val other = hours.sumOf { it.second }
                    binding.tvLegendDistraction.text = getString(
                        R.string.dashboard_legend_distraction, TimeUtils.formatDuration(distraction)
                    )
                    binding.tvLegendOther.text = getString(
                        R.string.dashboard_legend_other, TimeUtils.formatDuration(other)
                    )
                }
            }
            launch {
                viewModel.focusScore.collectLatest { breakdown ->
                    if (breakdown == null) {
                        // No data yet: a dash, never a zero — zero reads as "you failed".
                        binding.tvScore.text = getString(R.string.dashboard_no_peak)
                        binding.scoreRing.setProgress(0f)
                        return@collectLatest
                    }
                    CountUp.animate(binding.tvScore, breakdown.score.toLong()) { it.toString() }
                    val color = ContextCompat.getColor(requireContext(), scoreColor(breakdown.score))
                    binding.scoreRing.progressColor = color
                    binding.tvScore.setTextColor(color)
                    binding.scoreRing.setProgress(breakdown.score / 100f)
                }
            }
            launch {
                viewModel.categoryResolver.collectLatest { resolver ->
                    topAppsAdapter.categoryOf = resolver
                    periodAppsAdapter.categoryOf = resolver
                }
            }
            launch {
                viewModel.bestStreak.collectLatest { days ->
                    CountUp.animate(binding.tvStreak, days.toLong()) { it.toString() }
                }
            }
            launch {
                viewModel.peakHour.collectLatest { hour ->
                    binding.tvPeakTime.text = peakCaption(hour)
                }
            }
            launch {
                viewModel.periodAverageScreenTime.collectLatest { ms ->
                    CountUp.animate(binding.tvPeriodTotalTime, ms) { TimeUtils.formatDuration(it) }
                }
            }
            launch {
                viewModel.periodTotalScreenTime.collectLatest { ms ->
                    CountUp.animate(binding.tvPeriodTotalSum, ms) { TimeUtils.formatDuration(it) }
                }
            }
            launch {
                viewModel.periodTotalUnlocks.collectLatest { count ->
                    CountUp.animate(binding.tvPeriodUnlocks, count.toLong()) { it.toString() }
                }
            }
            launch {
                viewModel.screenTimeComparison.collectLatest { comparison ->
                    bindTrendBadge(comparison)
                }
            }
            launch {
                viewModel.periodAverageScore.collectLatest { score ->
                    if (score == null) {
                        binding.tvPeriodScore.text = getString(R.string.dashboard_no_peak)
                        binding.periodScoreRing.setProgress(0f)
                        return@collectLatest
                    }
                    CountUp.animate(binding.tvPeriodScore, score.toLong()) { it.toString() }
                    val color = ContextCompat.getColor(requireContext(), scoreColor(score))
                    binding.periodScoreRing.progressColor = color
                    binding.tvPeriodScore.setTextColor(color)
                    binding.periodScoreRing.setProgress(score / 100f)
                }
            }
            launch {
                viewModel.periodDaysInGoal.collectLatest { (inGoal, total) ->
                    binding.tvPeriodInGoal.text =
                        getString(R.string.dashboard_period_in_goal_value, inGoal, total)
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
        // Less screen time reads as a win; more is a nudge, never an alarm.
        val (fill, text) = if (deltaPct < 0) {
            R.color.skor_focus_soft to R.color.skor_focus
        } else {
            R.color.skor_warn_soft to R.color.skor_warn
        }
        badge.backgroundTintList = ContextCompat.getColorStateList(requireContext(), fill)
        badge.setTextColor(ContextCompat.getColor(requireContext(), text))
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
