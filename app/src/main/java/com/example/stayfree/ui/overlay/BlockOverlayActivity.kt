package com.example.stayfree.ui.overlay

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.stayfree.R
import com.example.stayfree.data.local.preferences.AppPreferences
import com.example.stayfree.databinding.ActivityBlockOverlayBinding
import com.example.stayfree.databinding.DialogPinEntryBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.example.stayfree.util.AppInfoUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.security.MessageDigest
import javax.inject.Inject

@AndroidEntryPoint
class BlockOverlayActivity : AppCompatActivity() {

    @Inject lateinit var prefs: AppPreferences

    private lateinit var binding: ActivityBlockOverlayBinding
    private var packageName_: String = ""
    private var blockReason: String = ""

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_BLOCK_REASON = "extra_block_reason"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBlockOverlayBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setFinishOnTouchOutside(false)

        packageName_ = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""
        blockReason = intent.getStringExtra(EXTRA_BLOCK_REASON) ?: "BLOCK_NOW"

        setupUI()

        binding.btnGoBack.setOnClickListener {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
            finish()
        }

        binding.btnOverride.setOnClickListener {
            showPinDialog()
        }
    }

    override fun onResume() {
        super.onResume()
        // Check if the blocked app is still foreground — if so, stay visible
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Navigate to home instead of closing overlay
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
    }

    private fun setupUI() {
        val appName = AppInfoUtils.getAppName(this, packageName_)
        val appIcon = AppInfoUtils.getAppIcon(this, packageName_)

        binding.tvAppName.text = appName
        if (appIcon != null) binding.ivAppIcon.setImageDrawable(appIcon)

        binding.tvBlockReason.text = when (blockReason) {
            "SCHEDULED" -> getString(R.string.overlay_by_schedule)
            "DAILY_LIMIT" -> getString(R.string.overlay_daily_limit_reached)
            "FOCUS" -> getString(R.string.overlay_focus_mode)
            "SLEEP" -> getString(R.string.overlay_sleep_mode)
            "WEBSITE_BLOCKED", "WEBSITE_CAP_REACHED" -> getString(R.string.overlay_website_blocked)
            else -> getString(R.string.overlay_title)
        }

        lifecycleScope.launch {
            // Override is only offered when a PIN actually exists — otherwise the
            // dialog would accept any input (no stored hash to compare against).
            val hasPin = prefs.pinHash.first() != null
            binding.btnOverride.visibility =
                if (hasPin) android.view.View.VISIBLE else android.view.View.GONE
            binding.tvPinHint.visibility =
                if (hasPin) android.view.View.GONE else android.view.View.VISIBLE
        }
    }

    private fun showPinDialog() {
        val dialogBinding = DialogPinEntryBinding.inflate(layoutInflater)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.pin_override_title)
            .setMessage(R.string.pin_override_message)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.btn_confirm) { _, _ ->
                val entered = dialogBinding.etPin.text?.toString().orEmpty()
                if (entered.isEmpty()) {
                    Toast.makeText(this, R.string.pin_empty, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    val storedHash = prefs.pinHash.first()
                    if (storedHash != null && sha256(entered) == storedHash) {
                        finish()
                    } else {
                        Toast.makeText(this@BlockOverlayActivity, R.string.pin_incorrect, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }
}
