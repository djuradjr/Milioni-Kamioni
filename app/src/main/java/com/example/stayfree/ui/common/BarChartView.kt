package com.example.stayfree.ui.common

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.example.stayfree.R

/**
 * Lightweight orange bar chart drawn directly on a Canvas — replaces the
 * MPAndroidChart dependency. Call [setData] with the per-bar values; bars are
 * scaled to the max value and animate up on change.
 */
class BarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.primary)
    }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.surface_variant)
    }

    private var values: List<Float> = emptyList()
    private var maxValue: Float = 1f
    private var animProgress: Float = 1f
    private var animator: ValueAnimator? = null

    private val barRect = RectF()
    private val cornerRadius = dp(6f)
    private val barGap = dp(8f)

    fun setData(newValues: List<Float>) {
        values = newValues
        maxValue = (newValues.maxOrNull() ?: 0f).coerceAtLeast(1f)
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 400
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                animProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (values.isEmpty()) return

        val count = values.size
        val totalGap = barGap * (count + 1)
        val barWidth = ((width - totalGap) / count).coerceAtLeast(1f)
        val usableHeight = height.toFloat()

        var x = barGap
        for (value in values) {
            val full = (value / maxValue) * usableHeight
            val barHeight = full * animProgress
            // Faint full-height track behind each bar.
            barRect.set(x, 0f, x + barWidth, usableHeight)
            canvas.drawRoundRect(barRect, cornerRadius, cornerRadius, trackPaint)
            // Actual value bar, grounded at the bottom.
            barRect.set(x, usableHeight - barHeight, x + barWidth, usableHeight)
            canvas.drawRoundRect(barRect, cornerRadius, cornerRadius, barPaint)
            x += barWidth + barGap
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
