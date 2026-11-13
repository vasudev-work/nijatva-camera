package com.nijatva.camera.processor

import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.media.ImageReader
import android.util.Size

/**
 * Helpers for picking a RAW-capable configuration and creating the RAW ImageReader.
 * Use these while building the Camera2 session in your app.
 */
object RawCaptureSetup {

    /** True if the camera supports RAW capture (needed before creating a RAW ImageReader). */
    fun supportsRaw(characteristics: CameraCharacteristics): Boolean {
        val caps = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            ?: return false
        return caps.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW)
    }

    /** Largest RAW_SENSOR output size the camera offers. */
    fun largestRawSize(characteristics: CameraCharacteristics): Size? {
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?: return null
        return map.getOutputSizes(ImageFormat.RAW_SENSOR)
            ?.maxByOrNull { it.width * it.height }
    }

    /**
     * Creates the ImageReader for RAW frames. maxImages should be at least the
     * number of frames you plan to hold at once (for the burst later, 8-12).
     */
    fun createRawReader(size: Size, maxImages: Int = 2): ImageReader =
        ImageReader.newInstance(size.width, size.height, ImageFormat.RAW_SENSOR, maxImages)
}
