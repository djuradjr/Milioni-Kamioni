package com.example.stayfree.ui.blocking

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.stayfree.R
import com.example.stayfree.databinding.FragmentBlockingBinding
import com.google.android.material.transition.MaterialFadeThrough
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

@AndroidEntryPoint
class BlockingFragment : Fragment() {

    private var _binding: FragmentBlockingBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BlockingViewModel by viewModels()
    private lateinit var adapter: BlockRulesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialFadeThrough()
        exitTransition = MaterialFadeThrough()
        reenterTransition = MaterialFadeThrough()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBlockingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = BlockRulesAdapter(
            onToggle = { id, active -> viewModel.toggleRule(id, active) },
            onDelete = { id -> viewModel.deleteRule(id) },
            onOpenApps = { findNavController().navigate(R.id.action_blocking_to_blockApps) },
            onOpenSites = { findNavController().navigate(R.id.action_blocking_to_website) }
        )
        binding.rvBlockRules.apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = this@BlockingFragment.adapter
        }

        binding.btnFocusMode.setOnClickListener {
            findNavController().navigate(R.id.action_blocking_to_focusMode)
        }

        binding.btnSleepMode.setOnClickListener {
            findNavController().navigate(R.id.action_blocking_to_sleepMode)
        }

        binding.btnWebsiteBlocking.setOnClickListener {
            findNavController().navigate(R.id.action_blocking_to_website)
        }

        binding.btnInAppBlocking.setOnClickListener {
            findNavController().navigate(R.id.action_blocking_to_blockApps)
        }

        observeData()
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            launch {
                viewModel.activeBlocks.collectLatest { items ->
                    adapter.submitList(items)
                    binding.tvRulesCount.text =
                        resources.getString(R.string.blocking_rules_count, items.size)
                    val empty = items.isEmpty()
                    binding.emptyState.visibility = if (empty) View.VISIBLE else View.GONE
                    binding.rvBlockRules.visibility = if (empty) View.GONE else View.VISIBLE
                }
            }
            launch {
                viewModel.focusActive.collectLatest { active ->
                    bindStatus(
                        binding.statusFocus, active,
                        getString(if (active) R.string.blocking_status_active else R.string.blocking_status_off)
                    )
                }
            }
            launch {
                viewModel.sleepEndMinutes.collectLatest { end ->
                    bindStatus(
                        binding.statusSleep, end != null,
                        if (end != null) {
                            val time = String.format(Locale.US, "%02d:%02d", end / 60, end % 60)
                            getString(R.string.blocking_status_until, time)
                        } else getString(R.string.blocking_status_off)
                    )
                }
            }
            launch {
                viewModel.activeWebsiteCount.collectLatest { count ->
                    bindStatus(
                        binding.statusWebsites, count > 0,
                        resources.getQuantityString(R.plurals.blocking_sites_status, count, count)
                    )
                }
            }
            launch {
                viewModel.contentTargetCount.collectLatest { count ->
                    bindStatus(
                        binding.statusBlockApps, count > 0,
                        resources.getQuantityString(R.plurals.blocking_targets_status, count, count)
                    )
                }
            }
        }
    }

    /** Active statuses go amber; off states stay muted white. */
    private fun bindStatus(view: TextView, active: Boolean, text: String) {
        view.text = text
        view.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (active) R.color.dash_amber else R.color.dash_status_off
            )
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
