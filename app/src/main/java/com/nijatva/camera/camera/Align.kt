package com.nijatva.camera.camera

/**
 * Estimates the global translation between two luma frames with a coarse-to-fine
 * pyramid search.
 *
 * Handheld stacking only pays off if the frames are re-registered first: a
 * one-pixel drift at 12 MP smears every edge, which looks worse than a single
 * noisy frame.
 *
 * The search runs three times on progressively finer copies of the frame:
 *
 *  1. At 1/32 of the frame a three-pixel match already covers ±96 pixels of
 *     full-resolution drift, so a big handshake is found instead of clipped.
 *  2. At 1/16 the coarse answer is refined by ±2 pixels.
 *  3. At 1/8 it is refined once more, which is the resolution the caller gets.
 *
 * Each level is a box average rather than a point sample, so fine texture does
 * not alias into a false minimum at the coarse levels. That aliasing is what
 * makes a single-scale SSD search fail on brickwork, foliage and fabric.
 *
 * Build the reference pyramid once with [AlignPyramid.of] and reuse it for every
 * frame in a burst; only the moving frame is downsampled per call.
 *
 * Still not modelled: rotation. A frame tilted about its centre cannot be fixed
 * by translation alone, which is why the stacked output still leans on the
 * tripod gate for long exposures.
 */
object Align {

    data class Shift(val dx: Int, val dy: Int) {
        val magnitude: Int get() = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
    }

    /** Finest level, 1/8 of the frame. The returned shift is in full-resolution pixels. */
    private const val FINEST = 8

    /** Coarsest level, 1/32 of the frame. */
    private const val COARSEST = 32

    /** Refinement radius in pixels at each level below the coarsest. */
    private const val REFINE_RADIUS = 2

    /** Search radius at the coarsest level, in 1/32 pixels. */
    private const val COARSE_RADIUS = 3

    /**
     * Convenience for a single comparison: builds the reference pyramid, then
     * estimates. Prefer the [AlignPyramid] overload for a burst.
     *
     * @param maxShift refinement radius at the finest level, in 1/8 pixels; the
     *   pyramid itself reaches further, up to ±96 full-resolution pixels
     */
    fun estimate(
        reference: ByteArray,
        moving: ByteArray,
        width: Int,
        height: Int,
        maxShift: Int = 12,
    ): Shift = estimate(AlignPyramid.of(reference, width, height), moving, maxShift)

    fun estimate(reference: AlignPyramid, moving: ByteArray, maxShift: Int = 12): Shift {
        val width = reference.width
        val height = reference.height
        val scales = reference.scales
        if (scales.isEmpty()) return Shift(0, 0)

        var scale = scales.first()
        var shift = search(
            reference = reference.level(scale),
            moving = downsample(moving, width, height, scale),
            width = width / scale,
            height = height / scale,
            radius = COARSE_RADIUS,
            centreX = 0,
            centreY = 0,
        )

        for (next in scales.drop(1)) {
            scale = next
            val radius = if (scale == FINEST) minOf(maxShift, REFINE_RADIUS).coerceAtLeast(1) else REFINE_RADIUS
            shift = search(
                reference = reference.level(scale),
                moving = downsample(moving, width, height, scale),
                width = width / scale,
                height = height / scale,
                radius = radius,
                centreX = shift.dx * 2,
                centreY = shift.dy * 2,
            )
        }

        // A shift is only meaningful at the finest level; a frame too small for
        // it is left where it is.
        if (scale != FINEST) return Shift(0, 0)
        return Shift(shift.dx * FINEST, shift.dy * FINEST)
    }

    /** One SSD search over a [radius] box, in the pixels of the level passed in. */
    private fun search(
        reference: ByteArray,
        moving: ByteArray,
        width: Int,
        height: Int,
        radius: Int,
        centreX: Int,
        centreY: Int,
    ): Shift {
        val margin = radius + 2
        if (width <= 2 * margin + 2 || height <= 2 * margin + 2) return Shift(centreX, centreY)
        var bestDx = centreX
        var bestDy = centreY
        var bestScore = Long.MAX_VALUE

        for (dy in centreY - radius..centreY + radius) {
            for (dx in centreX - radius..centreX + radius) {
                var score = 0L
                var counted = 0
                var y = margin
                while (y < height - margin) {
                    val movRow = (y + dy) * width
                    val refRow = y * width
                    if (movRow < 0 || movRow + width > moving.size) {
                        y += 2
                        continue
                    }
                    var x = margin
                    while (x < width - margin) {
                        val mx = x + dx
                        if (mx in 0 until width) {
                            val diff = (reference[refRow + x].toInt() and 0xFF) -
                                (moving[movRow + mx].toInt() and 0xFF)
                            score += (diff * diff).toLong()
                            counted++
                        }
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
        return Shift(bestDx, bestDy)
    }

    /**
     * Box-averaged luma at 1/[scale] of the frame. Averaging rather than point
     * sampling keeps high-frequency texture from aliasing at the coarse levels.
     */
    internal fun downsample(source: ByteArray, width: Int, height: Int, scale: Int): ByteArray {
        val sw = width / scale
        val sh = height / scale
        if (sw <= 0 || sh <= 0) return ByteArray(0)

        val out = ByteArray(sw * sh)
        for (y in 0 until sh) {
            val yStart = y * scale
            val yEnd = minOf(yStart + scale, height)
            val outRow = y * sw
            for (x in 0 until sw) {
                val xStart = x * scale
                val xEnd = minOf(xStart + scale, width)
                var sum = 0
                var count = 0
                var sy = yStart
                while (sy < yEnd) {
                    val row = sy * width
                    var sx = xStart
                    while (sx < xEnd) {
                        sum += source[row + sx].toInt() and 0xFF
                        count++
                        sx++
                    }
                    sy++
                }
                out[outRow + x] = if (count == 0) 0 else (sum / count).toByte()
            }
        }
        return out
    }
}

/**
 * The three downsampled copies of one reference frame, built once per burst.
 */
class AlignPyramid private constructor(
    val width: Int,
    val height: Int,
    private val levels: Map<Int, ByteArray>,
) {

    /** Scales this frame actually has, coarse to fine. */
    val scales: List<Int> get() = levels.keys.sortedDescending()

    fun level(scale: Int): ByteArray = levels.getValue(scale)

    fun has(scale: Int): Boolean = levels.containsKey(scale)

    companion object {
        private val SCALES = intArrayOf(32, 16, 8)

        /** A level needs this many pixels on its short side to carry a search. */
        private const val MIN_SIDE = 12

        fun of(luma: ByteArray, width: Int, height: Int): AlignPyramid {
            val levels = HashMap<Int, ByteArray>(SCALES.size)
            for (scale in SCALES) {
                if (width / scale < MIN_SIDE || height / scale < MIN_SIDE) continue
                levels[scale] = Align.downsample(luma, width, height, scale)
            }
            return AlignPyramid(width, height, levels)
        }
    }
}
