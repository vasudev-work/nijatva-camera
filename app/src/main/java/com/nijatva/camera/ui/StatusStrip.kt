package com.nijatva.camera.ui

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * The thin readout across the top of the viewfinder: lens, focus state, shutter
 * speed, ISO. These are live values read back from the capture result, not what
 * the app asked for - the difference is what makes the readout worth having.
 */
class StatusStrip(context: Context) : LinearLayout(context) {

    private val lens = field()
    private val focus = field()
    private val shutter = field()
    private val iso = field()

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14), dp(6), dp(14), dp(6))
        setBackgroundColor(Color.argb(110, 0, 0, 0))
        addView(lens, weight())
        addView(focus, weight())
        addView(shutter, weight())
        addView(iso, weight())
    }

    fun update(lensLabel: String, focusMode: Int, exposureNs: Long?, sensitivity: Int?) {
        lens.text = lensLabel
        focus.text = focusLabel(focusMode)
        shutter.text = exposureNs?.let { formatShutter(it) } ?: "--"
        iso.text = sensitivity?.let { "ISO $it" } ?: "ISO --"
    }

    private fun focusLabel(mode: Int): String = when (mode) {
        0 -> "Manual focus"
        1, 2 -> "Auto focus"
        3 -> "Continuous"
        4 -> "Locked"
        else -> "Auto focus"
    }

    private fun formatShutter(nanoseconds: Long): String {
        if (nanoseconds <= 0) return "--"
        return if (nanoseconds >= 1_000_000_000L) {
            "${"%.1f".format(nanoseconds / 1_000_000_000.0)}s"
        } else {
            "1/${(1_000_000_000.0 / nanoseconds).roundToInt()}s"
        }
    }

    private fun field(): TextView = TextView(context).apply {
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        gravity = Gravity.CENTER
        text = "--"
    }

    private fun weight(): LayoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
}
