package com.example.stayfree.util

import android.content.Context
import android.view.LayoutInflater
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.example.stayfree.R
import com.example.stayfree.databinding.DialogPinEntryBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Shared PIN-entry dialog. Verifies through [PinGate] (throttled), stays open
 * on a wrong PIN, and shows the lockout countdown inline.
 */
object PinPrompt {

    fun show(
        context: Context,
        scope: CoroutineScope,
        pinGate: PinGate,
        @StringRes title: Int = R.string.pin_enter_hint,
        @StringRes message: Int? = null,
        onCancel: () -> Unit = {},
        onSuccess: () -> Unit
    ) {
        val binding = DialogPinEntryBinding.inflate(LayoutInflater.from(context))
        val builder = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(binding.root)
            .setPositiveButton(R.string.btn_confirm, null)
            .setNegativeButton(R.string.btn_cancel) { _, _ -> onCancel() }
            .setOnCancelListener { onCancel() }
        if (message != null) builder.setMessage(message)
        val dialog = builder.show()
        // Manual click handler so a wrong PIN keeps the dialog open.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val entered = binding.etPin.text?.toString().orEmpty()
            if (entered.isEmpty()) {
                binding.tilPin.error = context.getString(R.string.pin_empty)
                return@setOnClickListener
            }
            scope.launch {
                when (val result = pinGate.verify(entered)) {
                    PinGate.Result.Success, PinGate.Result.NoPin -> {
                        dialog.dismiss()
                        onSuccess()
                    }
                    PinGate.Result.Wrong -> {
                        binding.tilPin.error = context.getString(R.string.pin_incorrect)
                        binding.etPin.text?.clear()
                    }
                    is PinGate.Result.LockedOut -> {
                        binding.tilPin.error =
                            context.getString(R.string.pin_locked_out, result.remainingSeconds)
                        binding.etPin.text?.clear()
                    }
                }
            }
        }
    }
}
