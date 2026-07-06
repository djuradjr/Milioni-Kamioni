package com.example.stayfree.ui.stats

import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.stayfree.R
import com.example.stayfree.databinding.FragmentStatsBinding
import com.example.stayfree.domain.model.AppUsage
import com.example.stayfree.ui.common.CountUp
import com.example.stayfree.util.TimeUtils
import com.google.android.material.transition.MaterialFadeThrough
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class StatsFragment : Fragment() {

    private var _binding: FragmentStatsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: StatsViewModel by viewModels()
    private lateinit var appsAdapter: AppUsageListAdapter

    /** Apps currently shown in the chart, for the tap tooltip. */
    private var chartApps: List<AppUsage> = emptyList()
    private var lastPeriod: StatsPeriod? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialFadeThrough()
        exitTransition = MaterialFadeThrough()
        reenterTransition = MaterialFadeThrough()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupTabs()
        setupRecyclerView()
        setupChart()
        observeData()
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                tab.view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                viewModel.setPeriod(when (tab.position) {
                    0 -> StatsPeriod.DAILY
                    1 -> StatsPeriod.WEEKLY
                    else -> StatsPeriod.MONTHLY
                })
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
        })
    }

    private fun setupRecyclerView() {
        appsAdapter = AppUsageListAdapter { appUsage ->
            findNavController().navigate(
                R.id.action_stats_to_appDetail,
                bundleOf("packageName" to appUsage.packageName)
            )
        }
        binding.rvApps.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = appsAdapter
        }
    }

    private fun setupChart() {
        binding.barChart.tooltipFormatter = { index, _ ->
            chartApps.getOrNull(index)?.let {
                "${it.appName} · ${TimeUtils.formatDuration(it.totalTimeMs)}"
            } ?: ""
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            launch {
                viewModel.period.collectLatest { period ->
                    animatePeriodChange(period)
                }
            }
            launch {
                viewModel.totalScreenTime.collectLatest { ms ->
                    CountUp.animate(binding.tvTotalTime, ms) { TimeUtils.formatDuration(it) }
                }
            }
            launch {
                viewModel.totalUnlocks.collectLatest { count ->
                    binding.tvTotalUnlocks.text = getString(R.string.stats_unlocks_count, count)
                }
            }
            launch {
                viewModel.screenTimeComparison.collectLatest { comparison ->
                    bindTrendBadge(comparison)
                }
            }
            launch {
                viewModel.appUsageList.collectLatest { list ->
                    appsAdapter.submitList(list)
                    updateChart(list.take(7))
                    val empty = list.isEmpty()
                    binding.emptyState.visibility = if (empty) View.VISIBLE else View.GONE
                    binding.rvApps.visibility = if (empty) View.GONE else View.VISIBLE
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
        binding.contentContainer.alpha = 0f
        binding.contentContainer.translationY = 24f
        binding.contentContainer.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(250)
            .setInterpolator(DecelerateInterpolator())
            .start()
        binding.rvApps.scheduleLayoutAnimation()
    }

    private fun updateChart(usageList: List<AppUsage>) {
        chartApps = usageList
        binding.barChart.setData(usageList.map { it.totalTimeMs.toFloat() / 60_000f })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
