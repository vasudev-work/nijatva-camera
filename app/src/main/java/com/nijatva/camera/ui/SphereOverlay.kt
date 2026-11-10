package com.nijatva.camera.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.roundToInt

/**
 * The panorama guidance overlay: a field of target marks, a centre frame, and
 * the portion already captured.
 *
 * It is deliberately a flat grid rather than a true spherical projection. What
 * the user needs from it is "turn this far, keep it level", and a grid that
 * fills in left to right communicates that better than a distorted sphere.
 */
class SphereOverlay(context: Context) : View(context) {

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(90, 255, 255, 255) }
    private val donePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(230, 120, 200, 255) }
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
        color = Color.argb(200, 255, 255, 255)
    }
    private val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(200, 255, 255, 255) }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 13f * resources.displayMetrics.density
    }

    private var progress = 0f
    private var targetOffset = 0f
    private var hint = "Turn slowly to the right"

    fun setProgress(fraction: Float) {
        progress = fraction.coerceIn(0f, 1f)
        invalidate()
    }

    fun setTarget(offset: Float) {
        targetOffset = offset.coerceIn(-1f, 1f)
        invalidate()
    }

    fun setHint(text: String) {
        hint = text
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val columns = 11
        val rows = 13
        val stepX = width.toFloat() / columns
        val stepY = height.toFloat() / rows
        val dot = 3f * resources.displayMetrics.density

        for (row in 1 until rows) {
            for (column in 0 until columns) {
                val x = stepX * (column + 0.5f)
                val y = stepY * row
                val covered = x / width <= progress
                canvas.drawCircle(x, y, dot, if (covered) donePaint else dotPaint)
            }
        }

        val frameWidth = width * 0.62f
        val frameHeight = height * 0.46f
        val left = (width - frameWidth) / 2f + targetOffset * width * 0.12f
        val top = (height - frameHeight) / 2f
        canvas.drawRoundRect(RectF(left, top, left + frameWidth, top + frameHeight), 18f, 18f, framePaint)

        val cx = width / 2f
        val cy = top - 26f * resources.displayMetrics.density
        canvas.drawCircle(cx, cy, 5f * resources.displayMetrics.density, targetPaint)

        canvas.drawText(hint, width / 2f, height - 28f * resources.displayMetrics.density, hintPaint)
    }

    fun progressPercent(): Int = (progress * 100).roundToInt()
}
