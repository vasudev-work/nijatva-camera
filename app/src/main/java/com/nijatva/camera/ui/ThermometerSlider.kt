package com.nijatva.camera.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The thermometer-style slider: one bar, a cold end and a warm end, and a centre
 * position that means "let the camera decide".
 *
 * The detent at the centre matters. A plain slider makes zero a value like any
 * other, so a user who wants automatic behaviour has to hunt for the middle.
 * Here releasing near the centre snaps back to automatic and says so.
 */
class ThermometerSlider(context: Context) : View(context) {

    interface Listener {
        /** value is -1 (cold) to +1 (warm); 0 means automatic. */
        fun onValueChanged(value: Float)
        fun onResetToAuto()
    }

    var listener: Listener? = null

    private var value = 0f
    private var label = "Auto"
    private var enabledState = true

    private val density = resources.displayMetrics.density
    private val barHeight = 44f * density
    private val thumbWidth = 7f * density
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 255, 255, 255)
        strokeWidth = 1f * density
    }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 13f * density
        textAlign = Paint.Align.CENTER
    }
    private val endPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 3f * density
        strokeCap = Paint.Cap.ROUND
    }

    private val barRect = RectF()

    init {
        isClickable = true
    }

    fun setValueLabel(newLabel: String) {
        label = newLabel
        invalidate()
    }

    fun setSliderEnabled(enabled: Boolean) {
        enabledState = enabled
        alpha = if (enabled) 1f else 0.4f
        invalidate()
    }

    fun resetToAuto() {
        value = 0f
        label = "Auto"
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val desired = (barHeight + 34f * density).roundToInt()
        setMeasuredDimension(width, resolveSize(desired, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val left = paddingLeft + 24f * density
        val right = width - paddingRight - 24f * density
        val top = (24f * density)
        barRect.set(left, top, right, top + barHeight)

        trackPaint.shader = LinearGradient(
            left, 0f, right, 0f,
            intArrayOf(
                Color.parseColor("#2F6FB0"),
                Color.parseColor("#3A3A3A"),
                Color.parseColor("#B04A3A"),
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
        val radius = barHeight / 2f
        canvas.drawRoundRect(barRect, radius, radius, trackPaint)

        val ticks = 22
        for (i in 0..ticks) {
            val x = left + (right - left) * i / ticks
            val tall = i % 11 == 0
            val inset = if (tall) 6f * density else 12f * density
            canvas.drawLine(x, top + inset, x, top + barHeight - inset, tickPaint)
        }

        val thumbX = left + (right - left) * ((value + 1f) / 2f)
        canvas.drawRoundRect(
            RectF(
                thumbX - thumbWidth / 2f,
                top + 2f * density,
                thumbX + thumbWidth / 2f,
                top + barHeight - 2f * density,
            ),
            thumbWidth / 2f, thumbWidth / 2f, thumbPaint,
        )

        // Cold and warm end marks, as on the reference layout.
        endPaint.color = Color.parseColor("#5AA0E0")
        canvas.drawLine(left - 12f * density, top + 8f * density, left - 12f * density, top + barHeight - 8f * density, endPaint)
        endPaint.color = Color.parseColor("#E0705A")
        canvas.drawLine(right + 12f * density, top + 8f * density, right + 12f * density, top + barHeight - 8f * density, endPaint)

        canvas.drawText(label, (left + right) / 2f, top - 8f * density, labelPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!enabledState) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val left = paddingLeft + 24f * density
                val right = width - paddingRight - 24f * density
                val fraction = ((event.x - left) / (right - left)).coerceIn(0f, 1f)
                value = fraction * 2f - 1f
                listener?.onValueChanged(value)
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (abs(value) < 0.08f) {
                    value = 0f
                    listener?.onResetToAuto()
                    invalidate()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
