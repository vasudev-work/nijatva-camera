package com.nijatva.camera.camera

import kotlin.math.max
import kotlin.math.min

/**
 * Decides whether the phone has been held still long enough for a multi-frame
 * capture. Fed with the angle readings from GyroTracker on a fixed tick.
 *
 * GyroTracker.isSteady() only checks tilt from level. This checks motion over
 * time, which is what ruins a night stack.
 */
class StillnessTracker(
    private val windowSize: Int = 5,            // about 750 ms at a 150 ms tick
    private val maxSwingDegrees: Float = 0.8f,  // total movement allowed per axis
) {
    private val yaw = ArrayDeque<Float>()
    private val pitch = ArrayDeque<Float>()
    private val roll = ArrayDeque<Float>()

    fun push(yawDegrees: Float, pitchDegrees: Float, rollDegrees: Float) {
        add(yaw, yawDegrees)
        add(pitch, pitchDegrees)
        add(roll, rollDegrees)
    }

    fun reset() {
        yaw.clear()
        pitch.clear()
        roll.clear()
    }

    /** True once a full window has been seen and no axis moved more than the limit. */
    fun isStill(): Boolean {
        if (yaw.size < windowSize) return false
        return swing(yaw) <= maxSwingDegrees &&
            swing(pitch) <= maxSwingDegrees &&
            swing(roll) <= maxSwingDegrees
    }

    private fun add(queue: ArrayDeque<Float>, value: Float) {
        queue.addLast(value)
        while (queue.size > windowSize) queue.removeFirst()
    }

    /** Largest movement across the window, measured from the first sample. */
    private fun swing(queue: ArrayDeque<Float>): Float {
        val first = queue.first()
        var low = 0f
        var high = 0f
        for (value in queue) {
            val delta = wrap(value - first)
            low = min(low, delta)
            high = max(high, delta)
        }
        return high - low
    }

    private fun wrap(degrees: Float): Float {
        var d = degrees
        while (d > 180f) d -= 360f
        while (d < -180f) d += 360f
        return d
    }
}
