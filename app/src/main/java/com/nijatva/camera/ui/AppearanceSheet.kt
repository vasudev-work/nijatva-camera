package com.nijatva.camera.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.nijatva.camera.R
import com.nijatva.camera.settings.AccentChoice
import com.nijatva.camera.settings.Settings
import com.nijatva.camera.settings.ThemeChoice
import kotlin.math.roundToInt

/**
 * The Appearance panel, laid out the way the Nijatva site lays it out: a card with
 * an uppercase heading in the accent colour, a Theme row of pills, an Accent colour
 * row of swatches, and a one-line note that nothing leaves the device.
 *
 * Every choice is applied and saved immediately; there is no Apply button, because
 * a theme is something you judge by looking at it.
 */
class AppearanceSheet(
    context: Context,
    private var settings: Settings,
    private val onChange: (Settings) -> Unit,
) : Dialog(context) {

    private val cards = mutableListOf<Pair<ThemeChoice, TextView>>()
    private val swatches = mutableListOf<Pair<AccentChoice, TextView>>()

    init {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Palette.surface)
                cornerRadius = dp(22).toFloat()
                setStroke(dp(1), Palette.hairline)
            }
            setPadding(dp(20), dp(20), dp(20), dp(18))
        }

        root.addView(TextView(context).apply {
            text = context.getString(R.string.appearance_title)
            setTextColor(Palette.accent)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            letterSpacing = 0.16f
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(18) })

        root.addView(label(context.getString(R.string.appearance_theme)))
        val themeRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, dp(18))
        }
        ThemeChoice.values().forEach { choice ->
            val pill = pill(context.getString(choice.labelRes))
            pill.setOnClickListener {
                settings = settings.copy(theme = choice)
                refresh()
                onChange(settings)
            }
            cards.add(choice to pill)
            themeRow.addView(pill, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { rightMargin = dp(8) })
        }
        root.addView(themeRow)

        root.addView(label(context.getString(R.string.appearance_accent)))
        val accentRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, dp(14))
        }
        AccentChoice.values().forEach { choice ->
            val swatch = swatch(context.getString(choice.labelRes), choice.argb)
            swatch.setOnClickListener {
                settings = settings.copy(accent = choice)
                refresh()
                onChange(settings)
            }
            swatches.add(choice to swatch)
            accentRow.addView(swatch, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { rightMargin = dp(10) })
        }
        root.addView(accentRow)

        root.addView(TextView(context).apply {
            text = context.getString(R.string.appearance_note)
            setTextColor(Palette.muted)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setLineSpacing(dp(3).toFloat(), 1f)
        })

        setContentView(root)
        window?.setBackgroundDrawableResource(android.R.color.transparent)
        refresh()
    }

    private fun refresh() {
        cards.forEach { (choice, pill) ->
            val chosen = choice == settings.theme
            pill.background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(if (chosen) Palette.accent else Color.TRANSPARENT)
                setStroke(dp(1), if (chosen) Palette.accent else Palette.hairline)
            }
            pill.setTextColor(if (chosen) Color.parseColor("#0B0B0C") else Palette.onSurface)
        }
        swatches.forEach { (choice, swatch) ->
            val chosen = choice == settings.accent
            swatch.background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(choice.argb)
                setStroke(dp(if (chosen) 3 else 1), if (chosen) Palette.onSurface else Palette.hairline)
            }
        }
    }

    private fun label(text: String): TextView = TextView(context).apply {
        this.text = text
        setTextColor(Palette.onSurface)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
    }

    private fun pill(text: String): TextView = TextView(context).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        gravity = Gravity.CENTER
        setPadding(dp(18), dp(10), dp(18), dp(10))
        isClickable = true
    }

    private fun swatch(text: String, colour: Int): TextView = TextView(context).apply {
        this.text = text
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        gravity = Gravity.CENTER
        setPadding(dp(18), dp(10), dp(18), dp(10))
        background = GradientDrawable().apply {
            cornerRadius = dp(20).toFloat()
            setColor(colour)
        }
        isClickable = true
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).roundToInt()
}
