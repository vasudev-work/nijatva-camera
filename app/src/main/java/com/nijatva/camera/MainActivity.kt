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
import com.nijatva.camera.settings.AccentChoice
import com.nijatva.camera.settings.ThemeChoice
import com.nijatva.camera.ui.AppearanceSheet
import com.nijatva.camera.ui.AutoFitSurfaceView
import com.nijatva.camera.ui.DeveloperPage
import com.nijatva.camera.ui.Palette
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
    private lateinit var modeStrip: com.nijatva.camera.ui.ModeStrip
    private lateinit var zoomPill: com.nijatva.camera.ui.ZoomPill
    private lateinit var modesGrid: com.nijatva.camera.ui.ModesGrid
    private lateinit var developerPage: DeveloperPage
    private lateinit var controls: LinearLayout
    private lateinit var rightHost: FrameLayout
    private lateinit var flipButton: IconButton
    private lateinit var cameraToggle: IconButton
    private lateinit var videoToggle: IconButton
    private lateinit var proPanel: ProPanel
    private lateinit var topBar: LinearLayout
    private lateinit var bottomBar: LinearLayout

    private lateinit var store: SettingsStore
    private lateinit var flashButton: IconButton
    private lateinit var menuButton: IconButton
    private lateinit var gridButton: IconButton
    private lateinit var hdrButton: IconButton
    private var currentMode: Mode = Mode.PHOTO

    /**
     * Lite keeps the three modes that earn their place on a mid-range sensor:
     * a plain shot, a stacked night shot, and manual control. Panorama, video,
     * astro and RAW are still in the engine, just not offered. The rule itself
     * lives in Modes, so it can be unit tested.
     */
    private val stripModes: List<Mode> = com.nijatva.camera.camera.Modes.stripModes(BuildConfig.LITE)
    private val gridModes: List<Mode> = com.nijatva.camera.camera.Modes.gridModes(BuildConfig.LITE)
    private var settings = Settings()

    private var caps: DeviceCapabilities? = null

    private val panoTicker = Handler(Looper.getMainLooper())
    private val uiTicker = Handler(Looper.getMainLooper())

    private var thumbView: android.widget.ImageView? = null
    private var lastSavedUri: Uri? = null
    private val stillness = com.nijatva.camera.camera.StillnessTracker()
    private var ringColor = 0
    private var audioAsked = false
    private var movedSince = 0L

    /** Opens a settings JSON file from the system picker. */
    private val importLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) importSettings(uri) }
    private lateinit var histogramView: com.nijatva.camera.ui.HistogramView
    private lateinit var peakingOverlay: android.widget.ImageView
    private var wasRecording = false
    private lateinit var shutterRing: GradientDrawable
    private var panoActive = false
    private var panoFrames = 0
    private var startYaw = 0f
    private var lastPanoYaw = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        store = SettingsStore(this)
        settings = store.load()
        Palette.apply(settings.theme, settings.accent, isSystemDark())

        buildUi()

        engine = CameraEngine(this, this)
        engine.setDeviceRotation(currentRotationDegrees())
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

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        engine.setDeviceRotation(currentRotationDegrees())
        // The activity is not recreated on rotation, so the chrome is rearranged
        // by hand and the grid rebuilt for the new column count.
        applyOrientation()
        modesGrid.bind(
            modes = gridModes,
            stripModes = stripModes,
            columns = if (isTablet()) 4 else 3,
        )
        modesGrid.setSelected(currentMode)
    }

    /** Display rotation in degrees, which is what Camera2 expects for JPEG orientation. */
    private fun currentRotationDegrees(): Int = when (windowManager.defaultDisplay.rotation) {
        android.view.Surface.ROTATION_90 -> 90
        android.view.Surface.ROTATION_180 -> 180
        android.view.Surface.ROTATION_270 -> 270
        else -> 0
    }

    override fun onPause() {
        super.onPause()
        // Leaving the app ends the take cleanly instead of losing the file.
        if (engine.recording) engine.stopVideo()
    }

    override fun onAnalysis(
        histogram: IntArray,
        peaking: IntArray,
        width: Int,
        height: Int,
        rotationDegrees: Int,
    ) = runOnUiThread {
        if (settings.histogram && histogram.isNotEmpty()) {
            histogramView.setBins(histogram)
            histogramView.visibility = View.VISIBLE
        }

        if (settings.focusPeaking && peaking.isNotEmpty()) {
            val mask = android.graphics.Bitmap.createBitmap(
                peaking, width, height, android.graphics.Bitmap.Config.ARGB_8888,
            )
            // The analysis frame is sensor-native; turn it to match the upright preview.
            peakingOverlay.setImageBitmap(com.nijatva.camera.camera.Orientation.rotate(mask, rotationDegrees))
            val lp = peakingOverlay.layoutParams
            if (preview.width > 0 && (lp.width != preview.width || lp.height != preview.height)) {
                peakingOverlay.layoutParams = FrameLayout.LayoutParams(preview.width, preview.height, Gravity.CENTER)
            }
            peakingOverlay.visibility = View.VISIBLE
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
        root.setBackgroundColor(Palette.background)
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

        // Peaking sits over the preview, sized to the preview each time it changes.
        peakingOverlay = android.widget.ImageView(this).apply {
            scaleType = android.widget.ImageView.ScaleType.FIT_XY
            visibility = View.GONE
            isClickable = false
        }
        root.addView(peakingOverlay, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER
        ))

        histogramView = com.nijatva.camera.ui.HistogramView(this).apply { visibility = View.GONE }
        root.addView(histogramView, FrameLayout.LayoutParams(dp(140), dp(56), Gravity.TOP or Gravity.END).apply {
            topMargin = dp(64)
            rightMargin = dp(12)
        })

        // ---- top chrome: flash on the left, menu on the right
        topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(6), dp(10), dp(6))
            minimumHeight = dp(56)
        }
        flashButton = IconButton(this).apply {
            kind = IconButton.Kind.FLASH_OFF
            setOnClickListener { cycleFlash() }
        }
        topBar.addView(flashButton)
        topBar.addView(View(this), LinearLayout.LayoutParams(0, dp(1), 1f))
        menuButton = IconButton(this).apply {
            kind = IconButton.Kind.MENU
            setOnClickListener { openMenu() }
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

        // ---- zoom pill (only when the device really has more than one step)
        zoomPill = com.nijatva.camera.ui.ZoomPill(this).apply {
            onZoom = { factor ->
                engine.updateSettings { it.copy(zoom = factor) }
            }
            visibility = View.GONE
        }
        zoomPill.bind(listOf(1f, 2f), 1f)

        val quickRow = FrameLayout(this).apply { setPadding(0, 0, 0, dp(6)) }
        quickRow.addView(zoomPill, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER
        ))

        // Grid and HDR sit against the right edge above the strip, the way the
        // reference puts its utility buttons.
        gridButton = IconButton(this).apply {
            kind = IconButton.Kind.GRID
            setOnClickListener { cycleGrid() }
        }
        hdrButton = IconButton(this).apply {
            kind = IconButton.Kind.HDR
            setOnClickListener { cycleHdr() }
        }
        val utilities = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        utilities.addView(gridButton, LinearLayout.LayoutParams(dp(40), dp(40)))
        if (!BuildConfig.LITE) {
            utilities.addView(hdrButton, LinearLayout.LayoutParams(dp(40), dp(40)).apply { topMargin = dp(6) })
        }
        quickRow.addView(utilities, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.END or Gravity.BOTTOM
        ).apply { rightMargin = dp(2) })
        bottomBar.addView(quickRow)

        // ---- mode strip
        modeStrip = com.nijatva.camera.ui.ModeStrip(this).apply {
            onModeSelected = { mode -> selectMode(mode) }
            onMore = { openModesGrid() }
        }
        modeStrip.bind(stripModes)
        bottomBar.addView(modeStrip, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        // ---- bottom control row: thumbnail, shutter, flip
        controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        controls.addView(thumbnail(), LinearLayout.LayoutParams(dp(56), dp(56)).apply { leftMargin = dp(16) })
        controls.addView(View(this), LinearLayout.LayoutParams(0, dp(1), 1f))

        val shutter = FrameLayout(this)
        shutter.addView(View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(dp(2), Color.argb(190, 255, 255, 255))
            }
            isClickable = false
        }, FrameLayout.LayoutParams(dp(92), dp(92), Gravity.CENTER))
        shutter.addView(shutterButton(), FrameLayout.LayoutParams(
            dp(74), dp(74), Gravity.CENTER
        ))
        controls.addView(shutter, LinearLayout.LayoutParams(dp(92), dp(92)))

        controls.addView(View(this), LinearLayout.LayoutParams(0, dp(1), 1f))
        flipButton = IconButton(this).apply {
            kind = IconButton.Kind.FLIP
            setOnClickListener { engine.switchCamera() }
        }
        controls.addView(flipButton, LinearLayout.LayoutParams(dp(56), dp(56)).apply { rightMargin = dp(16) })
        bottomBar.addView(controls, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(96)
        ))

        // ---- utility row: settings gear, and the photo/video switch
        val utilityRow = FrameLayout(this)
        utilityRow.addView(IconButton(this).apply {
            kind = IconButton.Kind.GEAR
            setOnClickListener { openSettings() }
        }, FrameLayout.LayoutParams(dp(44), dp(44), Gravity.START or Gravity.CENTER_VERTICAL).apply {
            leftMargin = dp(8)
        })

        val togglePill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(Color.argb(60, 255, 255, 255))
            }
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        cameraToggle = IconButton(this).apply {
            kind = IconButton.Kind.CAMERA
            active = true
            setOnClickListener { selectMode(Mode.PHOTO) }
        }
        videoToggle = IconButton(this).apply {
            kind = IconButton.Kind.VIDEO
            setOnClickListener { selectMode(Mode.VIDEO) }
        }
        togglePill.addView(cameraToggle, LinearLayout.LayoutParams(dp(40), dp(40)))
        togglePill.addView(videoToggle, LinearLayout.LayoutParams(dp(40), dp(40)))

        // Lite has no video mode, so the switch would be a dead control.
        if (BuildConfig.LITE) togglePill.visibility = View.GONE
        utilityRow.addView(togglePill, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.END or Gravity.CENTER_VERTICAL
        ).apply { rightMargin = dp(8) })
        bottomBar.addView(utilityRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
        ))

        root.addView(bottomBar, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM
        ))

        // Landscape puts the control row on the right edge; the host stays empty
        // in portrait.
        rightHost = FrameLayout(this)
        root.addView(rightHost, FrameLayout.LayoutParams(
            dp(96), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END or Gravity.CENTER_VERTICAL
        ))

        modesGrid = com.nijatva.camera.ui.ModesGrid(this).apply {
            onModeSelected = { mode ->
                selectMode(mode)
                closeModesGrid()
            }
            onMoreTapped = { closeModesGrid() }
        }
        modesGrid.bind(
            modes = gridModes,
            stripModes = stripModes,
            columns = if (isTablet()) 4 else 3,
        )
        root.addView(modesGrid, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))

        developerPage = DeveloperPage(this).apply {
            onClose = { closeDeveloper() }
        }
        root.addView(developerPage, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
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
        applyOrientation()

        val scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val next = (engine.currentSettings.zoom * detector.scaleFactor).coerceIn(1f, 8f)
                engine.updateSettings { it.copy(zoom = next) }
                zoomPill.select(if (next >= 1.6f) 2f else 1f)
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
            if (::modesGrid.isInitialized) modesGrid.setBottomInset(bars.bottom)
            topBar.updatePadding(top = bars.top + dp(4))
            bottomBar.updatePadding(bottom = dp(12) + bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }


    /** Last-shot thumbnail. Shows the real photo after each save; tap opens it in the gallery. */
    private fun thumbnail(): View = android.widget.ImageView(this).apply {
        thumbView = this
        background = GradientDrawable().apply {
            cornerRadius = dp(8).toFloat()
            setColor(Color.parseColor("#2A2E33"))
            setStroke(dp(1), Color.argb(90, 255, 255, 255))
        }
        clipToOutline = true
        outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
        setOnClickListener { openLastShot() }
    }

    private fun refreshThumbnail(uri: Uri) {
        Thread {
            // Fails for some formats (DNG may have no thumbnail); keep the old one then.
            val bitmap = try {
                contentResolver.loadThumbnail(uri, android.util.Size(192, 192), null)
            } catch (_: Exception) {
                null
            }
            runOnUiThread { bitmap?.let { thumbView?.setImageBitmap(it) } }
        }.start()
    }

    private fun openLastShot() {
        val uri = lastSavedUri
        if (uri == null) {
            Toast.makeText(this, "No photo yet. Saved photos go to Pictures/NijatvaCamera", Toast.LENGTH_SHORT).show()
            return
        }
        val type = contentResolver.getType(uri) ?: "image/*"
        try {
            startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, type)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        } catch (_: Exception) {
            Toast.makeText(this, "No app can open this file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shutterButton(): View = View(this).apply {
        shutterRing = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.WHITE)
            setStroke(dp(5), Color.argb(150, 255, 255, 255))
        }
        background = shutterRing
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
        if (mode !in gridModes) return
        // A live take owns the session; switching mode would tear it down mid-file.
        if (engine.recording && mode != currentMode) {
            Toast.makeText(this, getString(R.string.stop_recording_first), Toast.LENGTH_SHORT).show()
            return
        }
        if (panoActive && mode != Mode.PANORAMA) stopPanorama()
        currentMode = mode

        engine.setMode(mode)
        proPanel.visibility = if (mode == Mode.PRO) View.VISIBLE else View.GONE

        modeStrip.setSelected(mode)
        cameraToggle.active = mode != Mode.VIDEO
        videoToggle.active = mode == Mode.VIDEO
        modesGrid.setSelected(mode)
        statusStrip.visibility = if (mode == Mode.PRO) View.VISIBLE else View.GONE
        // The pro panel already shows ISO and shutter live, and it sits over this
        // line; hiding it keeps the readout from being drawn through the panel.
        statusText.visibility = if (mode == Mode.PRO) View.GONE else View.VISIBLE
        // Analysis only runs in Photo and Pro, so hide its overlays in other modes.
        if (mode != Mode.PHOTO && mode != Mode.PRO) {
            histogramView.visibility = View.GONE
            peakingOverlay.visibility = View.GONE
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

    /** Opens the MORE grid over the running preview. */
    private fun openModesGrid() {
        modeStrip.setMoreHighlighted(true)
        modesGrid.setSelected(currentMode)
        modesGrid.open()
    }

    private fun closeModesGrid() {
        modesGrid.close()
        modeStrip.setMoreHighlighted(false)
    }

    /** Tablets and foldables get a fourth column in the grid. */
    private fun isTablet(): Boolean =
        resources.configuration.smallestScreenWidthDp >= 600

    private fun updateZoomPill() {
        val supported = caps?.supportsZoom == true
        zoomPill.visibility = if (supported) View.VISIBLE else View.GONE
    }

    /**
     * Moves the control row between the bottom of the screen and the right edge.
     *
     * The manifest handles orientation changes without recreating the activity, so
     * the same views are reparented instead of rebuilt: the camera session, the
     * preview surface and the engine all keep running through a rotation.
     */
    private fun applyOrientation() {
        val landscape = resources.configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE

        (controls.parent as? ViewGroup)?.removeView(controls)

        if (landscape) {
            controls.orientation = LinearLayout.VERTICAL
            rightHost.removeAllViews()
            rightHost.addView(controls, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ))
            bottomBar.setPadding(dp(10), dp(6), dp(96), dp(10))
        } else {
            controls.orientation = LinearLayout.HORIZONTAL
            rightHost.removeAllViews()
            bottomBar.addView(controls, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(96)
            ))
            bottomBar.setPadding(dp(10), dp(6), dp(10), dp(12))
        }
    }

    /**
     * The hamburger opens a short menu, the way the site's Menu button does:
     * Settings for capture behaviour, Appearance for theme and accent, Developer
     * for the profile page.
     */
    private fun openMenu() {
        val items = arrayOf(
            getString(R.string.menu_settings),
            getString(R.string.menu_appearance),
            getString(R.string.menu_developer),
        )
        AlertDialog.Builder(this)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> openSettings()
                    1 -> openAppearance()
                    2 -> openDeveloper()
                }
            }
            .show()
    }

    private fun openAppearance() {
        val before = settings
        AppearanceSheet(this, settings) { updated ->
            settings = updated
            store.save(updated)
            Palette.apply(updated.theme, updated.accent, isSystemDark())
            if (updated.theme != before.theme) {
                // Surfaces are built once, so a theme change rebuilds the chrome.
                // The camera restarts with it; that is the honest cost of a
                // theme switch, and it happens once per switch.
                recreate()
            } else {
                refreshAccent()
            }
        }.show()
    }

    /** Accent-only change: recolour what is already on screen. */
    private fun refreshAccent() {
        modeStrip.setSelected(currentMode)
        modesGrid.setSelected(currentMode)
        zoomPill.select(if (engine.currentSettings.zoom >= 1.6f) 2f else 1f)
        ringColor = 0
        updateShutterRing()
    }

    private fun openDeveloper() {
        developerPage.open()
    }

    private fun closeDeveloper() {
        developerPage.close()
    }

    private fun isSystemDark(): Boolean =
        (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

    private fun openSettings() {
        SettingsSheet(this, settings, onPresets = { showPresets() }) { updated ->
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
        engine.setAstroConfig(settings.astroSeconds, settings.astroFrames)
        engine.setVideoAudio(settings.recordAudio && hasAudioPermission())
        engine.histogramWanted = settings.histogram
        engine.peakingWanted = settings.focusPeaking
        if (!settings.histogram) histogramView.visibility = View.GONE
        if (!settings.focusPeaking) peakingOverlay.visibility = View.GONE
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
    /**
     * Video needs the microphone permission before the recorder is built. The first
     * record press asks for it and waits for the answer. A denial, or an earlier one,
     * records picture only. Returns false when recording should wait for the prompt.
     */
    private fun prepareVideoAudio(): Boolean {
        val wanted = currentMode == Mode.VIDEO && !engine.recording && settings.recordAudio
        if (!wanted) {
            engine.setVideoAudio(false)
            return true
        }
        if (hasAudioPermission()) {
            engine.setVideoAudio(true)
            return true
        }
        if (!audioAsked) {
            audioAsked = true
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO)
            return false
        }
        engine.setVideoAudio(false)
        return true
    }

    /**
     * Astro: if the phone keeps moving for two seconds during a stack, cancel it.
     * A blurred stack is worse than no stack, and the preview must come back.
     */
    private fun watchAstroMotion() {
        if (currentMode != Mode.ASTRO || !engine.collecting) {
            movedSince = 0L
            return
        }
        if (!gyro.isAvailable || stillness.isStill()) {
            movedSince = 0L
            return
        }
        val now = System.currentTimeMillis()
        if (movedSince == 0L) {
            movedSince = now
        } else if (now - movedSince > 2_000L) {
            movedSince = 0L
            engine.abortCapture("Phone moved. Astro stack cancelled, nothing was saved")
        }
    }

    /** Presets and backup. Save or load named setups, share or import JSON, or reset to defaults. */
    private fun showPresets() {
        val names = store.presetNames()
        val items = mutableListOf(
            "Save current settings as a preset",
            "Share settings as JSON",
            "Import settings from a JSON file",
            "Reset to defaults",
        )
        names.forEach { items += "Load: $it" }
        names.forEach { items += "Delete: $it" }
        val loadBase = 4

        AlertDialog.Builder(this)
            .setTitle("Presets and backup")
            .setItems(items.toTypedArray()) { _, which ->
                when {
                    which == 0 -> askPresetName()
                    which == 1 -> shareSettings()
                    which == 2 -> importLauncher.launch(arrayOf("application/json", "text/plain"))
                    which == 3 -> {
                        store.reset()
                        settings = Settings()
                        applySettings()
                        Toast.makeText(this, "Settings reset to defaults", Toast.LENGTH_SHORT).show()
                    }
                    which - loadBase < names.size -> {
                        val name = names[which - loadBase]
                        store.loadPreset(name)?.let {
                            settings = it
                            store.save(it)
                            applySettings()
                            Toast.makeText(this, "Loaded $name", Toast.LENGTH_SHORT).show()
                        } ?: Toast.makeText(this, "Preset $name could not be read", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        val name = names[which - loadBase - names.size]
                        store.deletePreset(name)
                        Toast.makeText(this, "Deleted $name", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    /**
     * Reads a settings file chosen by the user and applies it. Files over 64 KB are
     * refused, since a real settings file is a few hundred bytes.
     */
    private fun importSettings(uri: Uri) {
        val text = try {
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                val buffer = CharArray(64 * 1024 + 1)
                val read = reader.read(buffer)
                if (read > 64 * 1024) null else String(buffer, 0, maxOf(read, 0))
            }
        } catch (_: Exception) {
            null
        }

        val imported = text?.let { store.importJsonOrNull(it) }
        if (imported == null) {
            Toast.makeText(this, "That file is not a Nijatva settings file", Toast.LENGTH_LONG).show()
            return
        }
        settings = imported
        store.save(imported)
        applySettings()
        Toast.makeText(this, "Settings imported", Toast.LENGTH_SHORT).show()
    }

    private fun askPresetName() {
        val input = android.widget.EditText(this).apply { hint = "Preset name" }
        AlertDialog.Builder(this)
            .setTitle("Save preset")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, "Give the preset a name", Toast.LENGTH_SHORT).show()
                } else {
                    store.savePreset(name, settings)
                    Toast.makeText(this, "Saved $name", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Sends the settings as JSON through the system share sheet, for a backup or another phone. */
    private fun shareSettings() {
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(android.content.Intent.EXTRA_SUBJECT, "Nijatva Camera settings")
            putExtra(android.content.Intent.EXTRA_TEXT, store.exportJson(settings))
        }
        startActivity(android.content.Intent.createChooser(send, "Share settings"))
    }

    private fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun captureWithTimer() {
        if (!prepareVideoAudio()) return
        // Astro needs a tripod. Refuse to start while the phone is moving.
        if (currentMode == Mode.ASTRO && gyro.isAvailable && !stillness.isStill()) {
            statusText.text = "Put the phone on a tripod first. It must be still"
            return
        }
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
            stillness.push(gyro.yawDegrees, gyro.pitchDegrees, gyro.rollDegrees)
            updateShutterRing()
            updateRecordingStatus()
            watchAstroMotion()
            uiTicker.postDelayed(this, 150)
        }
    }

    /**
     * Shutter ring state: red while recording, green or amber in Night mode for
     * steady or moving, and the plain ring otherwise. Only restrokes on change.
     */
    private fun updateShutterRing() {
        val color = when {
            engine.recording -> Color.parseColor("#FF3B30")
            (currentMode == Mode.NIGHT || currentMode == Mode.ASTRO) && gyro.isAvailable ->
                if (stillness.isStill()) Color.parseColor("#34C759") else Palette.accent
            else -> Color.argb(150, 255, 255, 255)
        }
        if (color == ringColor) return
        ringColor = color
        shutterRing.setStroke(dp(5), color)
    }

    /** Shows the elapsed time while recording, and clears it once the take stops. */
    private fun updateRecordingStatus() {
        if (engine.recording) {
            val secs = engine.recordingElapsedMs() / 1000
            statusText.text = "REC %02d:%02d".format(secs / 60, secs % 60)
            wasRecording = true
        } else if (wasRecording) {
            wasRecording = false
            statusText.text = ""
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
        statusText.text = "${caps.levelName} · RAW ${if (caps.supportsRaw) "yes" else "no"}"
        updateZoomPill()
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
        lastSavedUri = uri
        refreshThumbnail(uri)
        statusText.text = "$message · DCIM/NijatvaCamera"
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
        } else if (requestCode == REQUEST_AUDIO) {
            val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
            engine.setVideoAudio(granted && settings.recordAudio)
            statusText.text = if (granted) "Audio on. Tap record again" else "Recording without audio"
        }
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private companion object {
        const val REQUEST_CAMERA = 101
        const val REQUEST_AUDIO = 102
        const val PANO_STEP_DEGREES = 8f
        const val PANO_MAX_FRAMES = 24
    }
}
