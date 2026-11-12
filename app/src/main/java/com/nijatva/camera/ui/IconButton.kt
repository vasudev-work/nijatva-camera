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

    enum class Kind {
        FLASH_OFF, FLASH_AUTO, FLASH_ON, MENU, FLIP, GRID, HDR, THUMB,
        // Settings rows use the same round buttons as the viewfinder chrome.
        HDR_OFF, HDR_ENHANCED,
        TIMER_OFF, TIMER_3, TIMER_10,
        RATIO_4_3, RATIO_16_9,
        CAMERA, VIDEO, GEAR,
    }

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

            Kind.HDR_OFF, Kind.HDR_ENHANCED -> {
                canvas.drawCircle(cx, cy, r * 0.85f, glyphPaint)
                canvas.drawLine(cx - r * 0.42f, cy - r * 0.18f, cx - r * 0.42f, cy + r * 0.18f, glyphPaint)
                canvas.drawLine(cx, cy - r * 0.48f, cx, cy + r * 0.48f, glyphPaint)
                if (kind == Kind.HDR_OFF) {
                    canvas.drawLine(cx + r * 0.42f, cy - r * 0.3f, cx + r * 0.42f, cy + r * 0.3f, glyphPaint)
                    canvas.drawLine(cx - r * 0.9f, cy + r * 0.9f, cx + r * 0.9f, cy - r * 0.9f, glyphPaint)
                } else {
                    canvas.drawCircle(cx + r * 0.42f, cy, r * 0.14f, fillPaint)
                }
            }

            Kind.TIMER_OFF, Kind.TIMER_3, Kind.TIMER_10 -> {
                canvas.drawCircle(cx, cy - r * 0.12f, r * 0.8f, glyphPaint)
                canvas.drawLine(cx - r * 0.28f, cy - r * 1.05f, cx + r * 0.28f, cy - r * 1.05f, glyphPaint)
                canvas.drawLine(cx, cy - r * 0.12f, cx, cy - r * 0.6f, glyphPaint)
                canvas.drawLine(cx, cy - r * 0.12f, cx + r * 0.34f, cy + r * 0.1f, glyphPaint)
                if (kind == Kind.TIMER_OFF) {
                    canvas.drawLine(cx - r, cy + r, cx + r, cy - r, glyphPaint)
                } else if (kind == Kind.TIMER_10) {
                    val text = "10"
                    val size = r * 0.6f
                    glyphPaint.textSize = size
                    glyphPaint.textAlign = Paint.Align.CENTER
                    canvas.drawText(text, cx, cy + r * 0.95f, glyphPaint)
                    glyphPaint.textAlign = Paint.Align.LEFT
                }
            }

            Kind.RATIO_4_3, Kind.RATIO_16_9 -> {
                val w = if (kind == Kind.RATIO_4_3) r * 1.35f else r * 1.8f
                val h = if (kind == Kind.RATIO_4_3) r * 1.0f else r * 1.0f
                canvas.drawRoundRect(
                    RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f),
                    r * 0.18f, r * 0.18f, glyphPaint,
                )
            }

            Kind.CAMERA -> {
                canvas.drawRoundRect(
                    RectF(cx - r, cy - r * 0.62f, cx + r, cy + r * 0.75f),
                    r * 0.22f, r * 0.22f, glyphPaint,
                )
                canvas.drawCircle(cx, cy + r * 0.06f, r * 0.34f, glyphPaint)
                canvas.drawLine(cx - r * 0.42f, cy - r * 0.62f, cx - r * 0.2f, cy - r, glyphPaint)
                canvas.drawLine(cx - r * 0.2f, cy - r, cx + r * 0.2f, cy - r, glyphPaint)
                canvas.drawLine(cx + r * 0.2f, cy - r, cx + r * 0.42f, cy - r * 0.62f, glyphPaint)
            }

            Kind.VIDEO -> {
                canvas.drawRoundRect(
                    RectF(cx - r, cy - r * 0.55f, cx + r * 0.42f, cy + r * 0.55f),
                    r * 0.22f, r * 0.22f, glyphPaint,
                )
                glyph.reset()
                glyph.moveTo(cx + r * 0.52f, cy - r * 0.28f)
                glyph.lineTo(cx + r, cy - r * 0.55f)
                glyph.lineTo(cx + r, cy + r * 0.55f)
                glyph.lineTo(cx + r * 0.52f, cy + r * 0.28f)
                glyph.close()
                canvas.drawPath(glyph, glyphPaint)
            }

            Kind.GEAR -> {
                canvas.drawCircle(cx, cy, r * 0.38f, glyphPaint)
                for (i in 0 until 8) {
                    val angle = Math.toRadians((i * 45).toDouble())
                    val sx = cx + (Math.cos(angle) * r * 0.6f).toFloat()
                    val sy = cy + (Math.sin(angle) * r * 0.6f).toFloat()
                    val ex = cx + (Math.cos(angle) * r * 0.95f).toFloat()
                    val ey = cy + (Math.sin(angle) * r * 0.95f).toFloat()
                    canvas.drawLine(sx, sy, ex, ey, glyphPaint)
                }
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
