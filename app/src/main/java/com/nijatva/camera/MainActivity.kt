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
import com.nijatva.camera.ui.IconButton
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
    private lateinit var topBar: LinearLayout
    private lateinit var bottomBar: LinearLayout

    private lateinit var store: SettingsStore
    private lateinit var flashButton: IconButton
    private lateinit var menuButton: IconButton
    private lateinit var gridButton: IconButton
    private lateinit var hdrButton: IconButton
    private val zoomChips = mutableMapOf<Float, TextView>()
    private var currentMode: Mode = Mode.PHOTO
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

        showPreviousCrash()
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
        // The preview is deliberately larger than the screen in cover mode, so
        // the parent has to crop it rather than let it spill.
        root.clipChildren = true

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

        // ---- top chrome: flash on the left, menu on the right
        topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }
        flashButton = IconButton(this).apply {
            kind = IconButton.Kind.FLASH_OFF
            setOnClickListener { cycleFlash() }
        }
        topBar.addView(flashButton)
        topBar.addView(View(this), LinearLayout.LayoutParams(0, dp(1), 1f))
        menuButton = IconButton(this).apply {
            kind = IconButton.Kind.MENU
            setOnClickListener { openSettings() }
        }
        topBar.addView(menuButton)
        root.addView(topBar, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP
        ))

        // ---- bottom chrome
        bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(6), dp(10), dp(14))
        }

        statusStrip = StatusStrip(this).apply { visibility = View.GONE }
        bottomBar.addView(statusStrip, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        statusText = TextView(this).apply {
            setTextColor(Color.parseColor("#D6DBE1"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
            text = ""
            setPadding(0, dp(2), 0, dp(6))
        }
        bottomBar.addView(statusText)

        // zoom pill and the two quick toggles, sitting above the mode row
        val quickRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(8))
        }
        quickRow.addView(View(this), LinearLayout.LayoutParams(0, dp(1), 1f))
        quickRow.addView(zoomPill())
        quickRow.addView(View(this), LinearLayout.LayoutParams(0, dp(1), 1f))

        gridButton = IconButton(this).apply {
            kind = IconButton.Kind.GRID
            setOnClickListener { cycleGrid() }
        }
        hdrButton = IconButton(this).apply {
            kind = IconButton.Kind.HDR
            setOnClickListener { cycleHdr() }
        }
        quickRow.addView(gridButton)
        quickRow.addView(hdrButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { leftMargin = dp(6) })
        bottomBar.addView(quickRow)

        modeBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(4), 0, dp(4), dp(6))
        }
        Mode.values().forEach { mode ->
            val tab = modeTab(mode)
            modeChips[mode] = tab
            modeBar.addView(tab)
        }
        bottomBar.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(modeBar)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val shutterRow = FrameLayout(this).apply { minimumHeight = dp(76) }
        shutterRow.addView(thumbnail(), FrameLayout.LayoutParams(
            dp(48), dp(48), Gravity.START or Gravity.CENTER_VERTICAL
        ).apply { leftMargin = dp(12) })
        shutterRow.addView(shutterButton(), FrameLayout.LayoutParams(
            dp(72), dp(72), Gravity.CENTER
        ))
        shutterRow.addView(IconButton(this).apply {
            kind = IconButton.Kind.FLIP
            setOnClickListener { engine.switchCamera() }
        }, FrameLayout.LayoutParams(
            dp(44), dp(44), Gravity.END or Gravity.CENTER_VERTICAL
        ).apply { rightMargin = dp(14) })
        bottomBar.addView(shutterRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        root.addView(bottomBar, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM
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
        ).apply { bottomMargin = dp(178) })

        setContentView(root)
        applyInsets(root)

        val scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val next = (engine.currentSettings.zoom * detector.scaleFactor).coerceIn(1f, 8f)
                engine.updateSettings { it.copy(zoom = next) }
                updateZoomChips(next)
                return true
            }
        })

        val tapDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(event: MotionEvent): Boolean {
                engine.setMeteringPoint(event.x / preview.width, event.y / preview.height)
                gridOverlay.showReticle(event.x, event.y)
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
            bottomBar.updatePadding(bottom = dp(12) + bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    /** Mode selector as plain uppercase text, the way the stock camera shows it. */
    private fun modeTab(mode: Mode): TextView = TextView(this).apply {
        text = mode.label.uppercase()
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        letterSpacing = 0.08f
        gravity = Gravity.CENTER
        setPadding(dp(10), dp(8), dp(10), dp(8))
        setOnClickListener { selectMode(mode) }
    }

    private fun zoomPill(): View {
        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(Color.argb(70, 0, 0, 0))
            }
            setPadding(dp(4), dp(3), dp(4), dp(3))
        }
        listOf(1f, 2f).forEach { factor ->
            val chip = TextView(this).apply {
                text = if (factor == 1f) "1x" else "2x"
                setTextColor(if (factor == 1f) Color.parseColor("#101418") else Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat()
                    setColor(if (factor == 1f) Color.WHITE else Color.TRANSPARENT)
                }
                setPadding(dp(12), dp(4), dp(12), dp(4))
                setOnClickListener {
                    engine.updateSettings { it.copy(zoom = factor) }
                    updateZoomChips(factor)
                }
            }
            zoomChips[factor] = chip
            pill.addView(chip)
        }
        return pill
    }

    private fun updateZoomChips(zoom: Float) {
        val selected = if (zoom >= 1.6f) 2f else 1f
        zoomChips.forEach { (factor, chip) ->
            val chosen = factor == selected
            chip.setTextColor(if (chosen) Color.parseColor("#101418") else Color.WHITE)
            chip.background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(if (chosen) Color.WHITE else Color.TRANSPARENT)
            }
        }
    }

    private fun thumbnail(): View = View(this).apply {
        background = GradientDrawable().apply {
            cornerRadius = dp(8).toFloat()
            setColor(Color.parseColor("#2A2E33"))
            setStroke(dp(1), Color.argb(90, 255, 255, 255))
        }
        setOnClickListener {
            Toast.makeText(context, "Photos are saved to Pictures/NijatvaCamera", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shutterButton(): View = View(this).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.WHITE)
            setStroke(dp(5), Color.argb(150, 255, 255, 255))
        }
        setOnClickListener {
            if (settings.vibrationOnShutter) {
                window.decorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
            if (panoActive) {
                engine.capturePanoramaFrame(gyro.yawDegrees)
                lastPanoYaw = gyro.yawDegrees
            } else if (currentMode == Mode.PANORAMA) {
                finishPanorama()
            } else {
                captureWithTimer()
            }
        }
    }

    private fun cycleFlash() {
        val next = when (settings.flash) {
            FlashMode.OFF -> FlashMode.AUTO
            FlashMode.AUTO -> FlashMode.ON
            FlashMode.ON -> FlashMode.OFF
        }
        settings = settings.copy(flash = next)
        store.save(settings)
        applySettings()
    }

    private fun cycleGrid() {
        val next = GridType.values()[(settings.grid.ordinal + 1) % GridType.values().size]
        settings = settings.copy(grid = next)
        store.save(settings)
        applySettings()
        statusText.text = next.label
    }

    private fun cycleHdr() {
        val next = com.nijatva.camera.settings.HdrMode.values()[
            (settings.hdr.ordinal + 1) % com.nijatva.camera.settings.HdrMode.values().size
        ]
        settings = settings.copy(hdr = next)
        store.save(settings)
        applySettings()
        statusText.text = "HDR+ ${next.label}"
    }

    private fun selectMode(mode: Mode) {
        if (panoActive && mode != Mode.PANORAMA) stopPanorama()
        currentMode = mode

        engine.setMode(mode)
        proPanel.visibility = if (mode == Mode.PRO) View.VISIBLE else View.GONE

        modeChips.forEach { (chipMode, chip) ->
            val chosen = chipMode == mode
            chip.setTextColor(if (chosen) Color.parseColor("#FFB020") else Color.WHITE)
            chip.alpha = if (chosen) 1f else 0.75f
        }
        statusStrip.visibility = if (mode == Mode.PRO) View.VISIBLE else View.GONE

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
            caps?.preferredJpegSize(wanted.ratio)?.let { preview.setAspectRatio(it.width, it.height) }
        }

        flashButton.kind = when (settings.flash) {
            FlashMode.OFF -> IconButton.Kind.FLASH_OFF
            FlashMode.AUTO -> IconButton.Kind.FLASH_AUTO
            FlashMode.ON -> IconButton.Kind.FLASH_ON
        }
        hdrButton.active = settings.hdr != com.nijatva.camera.settings.HdrMode.OFF
        gridButton.active = settings.grid != GridType.NONE

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

    /** If the last run died, show why before anything else. */
    private fun showPreviousCrash() {
        val file = NijatvaApp.crashFile(this)
        if (!file.exists()) return
        val text = runCatching { file.readText() }.getOrNull() ?: return
        if (text.isBlank()) return

        file.delete()
        AlertDialog.Builder(this)
            .setTitle("Last run crashed")
            .setMessage(text.take(4000))
            .setPositiveButton("Close", null)
            .show()
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
