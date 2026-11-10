package com.nijatva.camera.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.nijatva.camera.R
import com.nijatva.camera.settings.FlashMode
import com.nijatva.camera.settings.GridType
import com.nijatva.camera.settings.HdrMode
import com.nijatva.camera.settings.Ratio
import com.nijatva.camera.settings.Settings
import com.nijatva.camera.settings.TimerDelay
import kotlin.math.roundToInt

/**
 * The photo settings sheet: a bottom sheet with General and Pro tabs, and an
 * Advanced page behind it.
 *
 * Every control here changes something the app actually does. Google Camera's
 * Advanced page carries switches for HDRnet, Top Shot, depth capture and
 * astrophotography time lapse; those are listed as unsupported in
 * docs/SETTINGS.md instead of being shown as toggles that quietly do nothing.
 */
class SettingsSheet(
    context: Context,
    private var settings: Settings,
    private val onChange: (Settings) -> Unit,
) : Dialog(context, R.style.SheetDialog) {

    private var tab = 0
    private val content = LinearLayout(context)

    init {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#121417"))
                cornerRadii = floatArrayOf(dp(24).toFloat(), dp(24).toFloat(), dp(24).toFloat(), dp(24).toFloat(), 0f, 0f, 0f, 0f)
            }
            setPadding(dp(20), dp(10), dp(20), dp(24))
        }

        root.addView(handle())
        root.addView(titleRow("Photo settings"))
        root.addView(tabRow())

        val scroll = ScrollView(context).apply {
            addView(content)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        root.addView(scroll)

        setContentView(root)
        window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            setBackgroundDrawableResource(android.R.color.transparent)
        }
        rebuild()
    }

    private fun rebuild() {
        content.removeAllViews()
        if (tab == 0) buildGeneral() else buildPro()
        content.addView(sectionGap())
        content.addView(outlinedButton("More settings") {
            dismiss()
            AdvancedSheet(context, settings, onChange).show()
        })
    }

    private fun buildGeneral() {
        content.addView(sectionLabel("General"))

        content.addView(segmentedRow(
            title = "Flash",
            subtitle = "Torch is toggled separately in the viewfinder",
            options = FlashMode.values().map { it.label },
            selected = settings.flash.ordinal,
        ) { index ->
            settings = settings.copy(flash = FlashMode.values()[index])
            push()
        })

        content.addView(segmentedRow(
            title = "HDR+",
            subtitle = "Enhanced brackets three exposures instead of one",
            options = HdrMode.values().map { it.label },
            selected = settings.hdr.ordinal,
        ) { index ->
            settings = settings.copy(hdr = HdrMode.values()[index])
            push()
        })

        content.addView(segmentedRow(
            title = "Timer",
            subtitle = "Countdown before the shutter fires",
            options = TimerDelay.values().map { it.label },
            selected = settings.timer.ordinal,
        ) { index ->
            settings = settings.copy(timer = TimerDelay.values()[index])
            push()
        })

        content.addView(segmentedRow(
            title = "Ratio",
            subtitle = settings.ratio.subtitle,
            options = Ratio.values().map { it.label },
            selected = settings.ratio.ordinal,
        ) { index ->
            settings = settings.copy(ratio = Ratio.values()[index])
            push()
            rebuild()
        })

        content.addView(sectionLabel("Composition"))

        content.addView(valueRow(
            title = "Grid type",
            subtitle = "Composition guides on the viewfinder",
            value = settings.grid.label,
        ) {
            val next = GridType.values()[(settings.grid.ordinal + 1) % GridType.values().size]
            settings = settings.copy(grid = next)
            push()
            rebuild()
        })

        content.addView(switchRow(
            title = "Framing hints",
            subtitle = "Level indicator on the viewfinder",
            checked = settings.framingHints,
        ) { checked ->
            settings = settings.copy(framingHints = checked)
            push()
        })
    }

    private fun buildPro() {
        content.addView(sectionLabel("Lens selection"))

        content.addView(valueRow(
            title = "Lens",
            subtitle = "Auto follows the last used camera",
            value = if (settings.saveSelfieAsPreviewed) "Auto" else "Manual",
        ) {
            settings = settings.copy(saveSelfieAsPreviewed = !settings.saveSelfieAsPreviewed)
            push()
            rebuild()
        })

        content.addView(sectionLabel("Manual controls"))

        content.addView(switchRow(
            title = "Quick access controls",
            subtitle = "Tap the viewfinder to set exposure and focus",
            checked = settings.quickAccessControls,
        ) { checked ->
            settings = settings.copy(quickAccessControls = checked)
            push()
        })

        content.addView(sectionLabel("Photo"))

        content.addView(switchRow(
            title = "RAW + JPEG",
            subtitle = "Save a DNG next to the JPEG when RAW mode is used",
            checked = settings.rawPlusJpeg,
        ) { checked ->
            settings = settings.copy(rawPlusJpeg = checked)
            push()
        })

        content.addView(switchRow(
            title = "Save selfie as previewed",
            subtitle = "Mirror the front camera image, as the preview shows it",
            checked = settings.saveSelfieAsPreviewed,
        ) { checked ->
            settings = settings.copy(saveSelfieAsPreviewed = checked)
            push()
        })
    }

    private fun push() = onChange(settings)

    // ---------------------------------------------------------------- widgets

    private fun handle(): View = View(context).apply {
        background = GradientDrawable().apply {
            setColor(Color.parseColor("#4A4F55"))
            cornerRadius = dp(3).toFloat()
        }
        layoutParams = LinearLayout.LayoutParams(dp(38), dp(4)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(6)
            bottomMargin = dp(14)
        }
    }

    private fun titleRow(text: String): View = TextView(context).apply {
        this.text = text
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        setPadding(0, 0, 0, dp(14))
    }

    private fun tabRow(): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }
        val pills = mutableListOf<TextView>()

        listOf("General", "Pro").forEachIndexed { index, label ->
            val selected = index == tab
            val pill = TextView(context).apply {
                text = label
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(if (selected) Color.parseColor("#101820") else Color.WHITE)
                background = GradientDrawable().apply {
                    cornerRadius = dp(20).toFloat()
                    setColor(if (selected) Color.parseColor("#A8C7FA") else Color.parseColor("#26292E"))
                }
                setPadding(0, dp(12), 0, dp(12))
                setOnClickListener {
                    tab = index
                    pills.forEachIndexed { i, item -> stylePill(item, i == tab) }
                    rebuild()
                }
            }
            pills.add(pill)
            row.addView(pill, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = if (index == 0) dp(6) else 0
                leftMargin = if (index == 1) dp(6) else 0
            })
        }
        return row
    }

    private fun stylePill(pill: TextView, selected: Boolean) {
        pill.setTextColor(if (selected) Color.parseColor("#101820") else Color.WHITE)
        pill.background = GradientDrawable().apply {
            cornerRadius = dp(20).toFloat()
            setColor(if (selected) Color.parseColor("#A8C7FA") else Color.parseColor("#26292E"))
        }
    }

    private fun sectionLabel(text: String): View = TextView(context).apply {
        this.text = text
        setTextColor(Color.parseColor("#9AA3AD"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        setPadding(0, dp(14), 0, dp(6))
    }

    private fun sectionGap(): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(10))
    }

    private fun switchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit): View {
        val row = rowContainer(title, subtitle)
        val toggle = Switch(context).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, value -> onChange(value) }
        }
        row.addView(toggle, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        return row
    }

    private fun valueRow(title: String, subtitle: String, value: String, onClick: () -> Unit): View {
        val row = rowContainer(title, subtitle)
        row.addView(TextView(context).apply {
            text = value
            setTextColor(Color.parseColor("#A8C7FA"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        })
        row.setOnClickListener { onClick() }
        row.isClickable = true
        return row
    }

    private fun segmentedRow(
        title: String,
        subtitle: String,
        options: List<String>,
        selected: Int,
        onSelect: (Int) -> Unit,
    ): View {
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, dp(10))
        }
        column.addView(TextView(context).apply {
            text = title
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        })
        if (subtitle.isNotEmpty()) {
            column.addView(TextView(context).apply {
                text = subtitle
                setTextColor(Color.parseColor("#9AA3AD"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setPadding(0, dp(2), 0, 0)
            })
        }

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }
        options.forEachIndexed { index, option ->
            val isSelected = index == selected
            row.addView(TextView(context).apply {
                text = option
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(if (isSelected) Color.parseColor("#101820") else Color.WHITE)
                background = GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat()
                    setColor(if (isSelected) Color.parseColor("#A8C7FA") else Color.parseColor("#26292E"))
                }
                setPadding(dp(10), dp(9), dp(10), dp(9))
                setOnClickListener { onSelect(index) }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = if (index < options.size - 1) dp(6) else 0
            })
        }
        column.addView(row)
        return column
    }

    private fun rowContainer(title: String, subtitle: String): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(12))
        }
        val text = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        text.addView(TextView(context).apply {
            this.text = title
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        })
        if (subtitle.isNotEmpty()) {
            text.addView(TextView(context).apply {
                this.text = subtitle
                setTextColor(Color.parseColor("#9AA3AD"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setPadding(0, dp(2), dp(12), 0)
            })
        }
        row.addView(text, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    private fun outlinedButton(label: String, onClick: () -> Unit): View = TextView(context).apply {
        text = label
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        background = GradientDrawable().apply {
            cornerRadius = dp(24).toFloat()
            setColor(Color.TRANSPARENT)
            setStroke(dp(1), Color.parseColor("#6A7178"))
        }
        setPadding(0, dp(13), 0, dp(13))
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).roundToInt()
}

/**
 * The Advanced page. Four working switches and one honest note.
 */
class AdvancedSheet(
    context: Context,
    private var settings: Settings,
    private val onChange: (Settings) -> Unit,
) : Dialog(context, R.style.SheetDialog) {

    init {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#121417"))
                cornerRadii = floatArrayOf(dp(24).toFloat(), dp(24).toFloat(), dp(24).toFloat(), dp(24).toFloat(), 0f, 0f, 0f, 0f)
            }
            setPadding(dp(20), dp(18), dp(20), dp(26))
        }

        root.addView(TextView(context).apply {
            text = "Advanced"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setPadding(0, 0, 0, dp(10))
        })

        root.addView(toggle(context, "RAW/JPEG control",
            "Save a DNG alongside the JPEG in RAW mode", settings.rawPlusJpeg) {
            settings = settings.copy(rawPlusJpeg = it); onChange(settings)
        })

        root.addView(toggle(context, "Timer light",
            "Keep the torch on during the countdown", settings.timerLight) {
            settings = settings.copy(timerLight = it); onChange(settings)
        })

        root.addView(sliderRow(
            title = "JPEG quality",
            subtitle = "Higher means a larger file and fewer compression artefacts",
            value = settings.jpegQuality,
        ) { quality ->
            settings = settings.copy(jpegQuality = quality)
            onChange(settings)
        })

        root.addView(segmentedRow(
            title = "RAW bit depth",
            subtitle = "10-bit packs four pixels into five bytes; half the DNG size",
            options = listOf("10-bit", "16-bit"),
            selected = if (settings.rawBitDepth == 10) 0 else 1,
        ) { index ->
            settings = settings.copy(rawBitDepth = if (index == 0) 10 else 16)
            onChange(settings)
        })

        root.addView(toggle(context, "Optical stabilization (OIS)",
            "Hardware stabilisation. Off can allow a longer handheld exposure", settings.ois) {
            settings = settings.copy(ois = it); onChange(settings)
        })

        root.addView(toggle(context, "Vibration on shutter",
            "Short haptic tick when a photo is taken", settings.vibrationOnShutter) {
            settings = settings.copy(vibrationOnShutter = it); onChange(settings)
        })

        root.addView(toggle(context, "Rich colour in photos",
            "Save wide-gamut (Display P3) instead of sRGB", settings.wideColour) {
            settings = settings.copy(wideColour = it); onChange(settings)
        })

        root.addView(toggle(context, "Framing hints",
            "Level indicator while framing", settings.framingHints) {
            settings = settings.copy(framingHints = it); onChange(settings)
        })

        root.addView(TextView(context).apply {
            text = "Not available in this app: HDRnet, Top Shot, depth capture, " +
                "astrophotography time lapse and Ultra HDR gain maps. Each needs " +
                "processing this app does not implement; see docs/SETTINGS.md."
            setTextColor(Color.parseColor("#9AA3AD"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(0, dp(16), 0, 0)
        })

        setContentView(root)
        window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            setBackgroundDrawableResource(android.R.color.transparent)
        }
    }

    private fun toggle(
        context: Context,
        title: String,
        subtitle: String,
        checked: Boolean,
        onChange: (Boolean) -> Unit,
    ): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(12))
        }
        val text = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        text.addView(TextView(context).apply {
            this.text = title
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        })
        text.addView(TextView(context).apply {
            this.text = subtitle
            setTextColor(Color.parseColor("#9AA3AD"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(0, dp(2), dp(12), 0)
        })
        row.addView(text, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(Switch(context).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, value -> onChange(value) }
        })
        return row
    }

    private fun sliderRow(title: String, subtitle: String, value: Int, onChange: (Int) -> Unit): View {
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, dp(12))
        }
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val text = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        text.addView(TextView(context).apply {
            this.text = title
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        })
        text.addView(TextView(context).apply {
            this.text = subtitle
            setTextColor(Color.parseColor("#9AA3AD"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(0, dp(2), dp(12), 0)
        })
        header.addView(text, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val readout = TextView(context).apply {
            this.text = value.toString()
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        }
        header.addView(readout)
        column.addView(header)

        column.addView(android.widget.SeekBar(context).apply {
            max = 15
            progress = (value - 85).coerceIn(0, 15)
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: android.widget.SeekBar, progress: Int, fromUser: Boolean) {
                    val quality = 85 + progress
                    readout.text = quality.toString()
                    if (fromUser) onChange(quality)
                }

                override fun onStartTrackingTouch(bar: android.widget.SeekBar) = Unit
                override fun onStopTrackingTouch(bar: android.widget.SeekBar) = Unit
            })
        })
        return column
    }

    private fun segmentedRow(
        title: String,
        subtitle: String,
        options: List<String>,
        selected: Int,
        onSelect: (Int) -> Unit,
    ): View {
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, dp(12))
        }
        column.addView(TextView(context).apply {
            this.text = title
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        })
        column.addView(TextView(context).apply {
            this.text = subtitle
            setTextColor(Color.parseColor("#9AA3AD"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(0, dp(2), 0, dp(8))
        })

        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        options.forEachIndexed { index, option ->
            val isSelected = index == selected
            row.addView(TextView(context).apply {
                text = option
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(if (isSelected) Color.parseColor("#101820") else Color.WHITE)
                background = GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat()
                    setColor(if (isSelected) Color.parseColor("#A8C7FA") else Color.parseColor("#26292E"))
                }
                setPadding(dp(10), dp(9), dp(10), dp(9))
                setOnClickListener { onSelect(index) }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = if (index < options.size - 1) dp(6) else 0
            })
        }
        column.addView(row)
        return column
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).roundToInt()
}
