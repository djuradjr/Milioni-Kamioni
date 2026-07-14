package com.example.stayfree.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.stayfree.databinding.FragmentResetTimeBinding
import com.example.stayfree.ui.common.bindBackHeader
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ResetTimeFragment : Fragment() {

    private var _binding: FragmentResetTimeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentResetTimeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bindBackHeader(binding.backHeader)

        binding.btnSave.setOnClickListener {
            val hour = binding.timePicker.hour
            val minute = binding.timePicker.minute
            val totalMinutes = hour * 60 + minute
            viewModel.setDailyResetTime(totalMinutes)
            findNavController().popBackStack()
        }

        binding.btnCancel.setOnClickListener { findNavController().popBackStack() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
