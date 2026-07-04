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

    companion object {
        // Hosted on GitHub Pages (gh-pages branch of the app repo); the same URL
        // goes into Play Console -> App content -> Privacy policy.
        private const val PRIVACY_POLICY_URL =
            "https://mentalyill.github.io/Milioni-Kamioni/privacy.html"
    }

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

        val versionName = requireContext().packageManager
            .getPackageInfo(requireContext().packageName, 0).versionName
        binding.tvVersion.text = getString(R.string.settings_version, versionName)

        binding.btnPrivacyPolicy.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL)))
            } catch (e: Exception) {
                // No browser installed — nothing to do.
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            launch {
                viewModel.pinEnabled.collectLatest { enabled ->
                    binding.tvPinSubtitle.setText(
                        if (enabled) R.string.settings_pin_on else R.string.settings_pin_off
                    )
                }
            }
            launch {
                viewModel.dailyResetTime.collectLatest { minutes ->
                    binding.tvResetTime.text = "%02d:%02d".format(minutes / 60, minutes % 60)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
