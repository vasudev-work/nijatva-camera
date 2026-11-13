package com.nijatva.camera.processor

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import java.io.File

/**
 * Captures a burst of underexposed RAW frames and saves each one as a .dng.
 *
 * Exposure is fixed (AE off) so every frame has the same brightness, which the
 * later merge step relies on. Underexposure keeps bright highlights from clipping.
 *
 * Image and capture result arrive on separate callbacks, so they are matched by
 * timestamp (Image.timestamp == CaptureResult.SENSOR_TIMESTAMP).
 */
class RawBurstController(private val context: Context) {

    data class Settings(
        val frameCount: Int = 8,
        val exposureNs: Long = 1_000_000_000L / 60, // 1/60 s; lower than a normal shot = underexposed
        val iso: Int = 100
    )

    private val cameraManager =
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var chars: CameraCharacteristics? = null

    // Matched by sensor timestamp, guarded by the background thread.
    private val images = HashMap<Long, Image>()
    private val results = HashMap<Long, TotalCaptureResult>()
    private var outDir: File? = null
    private var expected = 0
    private var saved = 0
    private var onDone: ((Int) -> Unit)? = null

    @SuppressLint("MissingPermission")
    fun capture(outputDir: File, settings: Settings = Settings(), onDone: (savedCount: Int) -> Unit) {
        this.onDone = onDone
        this.outDir = outputDir.apply { mkdirs() }
        this.expected = settings.frameCount
        startThread()

        val id = findRawCameraId() ?: return finish()
        val c = cameraManager.getCameraCharacteristics(id)
        chars = c
        val size = RawCaptureSetup.largestRawSize(c) ?: return finish()

        // Enough reader slots for the whole burst, plus a little headroom.
        // Each RAW frame is roughly 2 bytes per pixel, so 12MP is about 24MB per frame.
        val r = RawCaptureSetup.createRawReader(size, maxImages = settings.frameCount + 2)
        r.setOnImageAvailableListener({ rd ->
            val img = rd.acquireNextImage()
            synchronized(images) { images[img.timestamp] = img }
            tryPair()
        }, handler)
        reader = r

        cameraManager.openCamera(id, object : CameraDevice.StateCallback() {
            override fun onOpened(cam: CameraDevice) {
                device = cam
                createSession(cam, r, settings)
            }
            override fun onDisconnected(cam: CameraDevice) { cam.close(); finish() }
            override fun onError(cam: CameraDevice, error: Int) {
                Log.e(TAG, "Camera error $error"); cam.close(); finish()
            }
        }, handler)
    }

    private fun createSession(cam: CameraDevice, r: ImageReader, s: Settings) {
        cam.createCaptureSession(listOf(r.surface), object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(sess: CameraCaptureSession) {
                session = sess
                val requests = (0 until s.frameCount).map {
                    cam.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                        addTarget(r.surface)
                        set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
                        set(CaptureRequest.SENSOR_EXPOSURE_TIME, s.exposureNs)
                        set(CaptureRequest.SENSOR_SENSITIVITY, s.iso)
                        set(CaptureRequest.NOISE_REDUCTION_MODE, CameraMetadata.NOISE_REDUCTION_MODE_OFF)
                        set(CaptureRequest.EDGE_MODE, CameraMetadata.EDGE_MODE_OFF)
                    }.build()
                }
                sess.captureBurst(requests, object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(
                        s: CameraCaptureSession, req: CaptureRequest, result: TotalCaptureResult
                    ) {
                        val ts = result.get(CaptureResult.SENSOR_TIMESTAMP) ?: return
                        synchronized(images) { results[ts] = result }
                        tryPair()
                    }
                }, handler)
            }
            override fun onConfigureFailed(sess: CameraCaptureSession) {
                Log.e(TAG, "Session configuration failed"); finish()
            }
        }, handler)
    }

    /** Saves every image that has a matching result, then closes when the burst is done. */
    private fun tryPair() {
        val c = chars ?: return
        val dir = outDir ?: return
        val ready = synchronized(images) {
            val keys = images.keys.filter { results.containsKey(it) }
            keys.map { k -> Triple(k, images.remove(k)!!, results.remove(k)!!) }
        }
        for ((ts, img, res) in ready) {
            try {
                val file = File(dir, "frame_${String.format("%02d", saved)}_$ts.dng")
                DngSaver.save(img, c, res, file)
                saved++
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save frame at $ts", e)
            } finally {
                img.close()
            }
        }
        if (saved >= expected) finish()
    }

    private fun finish() {
        val cb = onDone
        onDone = null
        handler?.post {
            session?.close(); session = null
            device?.close(); device = null
            reader?.close(); reader = null
            synchronized(images) { images.values.forEach { it.close() }; images.clear() }
            synchronized(images) { results.clear() }
            stopThread()
            cb?.invoke(saved)
        } ?: cb?.invoke(saved)
    }

    private fun findRawCameraId(): String? =
        cameraManager.cameraIdList.firstOrNull { id ->
            val ch = cameraManager.getCameraCharacteristics(id)
            ch.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK &&
                RawCaptureSetup.supportsRaw(ch)
        }

    private fun startThread() {
        if (thread != null) return
        thread = HandlerThread("RawBurst").also { it.start() }
        handler = Handler(thread!!.looper)
    }

    private fun stopThread() {
        thread?.quitSafely()
        thread = null
        handler = null
    }

    companion object {
        private const val TAG = "RawBurstController"
    }
}
