package com.nijatva.camera.camera

/**
 * Estimates the global translation between two luma frames.
 *
 * Handheld stacking only pays off if the frames are re-registered first: a
 * one-pixel drift at 12 MP smears every edge, which looks worse than a single
 * noisy frame. This is a plain downsampled SSD search, not optical flow. It
 * handles the handshake drift that matters and nothing more, which keeps it
 * fast enough to run between captures.
 */
object Align {
    private const val DOWNSCALE = 8

    data class Shift(val dx: Int, val dy: Int)

    fun estimate(reference: ByteArray, moving: ByteArray, width: Int, height: Int, maxShift: Int = 12): Shift {
        val sw = width / DOWNSCALE
        val sh = height / DOWNSCALE
        if (sw < maxShift * 2 || sh < maxShift * 2) return Shift(0, 0)

        val refSmall = downsample(reference, width, height, sw, sh)
        val movSmall = downsample(moving, width, height, sw, sh)

        var bestDx = 0
        var bestDy = 0
        var bestScore = Long.MAX_VALUE

        for (dy in -maxShift until maxShift) {
            for (dx in -maxShift until maxShift) {
                var score = 0L
                var counted = 0
                var y = maxShift
                while (y < sh - maxShift) {
                    val movRow = (y + dy) * sw
                    val refRow = y * sw
                    var x = maxShift
                    while (x < sw - maxShift) {
                        val diff = (refSmall[refRow + x].toInt() and 0xFF) - (movSmall[movRow + x + dx].toInt() and 0xFF)
                        score += (diff * diff).toLong()
                        counted++
                        x += 2
                    }
                    y += 2
                }
                if (counted == 0) continue
                val normalised = score / counted
                if (normalised < bestScore) {
                    bestScore = normalised
                    bestDx = dx
                    bestDy = dy
                }
            }
        }
        return Shift(bestDx * DOWNSCALE, bestDy * DOWNSCALE)
    }

    private fun downsample(source: ByteArray, width: Int, height: Int, sw: Int, sh: Int): ByteArray {
        val out = ByteArray(sw * sh)
        for (y in 0 until sh) {
            val srcRow = (y * DOWNSCALE) * width
            val outRow = y * sw
            for (x in 0 until sw) {
                out[outRow + x] = source[srcRow + x * DOWNSCALE]
            }
        }
        return out
    }
}
