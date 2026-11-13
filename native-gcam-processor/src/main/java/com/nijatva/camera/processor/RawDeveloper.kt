package com.nijatva.camera.processor

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import java.io.File
import java.io.FileOutputStream

/**
 * Turns a RAW_SENSOR Image into a viewable Bitmap and JPEG, using the native
 * development stage. Black level, white level and white balance are read from
 * the same capture metadata the DNG uses, so both outputs agree.
 */
class RawDeveloper(private val engine: GcamEngine = GcamEngine()) {

    fun develop(
        image: Image,
        characteristics: CameraCharacteristics,
        captureResult: TotalCaptureResult
    ): Bitmap? {
        require(image.format == ImageFormat.RAW_SENSOR) { "Expected RAW_SENSOR, got ${image.format}" }
        val plane = image.planes[0]
        val pixels = engine.nativeDevelopRaw(
            plane.buffer,
            image.width,
            image.height,
            plane.rowStride,
            blackLevel(characteristics),
            whiteLevel(characteristics),
            whiteBalanceGains(captureResult)
        ) ?: return null
        return Bitmap.createBitmap(pixels, image.width, image.height, Bitmap.Config.ARGB_8888)
    }

    fun saveJpeg(bitmap: Bitmap, file: File, quality: Int = 95) {
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }
    }

    companion object {
        /** Average of the four black-level pattern values, in raw sensor units. */
        fun blackLevel(c: CameraCharacteristics): Float {
            val pattern = c.get(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN) ?: return 0f
            val values = listOf(pattern.getOffsetForIndex(0, 0), pattern.getOffsetForIndex(1, 0),
                pattern.getOffsetForIndex(0, 1), pattern.getOffsetForIndex(1, 1))
            return values.average().toFloat()
        }

        fun whiteLevel(c: CameraCharacteristics): Float =
            (c.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL) ?: 1023).toFloat()

        /**
         * COLOR_CORRECTION_GAINS is {R, Geven, Godd, B} for an RGGB sensor.
         * Returns {r, g, b} with g normalised to 1.0.
         */
        fun whiteBalanceGains(result: TotalCaptureResult): FloatArray {
            val gains = result.get(CaptureResult.COLOR_CORRECTION_GAINS) ?: return floatArrayOf(1f, 1f, 1f)
            // RggbChannelVector exposes greenEven and greenOdd, not an indexed green().
            val g = (gains.greenEven + gains.greenOdd) / 2f
            if (g <= 0f) return floatArrayOf(1f, 1f, 1f)
            return floatArrayOf(gains.red / g, 1f, gains.blue / g)
        }
    }
}
