package com.nijatva.camera.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.roundToInt

/**
 * One circular tool button with a drawn glyph and a caption underneath.
 *
 * The glyphs are drawn rather than loaded: five small icons do not justify five
 * vector assets, and drawing them keeps the whole UI in code.
 */
class ToolButton(context: Context) : View(context) {

    enum class Kind { RESET, EXPOSURE, WHITE_BALANCE, FOCUS, SHUTTER, ISO }

    var kind: Kind = Kind.EXPOSURE
        set(value) {
            field = value
            invalidate()
        }

    var selected: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var caption: String = ""
        set(value) {
            field = value
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 10f * density
    }
    private val circle = RectF()
    private val glyph = Path()

    init {
        isClickable = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val desired = (76f * density).roundToInt()
        setMeasuredDimension(width, resolveSize(desired, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val radius = 21f * density
        val cy = radius + 8f * density
        circle.set(cx - radius, cy - radius, cx + radius, cy + radius)

        circlePaint.color = if (selected) Color.parseColor("#9FC0EE") else Color.parseColor("#33373C")
        canvas.drawOval(circle, circlePaint)

        glyphPaint.color = if (selected) Color.parseColor("#101820") else Color.WHITE
        drawGlyph(canvas, cx, cy, radius * 0.52f)

        captionPaint.color = if (selected) Color.WHITE else Color.parseColor("#C8CDD4")
        canvas.drawText(caption, cx, cy + radius + 16f * density, captionPaint)
    }

    private fun drawGlyph(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        glyph.reset()
        when (kind) {
            Kind.RESET -> {
                glyph.addArc(RectF(cx - r, cy - r, cx + r, cy + r), 40f, 280f)
                canvas.drawPath(glyph, glyphPaint)
                canvas.drawLine(cx + r * 0.55f, cy - r * 0.95f, cx + r * 0.95f, cy - r * 0.35f, glyphPaint)
            }

            Kind.EXPOSURE -> {
                canvas.drawCircle(cx, cy, r * 0.45f, glyphPaint)
                for (i in 0 until 8) {
                    val angle = Math.toRadians((i * 45).toDouble())
                    val sx = cx + (Math.cos(angle) * r * 0.72).toFloat()
                    val sy = cy + (Math.sin(angle) * r * 0.72).toFloat()
                    val ex = cx + (Math.cos(angle) * r).toFloat()
                    val ey = cy + (Math.sin(angle) * r).toFloat()
                    canvas.drawLine(sx, sy, ex, ey, glyphPaint)
                }
            }

            Kind.WHITE_BALANCE -> {
                canvas.drawLine(cx, cy - r, cx, cy + r * 0.35f, glyphPaint)
                canvas.drawCircle(cx, cy + r * 0.6f, r * 0.38f, glyphPaint)
            }

            Kind.FOCUS -> {
                val s = r * 0.85f
                val len = r * 0.45f
                canvas.drawLine(cx - s, cy - s, cx - s + len, cy - s, glyphPaint)
                canvas.drawLine(cx - s, cy - s, cx - s, cy - s + len, glyphPaint)
                canvas.drawLine(cx + s, cy - s, cx + s - len, cy - s, glyphPaint)
                canvas.drawLine(cx + s, cy - s, cx + s, cy - s + len, glyphPaint)
                canvas.drawLine(cx - s, cy + s, cx - s + len, cy + s, glyphPaint)
                canvas.drawLine(cx - s, cy + s, cx - s, cy + s - len, glyphPaint)
                canvas.drawLine(cx + s, cy + s, cx + s - len, cy + s, glyphPaint)
                canvas.drawLine(cx + s, cy + s, cx + s, cy + s - len, glyphPaint)
            }

            Kind.SHUTTER -> {
                glyph.addArc(RectF(cx - r, cy - r, cx + r, cy + r), 110f, 140f)
                canvas.drawPath(glyph, glyphPaint)
                glyph.reset()
                glyph.addArc(RectF(cx - r, cy - r, cx + r, cy + r), 290f, 140f)
                canvas.drawPath(glyph, glyphPaint)
            }

            Kind.ISO -> {
                canvas.drawCircle(cx, cy, r * 0.85f, glyphPaint)
                canvas.drawLine(cx, cy - r * 0.45f, cx, cy + r * 0.45f, glyphPaint)
            }
        }
    }
}
