package com.nijatva.camera.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.DngCreator
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.MeteringRectangle
import android.hardware.camera2.params.RggbChannelVector
import android.media.ImageReader
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import com.nijatva.camera.io.ImageSaver
import com.nijatva.camera.process.ExposureFusion
import com.nijatva.camera.process.PanoramaStitcher
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.ln
import kotlin.math.pow

/**
 * Owns the Camera2 session and every capture path in the app.
 *
 * One session per mode, because each mode needs a different set of output
 * surfaces: a JPEG reader for single shots, a YUV reader for the computational
 * modes, a RAW reader for DNG. Rebuilding on mode change is cheap and keeps the
 * per-mode logic readable.
 *
 * All Camera2 work happens on one background handler thread. Image fusion runs
 * on a single-thread executor so a slow night stack cannot stall the preview.
 */
class CameraEngine(
    private val context: Context,
    private val callback: Callback,
) {

    interface Callback {
        fun onReady(caps: DeviceCapabilities)
        fun onFailure(message: String)
        fun onNotice(message: String)
        fun onSaved(uri: Uri, message: String)

        /** Live values read back from the capture result, for the viewfinder readout. */
        fun onMetrics(sensitivity: Int, exposureNs: Long, afState: Int)

        /** Panorama frame count after each accepted frame. */
        fun onPanoramaProgress(frames: Int)
    }

    private class Step(val exposureNs: Long?, val iso: Int?, val skip: Int, val keep: Int)

    private val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val saver = ImageSaver(context)
    private val thread = HandlerThread("nijatva-camera").apply { start() }
    private val handler = Handler(thread.looper)
    private val processor: ExecutorService = Executors.newSingleThreadExecutor()

    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var previewSurface: Surface? = null

    private var jpegReader: ImageReader? = null
    private var yuvReader: ImageReader? = null
    private var rawReader: ImageReader? = null

    private var caps: DeviceCapabilities? = null
    private var cameraId: String = ""

    /** Display rotation in degrees (0, 90, 180, 270), set by the activity. */
    @Volatile private var deviceRotation = 0

    private val VIDEO_FPS = 30
    private var videoRecorder: com.nijatva.camera.video.VideoRecorder? = null
    private var videoFile: java.io.File? = null
    private var recordStartedAt = 0L

    /** True while a take is being encoded. Read by the activity for the REC indicator. */
    @Volatile var recording: Boolean = false
        private set

    var mode: Mode = Mode.PHOTO
        private set

    var aspect: Aspect = Aspect.RATIO_4_3
        private set

    private var settings = CaptureSettings()

    /** Set from the settings sheet: mirror front-camera output, wide-gamut saves. */
    var mirrorFront: Boolean = false
    var wideColour: Boolean = false

    /** When true, RAW mode also writes a JPEG from the same session. */
    var rawPlusJpeg: Boolean = false

    /** 10 for packed RAW10 (half the file), 16 for the unpacked RAW_SENSOR stream. */
    var rawBitDepth: Int = 16

    /** JPEG quality used for merged, stitched and DNG-companion output. */
    var jpegQuality: Int = 97

    @Volatile
    private var busy = false

    private var meteringPoint: android.graphics.PointF? = null

    private var panorama: PanoramaStitcher? = null
    private var panoramaGrab: ((YuvFrame) -> Unit)? = null
    private var panoramaFrames = 0

    private var metricsCounter = 0

    private var plan: List<Step> = emptyList()
    private var planIndex = 0
    private var skipCount = 0
    private var stepCollected = 0
    private val frames = mutableListOf<YuvFrame>()

    val capabilities: DeviceCapabilities? get() = caps
    val isBusy: Boolean get() = busy

    // ---------------------------------------------------------------- lifecycle

    fun start(preferredFacing: Int = CameraCharacteristics.LENS_FACING_BACK) {
        val id = findCameraId(preferredFacing)
        if (id == null) {
            callback.onFailure("No camera found for this facing")
            return
        }
        open(id)
    }

    fun switchCamera() {
        val current = caps?.facing ?: CameraCharacteristics.LENS_FACING_BACK
        val target = if (current == CameraCharacteristics.LENS_FACING_BACK) {
            CameraCharacteristics.LENS_FACING_FRONT
        } else {
            CameraCharacteristics.LENS_FACING_BACK
        }
        val id = findCameraId(target) ?: run {
            callback.onNotice("This device has no other camera")
            return
        }
        open(id)
    }

    fun attachPreview(surface: Surface) {
        previewSurface = surface
        if (device != null) createSession()
    }

    fun detachPreview() {
        previewSurface = null
    }

    fun setMode(newMode: Mode) {
        if (busy) {
            callback.onNotice("Finish the current capture first")
            return
        }
        if (recording) {
            callback.onNotice("Stop recording first")
            return
        }
        if (newMode == mode) return
        mode = newMode
        val required = caps
        if (required != null) {
            if (newMode.isComputational && !required.supportsManualControls) {
                callback.onNotice(
                    "This camera reports ${required.levelName}. Night and HDR need FULL or LEVEL_3, " +
                        "so those modes will not work here."
                )
            }
            if (newMode == Mode.RAW && !required.supportsRawCapture) {
                callback.onNotice("This camera does not expose RAW capture")
            }
        }
        createSession()
    }

    /** Switches between 4:3 and 16:9 capture; rebuilds the session, so it is blocked mid-capture. */
    fun setAspect(newAspect: Aspect) {
        if (busy) {
            callback.onNotice("Finish the current capture first")
            return
        }
        if (newAspect == aspect) return
        aspect = newAspect
        createSession()
        callback.onNotice("${newAspect.label} capture")
    }

    fun updateSettings(transform: (CaptureSettings) -> CaptureSettings) {
        settings = transform(settings)
        if (!busy) restartPreview()
    }

    val currentSettings: CaptureSettings get() = settings

    /**
     * Moves the autofocus and auto-exposure metering window to a point on the
     * preview, given in normalised 0..1 view coordinates.
     */
    fun setMeteringPoint(normalizedX: Float, normalizedY: Float) {
        meteringPoint = android.graphics.PointF(normalizedX.coerceIn(0f, 1f), normalizedY.coerceIn(0f, 1f))
        if (!busy) restartPreview()
    }

    fun close() {
        closeSession()
        closeDevice()
        closeReaders()
        processor.shutdown()
        thread.quitSafely()
    }

    // ---------------------------------------------------------------- panorama

    /**
     * Prepares a panorama session. Frames are grabbed on demand so the caller
     * decides when the phone has turned far enough.
     */
    fun beginPanorama(): Boolean {
        val c = caps ?: return false
        val size = c.yuvSizeWithin(PANORAMA_MAX_PIXELS, aspect.ratio) ?: return false
        val focal = PanoramaStitcher.focalPixels(c.focalLength, c.physicalWidthMm, size.width)
        panorama = PanoramaStitcher(focal)
        panoramaFrames = 0
        callback.onPanoramaProgress(0)
        return true
    }

    /** Grabs the next YUV frame and files it under the given heading. */
    fun capturePanoramaFrame(yawDegrees: Float) {
        val stitcher = panorama ?: return
        panoramaGrab = { frame ->
            stitcher.add(frame, Math.toRadians(yawDegrees.toDouble()).toFloat())
            panoramaFrames++
            callback.onPanoramaProgress(panoramaFrames)
        }
    }

    fun finishPanorama() {
        val stitcher = panorama
        panorama = null
        panoramaGrab = null
        if (stitcher == null || stitcher.count < 2) {
            callback.onNotice("A panorama needs at least two frames")
            return
        }
        busy = true
        callback.onNotice("Stitching ${stitcher.count} frames")
        processor.execute {
            try {
                val bitmap = stitcher.stitch()
                val uri = saver.saveJpeg(bitmap, quality = jpegQuality, label = "PANO", wideGamut = wideColour)
                bitmap.recycle()
                busy = false
                handler.post { callback.onSaved(uri, "Panorama saved from ${stitcher.count} frames") }
            } catch (t: Throwable) {
                Log.e(TAG, "Panorama stitch failed", t)
                busy = false
                handler.post { callback.onFailure("Stitching failed: ${t.message}") }
            }
        }
    }

    fun cancelPanorama() {
        panorama = null
        panoramaGrab = null
        panoramaFrames = 0
        callback.onPanoramaProgress(0)
    }

    // ---------------------------------------------------------------- capture

    fun capture() {
        if (busy) {
            callback.onNotice("Still working on the previous shot")
            return
        }
        when (mode) {
            Mode.PHOTO, Mode.PRO -> captureJpeg()
            Mode.RAW -> captureRaw()
            Mode.NIGHT, Mode.HDR -> startComputational()
            Mode.PANORAMA -> callback.onNotice("Panorama frames are captured automatically as you turn")
            Mode.VIDEO -> if (recording) stopVideo() else startVideo()
        }
    }

    private fun captureJpeg() {
        val s = session
        val reader = jpegReader
        if (s == null || reader == null) {
            callback.onFailure("Capture session is not ready")
            return
        }
        try {
            s.capture(buildRequest(CameraDevice.TEMPLATE_STILL_CAPTURE, reader.surface), null, handler)
        } catch (t: Throwable) {
            callback.onFailure("Capture failed: ${t.message}")
        }
    }

    private fun captureRaw() {
        val s = session
        val reader = rawReader
        if (s == null || reader == null) {
            callback.onFailure("RAW reader is not available")
            return
        }
        val characteristics = manager.getCameraCharacteristics(cameraId)

        try {
            jpegReader?.let { jpeg -> s.capture(buildRequest(CameraDevice.TEMPLATE_STILL_CAPTURE, jpeg.surface), null, handler) }

            s.capture(
                buildRequest(CameraDevice.TEMPLATE_STILL_CAPTURE, reader.surface),
                object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(
                        captureSession: CameraCaptureSession,
                        request: CaptureRequest,
                        result: android.hardware.camera2.TotalCaptureResult,
                    ) {
                        val image = reader.acquireNextImage() ?: return
                        try {
                            val creator = DngCreator(characteristics, result)
                            val file = File(context.cacheDir, "raw_${System.currentTimeMillis()}.dng")
                            FileOutputStream(file).use { creator.writeImage(it, image) }
                            creator.close()
                            val uri = saver.saveDng(file)
                            callback.onSaved(uri, "DNG saved")
                        } catch (t: Throwable) {
                            Log.e(TAG, "RAW save failed", t)
                            callback.onFailure("Could not write the DNG: ${t.message}")
                        } finally {
                            image.close()
                        }
                    }
                },
                handler,
            )
        } catch (t: Throwable) {
            callback.onFailure("RAW capture failed: ${t.message}")
        }
    }

    private fun startComputational() {
        val c = caps ?: return
        if (!c.supportsManualControls) {
            callback.onFailure(
                "Night and HDR need FULL or LEVEL_3 hardware control; this camera reports ${c.levelName}."
            )
            return
        }
        val yuv = yuvReader
        if (yuv == null) {
            callback.onFailure("No YUV stream available for this mode")
            return
        }

        busy = true
        frames.clear()
        planIndex = 0
        skipCount = 0
        stepCollected = 0

        plan = if (mode == Mode.NIGHT) {
            listOf(Step(exposureNs = nightExposureNs(c), iso = nightIso(c), skip = 3, keep = NIGHT_FRAMES))
        } else {
            val base = baseExposureNs(c)
            listOf(
                Step(exposureNs = clampExposure(base * 4, c), iso = null, skip = 2, keep = 1),
                Step(exposureNs = clampExposure(base, c), iso = null, skip = 2, keep = 1),
                Step(exposureNs = clampExposure(base / 4, c), iso = null, skip = 2, keep = 1),
            )
        }
        applyStep()
    }

    private fun applyStep() {
        val s = session ?: return
        val step = plan.getOrNull(planIndex) ?: return
        try {
            val request = buildRequest(CameraDevice.TEMPLATE_PREVIEW, null, step.iso, step.exposureNs)
            s.setRepeatingRequest(request, metricsCallback, handler)
            callback.onNotice(
                if (mode == Mode.NIGHT) "Capturing ${NIGHT_FRAMES} frames, hold still"
                else "Bracketing ${planIndex + 1}/${plan.size}"
            )
        } catch (t: Throwable) {
            busy = false
            callback.onFailure("Could not set the exposure step: ${t.message}")
        }
    }

    private fun onYuvFrame(image: android.media.Image) {
        val step = plan.getOrNull(planIndex)
        if (step == null) {
            image.close()
            return
        }
        if (skipCount < step.skip) {
            skipCount++
            image.close()
            return
        }
        try {
            frames += YuvFrame.from(image)
        } finally {
            image.close()
        }
        stepCollected++

        if (stepCollected >= step.keep) {
            stepCollected = 0
            skipCount = 0
            planIndex++
            if (planIndex >= plan.size) finishComputational() else applyStep()
        }
    }

    private fun finishComputational() {
        val captured = frames.toList()
        frames.clear()
        val capturedMode = mode

        restartPreview()
        callback.onNotice("Merging ${captured.size} frames")

        processor.execute {
            try {
                val raw = if (capturedMode == Mode.NIGHT) stackNight(captured) else fuseHdr(captured)
                // Stacking works on sensor-native frames, so turn the merged result
                // upright here. Its pixels then look right in every gallery app.
                val upright = Orientation.rotate(raw, jpegRotation())
                if (upright !== raw) raw.recycle()
                val uri = saver.saveJpeg(
                    upright,
                    quality = jpegQuality,
                    label = capturedMode.label.uppercase(),
                    mirror = shouldMirror(),
                    wideGamut = wideColour,
                )
                saver.tagMerged(uri)
                upright.recycle()
                busy = false
                handler.post { callback.onSaved(uri, "${capturedMode.label}: ${captured.size} frames merged") }
            } catch (t: Throwable) {
                Log.e(TAG, "Merge failed", t)
                busy = false
                handler.post { callback.onFailure("Merging failed: ${t.message}") }
            }
        }
    }

    private fun stackNight(captured: List<YuvFrame>): android.graphics.Bitmap {
        check(captured.isNotEmpty()) { "No frames captured" }
        val first = captured.first()
        val stacker = Stacker(first.width, first.height)
        captured.forEach { stacker.add(it) }
        return stacker.toBitmap(gain = 1.15f, gamma = 1.55f, saturation = 1.05f)
    }

    private fun fuseHdr(captured: List<YuvFrame>): android.graphics.Bitmap {
        check(captured.size >= 2) { "Need at least two bracketed frames" }
        val bitmaps = captured.map { it.toBitmap() }
        return ExposureFusion.fuse(bitmaps)
    }

    // ---------------------------------------------------------------- session

    @SuppressLint("MissingPermission")
    private fun open(id: String) {
        closeSession()
        closeDevice()
        closeReaders()

        cameraId = id
        caps = DeviceCapabilities.from(id, manager.getCameraCharacteristics(id))
        caps?.let { callback.onReady(it) }

        try {
            manager.openCamera(id, deviceCallback, handler)
        } catch (t: Throwable) {
            callback.onFailure("Could not open camera $id: ${t.message}")
        }
    }

    private val deviceCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            device = camera
            createSession()
        }

        override fun onDisconnected(camera: CameraDevice) {
            camera.close()
            device = null
            callback.onFailure("Camera was disconnected")
        }

        override fun onError(camera: CameraDevice, error: Int) {
            camera.close()
            device = null
            callback.onFailure("Camera error $error")
        }
    }

    private fun createSession() {
        val dev = device ?: return
        val surface = previewSurface ?: return
        val c = caps ?: return

        closeSession()
        closeReaders()

        val outputs = mutableListOf<Surface>()
        outputs += surface

        when (mode) {
            Mode.PHOTO, Mode.PRO -> {
                val size = c.preferredJpegSize(aspect.ratio) ?: return
                jpegReader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 2).apply {
                    setOnImageAvailableListener({ reader -> onJpegAvailable(reader) }, handler)
                }
                jpegReader?.surface?.let { outputs += it }
            }

            Mode.RAW -> {
                val useRaw10 = rawBitDepth == 10 && c.supportsRaw10
                val size = if (useRaw10) {
                    c.raw10Sizes.maxByOrNull { it.width.toLong() * it.height }
                } else {
                    c.preferredRawSize()
                }
                if (size == null || !c.supportsRaw) {
                    callback.onFailure("RAW is not available on this camera")
                    return
                }
                val rawFormat = if (useRaw10) ImageFormat.RAW10 else ImageFormat.RAW_SENSOR
                rawReader = ImageReader.newInstance(size.width, size.height, rawFormat, 2)
                rawReader?.surface?.let { outputs += it }

                if (rawPlusJpeg) {
                    val jpegSize = c.preferredJpegSize(aspect.ratio) ?: size
                    jpegReader = ImageReader.newInstance(jpegSize.width, jpegSize.height, ImageFormat.JPEG, 2).apply {
                        setOnImageAvailableListener({ reader -> onJpegAvailable(reader) }, handler)
                    }
                    jpegReader?.surface?.let { outputs += it }
                }
            }

            Mode.VIDEO -> {
                val size = videoSize()
                val file = java.io.File(context.cacheDir, "nijatva_${System.currentTimeMillis()}.mp4")
                videoFile = file
                try {
                    val recorder = com.nijatva.camera.video.VideoRecorder(
                        context, file, size, VIDEO_FPS, jpegRotation(),
                    )
                    videoRecorder = recorder
                    outputs += recorder.surface
                } catch (t: Throwable) {
                    callback.onFailure("Video could not be prepared: ${t.message}")
                    return
                }
            }

            Mode.NIGHT, Mode.HDR, Mode.PANORAMA -> {
                val limit = when (mode) {
                    Mode.NIGHT -> NIGHT_MAX_PIXELS
                    Mode.HDR -> HDR_MAX_PIXELS
                    else -> PANORAMA_MAX_PIXELS
                }
                val size = c.yuvSizeWithin(limit, aspect.ratio)
                if (size == null) {
                    callback.onFailure("This camera exposes no YUV output, so fusion is not possible")
                    return
                }
                val maxImages = if (mode == Mode.NIGHT) NIGHT_FRAMES + 4 else 6
                yuvReader = ImageReader.newInstance(size.width, size.height, ImageFormat.YUV_420_888, maxImages).apply {
                    setOnImageAvailableListener({ reader -> onYuvAvailable(reader) }, handler)
                }
                yuvReader?.surface?.let { outputs += it }
            }
        }

        try {
            dev.createCaptureSession(outputs, sessionCallback, handler)
        } catch (t: Throwable) {
            callback.onFailure("Could not create the capture session: ${t.message}")
        }
    }

    private val sessionCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(configured: CameraCaptureSession) {
            session = configured
            restartPreview()
        }

        override fun onConfigureFailed(failed: CameraCaptureSession) {
            callback.onFailure("The camera refused this surface combination for ${mode.label}")
        }
    }

    private val metricsCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(
            captureSession: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult,
        ) {
            metricsCounter++
            if (metricsCounter % 4 != 0) return
            val sensitivity = result.get(CaptureResult.SENSOR_SENSITIVITY) ?: return
            val exposure = result.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: 0L
            val afState = result.get(CaptureResult.CONTROL_AF_STATE) ?: 0
            callback.onMetrics(sensitivity, exposure, afState)
        }
    }

    /**
     * Starts a take. The recorder was prepared when the session was built, so this
     * only has to turn it on and add its surface to the repeating request.
     */
    fun startVideo(): Boolean {
        if (recording) return true
        val rec = videoRecorder
        if (rec == null) {
            callback.onNotice("Video is still getting ready")
            return false
        }
        return try {
            rec.start()
            recordStartedAt = System.currentTimeMillis()
            recording = true
            restartPreview()
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Recorder start failed", t)
            callback.onFailure("Could not start recording: ${t.message}")
            false
        }
    }

    /**
     * Ends the take. The session is closed first so nothing feeds the recorder while
     * it is finalised. The file is then moved into the gallery and a fresh session is
     * built for the next take.
     */
    fun stopVideo() {
        if (!recording) return
        recording = false
        val rec = videoRecorder
        videoRecorder = null
        val file = videoFile
        videoFile = null

        handler.post {
            closeSession()
            processor.execute {
                var ok = true
                try {
                    rec?.stop()
                } catch (t: Throwable) {
                    Log.e(TAG, "Recorder stop failed", t)
                    ok = false
                }
                try {
                    rec?.release()
                } catch (_: Throwable) {
                    // Already released; nothing to do.
                }

                if (ok && file != null && file.length() > 0) {
                    try {
                        val uri = saver.saveVideo(file)
                        handler.post { callback.onSaved(uri, "Video saved") }
                    } catch (t: Throwable) {
                        handler.post { callback.onFailure("Could not save the video: ${t.message}") }
                    }
                } else {
                    file?.delete()
                    handler.post { callback.onFailure("Recording was too short to save") }
                }

                handler.post { createSession() }
            }
        }
    }

    fun recordingElapsedMs(): Long =
        if (recording) System.currentTimeMillis() - recordStartedAt else 0L

    /**
     * Largest 16:9 size the MediaRecorder can encode, capped at 1080p. Falls back to
     * 720p when the camera reports nothing usable.
     */
    private fun videoSize(): android.util.Size {
        val map = manager.getCameraCharacteristics(cameraId)
            .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val all = map?.getOutputSizes(android.media.MediaRecorder::class.java)?.toList().orEmpty()
        val within = all.filter { it.width <= 1920 && it.height <= 1080 }
        val sixteenNine = within.filter { kotlin.math.abs(it.width * 9.0 / it.height - 16.0) < 0.05 }
        return (sixteenNine.ifEmpty { within })
            .maxByOrNull { it.width.toLong() * it.height }
            ?: android.util.Size(1280, 720)
    }

    private fun restartPreview() {
        val s = session ?: return
        try {
            s.setRepeatingRequest(
                buildRequest(CameraDevice.TEMPLATE_PREVIEW, null),
                metricsCallback,
                handler,
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Preview restart failed", t)
        }
    }

    fun setDeviceRotation(degrees: Int) {
        deviceRotation = degrees
    }

    /** How far the JPEG must be turned so it is upright on this screen. */
    private fun jpegRotation(): Int {
        val ch = manager.getCameraCharacteristics(cameraId)
        val sensor = ch.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        val front = ch.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
        return Orientation.jpegRotation(sensor, deviceRotation, front)
    }

    private fun buildRequest(
        template: Int,
        target: Surface?,
        isoOverride: Int? = null,
        exposureOverride: Long? = null,
    ): CaptureRequest {
        val dev = device ?: error("Camera is not open")
        val builder = dev.createCaptureRequest(template)
        builder.set(CaptureRequest.JPEG_ORIENTATION, jpegRotation())

        previewSurface?.let { builder.addTarget(it) }
        if (recording) videoRecorder?.surface?.let { builder.addTarget(it) }
        target?.let { builder.addTarget(it) }
        if (template != CameraDevice.TEMPLATE_STILL_CAPTURE) {
            yuvReader?.surface?.let { builder.addTarget(it) }
        }

        caps?.let { applyZoom(builder, it) }

        meteringPoint?.let { point ->
            val c = caps
            if (c != null) {
                val active = c.activeArray
                val halfWidth = (active.width() * 0.08f).toInt().coerceAtLeast(1)
                val halfHeight = (active.height() * 0.08f).toInt().coerceAtLeast(1)
                val cx = active.left + (active.width() * point.x).toInt()
                val cy = active.top + (active.height() * point.y).toInt()
                val region = MeteringRectangle(
                    Rect(cx - halfWidth, cy - halfHeight, cx + halfWidth, cy + halfHeight),
                    MeteringRectangle.METERING_WEIGHT_MAX,
                )
                builder.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(region))
                if (c.supportsAutoFocus && settings.focusDiopters == null) {
                    builder.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(region))
                    builder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_AUTO)
                }
            }
        }

        if (settings.torchOn) {
            builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH)
        } else if (settings.flashAlways) {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON_ALWAYS_FLASH)
        } else if (settings.autoFlash) {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH)
        }

        val iso = isoOverride ?: settings.iso
        val exposure = exposureOverride ?: settings.exposureNs
        if (iso != null || exposure != null) {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
            iso?.let { builder.set(CaptureRequest.SENSOR_SENSITIVITY, it) }
            exposure?.let { builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, it) }
        }

        settings.evSteps?.let { steps ->
            if (steps != 0) builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, steps)
        }

        settings.wbKelvin?.let { kelvin -> applyWhiteBalance(builder, kelvin) }

        settings.oisOn?.let { enabled ->
            builder.set(
                CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                if (enabled) CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON
                else CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_OFF,
            )
        }

        settings.focusDiopters?.let { diopters ->
            builder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
            builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, diopters)
        }

        return builder.build()
    }

    /**
     * Manual white balance as an RGB gain pair, derived from the requested colour
     * temperature with a blackbody approximation.
     *
     * This is not colour-accurate: the gains are applied to sensor data before the
     * device's own colour matrix, so without a per-device calibration the result
     * is a deliberate warm/cool shift rather than a true neutral. It moves the
     * image the way the slider promises, which is what the control is for.
     */
    private fun applyWhiteBalance(builder: CaptureRequest.Builder, kelvin: Int) {
        val temperature = kelvin.coerceIn(2000, 9000) / 100f
        val red: Float
        val green: Float
        val blue: Float

        if (temperature <= 66f) {
            red = 255f
            green = (99.4708025861 * ln(temperature.toDouble()) - 161.1195681661).toFloat().coerceIn(0f, 255f)
            blue = if (temperature <= 19f) 0f else
                (138.5177312231 * ln((temperature - 10f).toDouble()) - 305.0447927307).toFloat().coerceIn(0f, 255f)
        } else {
            red = (329.698727446 * (temperature - 60f).toDouble().pow(-0.1332047592)).toFloat().coerceIn(0f, 255f)
            green = (288.1221695283 * (temperature - 60f).toDouble().pow(-0.0755148492)).toFloat().coerceIn(0f, 255f)
            blue = 255f
        }

        val safeRed = red.coerceAtLeast(1f)
        val safeBlue = blue.coerceAtLeast(1f)
        val redGain = (green / safeRed).coerceIn(0.2f, 5f)
        val blueGain = (green / safeBlue).coerceIn(0.2f, 5f)

        builder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_OFF)
        builder.set(CaptureRequest.COLOR_CORRECTION_MODE, CameraMetadata.COLOR_CORRECTION_MODE_FAST)
        builder.set(
            CaptureRequest.COLOR_CORRECTION_GAINS,
            RggbChannelVector(redGain, 1f, 1f, blueGain),
        )
    }

    private fun applyZoom(builder: CaptureRequest.Builder, c: DeviceCapabilities) {
        val zoom = settings.zoom.coerceIn(1f, 8f)
        if (zoom <= 1.01f) return
        val active = c.activeArray
        val width = (active.width() / zoom).toInt()
        val height = (active.height() / zoom).toInt()
        val cx = active.centerX()
        val cy = active.centerY()
        val crop = Rect(
            (cx - width / 2).coerceAtLeast(active.left),
            (cy - height / 2).coerceAtLeast(active.top),
            (cx + width / 2).coerceAtMost(active.right),
            (cy + height / 2).coerceAtMost(active.bottom),
        )
        builder.set(CaptureRequest.SCALER_CROP_REGION, crop)
    }

    // ---------------------------------------------------------------- readers

    private fun onJpegAvailable(reader: ImageReader) {
        val image = reader.acquireNextImage() ?: return
        try {
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            val uri = saver.saveJpegBytes(bytes, label = if (mode == Mode.PRO) "PRO" else "IMG")
            callback.onSaved(uri, "Photo saved")
        } catch (t: Throwable) {
            Log.e(TAG, "JPEG save failed", t)
            callback.onFailure("Could not save the photo: ${t.message}")
        } finally {
            image.close()
        }
    }

    private fun onYuvAvailable(reader: ImageReader) {
        val image = reader.acquireNextImage() ?: return

        val grab = panoramaGrab
        if (grab != null) {
            panoramaGrab = null
            try {
                grab(YuvFrame.from(image))
            } catch (t: Throwable) {
                Log.e(TAG, "Panorama frame failed", t)
            } finally {
                image.close()
            }
            return
        }

        try {
            onYuvFrame(image)
        } catch (t: Throwable) {
            Log.e(TAG, "YUV frame failed", t)
            image.close()
        }
    }

    private fun closeSession() {
        try {
            session?.close()
        } catch (_: Throwable) {
        }
        session = null
    }

    private fun closeDevice() {
        try {
            device?.close()
        } catch (_: Throwable) {
        }
        device = null
    }

    private fun closeReaders() {
        videoRecorder?.release()
        videoRecorder = null
        jpegReader?.close()
        yuvReader?.close()
        rawReader?.close()
        jpegReader = null
        yuvReader = null
        rawReader = null
    }

    // ---------------------------------------------------------------- helpers

    private fun shouldMirror(): Boolean =
        mirrorFront && caps?.facing == CameraCharacteristics.LENS_FACING_FRONT

    private fun findCameraId(facing: Int): String? {
        val ids = manager.cameraIdList
        val fallback = ids.firstOrNull()
        for (id in ids) {
            val characteristics = manager.getCameraCharacteristics(id)
            if (characteristics.get(CameraCharacteristics.LENS_FACING) == facing) return id
        }
        return fallback
    }

    private fun nightExposureNs(c: DeviceCapabilities): Long {
        val max = c.exposureRange?.upper ?: 33_000_000L
        return minOf(max / 8, NIGHT_MAX_EXPOSURE_NS).coerceAtLeast(4_000_000L)
    }

    private fun nightIso(c: DeviceCapabilities): Int {
        val max = c.isoRange?.upper ?: 1600
        return minOf(max, NIGHT_ISO).coerceAtLeast(100)
    }

    private fun baseExposureNs(c: DeviceCapabilities): Long =
        minOf(c.exposureRange?.upper ?: 8_000_000L, 8_000_000L).coerceAtLeast(1_000_000L)

    private fun clampExposure(value: Long, c: DeviceCapabilities): Long {
        val range = c.exposureRange ?: return value.coerceAtLeast(1_000_000L)
        return value.coerceIn(range.lower, range.upper)
    }

    private companion object {
        const val TAG = "CameraEngine"
        const val NIGHT_FRAMES = 8
        // Tuned for an 8 MP-class YUV stream: 8 frames at 2560x1920 peak around
        // 110 MB with the accumulators, which fits a 4 GB phone's heap. Going to
        // 3264x2448 doubles that and starts throwing OutOfMemoryError mid-stack.
        const val NIGHT_MAX_PIXELS = 5_000_000L
        const val HDR_MAX_PIXELS = 5_000_000L
        const val NIGHT_MAX_EXPOSURE_NS = 40_000_000L
        const val NIGHT_ISO = 3200
        const val PANORAMA_MAX_PIXELS = 4_000_000L
    }
}
