package com.example.stayfree.ui.common

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.example.stayfree.R
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A circular value picker, drag-driven. Two shapes in one view because the
 * interaction is identical:
 *
 * - one handle  (Focus): the arc runs from 12 o'clock to the handle — a duration.
 * - two handles (Sleep): the arc runs between them and may cross midnight — a window.
 *
 * Values are whole units of [range] (minutes of a day for Sleep, minutes of a
 * session for Focus); the arc is the value, so nobody has to read a spinner.
 */
class ArcPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** Full scale of one turn. 1440 = minutes in a day. */
    var range: Int = 1440
        set(value) { field = value.coerceAtLeast(1); invalidate() }

    /** Step the value snaps to while dragging. */
    var step: Int = 5

    /** Smallest sweep the two-handle form may shrink to, so it can't collapse. */
    var minSweep: Int = 30

    var startValue: Int = 0
        set(value) { field = wrap(value); invalidate() }

    /** null = single-handle (duration) mode. */
    var endValue: Int? = null
        set(value) { field = value?.let { wrap(it) }; invalidate() }

    var arcColor: Int = ContextCompat.getColor(context, R.color.skor_focus)
        set(value) { field = value; arcPaint.color = value; handlePaint.color = value; invalidate() }

    var trackColor: Int = ContextCompat.getColor(context, R.color.skor_line)
        set(value) { field = value; trackPaint.color = value; invalidate() }

    /** Fired continuously while dragging: (start, end) — end is null in duration mode. */
    var onValueChanged: ((Int, Int?) -> Unit)? = null

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = trackColor
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = arcColor
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = arcColor }
    private val bounds = RectF()

    private val strokeWidth = dp(8f)
    private val handleRadius = dp(9f)

    /** 0 = dragging start, 1 = dragging end, -1 = not dragging. */
    private var dragging = -1
    private var lastHapticValue = Int.MIN_VALUE

    init {
        trackPaint.strokeWidth = strokeWidth
        arcPaint.strokeWidth = strokeWidth
    }

    private fun wrap(v: Int): Int = ((v % range) + range) % range

    /** Sweep from start to end going clockwise; the whole point of the Sleep case. */
    private fun sweep(): Int {
        val end = endValue ?: return startValue
        return wrap(end - startValue)
    }

    override fun onDraw(canvas: Canvas) {
        val inset = maxOf(strokeWidth / 2f, handleRadius)
        bounds.set(
            paddingLeft + inset,
            paddingTop + inset,
            width - paddingRight - inset,
            height - paddingBottom - inset
        )
        if (bounds.width() <= 0f || bounds.height() <= 0f) return

        canvas.drawOval(bounds, trackPaint)

        val single = endValue == null
        val from = if (single) 0 else startValue
        val length = if (single) startValue else sweep()
        if (length > 0) {
            canvas.drawArc(
                bounds,
                angleOf(from),
                360f * length / range,
                false,
                arcPaint
            )
        }

        if (single) {
            drawHandle(canvas, startValue)
        } else {
            drawHandle(canvas, startValue)
            endValue?.let { drawHandle(canvas, it) }
        }
    }

    private fun drawHandle(canvas: Canvas, value: Int) {
        val rad = Math.toRadians(angleOf(value).toDouble())
        val cx = bounds.centerX() + bounds.width() / 2f * cos(rad).toFloat()
        val cy = bounds.centerY() + bounds.height() / 2f * sin(rad).toFloat()
        canvas.drawCircle(cx, cy, handleRadius, handlePaint)
    }

    /** Canvas angles start at 3 o'clock; the dial starts at 12. */
    private fun angleOf(value: Int): Float = -90f + 360f * value / range

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val touched = valueAt(event.x, event.y) ?: return false
                parent?.requestDisallowInterceptTouchEvent(true)
                dragging = if (endValue == null) 0 else nearestHandle(touched)
                apply(touched)
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging < 0) return false
                valueAt(event.x, event.y)?.let { apply(it) }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                dragging = -1
                lastHapticValue = Int.MIN_VALUE
            }
            else -> return false
        }
        return true
    }

    /** Null when the touch lands near the centre, where the angle is meaningless. */
    private fun valueAt(x: Float, y: Float): Int? {
        val dx = x - bounds.centerX()
        val dy = y - bounds.centerY()
        if (hypot(dx, dy) < bounds.width() / 4f) return null
        val deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
        val raw = range * ((deg + 360f) % 360f) / 360f
        return wrap((raw / step).roundToInt() * step)
    }

    private fun nearestHandle(value: Int): Int {
        val end = endValue ?: return 0
        return if (circularDistance(value, startValue) <= circularDistance(value, end)) 0 else 1
    }

    private fun circularDistance(a: Int, b: Int): Int {
        val d = abs(wrap(a - b))
        return minOf(d, range - d)
    }

    private fun apply(value: Int) {
        val end = endValue
        if (end == null) {
            // Duration mode: 0 would mean "no session", so the smallest value is one step.
            val v = value.coerceAtLeast(step)
            if (v == startValue) return
            startValue = v
        } else if (dragging == 0) {
            if (wrap(end - value) < minSweep) return
            if (value == startValue) return
            startValue = value
        } else {
            if (wrap(value - startValue) < minSweep) return
            if (value == end) return
            endValue = value
        }
        haptic()
        onValueChanged?.invoke(startValue, endValue)
    }

    private fun haptic() {
        val current = if (endValue == null) startValue else startValue * 10_000 + (endValue ?: 0)
        if (current != lastHapticValue) {
            lastHapticValue = current
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density
}
