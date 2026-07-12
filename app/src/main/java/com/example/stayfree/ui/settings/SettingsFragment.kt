package com.example.stayfree.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.core.content.ContextCompat
import androidx.core.os.ConfigurationCompat
import androidx.core.os.LocaleListCompat
import androidx.navigation.fragment.findNavController
import com.example.stayfree.R
import com.example.stayfree.data.local.preferences.AppPreferences
import com.example.stayfree.databinding.FragmentSettingsBinding
import com.example.stayfree.util.AppearanceModes
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.transition.MaterialFadeThrough
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    companion object {
        // Hosted on GitHub Pages (gh-pages branch of the app repo); the same URL
        // goes into Play Console -> App content -> Privacy policy.
        private const val PRIVACY_POLICY_URL =
            "https://djuradjr.github.io/Milioni-Kamioni/privacy.html"
    }

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialFadeThrough()
        exitTransition = MaterialFadeThrough()
        reenterTransition = MaterialFadeThrough()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvLanguage.setText(
            if (currentLanguageTag() == "sr") R.string.language_serbian else R.string.language_english
        )
        binding.btnLanguage.setOnClickListener { showLanguageDialog() }
        binding.btnAppearance.setOnClickListener { showAppearanceDialog() }

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
                    // Protected state reads as a calm teal "all good" signal.
                    binding.tvPinSubtitle.setTextColor(
                        ContextCompat.getColor(
                            requireContext(),
                            if (enabled) R.color.accent_teal else R.color.on_surface_variant
                        )
                    )
                }
            }
            launch {
                viewModel.dailyResetTime.collectLatest { minutes ->
                    binding.tvResetTime.text = "%02d:%02d".format(minutes / 60, minutes % 60)
                }
            }
            launch {
                viewModel.appearanceMode.collectLatest { mode ->
                    binding.tvAppearance.setText(
                        if (mode == AppPreferences.APPEARANCE_DARK) R.string.appearance_dark
                        else R.string.appearance_light
                    )
                }
            }
        }
    }

    private fun currentLanguageTag(): String {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        val language = if (appLocales.isEmpty) {
            // No explicit choice yet — reflect the resolved system language.
            ConfigurationCompat.getLocales(resources.configuration)[0]?.language
        } else {
            appLocales[0]?.language
        }
        return if (language == "sr") "sr" else "en"
    }

    private fun showAppearanceDialog() {
        val modes = arrayOf(AppPreferences.APPEARANCE_LIGHT, AppPreferences.APPEARANCE_DARK)
        val names = arrayOf(getString(R.string.appearance_light), getString(R.string.appearance_dark))
        val checked = modes.indexOf(viewModel.appearanceMode.value)
            .coerceAtLeast(0) // "system" is stored-only; show it as Light
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_appearance)
            .setSingleChoiceItems(names, checked) { dialog, which ->
                dialog.dismiss()
                if (which != checked) {
                    viewModel.setAppearanceMode(modes[which])
                    AppCompatDelegate.setDefaultNightMode(AppearanceModes.toNightMode(modes[which]))
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun showLanguageDialog() {
        val tags = arrayOf("en", "sr")
        val names = arrayOf(getString(R.string.language_english), getString(R.string.language_serbian))
        val checked = tags.indexOf(currentLanguageTag())
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_language)
            .setSingleChoiceItems(names, checked) { dialog, which ->
                dialog.dismiss()
                if (which != checked) {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tags[which]))
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
