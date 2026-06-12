package com.example.stayfree.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.stayfree.R
import com.example.stayfree.databinding.FragmentSettingsBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnSetPin.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_pin)
        }

        binding.btnResetTime.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_resetTime)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            launch {
                viewModel.pinEnabled.collectLatest { enabled ->
                    binding.btnSetPin.text = if (enabled) "Change PIN" else "Set PIN"
                }
            }
            launch {
                viewModel.dailyResetTime.collectLatest { minutes ->
                    val h = minutes / 60
                    val m = minutes % 60
                    binding.tvResetTime.text = "Daily reset: %02d:%02d".format(h, m)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
