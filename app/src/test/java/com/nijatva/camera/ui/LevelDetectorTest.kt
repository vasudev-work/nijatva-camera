package com.nijatva.camera.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The level rule: half a degree to become level, one and a half to stop being
 * level, so gyro noise cannot flicker the line and the haptic tick.
 */
class LevelDetectorTest {

    @Test
    fun straightAheadIsLevel() {
        assertTrue(LevelDetector.isLevel(0f, wasLevel = false))
        assertTrue(LevelDetector.isLevel(0.3f, wasLevel = false))
        assertTrue(LevelDetector.isLevel(-0.3f, wasLevel = false))
    }

    @Test
    fun justPastTheEnterThresholdIsNotLevel() {
        assertFalse(LevelDetector.isLevel(0.6f, wasLevel = false))
        assertFalse(LevelDetector.isLevel(-0.6f, wasLevel = false))
    }

    @Test
    fun levelHoldsThroughSmallNoise() {
        // Already level: a wobble between half and one and a half degrees must not
        // drop the state, which is what stops the tick repeating.
        assertTrue(LevelDetector.isLevel(0.8f, wasLevel = true))
        assertTrue(LevelDetector.isLevel(1.4f, wasLevel = true))
        assertTrue(LevelDetector.isLevel(-1.4f, wasLevel = true))
    }

    @Test
    fun levelDropsPastTheExitThreshold() {
        assertFalse(LevelDetector.isLevel(1.6f, wasLevel = true))
        assertFalse(LevelDetector.isLevel(-2f, wasLevel = true))
    }

    @Test
    fun tiltedStaysTiltedUntilWellInside() {
        assertFalse(LevelDetector.isLevel(1.0f, wasLevel = false))
        assertTrue(LevelDetector.isLevel(0.4f, wasLevel = false))
    }

    @Test
    fun hysteresisBandIsWiderThanTheEnterBand() {
        assertTrue(LevelDetector.EXIT_DEGREES > LevelDetector.ENTER_DEGREES)
    }
}
