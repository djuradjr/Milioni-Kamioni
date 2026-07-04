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
 *
 * Optional extras (used by the dashboard hourly chart):
 *  - [setData]'s `highlightIndex` paints one bar in a deeper shade (peak hour).
 *  - `app:showHourLabels` draws 0/6/12/18 clock labels under a 24-bar chart.
 */
class BarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(11f)
        textAlign = Paint.Align.CENTER
    }

    private var values: List<Float> = emptyList()
    private var highlightIndex: Int = -1
    private var maxValue: Float = 1f
    private var animProgress: Float = 1f
    private var animator: ValueAnimator? = null

    private val showHourLabels: Boolean

    private val barRect = RectF()
    private val labelSpace: Float

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.BarChartView)
        showHourLabels = a.getBoolean(R.styleable.BarChartView_showHourLabels, false)
        barPaint.color = a.getColor(
            R.styleable.BarChartView_barColor,
            ContextCompat.getColor(context, R.color.primary)
        )
        highlightPaint.color = a.getColor(
            R.styleable.BarChartView_barHighlightColor,
            ContextCompat.getColor(context, R.color.on_primary_container)
        )
        trackPaint.color = a.getColor(
            R.styleable.BarChartView_barTrackColor,
            ContextCompat.getColor(context, R.color.surface_variant)
        )
        labelPaint.color = a.getColor(
            R.styleable.BarChartView_barLabelColor,
            ContextCompat.getColor(context, R.color.on_surface_variant)
        )
        a.recycle()
        labelSpace = if (showHourLabels) dp(18f) else 0f
    }

    @JvmOverloads
    fun setData(newValues: List<Float>, highlightIndex: Int = -1) {
        values = newValues
        this.highlightIndex = highlightIndex
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
        // Tighter gap for dense charts (24 hourly bars) so bars stay visible.
        val barGap = if (count > 12) dp(3f) else dp(8f)
        val totalGap = barGap * (count + 1)
        val barWidth = ((width - totalGap) / count).coerceAtLeast(1f)
        val cornerRadius = minOf(dp(6f), barWidth / 2f)
        val usableHeight = height - labelSpace

        var x = barGap
        for ((index, value) in values.withIndex()) {
            val full = (value / maxValue) * usableHeight
            val barHeight = full * animProgress
            // Faint full-height track behind each bar.
            barRect.set(x, 0f, x + barWidth, usableHeight)
            canvas.drawRoundRect(barRect, cornerRadius, cornerRadius, trackPaint)
            // Actual value bar, grounded at the bottom.
            barRect.set(x, usableHeight - barHeight, x + barWidth, usableHeight)
            val paint = if (index == highlightIndex) highlightPaint else barPaint
            canvas.drawRoundRect(barRect, cornerRadius, cornerRadius, paint)

            if (showHourLabels && index % 6 == 0) {
                canvas.drawText(index.toString(), x + barWidth / 2f, height - dp(4f), labelPaint)
            }
            x += barWidth + barGap
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
