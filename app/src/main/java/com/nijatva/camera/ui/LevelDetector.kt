package com.nijatva.camera.ui

import kotlin.math.abs

/**
 * Decides whether the phone counts as level, with hysteresis.
 *
 * A single threshold flickers: at exactly half a degree the gyro noise alone
 * flips the state several times a second, which would turn the level line and the
 * haptic tick on and off while the user holds still. Entering at half a degree and
 * leaving only past one and a half gives a stable answer.
 */
object LevelDetector {

    /** Within this many degrees of level counts as straight. */
    const val ENTER_DEGREES = 0.5f

    /** The state only drops once the phone is this far off, so noise cannot flicker it. */
    const val EXIT_DEGREES = 1.5f

    fun isLevel(rollDegrees: Float, wasLevel: Boolean): Boolean {
        val limit = if (wasLevel) EXIT_DEGREES else ENTER_DEGREES
        return abs(rollDegrees) <= limit
    }
}
