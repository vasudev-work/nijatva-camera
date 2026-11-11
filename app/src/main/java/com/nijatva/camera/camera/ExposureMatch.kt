package com.nijatva.camera.camera

/**
 * Mean-matches a frame onto a reference frame before it is fused.
 *
 * Auto-exposure keeps adjusting between the frames of a burst, and even when it
 * does not, the sun moves and a cloud crosses. Fusing frames at different
 * brightness leaves a visible step at a panorama seam and a muddy average in a
 * stack, so each frame is scaled by the ratio of the two means first.
 *
 * The mean is taken over the overlap of the two frames, after the alignment
 * shift is applied, and only over mid-tones: clipped whites and crushed blacks
 * carry no brightness information and would drag the ratio around.
 *
 * This is a single gain for the whole frame, not a per-pixel curve. It fixes a
 * step between frames; it does not fix a bright window that only appears in one
 * of them, which needs tone mapping rather than matching.
 */
object ExposureMatch {

    /** Every fourth pixel is enough for a mean and keeps a 12 MP frame under a few ms. */
    private const val STEP = 4

    /** Mid-tone window: values outside it are clipped and are skipped. */
    private const val LOW = 8
    private const val HIGH = 247

    /** A ratio this far from 1 is not an exposure difference but a bad match. */
    private const val MIN_GAIN = 0.25f
    private const val MAX_GAIN = 4f

    private const val MIN_SAMPLES = 32

    /**
     * @param dx moving frame's horizontal shift in pixels, as returned by [Align]
     * @param dy moving frame's vertical shift in pixels
     * @return multiplier that brings [moving] onto [reference]; 1.0 when there is
     *   not enough overlap to measure one
     */
    fun gain(
        reference: ByteArray,
        moving: ByteArray,
        width: Int,
        height: Int,
        dx: Int = 0,
        dy: Int = 0,
    ): Float {
        if (width <= 0 || height <= 0) return 1f
        if (reference.size < width * height || moving.size < width * height) return 1f

        var referenceSum = 0L
        var referenceCount = 0
        var movingSum = 0L
        var movingCount = 0

        var y = 0
        while (y < height) {
            val movingY = y + dy
            if (movingY in 0 until height) {
                val referenceRow = y * width
                val movingRow = movingY * width
                var x = 0
                while (x < width) {
                    val movingX = x + dx
                    if (movingX in 0 until width) {
                        val referenceValue = reference[referenceRow + x].toInt() and 0xFF
                        if (referenceValue in LOW..HIGH) {
                            referenceSum += referenceValue
                            referenceCount++
                        }
                        val movingValue = moving[movingRow + movingX].toInt() and 0xFF
                        if (movingValue in LOW..HIGH) {
                            movingSum += movingValue
                            movingCount++
                        }
                    }
                    x += STEP
                }
            }
            y += STEP
        }

        if (referenceCount < MIN_SAMPLES || movingCount < MIN_SAMPLES) return 1f

        val referenceMean = referenceSum.toFloat() / referenceCount
        val movingMean = movingSum.toFloat() / movingCount
        if (movingMean < 1f) return 1f

        return (referenceMean / movingMean).coerceIn(MIN_GAIN, MAX_GAIN)
    }
}
