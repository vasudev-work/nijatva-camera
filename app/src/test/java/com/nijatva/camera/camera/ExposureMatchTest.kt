package com.nijatva.camera.camera

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The exposure match has to recover a known brightness ratio, ignore clipped
 * pixels, and stay at 1.0 when it cannot measure anything.
 */
class ExposureMatchTest {

    private val width = 64
    private val height = 48

    private fun frame(value: Int): ByteArray = ByteArray(width * height) { value.toByte() }

    @Test
    fun identicalFramesNeedNoGain() {
        val reference = frame(90)
        val gain = ExposureMatch.gain(reference, reference, width, height)
        assertEquals(1.0f, gain, 0.001f)
    }

    @Test
    fun recoversADarkerFrame() {
        val reference = frame(100)
        val moving = frame(50)

        val gain = ExposureMatch.gain(reference, moving, width, height)

        assertEquals(2.0f, gain, 0.05f)
    }

    @Test
    fun recoversABrighterFrame() {
        val reference = frame(60)
        val moving = frame(120)

        val gain = ExposureMatch.gain(reference, moving, width, height)

        assertEquals(0.5f, gain, 0.02f)
    }

    @Test
    fun clippedPixelsAreIgnored() {
        // Mostly mid-tone at 80, with a quarter of the frame blown out.
        val reference = ByteArray(width * height) { i ->
            // Every third column is blown out; the sampler strides by four, so the
            // clipped stripes cannot line up with the samples it keeps.
            if ((i % width) % 3 == 0) 255.toByte() else 80.toByte()
        }
        val moving = frame(40)

        val gain = ExposureMatch.gain(reference, moving, width, height)

        assertEquals(2.0f, gain, 0.05f)
    }

    @Test
    fun noOverlapFallsBackToUnity() {
        val reference = frame(90)
        val moving = frame(45)

        // A shift larger than the frame leaves nothing to compare.
        val gain = ExposureMatch.gain(reference, moving, width, height, dx = width * 2, dy = 0)

        assertEquals(1.0f, gain, 0.001f)
    }

    @Test
    fun aFlatFrameIsNotRescaled() {
        val gain = ExposureMatch.gain(frame(0), frame(0), width, height)
        assertEquals(1.0f, gain, 0.001f)
    }
}
