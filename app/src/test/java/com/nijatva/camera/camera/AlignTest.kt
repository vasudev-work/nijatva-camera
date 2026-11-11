package com.nijatva.camera.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The alignment search has to recover a known shift from a synthetic frame, at
 * the full-resolution accuracy the stacker depends on.
 */
class AlignTest {

    private val width = 320
    private val height = 240

    /** Deterministic hash in [-1, 1] for a block coordinate. */
    private fun hash(x: Int, y: Int, seed: Int): Float {
        var h = x * 374761393 + y * 668265263 + seed * 1442695041
        h = (h xor (h shr 13)) * 1274126177
        h = h xor (h shr 16)
        return ((h and 0xFFFF).toFloat() / 65535f) * 2f - 1f
    }

    /**
     * A non-repeating multi-scale pattern.
     *
     * Pure white noise is useless here: it averages to flat grey at the coarse
     * pyramid levels, which is exactly what those levels look at. A smooth sine
     * grating is useless too, for the opposite reason: it repeats, so a coarse
     * search locks onto the wrong period and reports a shift that is a whole
     * period out. Block sizes that do not divide each other (5, 17, 61) plus a
     * per-pixel texture keep the minimum unique.
     */
    private fun pattern(): ByteArray {
        val out = ByteArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var value = 128f
                value += 34f * hash(x / 61, y / 61, 3)
                value += 40f * hash(x / 17, y / 17, 2)
                value += 46f * hash(x / 5, y / 5, 1)
                value += 8f * hash(x, y, 7)
                out[y * width + x] = value.toInt().coerceIn(0, 255).toByte()
            }
        }
        return out
    }

    /** Moves the frame by (dx, dy), repeating the nearest edge pixel. */
    private fun shift(source: ByteArray, dx: Int, dy: Int): ByteArray {
        val out = ByteArray(source.size)
        for (y in 0 until height) {
            val sourceY = (y - dy).coerceIn(0, height - 1)
            for (x in 0 until width) {
                val sourceX = (x - dx).coerceIn(0, width - 1)
                out[y * width + x] = source[sourceY * width + sourceX]
            }
        }
        return out
    }

    @Test
    fun identicalFramesNeedNoShift() {
        val reference = pattern()
        val shift = Align.estimate(reference, reference, width, height)
        assertEquals(0, shift.dx)
        assertEquals(0, shift.dy)
    }

    @Test
    fun recoversLargeHandheldShift() {
        val reference = pattern()
        val moving = shift(reference, 24, -16)

        val shift = Align.estimate(reference, moving, width, height)

        assertTrue("dx was ${shift.dx}, expected about 24", abs(shift.dx - 24) <= 8)
        assertTrue("dy was ${shift.dy}, expected about -16", abs(shift.dy + 16) <= 8)
    }

    @Test
    fun recoversSmallShift() {
        val reference = pattern()
        val moving = shift(reference, 8, 8)

        val shift = Align.estimate(reference, moving, width, height)

        assertTrue("dx was ${shift.dx}, expected about 8", abs(shift.dx - 8) <= 8)
        assertTrue("dy was ${shift.dy}, expected about 8", abs(shift.dy - 8) <= 8)
    }

    @Test
    fun pyramidIsBuiltForEverySearchLevel() {
        val bigWidth = 640
        val bigHeight = 480
        val luma = ByteArray(bigWidth * bigHeight) { ((it * 37) and 0xFF).toByte() }

        val pyramid = AlignPyramid.of(luma, bigWidth, bigHeight)

        assertEquals(bigWidth, pyramid.width)
        assertEquals(bigHeight, pyramid.height)
        assertEquals(listOf(32, 16, 8), pyramid.scales)
        assertEquals(bigWidth / 8 * (bigHeight / 8), pyramid.level(8).size)
    }

    @Test
    fun aFrameTooSmallForTheCoarseLevelStartsAtTheFinerOne() {
        val smallWidth = 320
        val smallHeight = 240
        val pyramid = AlignPyramid.of(pattern(), smallWidth, smallHeight)

        // 240 / 32 = 7 pixels is too little to search; 1/16 and 1/8 remain.
        assertEquals(listOf(16, 8), pyramid.scales)
    }

    @Test
    fun tinyFramesAreLeftAlone() {
        val small = ByteArray(16 * 16) { 120.toByte() }
        val shift = Align.estimate(small, small, 16, 16)
        assertEquals(0, shift.dx)
        assertEquals(0, shift.dy)
    }
}
