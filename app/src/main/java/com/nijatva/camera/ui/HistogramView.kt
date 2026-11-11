package com.nijatva.camera.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.max

/**
 * Small brightness histogram for the viewfinder corner. Bins 0 and 255 are drawn
 * amber: that is clipped shadow or highlight detail. The scale ignores the two
 * end bins, so a clipped spike does not flatten everything else.
 */
class HistogramView(context: Context) : View(context) {

    private var bins = IntArray(256)
    private var scale = 1

    private val background = Paint().apply { color = Color.argb(120, 0, 0, 0) }
    private val bar = Paint().apply { color = Color.argb(220, 235, 235, 235) }
    private val clipped = Paint().apply { color = Color.parseColor("#FFB020") }
    private val rect = RectF()

    fun setBins(data: IntArray) {
        if (data.size != 256) return
        bins = data
        scale = max(1, (1..254).maxOf { data[it] })
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, 6f, 6f, background)

        val barWidth = w / 256f
        for (i in 0 until 256) {
            val count = bins[i]
            if (count == 0) continue
            val barHeight = max(1f, h * count / scale)
            val left = i * barWidth
            rect.set(left, h - barHeight, left + barWidth, h)
            canvas.drawRect(rect, if (i == 0 || i == 255) clipped else bar)
        }
    }
}
