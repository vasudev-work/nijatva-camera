package com.nijatva.camera.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import com.nijatva.camera.settings.GridType

/**
 * Composition guides: the rule-of-thirds grid, a 4 x 4 grid, or golden-ratio
 * lines, plus a level indicator.
 *
 * The level is the part that earns its place. A horizon that is one degree off is
 * obvious in the finished photo and invisible on a live preview, so the roll
 * angle from the gyroscope is drawn as a line that turns green when the phone is
 * within half a degree of level.
 */
class GridOverlay(context: Context) : View(context) {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(90, 255, 255, 255)
        strokeWidth = 1f * resources.displayMetrics.density
    }
    private val levelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 2f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
    }

    var gridType: GridType = GridType.NONE
        set(value) {
            field = value
            invalidate()
        }

    var levelVisible: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private var rollDegrees = 0f

    private var reticleX = -1f
    private var reticleY = -1f
    private val reticlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFB020")
        strokeWidth = 1.6f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
    }

    /** Shows the focus crosshair where the user tapped. */
    fun showReticle(x: Float, y: Float) {
        reticleX = x
        reticleY = y
        invalidate()
    }

    /** Called when the phone becomes level, or stops being level. */
    var onLevelChanged: ((Boolean) -> Unit)? = null

    private var level = false

    fun setRoll(degrees: Float) {
        rollDegrees = degrees
        val nowLevel = LevelDetector.isLevel(degrees, level)
        if (nowLevel != level) {
            level = nowLevel
            onLevelChanged?.invoke(nowLevel)
        }
        if (levelVisible) invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        when (gridType) {
            GridType.NONE -> Unit
            GridType.THIRDS -> drawDivisions(canvas, listOf(1f / 3f, 2f / 3f))
            GridType.QUARTERS -> drawDivisions(canvas, listOf(0.25f, 0.5f, 0.75f))
            GridType.GOLDEN -> drawDivisions(canvas, listOf(0.382f, 0.618f))
        }

        if (levelVisible) drawLevel(canvas)
        drawReticle(canvas)
    }

    private fun drawReticle(canvas: Canvas) {
        val x = if (reticleX < 0f) width / 2f else reticleX
        val y = if (reticleY < 0f) height / 2f else reticleY
        val arm = 11f * resources.displayMetrics.density
        val gap = 4f * resources.displayMetrics.density

        canvas.drawLine(x - arm, y, x - gap, y, reticlePaint)
        canvas.drawLine(x + gap, y, x + arm, y, reticlePaint)
        canvas.drawLine(x, y - arm, x, y - gap, reticlePaint)
        canvas.drawLine(x, y + gap, x, y + arm, reticlePaint)
    }

    private fun drawDivisions(canvas: Canvas, fractions: List<Float>) {
        for (fraction in fractions) {
            val x = width * fraction
            val y = height * fraction
            canvas.drawLine(x, 0f, x, height.toFloat(), linePaint)
            canvas.drawLine(0f, y, width.toFloat(), y, linePaint)
        }
    }

    private fun drawLevel(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val half = width * 0.22f
        levelPaint.color = if (level) Color.parseColor("#8BE38B") else Color.argb(200, 255, 255, 255)

        canvas.save()
        canvas.rotate(-rollDegrees, cx, cy)
        canvas.drawLine(cx - half, cy, cx + half, cy, levelPaint)
        canvas.restore()

        // A fixed reference line, so the moving one has something to align to.
        levelPaint.color = Color.argb(90, 255, 255, 255)
        canvas.drawLine(cx - half, cy, cx + half, cy, levelPaint)
    }
}
