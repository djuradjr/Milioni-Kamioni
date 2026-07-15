package com.example.stayfree.ui.common

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.example.stayfree.R
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Navy line + area chart on a white card, styled after the dashboard reference.
 * Feeds on per-point values expressed in **minutes** ([setData]); draws a nice
 * rounded Y axis, sparse X ticks and hollow markers, and shows a tap tooltip
 * with a bold value plus a caption line.
 *
 * The three dashboard modes drive it differently through the formatters:
 *  - [xLabelFormatter]     index → x-axis tick text ("" to skip)
 *  - [tooltipValueFormatter] minutes → bold bubble value
 *  - [tooltipCaptionFormatter] index → bubble caption (hour / date)
 *  - `app:markersAllPoints` draws a marker on every point (weekly) vs only the
 *    tapped one (dense 24h / 30d series).
 */
class UsageLineChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val lineColor: Int
    private val gridColor: Int
    private val axisColor: Int
    private val captionColor: Int

    /** Marker on every point (weekly) vs only the tapped one (dense 24h/30d).
     *  Initialised from `app:markersAllPoints`, overridable per mode at runtime. */
    var markersAllPoints: Boolean = false

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val markerFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val markerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(10f)
        textAlign = Paint.Align.RIGHT
    }
    private val xLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(10f)
        textAlign = Paint.Align.CENTER
    }
    private val tooltipBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val tooltipValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(14f)
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val tooltipCaptionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(11f)
        textAlign = Paint.Align.CENTER
    }

    /** Nice-looking Y-axis ceilings, in minutes. */
    private val stepMinutes = intArrayOf(5, 10, 15, 30, 60, 120, 180, 240, 360, 480, 600, 720, 960, 1200, 1440)

    private var values: List<Float> = emptyList()
    private var niceMax: Float = 1f
    private var step: Float = 1f
    private var animProgress: Float = 1f
    private var animator: ValueAnimator? = null
    private var fillShader: LinearGradient? = null

    private var selectedIndex: Int = -1
    private val linePath = Path()
    private val fillPath = Path()
    private val tooltipRect = RectF()

    var xLabelFormatter: ((Int) -> String)? = null
    var tooltipValueFormatter: ((Float) -> String)? = null
    var tooltipCaptionFormatter: ((Int) -> String)? = null

    private val hideTooltip = Runnable {
        selectedIndex = -1
        invalidate()
    }

    // Internal drawing gutters (independent of android:padding).
    private val gutterLeft = dp(30f)
    private val insetTop = dp(12f)
    private val insetRight = dp(10f)
    private val gutterBottom = dp(18f)

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.UsageLineChartView)
        lineColor = a.getColor(
            R.styleable.UsageLineChartView_lineColor,
            ContextCompat.getColor(context, R.color.dash_line_color)
        )
        gridColor = a.getColor(
            R.styleable.UsageLineChartView_gridColor,
            ContextCompat.getColor(context, R.color.dash_line_grid)
        )
        axisColor = a.getColor(
            R.styleable.UsageLineChartView_axisTextColor,
            ContextCompat.getColor(context, R.color.dash_line_axis)
        )
        captionColor = a.getColor(
            R.styleable.UsageLineChartView_captionTextColor,
            ContextCompat.getColor(context, R.color.dash_line_caption)
        )
        markersAllPoints = a.getBoolean(R.styleable.UsageLineChartView_markersAllPoints, false)
        a.recycle()

        linePaint.color = lineColor
        linePaint.strokeWidth = dp(2.5f)
        gridPaint.color = gridColor
        gridPaint.strokeWidth = dp(1f)
        markerFillPaint.color = ContextCompat.getColor(context, R.color.dash_line_card)
        markerRingPaint.color = lineColor
        markerRingPaint.strokeWidth = dp(2f)
        highlightPaint.color = withAlpha(lineColor, 0x33)
        highlightPaint.strokeWidth = dp(1.5f)
        axisPaint.color = axisColor
        xLabelPaint.color = axisColor
        tooltipBgPaint.color = ContextCompat.getColor(context, R.color.dash_line_card)
        tooltipValuePaint.color = lineColor
        tooltipCaptionPaint.color = captionColor
        // Software layer so the tooltip's drop shadow renders.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun setData(valuesMinutes: List<Float>) {
        values = valuesMinutes
        selectedIndex = -1
        removeCallbacks(hideTooltip)
        recomputeAxis()
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 500
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                animProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun recomputeAxis() {
        val dataMax = values.maxOrNull() ?: 0f
        var chosen = stepMinutes.last().toFloat()
        for (s in stepMinutes) {
            if (dataMax <= s * 4f) { chosen = s.toFloat(); break }
        }
        step = chosen
        val lines = max(1, ceil(dataMax / chosen).toInt())
        niceMax = chosen * lines
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val top = insetTop
        val bottom = h - gutterBottom
        fillShader = LinearGradient(
            0f, top, 0f, bottom,
            withAlpha(lineColor, 0x40), withAlpha(lineColor, 0x00),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val count = values.size
        if (count == 0) return

        val plotLeft = gutterLeft
        val plotRight = width - insetRight
        val plotTop = insetTop
        val plotBottom = height - gutterBottom
        val plotWidth = plotRight - plotLeft
        val plotHeight = plotBottom - plotTop
        if (plotWidth <= 0 || plotHeight <= 0) return

        drawGrid(canvas, plotLeft, plotRight, plotTop, plotBottom)

        fun xAt(i: Int) = if (count == 1) (plotLeft + plotRight) / 2f
            else plotLeft + plotWidth * i / (count - 1)
        fun yFull(v: Float) = plotBottom - (v / niceMax) * plotHeight
        // Animated y grows from the baseline on entry.
        fun yAnim(v: Float) = plotBottom - (plotBottom - yFull(v)) * animProgress

        if (selectedIndex in 0 until count) {
            val hx = xAt(selectedIndex)
            canvas.drawLine(hx, plotTop, hx, plotBottom, highlightPaint)
        }

        fillPath.reset()
        fillPath.moveTo(xAt(0), plotBottom)
        for (i in 0 until count) fillPath.lineTo(xAt(i), yAnim(values[i]))
        fillPath.lineTo(xAt(count - 1), plotBottom)
        fillPath.close()
        fillPaint.shader = fillShader
        canvas.drawPath(fillPath, fillPaint)

        linePath.reset()
        for (i in 0 until count) {
            val x = xAt(i); val y = yAnim(values[i])
            if (i == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
        }
        canvas.drawPath(linePath, linePaint)

        for (i in 0 until count) {
            val emphasise = i == selectedIndex
            if (!markersAllPoints && !emphasise) continue
            val x = xAt(i); val y = yAnim(values[i])
            val r = if (emphasise) dp(5f) else dp(4f)
            canvas.drawCircle(x, y, r, markerFillPaint)
            canvas.drawCircle(x, y, r, markerRingPaint)
            if (emphasise) canvas.drawCircle(x, y, dp(2.5f), markerRingPaint)
        }

        drawXLabels(canvas, count, ::xAt, plotBottom)
        drawTooltip(canvas, count, ::xAt, ::yFull, plotTop)
    }

    private fun drawGrid(canvas: Canvas, left: Float, right: Float, top: Float, bottom: Float) {
        val lines = (niceMax / step).roundToInt().coerceAtLeast(1)
        for (i in 0..lines) {
            val value = step * i
            val y = bottom - (value / niceMax) * (bottom - top)
            canvas.drawLine(left, y, right, y, gridPaint)
            val label = compactMinutes(value)
            val ty = y - (axisPaint.descent() + axisPaint.ascent()) / 2f
            canvas.drawText(label, left - dp(6f), ty, axisPaint)
        }
    }

    private inline fun drawXLabels(canvas: Canvas, count: Int, xAt: (Int) -> Float, plotBottom: Float) {
        val formatter = xLabelFormatter ?: return
        val baseline = plotBottom + gutterBottom - dp(4f)
        for (i in 0 until count) {
            val label = formatter(i)
            if (label.isEmpty()) continue
            canvas.drawText(label, xAt(i), baseline, xLabelPaint)
        }
    }

    private inline fun drawTooltip(
        canvas: Canvas,
        count: Int,
        xAt: (Int) -> Float,
        yFull: (Float) -> Float,
        plotTop: Float
    ) {
        val index = selectedIndex
        if (index < 0 || index >= count) return
        val value = tooltipValueFormatter?.invoke(values[index]) ?: compactMinutes(values[index])
        val caption = tooltipCaptionFormatter?.invoke(index) ?: ""

        val padH = dp(12f)
        val padV = dp(8f)
        val gap = dp(3f)
        val hasCaption = caption.isNotEmpty()
        val contentW = max(
            tooltipValuePaint.measureText(value),
            if (hasCaption) tooltipCaptionPaint.measureText(caption) else 0f
        )
        val bubbleW = contentW + padH * 2
        val valueH = tooltipValuePaint.descent() - tooltipValuePaint.ascent()
        val captionH = if (hasCaption) tooltipCaptionPaint.descent() - tooltipCaptionPaint.ascent() else 0f
        val bubbleH = padV * 2 + valueH + (if (hasCaption) gap + captionH else 0f)

        val px = xAt(index)
        val py = yFull(values[index])
        var top = py - dp(12f) - bubbleH
        if (top < plotTop - dp(6f)) top = py + dp(12f)
        val left = (px - bubbleW / 2f).coerceIn(dp(2f), width - bubbleW - dp(2f))
        tooltipRect.set(left, top, left + bubbleW, top + bubbleH)

        tooltipBgPaint.setShadowLayer(dp(6f), 0f, dp(2f), 0x33000000)
        canvas.drawRoundRect(tooltipRect, dp(10f), dp(10f), tooltipBgPaint)
        tooltipBgPaint.clearShadowLayer()

        val cx = tooltipRect.centerX()
        val valueBaseline = tooltipRect.top + padV - tooltipValuePaint.ascent()
        canvas.drawText(value, cx, valueBaseline, tooltipValuePaint)
        if (hasCaption) {
            val captionBaseline = valueBaseline + tooltipValuePaint.descent() + gap - tooltipCaptionPaint.ascent()
            canvas.drawText(caption, cx, captionBaseline, tooltipCaptionPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val count = values.size
        if (count == 0) return super.onTouchEvent(event)
        if (event.action == MotionEvent.ACTION_DOWN) {
            val index = nearestIndex(event.x, count)
            if (index != -1) {
                selectedIndex = if (selectedIndex == index) -1 else index
                removeCallbacks(hideTooltip)
                if (selectedIndex != -1) postDelayed(hideTooltip, 2500)
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                invalidate()
            }
            performClick()
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun nearestIndex(x: Float, count: Int): Int {
        if (count == 1) return 0
        val plotLeft = gutterLeft
        val plotWidth = width - insetRight - plotLeft
        if (plotWidth <= 0) return -1
        val raw = ((x - plotLeft) / plotWidth * (count - 1)).roundToInt()
        return raw.coerceIn(0, count - 1)
    }

    private fun compactMinutes(minutes: Float): String {
        val m = minutes.roundToInt()
        return when {
            m <= 0 -> "0"
            m < 60 -> "${m}m"
            m % 60 == 0 -> "${m / 60}h"
            else -> "${m / 60}h${m % 60}m"
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha shl 24)

    override fun onDetachedFromWindow() {
        animator?.cancel()
        removeCallbacks(hideTooltip)
        super.onDetachedFromWindow()
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
