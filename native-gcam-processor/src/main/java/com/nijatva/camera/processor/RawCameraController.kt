package com.nijatva.camera.processor

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import java.io.File

/**
 * Opens a back camera, takes one RAW_SENSOR still and saves it as a .dng file.
 * Needs the CAMERA permission to be granted by the app before [capture] is called.
 */
class RawCameraController(private val context: Context) {

    private val cameraManager =
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var characteristics: CameraCharacteristics? = null

    // The image and its capture result arrive on different callbacks.
    // Whichever comes second triggers the save.
    private var pendingImage: Image? = null
    private var pendingResult: TotalCaptureResult? = null
    private var pendingFile: File? = null
    private var onDone: ((Boolean) -> Unit)? = null

    @SuppressLint("MissingPermission")
    fun capture(outputFile: File, onDone: (Boolean) -> Unit) {
        this.onDone = onDone
        this.pendingFile = outputFile
        startBackground()

        val cameraId = findRawCameraId() ?: run {
            Log.e(TAG, "No back camera with RAW support")
            finish(false)
            return
        }

        val chars = cameraManager.getCameraCharacteristics(cameraId)
        characteristics = chars
        val size = RawCaptureSetup.largestRawSize(chars) ?: run {
            Log.e(TAG, "No RAW_SENSOR output size")
            finish(false)
            return
        }

        val reader = RawCaptureSetup.createRawReader(size, maxImages = 2)
        reader.setOnImageAvailableListener({ r ->
            pendingImage = r.acquireNextImage()
            trySave()
        }, backgroundHandler)
        imageReader = reader

        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) {
                cameraDevice = device
                createSession(device, reader.surface)
            }

            override fun onDisconnected(device: CameraDevice) {
                device.close()
                finish(false)
            }

            override fun onError(device: CameraDevice, error: Int) {
                device.close()
                Log.e(TAG, "Camera error $error")
                finish(false)
            }
        }, backgroundHandler)
    }

    fun close() {
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
        imageReader?.close()
        imageReader = null
        stopBackground()
    }

    private fun createSession(device: CameraDevice, surface: Surface) {
        device.createCaptureSession(
            listOf(surface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    val request = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                        addTarget(surface)
                        // Turn off the ISP's processing so the DNG stays as close to raw as possible.
                        set(CaptureRequest.NOISE_REDUCTION_MODE, CameraMetadata.NOISE_REDUCTION_MODE_OFF)
                        set(CaptureRequest.EDGE_MODE, CameraMetadata.EDGE_MODE_OFF)
                        set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON)
                    }.build()

                    session.capture(request, object : CameraCaptureSession.CaptureCallback() {
                        override fun onCaptureCompleted(
                            s: CameraCaptureSession,
                            req: CaptureRequest,
                            result: TotalCaptureResult
                        ) {
                            pendingResult = result
                            trySave()
                        }
                    }, backgroundHandler)
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(TAG, "Session configuration failed")
                    finish(false)
                }
            },
            backgroundHandler
        )
    }

    private fun trySave() {
        val image = pendingImage ?: return
        val result = pendingResult ?: return
        val chars = characteristics ?: return
        val file = pendingFile ?: return

        var ok = false
        try {
            DngSaver.save(image, chars, result, file)
            ok = true
            Log.i(TAG, "Saved RAW DNG to ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "DNG save failed", e)
        } finally {
            image.close()
            finish(ok)
        }
    }

    private fun finish(success: Boolean) {
        val callback = onDone
        onDone = null
        pendingImage = null
        pendingResult = null
        // Close on the background thread so the camera is released before the callback runs.
        backgroundHandler?.post { close() }
        callback?.invoke(success)
    }

    /** First back-facing camera that supports RAW capture. */
    private fun findRawCameraId(): String? =
        cameraManager.cameraIdList.firstOrNull { id ->
            val chars = cameraManager.getCameraCharacteristics(id)
            chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK &&
                RawCaptureSetup.supportsRaw(chars)
        }

    private fun startBackground() {
        if (backgroundThread != null) return
        backgroundThread = HandlerThread("RawCamera").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    private fun stopBackground() {
        backgroundThread?.quitSafely()
        backgroundThread = null
        backgroundHandler = null
    }

    companion object {
        private const val TAG = "RawCameraController"
    }
}
