package com.example.stayfree.ui.onboarding

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.stayfree.R
import com.example.stayfree.databinding.ViewAccessibilityGuideBinding

/**
 * Shows where to tap in Android's accessibility settings: the highlight walks down the
 * three rows and the last one's switch turns on, then the loop starts over. With system
 * animations off it holds a still frame with every step marked.
 */
class AccessibilityGuideView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val binding = ViewAccessibilityGuideBinding.inflate(LayoutInflater.from(context), this)
    private val rows = listOf(binding.row1, binding.row2, binding.row3)
    private val numbers = listOf(binding.num1, binding.num2, binding.num3)
    private var step = -1
    private var fade: ValueAnimator? = null

    private val advance = object : Runnable {
        override fun run() {
            show((step + 1) % rows.size)
            postDelayed(this, STEP_MS)
        }
    }
    private val flipSwitch = Runnable { binding.guideSwitch.isChecked = true }

    init {
        orientation = VERTICAL
        rows.forEach { it.background = it.background.mutate() }
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        stop()
        if (!isVisible) return
        if (ValueAnimator.areAnimatorsEnabled()) post(advance) else showStill()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    private fun stop() {
        removeCallbacks(advance)
        removeCallbacks(flipSwitch)
        fade?.cancel()
        step = -1
    }

    private fun show(index: Int) {
        val previous = step
        step = index
        numbers.forEachIndexed { i, number -> mark(number, i == index) }
        fade?.cancel()
        fade = ValueAnimator.ofInt(0, 255).apply {
            duration = FADE_MS
            addUpdateListener {
                val alpha = it.animatedValue as Int
                rows.forEachIndexed { i, row ->
                    row.background.alpha = when (i) {
                        index -> alpha
                        previous -> 255 - alpha
                        else -> 0
                    }
                }
            }
            start()
        }
        binding.guideSwitch.isChecked = false
        if (index == rows.lastIndex) postDelayed(flipSwitch, SWITCH_DELAY_MS)
    }

    private fun showStill() {
        rows.forEach { it.background.alpha = 255 }
        numbers.forEach { mark(it, true) }
        binding.guideSwitch.isChecked = true
    }

    private fun mark(number: TextView, active: Boolean) {
        number.backgroundTintList = ContextCompat.getColorStateList(
            context, if (active) R.color.skor_focus else R.color.skor_line
        )
        number.setTextColor(
            ContextCompat.getColor(context, if (active) R.color.skor_on_focus else R.color.skor_text_dim)
        )
    }

    private companion object {
        const val STEP_MS = 1_600L
        const val FADE_MS = 280L
        const val SWITCH_DELAY_MS = 550L
    }
}
