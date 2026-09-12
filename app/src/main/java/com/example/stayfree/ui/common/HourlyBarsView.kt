package com.example.stayfree.ui.common

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.example.stayfree.R
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 24 stacked bars, one per clock hour: distraction time at the base, everything
 * else above it. Replaces the single-colour line chart — colour is what makes
 * the chart readable at a glance ("evenings are eating me"), not the shape.
 *
 * Dragging across the chart selects an hour and reports it through
 * [onHourSelected]; releasing clears the selection.
 */
class HourlyBarsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** (distractionMs, otherMs) per hour, 24 entries. */
    private var data: List<Pair<Long, Long>> = emptyList()
    private var maxTotal = 1L
    private var selectedHour: Int? = null
    private var reveal = 1f
    private var animator: ValueAnimator? = null

    private val distractionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.skor_distraction)
    }
    private val otherPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.skor_idle)
    }
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private val barRadius = dp(2f)
    private val barGap = dp(2f)

    /** Reports the hour under the finger, or null once the touch ends. */
    var onHourSelected: ((Int?) -> Unit)? = null

    fun setData(hours: List<Pair<Long, Long>>, animate: Boolean = true) {
        data = hours
        maxTotal = max(1L, hours.maxOfOrNull { it.first + it.second } ?: 1L)
        animator?.cancel()
        if (!animate || !isAttachedToWindow) {
            reveal = 1f
            invalidate()
            return
        }
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 600L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                reveal = it.animatedValue as Float
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

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (data.isEmpty()) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val usable = width - paddingLeft - paddingRight
                if (usable <= 0) return true
                val slot = usable.toFloat() / data.size
                val hour = ((event.x - paddingLeft) / slot).toInt().coerceIn(0, data.size - 1)
                if (hour != selectedHour) {
                    selectedHour = hour
                    onHourSelected?.invoke(hour)
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                selectedHour = null
                onHourSelected?.invoke(null)
                invalidate()
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        if (data.isEmpty()) return
        val left = paddingLeft.toFloat()
        val top = paddingTop.toFloat()
        val usableW = width - paddingLeft - paddingRight
        val usableH = height - paddingTop - paddingBottom
        if (usableW <= 0 || usableH <= 0) return

        val slot = usableW.toFloat() / data.size
        val barW = max(1f, slot - barGap)

        data.forEachIndexed { hour, (distractionMs, otherMs) ->
            val total = distractionMs + otherMs
            if (total <= 0L) return@forEachIndexed

            val dimmed = selectedHour != null && selectedHour != hour
            val x = left + hour * slot + (slot - barW) / 2f
            val fullH = usableH * (total.toFloat() / maxTotal) * reveal
            var bottom = top + usableH

            // Distraction sits at the base so the orange mass reads as the floor
            // of the day, with neutral time stacked on top of it.
            val distractionH = fullH * (distractionMs.toFloat() / total)
            if (distractionH > 0f) {
                rect.set(x, bottom - distractionH, x + barW, bottom)
                canvas.drawRoundRect(rect, barRadius, barRadius, paint(distractionPaint, dimmed))
                bottom -= distractionH
            }
            val otherH = fullH - distractionH
            if (otherH > 0f) {
                rect.set(x, bottom - otherH, x + barW, bottom)
                canvas.drawRoundRect(rect, barRadius, barRadius, paint(otherPaint, dimmed))
            }
        }
    }

    private fun paint(source: Paint, dimmed: Boolean): Paint {
        if (!dimmed) return source
        dimPaint.color = source.color
        dimPaint.alpha = (255 * DIM_ALPHA).roundToInt()
        return dimPaint
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density

    private companion object {
        const val DIM_ALPHA = 0.32f
    }
}
