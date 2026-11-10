package com.nijatva.camera.ui

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The manual panel: one thermometer slider and five tools that decide what the
 * slider controls.
 *
 * Only one parameter is adjustable at a time. That is the whole design: a screen
 * of eight sliders is unusable with one thumb while holding a phone up, and the
 * reference layout this follows makes the same choice.
 */
class ProPanel(context: Context) : LinearLayout(context) {

    enum class Tool(val caption: String) {
        EXPOSURE("Exposure"),
        WHITE_BALANCE("White balance"),
        FOCUS("Focus"),
        SHUTTER("Shutter speed"),
        ISO("ISO"),
    }

    interface Listener {
        fun onExposureCompensation(steps: Int)
        fun onWhiteBalance(kelvin: Int?)
        fun onFocus(diopters: Float?)
        fun onShutter(nanoseconds: Long?)
        fun onIso(iso: Int?)
        fun onResetAll()
    }

    var listener: Listener? = null

    private val slider = ThermometerSlider(context)
    private val toolRow = LinearLayout(context)
    private val buttons = mutableMapOf<Tool, ToolButton>()

    private var activeTool: Tool? = null

    private var minIso = 100
    private var maxIso = 3200
    private var minExposureNs = 1_000_000L
    private var maxExposureNs = 100_000_000L
    private var maxEvSteps = 6

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.argb(210, 8, 10, 12))
        setPadding(dp(8), dp(6), dp(8), dp(10))

        slider.listener = object : ThermometerSlider.Listener {
            override fun onValueChanged(value: Float) = applyValue(value)
            override fun onResetToAuto() {
                clearActiveTool()
                listener?.let {
                    it.onExposureCompensation(0)
                    it.onWhiteBalance(null)
                    it.onFocus(null)
                    it.onShutter(null)
                    it.onIso(null)
                }
            }
        }
        addView(slider, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        toolRow.orientation = HORIZONTAL
        toolRow.gravity = Gravity.CENTER

        addTool(ToolButton.Kind.RESET, "Reset all") { resetAll() }
        addTool(ToolButton.Kind.EXPOSURE, Tool.EXPOSURE.caption) { select(Tool.EXPOSURE) }
        addTool(ToolButton.Kind.WHITE_BALANCE, Tool.WHITE_BALANCE.caption) { select(Tool.WHITE_BALANCE) }
        addTool(ToolButton.Kind.FOCUS, Tool.FOCUS.caption) { select(Tool.FOCUS) }
        addTool(ToolButton.Kind.SHUTTER, Tool.SHUTTER.caption) { select(Tool.SHUTTER) }
        addTool(ToolButton.Kind.ISO, Tool.ISO.caption) { select(Tool.ISO) }

        addView(toolRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        slider.setSliderEnabled(false)
    }

    fun bind(minIso: Int, maxIso: Int, minExposureNs: Long, maxExposureNs: Long, maxEvSteps: Int) {
        this.minIso = minIso.coerceAtLeast(1)
        this.maxIso = maxIso.coerceAtLeast(this.minIso + 1)
        this.minExposureNs = minExposureNs.coerceAtLeast(100_000L)
        this.maxExposureNs = maxExposureNs.coerceAtLeast(this.minExposureNs + 1)
        this.maxEvSteps = maxEvSteps.coerceAtLeast(1)
    }

    fun resetAll() {
        clearActiveTool()
        slider.resetToAuto()
        listener?.onResetAll()
    }

    private fun addTool(kind: ToolButton.Kind, caption: String, onClick: () -> Unit) {
        val button = ToolButton(context).apply {
            this.kind = kind
            this.caption = caption
            setOnClickListener { onClick() }
        }
        if (kind != ToolButton.Kind.RESET) {
            val tool = when (kind) {
                ToolButton.Kind.EXPOSURE -> Tool.EXPOSURE
                ToolButton.Kind.WHITE_BALANCE -> Tool.WHITE_BALANCE
                ToolButton.Kind.FOCUS -> Tool.FOCUS
                ToolButton.Kind.SHUTTER -> Tool.SHUTTER
                else -> Tool.ISO
            }
            buttons[tool] = button
        }
        toolRow.addView(button, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun select(tool: Tool) {
        activeTool = tool
        buttons.forEach { (key, button) -> button.highlighted = key == tool }
        slider.setSliderEnabled(true)
        slider.resetToAuto()
        slider.setValueLabel("Auto")
    }

    private fun clearActiveTool() {
        activeTool = null
        buttons.values.forEach { it.highlighted = false }
        slider.setSliderEnabled(false)
        slider.setValueLabel("Auto")
    }

    private fun applyValue(value: Float) {
        val tool = activeTool ?: return
        when (tool) {
            Tool.EXPOSURE -> {
                val steps = (value * maxEvSteps).roundToInt()
                slider.setValueLabel(if (steps == 0) "Auto" else String.format("%+.1f EV", steps / 3f))
                listener?.onExposureCompensation(steps)
            }

            Tool.WHITE_BALANCE -> {
                val kelvin = (6500f * 2f.pow(value)).roundToInt().coerceIn(2000, 9000)
                slider.setValueLabel("$kelvin K")
                listener?.onWhiteBalance(kelvin)
            }

            Tool.FOCUS -> {
                val diopters = ((value + 1f) / 2f) * 10f
                slider.setValueLabel(if (diopters < 0.05f) "Infinity" else String.format("%.1f dpt", diopters))
                listener?.onFocus(diopters)
            }

            Tool.SHUTTER -> {
                val ratio = (value + 1f) / 2f
                val ns = (minExposureNs * (maxExposureNs.toDouble() / minExposureNs).pow(ratio.toDouble())).toLong()
                slider.setValueLabel(formatShutter(ns))
                listener?.onShutter(ns)
            }

            Tool.ISO -> {
                val ratio = (value + 1f) / 2f
                val iso = (minIso * (maxIso.toDouble() / minIso).pow(ratio.toDouble())).roundToInt()
                slider.setValueLabel("ISO $iso")
                listener?.onIso(iso)
            }
        }
        if (abs(value) < 0.02f) slider.setValueLabel("Auto")
    }

    private fun formatShutter(ns: Long): String =
        if (ns >= 1_000_000_000L) String.format("%.1fs", ns / 1_000_000_000.0)
        else "1/${(1_000_000_000.0 / ns).roundToInt()}s"

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
}
