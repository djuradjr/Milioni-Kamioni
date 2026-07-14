package com.example.stayfree.ui.blocking

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.stayfree.databinding.FragmentFocusModeBinding
import com.example.stayfree.ui.common.bindBackHeader
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FocusModeFragment : Fragment() {

    private var _binding: FragmentFocusModeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: FocusModeViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFocusModeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bindBackHeader(binding.backHeader)

        // Duration presets
        binding.chipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            val minutes = when (checkedIds.firstOrNull()) {
                binding.chip25min.id -> 25
                binding.chip45min.id -> 45
                binding.chip60min.id -> 60
                else -> binding.npCustomMinutes.value
            }
            viewModel.setDurationMinutes(minutes)
        }

        binding.switchMode.setOnCheckedChangeListener { _, checked ->
            // true = whitelist mode, false = blacklist mode
            viewModel.setWhitelistMode(checked)
        }

        binding.btnStartFocus.setOnClickListener {
            viewModel.startFocusMode()
            findNavController().popBackStack()
        }

        binding.btnStopFocus.setOnClickListener {
            viewModel.stopFocusMode()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isFocusActive.collectLatest { active ->
                binding.btnStartFocus.visibility = if (active) View.GONE else View.VISIBLE
                binding.btnStopFocus.visibility = if (active) View.VISIBLE else View.GONE
                binding.tvCountdown.visibility = if (active) View.VISIBLE else View.GONE
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.remainingMs.collectLatest { ms ->
                val minutes = ms / 60_000
                val seconds = (ms % 60_000) / 1000
                binding.tvCountdown.text = String.format("%02d:%02d", minutes, seconds)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
