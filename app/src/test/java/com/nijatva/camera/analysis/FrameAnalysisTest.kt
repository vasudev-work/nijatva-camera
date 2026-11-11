package com.nijatva.camera.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameAnalysisTest {

    @Test fun histogramCountsEachLumaValue() {
        val luma = byteArrayOf(0, 0, -1, 128.toByte()) // -1 is 255 unsigned
        val bins = FrameAnalysis.histogram(luma, 2, 2)
        assertEquals(2, bins[0])
        assertEquals(1, bins[255])
        assertEquals(1, bins[128])
        assertEquals(4, bins.sum())
    }

    @Test fun flatFrameHasNoPeaking() {
        val w = 6
        val h = 4
        val luma = ByteArray(w * h) { 100 }
        val mask = FrameAnalysis.peakingArgb(luma, w, h, threshold = 40)
        assertTrue(mask.all { it == 0 })
    }

    @Test fun sharpEdgeIsMarkedAndBorderIsNot() {
        val w = 5
        val h = 3
        // Left two columns dark, right three bright: a hard vertical edge.
        val luma = ByteArray(w * h) { i -> if (i % w < 2) 0 else 200.toByte() }
        val mask = FrameAnalysis.peakingArgb(luma, w, h, threshold = 40)
        assertEquals(FrameAnalysis.PEAK_ARGB, mask[1 * w + 2])
        assertEquals(0, mask[0])            // border never marked
        assertFalse(mask[1 * w + 4] == FrameAnalysis.PEAK_ARGB) // flat bright area, no gradient
    }

    @Test fun gentleGradientBelowThresholdIsIgnored() {
        val w = 5
        val h = 3
        val luma = ByteArray(w * h) { i -> (100 + (i % w) * 2).toByte() } // step of 4 per pixel
        val mask = FrameAnalysis.peakingArgb(luma, w, h, threshold = 40)
        assertTrue(mask.all { it == 0 })
    }
}
