package com.example.stayfree.ui.inapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.stayfree.databinding.FragmentInAppBlockBinding
import com.example.stayfree.domain.content.ContentSignatures
import com.example.stayfree.util.PinGate
import com.example.stayfree.util.PinPrompt
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class InAppBlockFragment : Fragment() {

    private var _binding: FragmentInAppBlockBinding? = null
    private val binding get() = _binding!!
    private val viewModel: InAppBlockViewModel by viewModels()
    private lateinit var adapter: InAppBlockAdapter

    @Inject lateinit var pinGate: PinGate
    // One successful PIN entry unlocks loosening changes until the screen is left.
    private var pinUnlocked = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentInAppBlockBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.initDefaultTargets()

        adapter = InAppBlockAdapter { entity ->
            if (!entity.isActive) viewModel.toggleTarget(entity)
            else withPinGate(onDenied = { adapter.notifyDataSetChanged() }) {
                viewModel.toggleTarget(entity)
            }
        }
        binding.rvInAppTargets.apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = this@InAppBlockFragment.adapter
        }

        binding.switchIgReels.setOnCheckedChangeListener { btn, checked ->
            onContentToggle(btn, ContentSignatures.INSTAGRAM_REELS, checked)
        }
        binding.switchIgStories.setOnCheckedChangeListener { btn, checked ->
            onContentToggle(btn, ContentSignatures.INSTAGRAM_STORIES, checked)
        }
        binding.switchYtShorts.setOnCheckedChangeListener { btn, checked ->
            onContentToggle(btn, ContentSignatures.YOUTUBE_SHORTS, checked)
        }
        binding.switchTiktok.setOnCheckedChangeListener { btn, checked ->
            onContentToggle(btn, ContentSignatures.TIKTOK, checked)
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

    // Enabling protection is always free; disabling it is PIN-gated so the
    // block can't be talked out of in a weak moment.
    private fun onContentToggle(btn: CompoundButton, id: String, checked: Boolean) {
        if (!btn.isPressed) return
        if (checked) {
            viewModel.setContentEnabled(id, true)
            return
        }
        withPinGate(onDenied = { btn.isChecked = true }) {
            viewModel.setContentEnabled(id, false)
        }
    }

    private fun withPinGate(onDenied: () -> Unit = {}, action: () -> Unit) {
        if (pinUnlocked) {
            action()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            if (!pinGate.isPinSet()) {
                action()
                return@launch
            }
            PinPrompt.show(
                requireContext(), viewLifecycleOwner.lifecycleScope, pinGate,
                onCancel = onDenied
            ) {
                pinUnlocked = true
                action()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
