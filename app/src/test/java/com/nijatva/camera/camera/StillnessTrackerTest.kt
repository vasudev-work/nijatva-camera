package com.nijatva.camera.camera

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StillnessTrackerTest {
    @Test fun notStillUntilWindowIsFull() {
        val t = StillnessTracker(windowSize = 5)
        repeat(4) { t.push(0f, 0f, 0f) }
        assertFalse(t.isStill())
    }

    @Test fun steadyPhoneIsStill() {
        val t = StillnessTracker(windowSize = 5)
        repeat(5) { t.push(10f, 2f, -1f) }
        assertTrue(t.isStill())
    }

    @Test fun shakingPhoneIsNotStill() {
        val t = StillnessTracker(windowSize = 5)
        listOf(0f, 3f, -2f, 4f, 1f).forEach { t.push(it, 0f, 0f) }
        assertFalse(t.isStill())
    }

    @Test fun yawWrapAroundIsNotMotion() {
        val t = StillnessTracker(windowSize = 5)
        listOf(179.9f, -179.9f, 179.95f, -179.92f, 179.9f).forEach { t.push(it, 0f, 0f) }
        assertTrue(t.isStill())
    }
}
