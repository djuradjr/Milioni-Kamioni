package com.example.stayfree.ui.stats

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.stayfree.R
import com.example.stayfree.databinding.FragmentAppDetailStatsBinding
import com.example.stayfree.ui.common.bindBackHeader
import com.example.stayfree.util.AppInfoUtils
import com.example.stayfree.util.TimeUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AppDetailStatsFragment : Fragment() {

    private var _binding: FragmentAppDetailStatsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AppDetailStatsViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAppDetailStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bindBackHeader(binding.backHeader)

        val packageName = arguments?.getString("packageName") ?: return
        viewModel.loadApp(packageName)

        val appName = AppInfoUtils.getAppName(requireContext(), packageName)
        val icon = AppInfoUtils.getAppIcon(requireContext(), packageName)
        binding.tvAppName.text = appName
        if (icon != null) binding.ivAppIcon.setImageDrawable(icon)

        binding.btnBlockApp.setOnClickListener {
            val bundle = Bundle().apply { putLong("ruleId", -1L) }
            findNavController().navigate(R.id.addBlockRuleFragment, bundle)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            launch {
                viewModel.todayUsage.collectLatest { ms ->
                    binding.tvTodayUsage.text = TimeUtils.formatDuration(ms)
                }
            }
            launch {
                viewModel.todayUnlocks.collectLatest { count ->
                    binding.tvTodayUnlocks.text = getString(R.string.stats_unlocks_today, count)
                }
            }
            launch {
                viewModel.weeklyUsage.collectLatest { list ->
                    binding.barChart.setData(list.map { it.totalTimeMs.toFloat() / 60_000f })
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
