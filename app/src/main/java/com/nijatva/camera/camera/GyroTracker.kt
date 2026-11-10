package com.nijatva.camera.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Yaw, pitch and roll from the rotation vector sensor.
 *
 * The panorama mode needs to know how far the phone has turned since the session
 * started. Frame-to-frame image alignment alone drifts over a long sweep; the
 * gyroscope does not. The two together are how every panorama implementation
 * works, including the one inside the stock camera.
 */
class GyroTracker(context: Context) : SensorEventListener {

    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val rotation = FloatArray(9)
    private val orientation = FloatArray(3)

    @Volatile
    var yawDegrees: Float = 0f
        private set

    @Volatile
    var pitchDegrees: Float = 0f
        private set

    @Volatile
    var rollDegrees: Float = 0f
        private set

    @Volatile
    var isAvailable: Boolean = false
        private set

    fun start() {
        val s = sensor
        if (s == null) {
            isAvailable = false
            return
        }
        isAvailable = true
        manager.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        manager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rotation, event.values)
        SensorManager.getOrientation(rotation, orientation)
        yawDegrees = Math.toDegrees(orientation[0].toDouble()).toFloat()
        pitchDegrees = Math.toDegrees(orientation[1].toDouble()).toFloat()
        rollDegrees = Math.toDegrees(orientation[2].toDouble()).toFloat()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    /** Smallest signed difference between two headings, in degrees. */
    fun deltaDegrees(from: Float, to: Float): Float {
        var delta = to - from
        while (delta > 180f) delta -= 360f
        while (delta < -180f) delta += 360f
        return delta
    }

    /** Rough "is the phone moving" test from pitch and roll jitter. */
    fun isSteady(thresholdDegrees: Float = 1.6f): Boolean =
        sqrt(pitchDegrees * pitchDegrees + rollDegrees * rollDegrees) < thresholdDegrees
}
