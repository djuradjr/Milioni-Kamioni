package com.example.stayfree.ui.content

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.example.stayfree.R
import com.example.stayfree.databinding.ActivityContentBlockBinding

/**
 * Hard-block screen shown the moment a blocked content surface (Reels, Stories,
 * Shorts, TikTok) is detected. The accessibility service optionally presses Back
 * first (so the host saves a non-blocked last state), then starts this activity
 * over it. There is no unlock path — Exit and Back both go home, so the user can
 * never bounce back into the blocked surface.
 */
class ContentBlockActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DISPLAY_NAME = "extra_display_name"

        fun newIntent(context: Context, displayName: String): Intent =
            Intent(context, ContentBlockActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                putExtra(EXTRA_DISPLAY_NAME, displayName)
            }
    }

    private lateinit var binding: ActivityContentBlockBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityContentBlockBinding.inflate(layoutInflater)
        setContentView(binding.root)
        goImmersive()

        val displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME) ?: getString(R.string.app_name)
        binding.tvTitle.text = getString(R.string.content_block_title, displayName)

        binding.btnExit.setOnClickListener { goHome() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })
    }

    private fun goHome() {
        val home = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(home)
        finish()
        overridePendingTransition(0, 0)
    }

    private fun goImmersive() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.hide(
                android.view.WindowInsets.Type.statusBars() or
                        android.view.WindowInsets.Type.navigationBars()
            )
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
    }
}
