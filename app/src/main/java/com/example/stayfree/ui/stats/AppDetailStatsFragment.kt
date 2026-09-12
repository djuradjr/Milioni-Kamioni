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
import androidx.core.content.ContextCompat
import com.example.stayfree.domain.score.AppCategory
import com.example.stayfree.util.AppInfoUtils
import kotlin.math.roundToInt
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

        binding.chipCategory.setOnClickListener { viewModel.cycleCategory() }

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
                    binding.tvTodayUnlocks.text = count.toString()
                }
            }
            launch {
                viewModel.category.collectLatest { category -> renderCategory(category) }
            }
            launch {
                viewModel.scoreImpact.collectLatest { points -> renderImpact(points) }
            }
            launch {
                viewModel.weeklyMinutes.collectLatest { list ->
                    binding.barChart.setData(list)
                }
            }
        }
    }

    private fun renderCategory(category: AppCategory) {
        val (label, color, fill) = when (category) {
            AppCategory.DISTRACTION -> Triple(
                R.string.category_distraction, R.color.skor_distraction, R.color.skor_distraction_soft
            )
            AppCategory.NEUTRAL -> Triple(
                R.string.category_neutral, R.color.skor_text_dim, R.color.skor_card_raised
            )
            AppCategory.PRODUCTIVE -> Triple(
                R.string.category_productive, R.color.skor_focus, R.color.skor_focus_soft
            )
        }
        binding.chipCategory.setText(label)
        binding.chipCategory.setTextColor(ContextCompat.getColor(requireContext(), color))
        binding.chipCategory.backgroundTintList =
            ContextCompat.getColorStateList(requireContext(), fill)
    }

    /** Points are shown as a loss; zero reads as "costs you nothing", not "-0". */
    private fun renderImpact(points: Double) {
        val rounded = points.roundToInt()
        if (rounded <= 0) {
            binding.tvScoreImpact.text = getString(R.string.dashboard_no_peak)
            binding.tvScoreImpact.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.skor_text_dim)
            )
            binding.tvScoreImpactSub.setText(R.string.app_detail_score_impact_none)
        } else {
            binding.tvScoreImpact.text = getString(R.string.app_detail_points_lost, rounded)
            binding.tvScoreImpact.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.skor_distraction)
            )
            binding.tvScoreImpactSub.setText(R.string.app_detail_score_impact_sub)
        }
        // The bar reads against a full 100-point day.
        binding.trackImpact.post {
            val parentWidth = (binding.trackImpact.parent as View).width
            val usable = parentWidth - binding.trackImpact.paddingStart
            binding.trackImpact.layoutParams = binding.trackImpact.layoutParams.apply {
                val minVisible = (12 * resources.displayMetrics.density).toInt()
                val scaled = (usable * (rounded.coerceIn(0, 100) / 100f)).toInt()
                width = if (rounded > 0) scaled.coerceAtLeast(minVisible) else usable
            }
            binding.trackImpact.backgroundTintList = ContextCompat.getColorStateList(
                requireContext(),
                if (rounded > 0) R.color.skor_distraction else R.color.skor_track
            )
            binding.trackImpact.requestLayout()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
