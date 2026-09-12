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
 * The focus-score ring: a track plus a rounded progress arc starting at 12
 * o'clock. Draws only the ring — the number sits on top as a normal TextView so
 * it can use [CountUp] and the type styles.
 */
class ScoreRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val bounds = RectF()
    private var animator: ValueAnimator? = null

    /** 0f..1f of the ring that is filled. */
    private var progress = 0f

    var ringWidth: Float = dp(9f)
        set(value) {
            field = value
            trackPaint.strokeWidth = value
            arcPaint.strokeWidth = value
            invalidate()
        }

    var trackColor: Int = ContextCompat.getColor(context, R.color.skor_line)
        set(value) {
            field = value
            trackPaint.color = value
            invalidate()
        }

    var progressColor: Int = ContextCompat.getColor(context, R.color.skor_focus)
        set(value) {
            field = value
            arcPaint.color = value
            invalidate()
        }

    init {
        trackPaint.strokeWidth = ringWidth
        trackPaint.color = trackColor
        arcPaint.strokeWidth = ringWidth
        arcPaint.color = progressColor
    }

    /**
     * @param value 0f..1f. An empty ring (no data yet) is [setProgress] 0f, which
     * still paints the track so the first-day state reads as "waiting", not broken.
     */
    fun setProgress(value: Float, animate: Boolean = true) {
        val target = value.coerceIn(0f, 1f)
        animator?.cancel()
        if (!animate || !isAttachedToWindow) {
            progress = target
            invalidate()
            return
        }
        animator = ValueAnimator.ofFloat(progress, target).apply {
            duration = 650L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val inset = ringWidth / 2f
        bounds.set(
            paddingLeft + inset,
            paddingTop + inset,
            width - paddingRight - inset,
            height - paddingBottom - inset
        )
        if (bounds.width() <= 0f || bounds.height() <= 0f) return

        canvas.drawOval(bounds, trackPaint)
        if (progress > 0f) {
            canvas.drawArc(bounds, START_ANGLE, 360f * progress, false, arcPaint)
        }
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density

    private companion object {
        /** 12 o'clock. */
        const val START_ANGLE = -90f
    }
}
