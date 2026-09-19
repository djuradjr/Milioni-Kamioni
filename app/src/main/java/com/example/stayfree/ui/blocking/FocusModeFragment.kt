package com.example.stayfree.ui.blocking

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.core.content.ContextCompat
import com.example.stayfree.R
import com.example.stayfree.data.billing.PremiumRepository
import com.example.stayfree.databinding.FragmentFocusModeBinding
import com.example.stayfree.ui.common.bindBackHeader
import com.example.stayfree.ui.premium.requirePremium
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val ARC_RANGE_MINUTES = 120
private const val ARC_STEP_MINUTES = 5

@AndroidEntryPoint
class FocusModeFragment : Fragment() {

    @Inject lateinit var premium: PremiumRepository

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

        binding.arcDuration.apply {
            range = ARC_RANGE_MINUTES
            step = ARC_STEP_MINUTES
            arcColor = ContextCompat.getColor(requireContext(), R.color.skor_focus)
            onValueChanged = { minutes, _ -> viewModel.setDurationMinutes(minutes) }
        }

        // Chips are shortcuts onto the same value the ring drives.
        binding.chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val minutes = when (checkedIds.firstOrNull()) {
                binding.chip25min.id -> 25
                binding.chip45min.id -> 45
                binding.chip60min.id -> 60
                binding.chip90min.id -> 90
                else -> null
            }
            if (minutes != null) viewModel.setDurationMinutes(minutes)
        }

        binding.switchMode.setOnCheckedChangeListener { _, checked ->
            // true = whitelist mode, false = blacklist mode
            viewModel.setWhitelistMode(checked)
        }

        binding.btnStartFocus.setOnClickListener {
            if (!requirePremium(premium)) return@setOnClickListener
            viewModel.startFocusMode()
            findNavController().popBackStack()
        }

        binding.btnStopFocus.setOnClickListener {
            viewModel.stopFocusMode()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.durationMinutes.collectLatest { minutes ->
                // Don't fight the finger: the ring already shows this value while dragging.
                if (binding.arcDuration.startValue != minutes) {
                    binding.arcDuration.startValue = minutes
                }
                binding.tvDuration.text = minutes.toString()
                binding.btnStartFocus.text =
                    getString(R.string.focus_start_with_length, minutes)
                syncChips(minutes)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isFocusActive.collectLatest { active ->
                binding.btnStartFocus.visibility = if (active) View.GONE else View.VISIBLE
                binding.btnStopFocus.visibility = if (active) View.VISIBLE else View.GONE
                // While a session runs the ring is a readout, not a control.
                binding.tvCountdown.visibility = if (active) View.VISIBLE else View.GONE
                binding.tvDuration.visibility = if (active) View.GONE else View.VISIBLE
                binding.chipGroup.visibility = if (active) View.GONE else View.VISIBLE
                binding.tvHint.visibility = if (active) View.GONE else View.VISIBLE
                binding.arcDuration.isEnabled = !active
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

    /** Checks the chip that matches [minutes], or clears them for a custom length. */
    private fun syncChips(minutes: Int) {
        val chipId = when (minutes) {
            25 -> binding.chip25min.id
            45 -> binding.chip45min.id
            60 -> binding.chip60min.id
            90 -> binding.chip90min.id
            else -> null
        }
        if (chipId == null) binding.chipGroup.clearCheck()
        else if (binding.chipGroup.checkedChipId != chipId) binding.chipGroup.check(chipId)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
