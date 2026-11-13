package com.nijatva.camera.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.nijatva.camera.R
import com.nijatva.camera.camera.Mode
import com.nijatva.camera.camera.Modes
import kotlin.math.roundToInt

/**
 * The MORE screen: a full-screen grid of mode tiles with the mode strip repeated
 * along the bottom, the layout the stock camera uses for the modes that do not
 * fit on the strip.
 *
 * It is an overlay inside the activity rather than a second activity, so the
 * camera session, the preview surface and the engine keep running underneath and
 * closing it costs nothing.
 *
 * Tiles only ever report a mode: the activity decides whether the change is
 * allowed (it refuses one while a recording is running).
 */
class ModesGrid(context: Context) : FrameLayout(context) {

    var onModeSelected: ((Mode) -> Unit)? = null
    var onMoreTapped: (() -> Unit)? = null

    private val column = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(24), dp(16), dp(12))
    }
    private val strip = ModeStrip(context)
    private val tiles = mutableListOf<Pair<Mode, LinearLayout>>()
    private var columns = 3

    init {
        visibility = View.GONE
        isClickable = true
        // Slightly lighter at the top, the way the reference screen is lit.
        background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#191B1E"), Color.parseColor("#0B0C0E")),
        )

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(context).apply {
            text = context.getString(R.string.modes_grid_title)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            letterSpacing = 0.14f
        })
        header.addView(View(context), LinearLayout.LayoutParams(0, dp(1), 1f))
        // The edit pencil in the reference reorders modes; this app has no mode
        // reordering, so the control is deliberately not shown.
        header.addView(TextView(context).apply {
            text = context.getString(R.string.action_close)
            setTextColor(ACCENT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            letterSpacing = 0.12f
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setOnClickListener { visibility = View.GONE }
        })
        column.addView(header, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) })

        val scroller = ScrollView(context).apply { isVerticalScrollBarEnabled = false }
        scroller.addView(column)
        addView(scroller, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        strip.onModeSelected = { mode -> onModeSelected?.invoke(mode) }
        strip.onMore = { onMoreTapped?.invoke() }
        strip.setMoreHighlighted(true)
        addView(strip, LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM,
        ))
    }

    /** Builds the tiles. Call again after a configuration change. */
    fun bind(modes: List<Mode>, stripModes: List<Mode>, columns: Int) {
        this.columns = columns
        tiles.clear()
        // Drop the old tile rows, keeping the header at index 0.
        while (column.childCount > 1) column.removeViewAt(1)

        modes.chunked(columns).forEach { rowModes ->
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            rowModes.forEach { mode ->
                val tile = tile(mode)
                tiles.add(mode to tile)
                row.addView(tile, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
            repeat(columns - rowModes.size) {
                row.addView(View(context), LinearLayout.LayoutParams(0, dp(1), 1f))
            }
            column.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(18) })
        }

        strip.bind(stripModes)
        strip.setMoreHighlighted(true)
    }

    fun setSelected(mode: Mode) {
        tiles.forEach { (tileMode, view) ->
            val chosen = tileMode == mode
            view.alpha = if (chosen) 1f else 0.75f
            (view.getChildAt(1) as? TextView)?.setTextColor(if (chosen) ACCENT else Color.WHITE)
        }
        strip.setSelected(mode)
        strip.setMoreHighlighted(true)
    }

    fun open() {
        visibility = View.VISIBLE
        bringToFront()
    }

    fun close() {
        visibility = View.GONE
    }

    private fun tile(mode: Mode): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(0, dp(10), 0, dp(10))
        isClickable = true
        addView(ImageView(context).apply {
            setImageResource(Modes.iconRes(mode))
            setColorFilter(Color.WHITE)
        }, LinearLayout.LayoutParams(dp(30), dp(30)))
        addView(TextView(context).apply {
            text = context.getString(Modes.labelRes(mode))
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            letterSpacing = 0.06f
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, 0)
        })
        setOnClickListener { onModeSelected?.invoke(mode) }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private companion object {
        val ACCENT: Int get() = Palette.accent
    }
}
