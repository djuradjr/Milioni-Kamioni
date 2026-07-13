package com.example.stayfree.ui.dashboard

import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.stayfree.R
import com.example.stayfree.databinding.FragmentDashboardBinding
import com.example.stayfree.domain.model.AppUsage
import com.example.stayfree.ui.common.CountUp
import com.example.stayfree.util.TimeUtils
import com.google.android.material.tabs.TabLayout
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

    /** Apps currently shown in the period chart, for the tap tooltip. */
    private var chartApps: List<AppUsage> = emptyList()

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
        setupTabs()
        setupRecyclerViews()
        setupDateNav()
        setupCharts()
        observeData()
    }

    private fun setupTabs() {
        // Re-select the VM's period before attaching the listener, so a config
        // change doesn't leave tab 0 highlighted while period content is shown.
        binding.tabLayout.getTabAt(viewModel.period.value.ordinal)?.select()
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                tab.view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                viewModel.setPeriod(when (tab.position) {
                    0 -> StatsPeriod.DAILY
                    1 -> StatsPeriod.WEEKLY
                    else -> StatsPeriod.MONTHLY
                })
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
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
        binding.chartHourly.tooltipFormatter = { hour, minutes ->
            String.format(
                Locale.US, "%02d:00 · %s",
                hour, TimeUtils.formatDuration((minutes * 60_000f).toLong())
            )
        }
        binding.chartPeriod.tooltipFormatter = { index, _ ->
            chartApps.getOrNull(index)?.let {
                "${it.appName} · ${TimeUtils.formatDuration(it.totalTimeMs)}"
            } ?: ""
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            launch {
                viewModel.period.collectLatest { period ->
                    val daily = period == StatsPeriod.DAILY
                    binding.dailyContent.visibility = if (daily) View.VISIBLE else View.GONE
                    binding.periodContent.visibility = if (daily) View.GONE else View.VISIBLE
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
                viewModel.activeBlockCount.collectLatest { count ->
                    CountUp.animate(binding.tvActiveBlocks, count.toLong()) { it.toString() }
                }
            }
            launch {
                viewModel.selectedDate.collectLatest { date ->
                    val today = date == TimeUtils.getTodayString()
                    binding.tvSelectedDate.text =
                        if (today) getString(R.string.dashboard_today)
                        else TimeUtils.formatDisplayDate(date)
                    binding.btnNextDay.isEnabled = !today
                    binding.btnNextDay.alpha = if (today) 0.3f else 1f
                    animateDayChange(date)
                }
            }
            launch {
                viewModel.hourlyUsage.collectLatest { buckets ->
                    val peak = buckets.maxOrNull()?.takeIf { it > 0 }?.let { buckets.indexOf(it) } ?: -1
                    binding.chartHourly.setData(buckets.map { it / 60_000f }, peak)
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
                viewModel.periodTotalScreenTime.collectLatest { ms ->
                    CountUp.animate(binding.tvPeriodTotalTime, ms) { TimeUtils.formatDuration(it) }
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
                    chartApps = list.take(7)
                    binding.chartPeriod.setData(chartApps.map { it.totalTimeMs.toFloat() / 60_000f })
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
