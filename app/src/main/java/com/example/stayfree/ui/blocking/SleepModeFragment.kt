package com.example.stayfree.ui.blocking

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.stayfree.databinding.FragmentSleepModeBinding
import com.example.stayfree.ui.common.bindBackHeader
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SleepModeFragment : Fragment() {

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

        binding.btnSave.setOnClickListener {
            val daysSelected = buildSelectedDays()
            val startMinutes = binding.tpStart.hour * 60 + binding.tpStart.minute
            val endMinutes = binding.tpEnd.hour * 60 + binding.tpEnd.minute
            viewModel.saveSleepMode(daysSelected, startMinutes, endMinutes)
            findNavController().popBackStack()
        }

        binding.btnCancel.setOnClickListener { findNavController().popBackStack() }
    }

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
