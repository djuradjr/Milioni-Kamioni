package com.example.stayfree.ui.blocking

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.core.content.ContextCompat
import com.example.stayfree.R
import com.example.stayfree.data.billing.PremiumRepository
import com.example.stayfree.databinding.FragmentSleepModeBinding
import com.example.stayfree.ui.premium.requirePremium
import com.example.stayfree.util.TimeUtils
import java.util.Locale
import com.example.stayfree.ui.common.bindBackHeader
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private const val MINUTES_PER_DAY = 1440
private const val ARC_STEP_MINUTES = 15
private const val MIN_QUIET_MINUTES = 30
private const val DEFAULT_START_MINUTES = 23 * 60
private const val DEFAULT_END_MINUTES = 7 * 60

@AndroidEntryPoint
class SleepModeFragment : Fragment() {

    @Inject lateinit var premium: PremiumRepository

    private var _binding: FragmentSleepModeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SleepModeViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSleepModeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bindBackHeader(binding.backHeader)

        binding.arcNight.apply {
            range = MINUTES_PER_DAY
            step = ARC_STEP_MINUTES
            minSweep = MIN_QUIET_MINUTES
            arcColor = ContextCompat.getColor(requireContext(), R.color.skor_night)
            startValue = DEFAULT_START_MINUTES
            endValue = DEFAULT_END_MINUTES
            onValueChanged = { start, end -> renderWindow(start, end ?: DEFAULT_END_MINUTES) }
        }
        renderWindow(DEFAULT_START_MINUTES, DEFAULT_END_MINUTES)

        binding.btnSave.setOnClickListener {
            if (!requirePremium(premium)) return@setOnClickListener
            val daysSelected = buildSelectedDays()
            val startMinutes = binding.arcNight.startValue
            val endMinutes = binding.arcNight.endValue ?: DEFAULT_END_MINUTES
            viewModel.saveSleepMode(daysSelected, startMinutes, endMinutes)
            findNavController().popBackStack()
        }

        binding.btnCancel.setOnClickListener { findNavController().popBackStack() }
    }

    /** Clock labels plus how long the quiet window actually lasts. */
    private fun renderWindow(startMinutes: Int, endMinutes: Int) {
        binding.tvStartTime.text = formatClock(startMinutes)
        binding.tvEndTime.text = formatClock(endMinutes)
        val quietMinutes = ((endMinutes - startMinutes) + MINUTES_PER_DAY) % MINUTES_PER_DAY
        binding.tvQuietLength.text = getString(
            R.string.sleep_quiet_length,
            TimeUtils.formatDuration(quietMinutes * 60_000L)
        )
    }

    private fun formatClock(minutes: Int): String =
        String.format(Locale.US, "%02d:%02d", minutes / 60, minutes % 60)

    private fun buildSelectedDays(): String {
        val days = mutableListOf<String>()
        if (binding.cbMon.isChecked) days.add("MON")
        if (binding.cbTue.isChecked) days.add("TUE")
        if (binding.cbWed.isChecked) days.add("WED")
        if (binding.cbThu.isChecked) days.add("THU")
        if (binding.cbFri.isChecked) days.add("FRI")
        if (binding.cbSat.isChecked) days.add("SAT")
        if (binding.cbSun.isChecked) days.add("SUN")
        return days.joinToString(",")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
