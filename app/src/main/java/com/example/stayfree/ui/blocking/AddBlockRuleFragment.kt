package com.example.stayfree.ui.blocking

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.stayfree.R
import com.example.stayfree.databinding.FragmentAddBlockRuleBinding
import com.example.stayfree.util.AppInfoUtils
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AddBlockRuleFragment : Fragment() {

    private var _binding: FragmentAddBlockRuleBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AddBlockRuleViewModel by viewModels()

    private var selectedPackage: String = ""
    private val installedApps by lazy { AppInfoUtils.getInstalledApps(requireContext()) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAddBlockRuleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Populate app picker
        val appNames = installedApps.map { it.appName }
        val appAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, appNames)
        binding.spinnerApp.adapter = appAdapter
        binding.spinnerApp.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>, v: View?, pos: Int, id: Long) {
                selectedPackage = installedApps[pos].packageName
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
        }

        // Block type radio group
        binding.rgBlockType.setOnCheckedChangeListener { _, checkedId ->
            binding.layoutDailyLimit.visibility = if (checkedId == binding.rbDailyLimit.id) View.VISIBLE else View.GONE
            binding.layoutSession.visibility = if (checkedId == binding.rbSession.id) View.VISIBLE else View.GONE
            binding.layoutSchedule.visibility = if (checkedId == binding.rbSchedule.id) View.VISIBLE else View.GONE
            validateInputs()
        }

        setupTimeField(binding.etLimitHours, maxValue = 23)
        setupTimeField(binding.etLimitMinutes, maxValue = 59)
        setupTimeField(binding.etSessionHours, maxValue = 23)
        setupTimeField(binding.etSessionMinutes, maxValue = 59)
        setupTimeField(binding.etBreakMinutes, maxValue = 59)

        binding.btnSave.setOnClickListener { saveRule() }
        binding.btnCancel.setOnClickListener { findNavController().popBackStack() }
    }

    /** Clamps out-of-range values on blur (typing stays free) and revalidates live. */
    private fun setupTimeField(field: TextInputEditText, maxValue: Int) {
        field.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val value = field.text?.toString()?.toIntOrNull()
                if (value != null && value > maxValue) field.setText(maxValue.toString())
            }
        }
        field.doAfterTextChanged { validateInputs() }
    }

    private fun fieldValue(field: TextInputEditText, maxValue: Int): Int =
        (field.text?.toString()?.toIntOrNull() ?: 0).coerceIn(0, maxValue)

    /**
     * A zero (or empty) duration would save a rule that never fires — flag the
     * minutes field and hold the Save button until the active type has a time.
     */
    private fun validateInputs() {
        val error = getString(R.string.add_rule_time_required)
        var valid = true

        fun check(layout: TextInputLayout, isZero: Boolean, active: Boolean) {
            layout.error = if (active && isZero) error.also { valid = false } else null
        }

        val checkedId = binding.rgBlockType.checkedRadioButtonId
        check(
            binding.tilLimitMinutes,
            fieldValue(binding.etLimitHours, 23) * 60 + fieldValue(binding.etLimitMinutes, 59) == 0,
            checkedId == binding.rbDailyLimit.id
        )
        check(
            binding.tilSessionMinutes,
            fieldValue(binding.etSessionHours, 23) * 60 + fieldValue(binding.etSessionMinutes, 59) == 0,
            checkedId == binding.rbSession.id
        )
        check(
            binding.tilBreakMinutes,
            fieldValue(binding.etBreakMinutes, 59) == 0,
            checkedId == binding.rbSession.id
        )
        binding.btnSave.isEnabled = valid
    }

    private fun saveRule() {
        if (selectedPackage.isEmpty()) return
        val isPinLocked = binding.switchPinLock.isChecked

        when (binding.rgBlockType.checkedRadioButtonId) {
            binding.rbBlockNow.id -> {
                viewModel.saveBlockNow(selectedPackage, isPinLocked)
            }
            binding.rbDailyLimit.id -> {
                val hours = fieldValue(binding.etLimitHours, 23)
                val minutes = fieldValue(binding.etLimitMinutes, 59)
                val limitMs = (hours * 3600L + minutes * 60L) * 1000L
                viewModel.saveDailyLimit(selectedPackage, limitMs, isPinLocked)
            }
            binding.rbSession.id -> {
                val sessionHours = fieldValue(binding.etSessionHours, 23)
                val sessionMins = fieldValue(binding.etSessionMinutes, 59)
                val breakMins = fieldValue(binding.etBreakMinutes, 59)
                val sessionMs = (sessionHours * 3600L + sessionMins * 60L) * 1000L
                val breakMs = breakMins * 60_000L
                viewModel.saveSessionLimit(selectedPackage, sessionMs, breakMs, isPinLocked)
            }
            binding.rbSchedule.id -> {
                val days = buildSelectedDays()
                val startH = binding.tpStart.hour
                val startM = binding.tpStart.minute
                val endH = binding.tpEnd.hour
                val endM = binding.tpEnd.minute
                viewModel.saveSchedule(
                    selectedPackage, days,
                    startH * 60 + startM,
                    endH * 60 + endM,
                    isPinLocked
                )
            }
        }
        findNavController().popBackStack()
    }

    private fun buildSelectedDays(): String {
        val selected = mutableListOf<String>()
        if (binding.cbMon.isChecked) selected.add("MON")
        if (binding.cbTue.isChecked) selected.add("TUE")
        if (binding.cbWed.isChecked) selected.add("WED")
        if (binding.cbThu.isChecked) selected.add("THU")
        if (binding.cbFri.isChecked) selected.add("FRI")
        if (binding.cbSat.isChecked) selected.add("SAT")
        if (binding.cbSun.isChecked) selected.add("SUN")
        return selected.joinToString(",")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
