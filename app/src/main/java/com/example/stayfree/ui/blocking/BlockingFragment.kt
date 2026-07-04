package com.example.stayfree.ui.blocking

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.stayfree.R
import com.example.stayfree.databinding.FragmentBlockingBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BlockingFragment : Fragment() {

    private var _binding: FragmentBlockingBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BlockingViewModel by viewModels()
    private lateinit var adapter: BlockRulesAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBlockingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = BlockRulesAdapter(
            onToggle = { id, active -> viewModel.toggleRule(id, active) },
            onDelete = { id -> viewModel.deleteRule(id) }
        )
        binding.rvBlockRules.apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = this@BlockingFragment.adapter
        }

        binding.fab.setOnClickListener {
            findNavController().navigate(R.id.action_blocking_to_addRule)
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

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.allRules.collectLatest { rules ->
                adapter.submitList(rules)
                val active = rules.count { it.isActive }
                binding.tvRulesCount.text =
                    resources.getString(R.string.blocking_rules_count, rules.size, active)
                val empty = rules.isEmpty()
                binding.emptyState.visibility = if (empty) View.VISIBLE else View.GONE
                binding.rvBlockRules.visibility = if (empty) View.GONE else View.VISIBLE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
