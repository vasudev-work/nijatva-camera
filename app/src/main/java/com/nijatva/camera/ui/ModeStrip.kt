package com.nijatva.camera.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.nijatva.camera.R
import com.nijatva.camera.camera.Mode
import com.nijatva.camera.camera.Modes
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The mode strip: one scrolling row of uppercase mode names with MORE at the end.
 *
 * The selected mode is amber and bold, its neighbours are white at 55%. The row
 * snaps to the nearest name when a swipe ends, so a swipe always lands on a mode
 * instead of halfway between two, and the ends fade out so a partly visible name
 * reads as "more to the side" rather than as a clipped label.
 *
 * It reports taps and never changes the mode itself: the activity routes every
 * change through selectMode(), which is where the "stop recording first" rule
 * lives.
 */
class ModeStrip(context: Context) : HorizontalScrollView(context) {

    var onModeSelected: ((Mode) -> Unit)? = null
    var onMore: (() -> Unit)? = null

    private val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private val chips = mutableListOf<Pair<Mode, TextView>>()
    private var moreChip: TextView? = null
    private var selected: Mode? = null

    private val snapHandler = Handler(Looper.getMainLooper())
    private val snapTask = Runnable { snapToNearest() }

    init {
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        isHorizontalFadingEdgeEnabled = true
        setFadingEdgeLength(dp(28))
        addView(row, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    /** Fills the strip with the edition's modes, then MORE. */
    fun bind(modes: List<Mode>) {
        row.removeAllViews()
        chips.clear()
        modes.forEach { mode ->
            val chip = chip(context.getString(Modes.labelRes(mode)))
            chip.setOnClickListener { onModeSelected?.invoke(mode) }
            chips.add(mode to chip)
            row.addView(chip)
        }
        val more = chip(context.getString(R.string.mode_more))
        more.setOnClickListener { onMore?.invoke() }
        moreChip = more
        row.addView(more)
        selected?.let { setSelected(it) }
    }

    fun setSelected(mode: Mode) {
        selected = mode
        chips.forEach { (chipMode, chip) -> styleChip(chip, chipMode == mode) }
        chips.firstOrNull { it.first == mode }?.second?.let { scrollIntoCentre(it) }
    }

    /** MORE is highlighted while the grid is open. */
    fun setMoreHighlighted(highlighted: Boolean) {
        moreChip?.let { chip -> styleChip(chip, highlighted) }
    }

    /**
     * The reference marks the current mode with a filled pill, not a coloured
     * word: a pill survives a busy viewfinder behind it, coloured text does not.
     */
    private fun styleChip(chip: TextView, chosen: Boolean) {
        chip.background = GradientDrawable().apply {
            cornerRadius = dp(18).toFloat()
            setColor(if (chosen) PILL else Color.TRANSPARENT)
        }
        chip.setTextColor(if (chosen) Color.parseColor("#101418") else Color.WHITE)
        chip.alpha = if (chosen) 1f else 0.62f
    }

    private fun chip(label: String): TextView = TextView(context).apply {
        text = label
        setTextColor(Color.WHITE)
        alpha = 0.55f
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        letterSpacing = 0.1f
        gravity = Gravity.CENTER
        setPadding(dp(16), dp(9), dp(16), dp(9))
        isClickable = true
    }

    private fun scrollIntoCentre(chip: View) {
        post {
            val centre = (chip.left + chip.right) / 2
            val target = (centre - width / 2).coerceIn(0, maxOf(0, row.width - width))
            if (abs(target - scrollX) > dp(4)) smoothScrollTo(target, 0)
        }
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        // Snap a moment after the finger lifts, so a fling lands on a name too.
        snapHandler.removeCallbacks(snapTask)
        snapHandler.postDelayed(snapTask, 160)
    }

    private fun snapToNearest() {
        if (chips.isEmpty()) return
        val centre = scrollX + width / 2
        val nearest = chips.minByOrNull { (_, chip) -> abs((chip.left + chip.right) / 2 - centre) } ?: return
        val chip = nearest.second
        val target = ((chip.left + chip.right) / 2 - width / 2)
            .coerceIn(0, maxOf(0, row.width - width))
        if (abs(target - scrollX) > dp(2)) smoothScrollTo(target, 0)
    }

    override fun onDetachedFromWindow() {
        snapHandler.removeCallbacks(snapTask)
        super.onDetachedFromWindow()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private companion object {
        val PILL: Int = Color.parseColor("#C3D0F5")
    }
}
