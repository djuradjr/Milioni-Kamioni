package com.example.stayfree.ui.stats

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.stayfree.R
import com.example.stayfree.databinding.FragmentStatsBinding
import com.example.stayfree.util.TimeUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class StatsFragment : Fragment() {

    private var _binding: FragmentStatsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: StatsViewModel by viewModels()
    private lateinit var appsAdapter: AppUsageListAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupTabs()
        setupRecyclerView()
        observeData()
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
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

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            launch {
                viewModel.totalScreenTime.collectLatest { ms ->
                    binding.tvTotalTime.text = TimeUtils.formatDuration(ms)
                }
            }
            launch {
                viewModel.totalUnlocks.collectLatest { count ->
                    binding.tvTotalUnlocks.text = getString(R.string.stats_unlocks_count, count)
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

    private fun updateChart(usageList: List<com.example.stayfree.domain.model.AppUsage>) {
        binding.barChart.setData(usageList.map { it.totalTimeMs.toFloat() / 60_000f })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
