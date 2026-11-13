package com.nijatva.camera.processor

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.DngCreator
import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import java.io.File
import java.io.FileOutputStream

/**
 * Writes a RAW_SENSOR Image to a .dng file using DngCreator.
 * Must be given the same CaptureResult and CameraCharacteristics the frame was captured with,
 * otherwise the DNG metadata (black level, white balance, color matrices) will be wrong.
 */
object DngSaver {

    fun save(
        image: Image,
        characteristics: CameraCharacteristics,
        captureResult: TotalCaptureResult,
        outputFile: File
    ) {
        require(image.format == android.graphics.ImageFormat.RAW_SENSOR) {
            "Expected RAW_SENSOR image, got format ${image.format}"
        }

        DngCreator(characteristics, captureResult).use { creator ->
            FileOutputStream(outputFile).use { out ->
                creator.writeImage(out, image)
            }
        }
    }
}
