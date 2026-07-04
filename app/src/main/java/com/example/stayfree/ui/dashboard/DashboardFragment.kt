package com.example.stayfree.ui.dashboard

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
import com.example.stayfree.databinding.FragmentDashboardBinding
import com.example.stayfree.util.TimeUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

@AndroidEntryPoint
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DashboardViewModel by viewModels()
    private lateinit var topAppsAdapter: TopAppsAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupDateNav()
        observeData()
    }

    private fun setupRecyclerView() {
        topAppsAdapter = TopAppsAdapter { appUsage ->
            findNavController().navigate(
                R.id.action_dashboard_to_appDetail,
                bundleOf("packageName" to appUsage.packageName)
            )
        }
        binding.rvTopApps.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = topAppsAdapter
        }
    }

    private fun setupDateNav() {
        binding.btnPrevDay.setOnClickListener { viewModel.goToPreviousDay() }
        binding.btnNextDay.setOnClickListener { viewModel.goToNextDay() }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            launch {
                viewModel.totalScreenTime.collectLatest { ms ->
                    binding.tvTotalScreenTime.text = TimeUtils.formatDuration(ms)
                }
            }
            launch {
                viewModel.totalUnlocks.collectLatest { count ->
                    binding.tvUnlocks.text = count.toString()
                }
            }
            launch {
                viewModel.topApps.collectLatest { apps ->
                    topAppsAdapter.submitList(apps)
                }
            }
            launch {
                viewModel.activeBlockCount.collectLatest { count ->
                    binding.tvActiveBlocks.text = count.toString()
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
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
