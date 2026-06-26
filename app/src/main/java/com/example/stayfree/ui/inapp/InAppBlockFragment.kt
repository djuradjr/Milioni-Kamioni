package com.example.stayfree.ui.inapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.stayfree.databinding.FragmentInAppBlockBinding
import com.example.stayfree.domain.content.ContentSignatures
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class InAppBlockFragment : Fragment() {

    private var _binding: FragmentInAppBlockBinding? = null
    private val binding get() = _binding!!
    private val viewModel: InAppBlockViewModel by viewModels()
    private lateinit var adapter: InAppBlockAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentInAppBlockBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.initDefaultTargets()

        adapter = InAppBlockAdapter { entity -> viewModel.toggleTarget(entity) }
        binding.rvInAppTargets.apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = this@InAppBlockFragment.adapter
        }

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
                viewModel.allTargets.collectLatest { list ->
                    adapter.submitList(list)
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
