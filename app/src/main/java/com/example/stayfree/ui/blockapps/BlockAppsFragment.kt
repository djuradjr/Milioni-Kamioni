package com.example.stayfree.ui.blockapps

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.stayfree.databinding.FragmentBlockAppsBinding
import com.example.stayfree.domain.content.ContentSignatures
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BlockAppsFragment : Fragment() {

    private var _binding: FragmentBlockAppsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BlockAppsViewModel by viewModels()
    private lateinit var adapter: BlockAppsAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBlockAppsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = BlockAppsAdapter { pkg, blocked -> viewModel.setBlocked(pkg, blocked) }
        binding.rvBlockApps.apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = this@BlockAppsFragment.adapter
        }

        // Content-block toggles (only react to real user taps, not programmatic checks)
        binding.switchIgReels.setOnCheckedChangeListener { btn, checked ->
            if (btn.isPressed) viewModel.setContentEnabled(ContentSignatures.INSTAGRAM_REELS, checked)
        }
        binding.switchIgStories.setOnCheckedChangeListener { btn, checked ->
            if (btn.isPressed) viewModel.setContentEnabled(ContentSignatures.INSTAGRAM_STORIES, checked)
        }
        binding.switchYtShorts.setOnCheckedChangeListener { btn, checked ->
            if (btn.isPressed) viewModel.setContentEnabled(ContentSignatures.YOUTUBE_SHORTS, checked)
        }
        binding.switchTiktok.setOnCheckedChangeListener { btn, checked ->
            if (btn.isPressed) viewModel.setContentEnabled(ContentSignatures.TIKTOK, checked)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            launch {
                viewModel.items.collectLatest { list ->
                    adapter.submitList(list)
                    binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                    binding.rvBlockApps.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
                }
            }
            launch {
                viewModel.contentBlockEnabledIds.collectLatest { enabled ->
                    binding.switchIgReels.isChecked = ContentSignatures.INSTAGRAM_REELS in enabled
                    binding.switchIgStories.isChecked = ContentSignatures.INSTAGRAM_STORIES in enabled
                    binding.switchYtShorts.isChecked = ContentSignatures.YOUTUBE_SHORTS in enabled
                    binding.switchTiktok.isChecked = ContentSignatures.TIKTOK in enabled
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
