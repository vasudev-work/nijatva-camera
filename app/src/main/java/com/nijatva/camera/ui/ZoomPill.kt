package com.nijatva.camera.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.nijatva.camera.R
import kotlin.math.roundToInt

/**
 * The zoom capsule: a dark translucent pill with the available steps, the active
 * one in amber.
 *
 * It is only added to the screen when the device reports more than one zoom step,
 * so a phone that cannot zoom does not get a control that does nothing.
 */
class ZoomPill(context: Context) : LinearLayout(context) {

    var onZoom: ((Float) -> Unit)? = null

    private val chips = mutableListOf<Pair<Float, TextView>>()

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            cornerRadius = dp(20).toFloat()
            setColor(Color.argb(110, 0, 0, 0))
            setStroke(dp(1), Color.argb(38, 255, 255, 255))
        }
        setPadding(dp(4), dp(3), dp(4), dp(3))
    }

    fun bind(steps: List<Float>, selected: Float) {
        removeAllViews()
        chips.clear()
        steps.forEach { factor ->
            val chip = TextView(context).apply {
                text = if (factor <= 1f) context.getString(R.string.zoom_one)
                else context.getString(R.string.zoom_two)
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(5), dp(12), dp(5))
                isClickable = true
                setOnClickListener {
                    select(factor)
                    onZoom?.invoke(factor)
                }
            }
            chips.add(factor to chip)
            addView(chip)
        }
        select(selected)
    }

    fun select(factor: Float) {
        chips.forEach { (value, chip) ->
            val chosen = value == factor
            chip.setTextColor(if (chosen) ACCENT else Color.WHITE)
            chip.alpha = if (chosen) 1f else 0.85f
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private companion object {
        val ACCENT: Int get() = Palette.accent
    }
}
