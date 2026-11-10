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
 * Circular icon button with a drawn glyph, matching the stock camera's chrome:
 * a dark disc, a thin white icon, no caption.
 */
class IconButton(context: Context) : View(context) {

    enum class Kind { FLASH_OFF, FLASH_AUTO, FLASH_ON, MENU, FLIP, GRID, HDR, THUMB }

    var kind: Kind = Kind.MENU
        set(value) {
            field = value
            invalidate()
        }

    var active: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val discPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.8f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glyph = Path()

    init {
        isClickable = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = (44f * density).roundToInt()
        setMeasuredDimension(resolveSize(size, widthMeasureSpec), resolveSize(size, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(cx, cy)

        discPaint.color = if (active) Color.argb(235, 255, 255, 255) else Color.argb(90, 0, 0, 0)
        canvas.drawCircle(cx, cy, radius, discPaint)

        val ink = if (active) Color.parseColor("#101418") else Color.WHITE
        glyphPaint.color = ink
        fillPaint.color = ink

        val r = radius * 0.44f
        when (kind) {
            Kind.FLASH_OFF, Kind.FLASH_AUTO, Kind.FLASH_ON -> {
                glyph.reset()
                glyph.moveTo(cx + r * 0.15f, cy - r)
                glyph.lineTo(cx - r * 0.55f, cy + r * 0.15f)
                glyph.lineTo(cx - r * 0.02f, cy + r * 0.15f)
                glyph.lineTo(cx - r * 0.18f, cy + r)
                glyph.lineTo(cx + r * 0.6f, cy - r * 0.2f)
                glyph.lineTo(cx + r * 0.05f, cy - r * 0.2f)
                glyph.close()
                canvas.drawPath(glyph, if (kind == Kind.FLASH_ON) fillPaint else glyphPaint)

                if (kind == Kind.FLASH_OFF) {
                    canvas.drawLine(cx - r, cy + r, cx + r, cy - r, glyphPaint)
                }
                if (kind == Kind.FLASH_AUTO) {
                    canvas.drawCircle(cx + r * 0.95f, cy + r * 0.85f, r * 0.22f, fillPaint)
                }
            }

            Kind.MENU -> {
                for (i in -1..1) {
                    val y = cy + i * r * 0.45f
                    canvas.drawLine(cx - r, y, cx + r, y, glyphPaint)
                }
            }

            Kind.FLIP -> {
                glyph.reset()
                glyph.addArc(RectF(cx - r, cy - r, cx + r, cy + r), 200f, 140f)
                canvas.drawPath(glyph, glyphPaint)
                glyph.reset()
                glyph.addArc(RectF(cx - r, cy - r, cx + r, cy + r), 20f, 140f)
                canvas.drawPath(glyph, glyphPaint)
                canvas.drawLine(cx + r * 0.72f, cy - r * 0.72f, cx + r * 0.95f, cy - r * 0.35f, glyphPaint)
                canvas.drawLine(cx - r * 0.72f, cy + r * 0.72f, cx - r * 0.95f, cy + r * 0.35f, glyphPaint)
            }

            Kind.GRID -> {
                canvas.drawRect(cx - r, cy - r, cx + r, cy + r, glyphPaint)
                canvas.drawLine(cx - r / 3f, cy - r, cx - r / 3f, cy + r, glyphPaint)
                canvas.drawLine(cx + r / 3f, cy - r, cx + r / 3f, cy + r, glyphPaint)
                canvas.drawLine(cx - r, cy - r / 3f, cx + r, cy - r / 3f, glyphPaint)
                canvas.drawLine(cx - r, cy + r / 3f, cx + r, cy + r / 3f, glyphPaint)
            }

            Kind.HDR -> {
                canvas.drawCircle(cx, cy, r * 0.85f, glyphPaint)
                canvas.drawLine(cx - r * 0.45f, cy - r * 0.2f, cx - r * 0.45f, cy + r * 0.2f, glyphPaint)
                canvas.drawLine(cx, cy - r * 0.5f, cx, cy + r * 0.5f, glyphPaint)
                canvas.drawLine(cx + r * 0.45f, cy - r * 0.35f, cx + r * 0.45f, cy + r * 0.35f, glyphPaint)
            }

            Kind.THUMB -> {
                canvas.drawRect(cx - r, cy - r, cx + r, cy + r, glyphPaint)
                canvas.drawCircle(cx - r * 0.35f, cy - r * 0.35f, r * 0.22f, fillPaint)
                glyph.reset()
                glyph.moveTo(cx - r, cy + r * 0.6f)
                glyph.lineTo(cx - r * 0.15f, cy - r * 0.15f)
                glyph.lineTo(cx + r * 0.35f, cy + r * 0.35f)
                glyph.lineTo(cx + r, cy - r * 0.2f)
                canvas.drawPath(glyph, glyphPaint)
            }
        }
    }
}
