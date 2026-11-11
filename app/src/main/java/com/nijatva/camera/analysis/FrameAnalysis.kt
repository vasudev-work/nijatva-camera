package com.nijatva.camera.analysis

import kotlin.math.abs

/**
 * Per-frame measurements for the viewfinder. Pure functions over a packed luma
 * plane, so they can be unit-tested on the JVM.
 */
object FrameAnalysis {

    /** Peaking colour: 80% opaque red. */
    const val PEAK_ARGB: Int = (204 shl 24) or (255 shl 16) or (40 shl 8) or 40

    /** 256-bin brightness histogram. */
    fun histogram(luma: ByteArray, width: Int, height: Int): IntArray {
        val bins = IntArray(256)
        val n = minOf(luma.size, width * height)
        for (i in 0 until n) bins[luma[i].toInt() and 0xFF]++
        return bins
    }

    /**
     * Focus peaking. A pixel is marked when the local gradient, summed over the
     * horizontal and vertical neighbours, is above the threshold. Sharp edges
     * have big gradients; soft or out-of-focus areas do not. Border pixels stay
     * unmarked. Returns ARGB pixels, transparent where nothing is marked.
     */
    fun peakingArgb(luma: ByteArray, width: Int, height: Int, threshold: Int): IntArray {
        val out = IntArray(width * height)
        for (y in 1 until height - 1) {
            val row = y * width
            for (x in 1 until width - 1) {
                val i = row + x
                val gx = abs((luma[i + 1].toInt() and 0xFF) - (luma[i - 1].toInt() and 0xFF))
                val gy = abs((luma[i + width].toInt() and 0xFF) - (luma[i - width].toInt() and 0xFF))
                if (gx + gy > threshold) out[i] = PEAK_ARGB
            }
        }
        return out
    }
}
