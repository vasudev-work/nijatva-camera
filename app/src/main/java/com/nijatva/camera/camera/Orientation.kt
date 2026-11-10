package com.nijatva.camera.camera

import android.graphics.Bitmap
import android.graphics.Matrix

/** Turns sensor-native pixels into upright ones. */
object Orientation {

    /**
     * Camera2's JPEG orientation rule. The back sensor is rotated against the
     * device, the front sensor with it. deviceDegrees is the display rotation
     * (0, 90, 180 or 270).
     */
    fun jpegRotation(sensorOrientation: Int, deviceDegrees: Int, frontFacing: Boolean): Int {
        val device = ((deviceDegrees % 360) + 360) % 360
        val raw = if (frontFacing) sensorOrientation + device else sensorOrientation - device
        return ((raw % 360) + 360) % 360
    }

    /** Returns the same bitmap when no rotation is needed, otherwise a new one. */
    fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
