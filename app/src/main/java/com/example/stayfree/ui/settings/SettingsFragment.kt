package com.example.stayfree.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
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
import com.example.stayfree.databinding.DialogAccountEditBinding
import com.example.stayfree.databinding.FragmentSettingsBinding
import com.example.stayfree.util.AppearanceModes
import com.example.stayfree.util.PinGate
import com.example.stayfree.util.PinPrompt
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.transition.MaterialFadeThrough
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

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

    @Inject lateinit var pinGate: PinGate

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
        binding.btnAccount.setOnClickListener { showAccountDialog() }

        binding.btnSetPin.setOnClickListener {
            if (viewModel.pinEnabled.value) showPinActionsDialog()
            else findNavController().navigate(R.id.action_settings_to_pin)
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
            launch {
                viewModel.profileUsername.collectLatest { name ->
                    binding.tvAccountName.text =
                        name.ifBlank { getString(R.string.account_name_placeholder) }
                }
            }
            launch {
                viewModel.profileEmail.collectLatest { email ->
                    binding.tvAccountEmail.text =
                        email.ifBlank { getString(R.string.account_email_placeholder) }
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

    private fun showPinActionsDialog() {
        val actions = arrayOf(getString(R.string.settings_change_pin), getString(R.string.settings_remove_pin))
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_pin_code)
            .setItems(actions) { _, which ->
                val scope = viewLifecycleOwner.lifecycleScope
                when (which) {
                    0 -> PinPrompt.show(requireContext(), scope, pinGate) {
                        findNavController().navigate(R.id.action_settings_to_pin)
                    }
                    1 -> PinPrompt.show(requireContext(), scope, pinGate) {
                        viewModel.clearPin()
                        Toast.makeText(requireContext(), R.string.pin_removed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun showAccountDialog() {
        val dialogBinding = DialogAccountEditBinding.inflate(layoutInflater)
        dialogBinding.etUsername.setText(viewModel.profileUsername.value)
        dialogBinding.etEmail.setText(viewModel.profileEmail.value)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_account)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.btn_save, null)
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
        // Manual click handler so an invalid email keeps the dialog open.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = dialogBinding.etUsername.text.toString().trim()
            val email = dialogBinding.etEmail.text.toString().trim()
            if (email.isNotEmpty() && !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                dialogBinding.tilEmail.error = getString(R.string.account_invalid_email)
                return@setOnClickListener
            }
            viewModel.setProfile(name, email)
            dialog.dismiss()
        }
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
