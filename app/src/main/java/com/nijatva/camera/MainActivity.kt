package com.nijatva.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.SurfaceHolder
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.nijatva.camera.camera.Aspect
import com.nijatva.camera.camera.CameraEngine
import com.nijatva.camera.camera.DeviceCapabilities
import com.nijatva.camera.camera.GyroTracker
import com.nijatva.camera.camera.Mode
import com.nijatva.camera.settings.FlashMode
import com.nijatva.camera.settings.GridType
import com.nijatva.camera.settings.Ratio
import com.nijatva.camera.settings.Settings
import com.nijatva.camera.settings.SettingsStore
import com.nijatva.camera.ui.AutoFitSurfaceView
import com.nijatva.camera.ui.GridOverlay
import com.nijatva.camera.ui.SettingsSheet
import com.nijatva.camera.ui.ProPanel
import com.nijatva.camera.ui.SphereOverlay
import com.nijatva.camera.ui.StatusStrip
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Viewfinder, mode bar, pro panel and panorama guidance, all built in code.
 *
 * The layout follows the two reference screens: a live readout strip across the
 * top of the frame, a single-parameter manual panel at the bottom, and a target
 * grid when a panorama is running.
 */
class MainActivity : AppCompatActivity(), CameraEngine.Callback {

    private lateinit var engine: CameraEngine
    private lateinit var gyro: GyroTracker

    private lateinit var preview: AutoFitSurfaceView
    private lateinit var sphereOverlay: SphereOverlay
    private lateinit var gridOverlay: GridOverlay
    private lateinit var statusStrip: StatusStrip
    private lateinit var statusText: TextView
    private lateinit var modeBar: LinearLayout
    private lateinit var proPanel: ProPanel
    private lateinit var infoChip: TextView
    private lateinit var aspectButton: TextView
    private lateinit var topBar: LinearLayout
    private lateinit var bottomBar: LinearLayout

    private lateinit var store: SettingsStore
    private var settings = Settings()

    private val modeChips = mutableMapOf<Mode, TextView>()
    private var caps: DeviceCapabilities? = null

    private val panoTicker = Handler(Looper.getMainLooper())
    private val uiTicker = Handler(Looper.getMainLooper())
    private var panoActive = false
    private var panoFrames = 0
    private var startYaw = 0f
    private var lastPanoYaw = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        store = SettingsStore(this)
        settings = store.load()

        buildUi()

        engine = CameraEngine(this, this)
        gyro = GyroTracker(this)
        selectMode(Mode.PHOTO)
        applySettings()
        gyro.start()
        uiTicker.post(levelRunnable)

        preview.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) = engine.attachPreview(holder.surface)
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
            override fun surfaceDestroyed(holder: SurfaceHolder) = engine.detachPreview()
        })

        if (hasCameraPermission()) {
            engine.start()
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA)
        }
    }

    override fun onDestroy() {
        uiTicker.removeCallbacksAndMessages(null)
        stopPanorama()
        gyro.stop()
        engine.close()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- layout

    private fun buildUi() {
        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)

        preview = AutoFitSurfaceView(this)
        root.addView(preview, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER
        ))

        gridOverlay = GridOverlay(this).apply { visibility = View.GONE }
        root.addView(gridOverlay, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))

        sphereOverlay = SphereOverlay(this).apply { visibility = View.GONE }
        root.addView(sphereOverlay, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))

        topBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        statusStrip = StatusStrip(this)
        topBar.addView(statusStrip, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }
        infoChip = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            text = "starting camera"
        }
        buttonRow.addView(infoChip, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        aspectButton = iconButton("4:3") { cycleAspect() }
        buttonRow.addView(aspectButton)
        buttonRow.addView(iconButton("Set") { openSettings() })
        buttonRow.addView(iconButton("Info") { showDiagnostics() })
        buttonRow.addView(iconButton("Flip") { engine.switchCamera() })
        buttonRow.addView(iconButton("Flash") { toggleFlash() })
        topBar.addView(buttonRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        root.addView(topBar, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP
        ))

        proPanel = ProPanel(this).apply {
            visibility = View.GONE
            listener = object : ProPanel.Listener {
                override fun onExposureCompensation(steps: Int) {
                    engine.updateSettings { it.withEv(steps) }
                }

                override fun onWhiteBalance(kelvin: Int?) {
                    engine.updateSettings { it.withWhiteBalance(kelvin) }
                }

                override fun onFocus(diopters: Float?) {
                    engine.updateSettings { it.withFocus(diopters) }
                }

                override fun onShutter(nanoseconds: Long?) {
                    engine.updateSettings { it.withExposure(nanoseconds) }
                }

                override fun onIso(iso: Int?) {
                    engine.updateSettings { it.withIso(iso) }
                }

                override fun onResetAll() {
                    engine.updateSettings {
                        it.copy(iso = null, exposureNs = null, focusDiopters = null, evSteps = null, wbKelvin = null)
                    }
                }
            }
        }
        root.addView(proPanel, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM
        ).apply { bottomMargin = dp(136) })

        bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(16))
            setBackgroundColor(Color.argb(160, 0, 0, 0))
        }

        statusText = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            text = ""
        }
        bottomBar.addView(statusText)

        modeBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        Mode.values().forEach { mode ->
            val chip = modeChip(mode)
            modeChips[mode] = chip
            modeBar.addView(chip)
        }
        bottomBar.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(modeBar)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val shutterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, 0)
        }
        shutterRow.addView(View(this), LinearLayout.LayoutParams(0, dp(1), 1f))
        shutterRow.addView(shutterButton())
        shutterRow.addView(sideButton(), LinearLayout.LayoutParams(dp(72), dp(72)))
        bottomBar.addView(shutterRow)

        root.addView(bottomBar, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM
        ))

        setContentView(root)
        applyInsets(root)

        val scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val next = (engine.currentSettings.zoom * detector.scaleFactor).coerceIn(1f, 8f)
                engine.updateSettings { it.copy(zoom = next) }
                statusText.text = "Zoom ${"%.1f".format(next)}x"
                return true
            }
        })

        val tapDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(event: MotionEvent): Boolean {
                engine.setMeteringPoint(event.x / preview.width, event.y / preview.height)
                statusText.text = "Focus locked"
                return true
            }
        })

        preview.setOnTouchListener { view, event ->
            scaleDetector.onTouchEvent(event)
            tapDetector.onTouchEvent(event)
            if (event.actionMasked == MotionEvent.ACTION_UP) view.performClick()
            true
        }
    }

    private fun applyInsets(root: View) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            topBar.updatePadding(top = bars.top + dp(4))
            bottomBar.updatePadding(bottom = dp(10) + bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun modeChip(mode: Mode): TextView = TextView(this).apply {
        text = mode.label
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        gravity = Gravity.CENTER
        setPadding(dp(12), dp(7), dp(12), dp(7))
        setOnClickListener { selectMode(mode) }
    }

    private fun shutterButton(): View = View(this).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.WHITE)
            setStroke(dp(4), Color.argb(180, 255, 255, 255))
        }
        setOnClickListener {
            if (panoActive) {
                engine.capturePanoramaFrame(gyro.yawDegrees)
                lastPanoYaw = gyro.yawDegrees
            } else {
                if (settings.vibrationOnShutter) {
                    window.decorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                }
                captureWithTimer()
            }
        }
        layoutParams = LinearLayout.LayoutParams(dp(72), dp(72)).apply {
            leftMargin = dp(20)
            rightMargin = dp(20)
        }
    }

    /** Right-hand button: finishes a panorama, otherwise resets everything to auto. */
    private fun sideButton(): TextView = TextView(this).apply {
        text = "AUTO"
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        gravity = Gravity.CENTER
        setOnClickListener {
            if (panoActive) {
                finishPanorama()
            } else {
                proPanel.resetAll()
                engine.updateSettings { it.copy(zoom = 1f) }
                statusText.text = "Back to automatic"
            }
        }
    }

    private fun iconButton(label: String, action: () -> Unit): TextView = TextView(this).apply {
        text = label
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        gravity = Gravity.CENTER
        setPadding(dp(10), dp(8), dp(10), dp(8))
        setOnClickListener { action() }
    }

    private fun selectMode(mode: Mode) {
        if (panoActive && mode != Mode.PANORAMA) stopPanorama()

        engine.setMode(mode)
        proPanel.visibility = if (mode == Mode.PRO) View.VISIBLE else View.GONE

        modeChips.forEach { (chipMode, chip) ->
            chip.background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(if (chipMode == mode) Color.argb(235, 255, 255, 255) else Color.argb(60, 255, 255, 255))
            }
            chip.setTextColor(if (chipMode == mode) Color.BLACK else Color.WHITE)
        }

        if (mode == Mode.PANORAMA) startPanorama()
    }

    // ---------------------------------------------------------------- panorama

    private fun startPanorama() {
        if (!engine.beginPanorama()) {
            statusText.text = "This camera cannot run panorama capture"
            return
        }
        panoActive = true
        panoFrames = 0
        sphereOverlay.visibility = View.VISIBLE
        sphereOverlay.setProgress(0f)
        gyro.start()
        startYaw = gyro.yawDegrees
        lastPanoYaw = startYaw
        statusText.text = "Turn slowly to the right"
        panoTicker.postDelayed(panoRunnable, 500)
    }

    private val panoRunnable = object : Runnable {
        override fun run() {
            if (!panoActive) return
            val yaw = gyro.yawDegrees

            if (gyro.isAvailable && panoFrames < PANO_MAX_FRAMES) {
                val turned = abs(gyro.deltaDegrees(lastPanoYaw, yaw))
                if (turned >= PANO_STEP_DEGREES && gyro.isSteady()) {
                    lastPanoYaw = yaw
                    engine.capturePanoramaFrame(yaw)
                }
            }

            val total = abs(gyro.deltaDegrees(startYaw, yaw))
            sphereOverlay.setProgress(total / 360f)
            sphereOverlay.setHint(
                if (!gyro.isAvailable) "Gyro not available - tap the shutter to add frames"
                else "Turn slowly  $panoFrames frames"
            )
            panoTicker.postDelayed(this, 200)
        }
    }

    private fun finishPanorama() {
        stopPanorama(cancelSession = false)
        engine.finishPanorama()
    }

    private fun stopPanorama(cancelSession: Boolean = true) {
        if (!panoActive) return
        panoActive = false
        panoTicker.removeCallbacks(panoRunnable)
        sphereOverlay.visibility = View.GONE
        sphereOverlay.setProgress(0f)
        if (cancelSession) engine.cancelPanorama()
    }

    // ---------------------------------------------------------------- misc

    private fun openSettings() {
        SettingsSheet(this, settings) { updated ->
            settings = updated
            store.save(updated)
            applySettings()
        }.show()
    }

    private fun applySettings() {
        engine.updateSettings {
            it.copy(
                flashAlways = settings.flash == FlashMode.ON,
                autoFlash = settings.flash == FlashMode.AUTO,
                torchOn = if (settings.flash == FlashMode.ON) false else it.torchOn,
            )
        }
        engine.rawPlusJpeg = settings.rawPlusJpeg
        engine.mirrorFront = settings.saveSelfieAsPreviewed
        engine.wideColour = settings.wideColour
        engine.rawBitDepth = settings.rawBitDepth
        engine.jpegQuality = settings.jpegQuality

        val oisSupported = caps?.supportsOis ?: true
        engine.updateSettings { it.copy(oisOn = if (oisSupported) settings.ois else null) }

        val wanted = if (settings.ratio == Ratio.FULL) Aspect.RATIO_4_3 else Aspect.RATIO_16_9
        if (engine.aspect != wanted) {
            engine.setAspect(wanted)
            aspectButton.text = wanted.label
            caps?.preferredJpegSize(wanted.ratio)?.let { preview.setAspectRatio(it.width, it.height) }
        }

        gridOverlay.gridType = settings.grid
        gridOverlay.levelVisible = settings.framingHints
        gridOverlay.visibility =
            if (settings.grid == GridType.NONE && !settings.framingHints) View.GONE else View.VISIBLE
    }

    /** Countdown, with the torch held on if the timer light is enabled. */
    private fun captureWithTimer() {
        val seconds = settings.timer.seconds
        if (seconds <= 0) {
            statusText.text = "Capturing"
            engine.capture()
            return
        }

        if (settings.timerLight) engine.updateSettings { it.copy(torchOn = true) }
        statusText.text = "Timer $seconds"

        var remaining = seconds
        val tick = object : Runnable {
            override fun run() {
                remaining--
                if (remaining > 0) {
                    statusText.text = "Timer $remaining"
                    uiTicker.postDelayed(this, 1000)
                } else {
                    statusText.text = "Capturing"
                    engine.capture()
                    if (settings.timerLight) engine.updateSettings { it.copy(torchOn = false) }
                }
            }
        }
        uiTicker.postDelayed(tick, 1000)
    }

    private val levelRunnable = object : Runnable {
        override fun run() {
            if (settings.framingHints && !panoActive) gridOverlay.setRoll(gyro.rollDegrees)
            uiTicker.postDelayed(this, 150)
        }
    }

    private fun cycleAspect() {
        val next = engine.aspect.next()
        engine.setAspect(next)
        aspectButton.text = next.label
        caps?.preferredJpegSize(next.ratio)?.let { preview.setAspectRatio(it.width, it.height) }
    }

    private fun toggleFlash() {
        val torchOn = !engine.currentSettings.torchOn
        engine.updateSettings { it.copy(torchOn = torchOn, autoFlash = false) }
        statusText.text = if (torchOn) "Torch on" else "Torch off"
    }

    private fun showDiagnostics() {
        val c = caps
        if (c == null) {
            Toast.makeText(this, "Camera not ready yet", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Camera capabilities")
            .setMessage(c.diagnostics())
            .setPositiveButton("Close", null)
            .show()
    }

    // ---------------------------------------------------------------- engine callbacks

    override fun onReady(caps: DeviceCapabilities) = runOnUiThread {
        this.caps = caps
        infoChip.text = "${caps.levelName}  RAW ${if (caps.supportsRaw) "yes" else "no"}"
        caps.preferredJpegSize(engine.aspect.ratio)?.let { preview.setAspectRatio(it.width, it.height) }
        aspectButton.text = engine.aspect.label

        val isoRange = caps.isoRange
        val exposureRange = caps.exposureRange
        if (isoRange != null && exposureRange != null) {
            val evMax = caps.evRange?.let { maxOf(abs(it.lower), abs(it.upper)) } ?: 6
            proPanel.bind(
                minIso = isoRange.lower,
                maxIso = isoRange.upper,
                minExposureNs = exposureRange.lower,
                maxExposureNs = minOf(exposureRange.upper, 500_000_000L),
                maxEvSteps = evMax,
            )
        }
        statusText.text = "Ready"
    }

    override fun onMetrics(sensitivity: Int, exposureNs: Long, afState: Int) = runOnUiThread {
        val zoom = engine.currentSettings.zoom
        val lens = if (zoom <= 1.01f) "Auto Lens" else "${"%.1f".format(zoom)}x lens"
        statusStrip.update(lens, afState, exposureNs, sensitivity)
    }

    override fun onPanoramaProgress(frames: Int) {
        panoFrames = frames
    }

    override fun onFailure(message: String) = runOnUiThread {
        statusText.text = message
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onNotice(message: String) = runOnUiThread {
        statusText.text = message
    }

    override fun onSaved(uri: Uri, message: String) = runOnUiThread {
        statusText.text = message
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    /**
     * Volume keys fire the shutter, as on every camera app. Returning true keeps
     * the key from also changing the media volume.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN &&
            (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || event.keyCode == KeyEvent.KEYCODE_VOLUME_UP)
        ) {
            if (settings.vibrationOnShutter) {
                window.decorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
            if (panoActive) {
                engine.capturePanoramaFrame(gyro.yawDegrees)
                lastPanoYaw = gyro.yawDegrees
            } else {
                captureWithTimer()
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    // ---------------------------------------------------------------- plumbing

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                engine.start()
            } else {
                statusText.text = "Camera permission is required"
            }
        }
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private companion object {
        const val REQUEST_CAMERA = 101
        const val PANO_STEP_DEGREES = 8f
        const val PANO_MAX_FRAMES = 24
    }
}
