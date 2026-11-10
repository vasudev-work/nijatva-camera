package com.nijatva.camera.camera

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Averages a burst of luma/chroma planes after re-registering them, with ghost
 * rejection on the luma channel.
 *
 * Two passes over the stored frames:
 *
 *  1. Re-register every frame against the first one and take the plain mean.
 *  2. Weight every sample by how far it sits from that mean. A sample close to
 *     the mean is trusted at full weight; one far from it is a moving subject or
 *     a specular glint, and its weight falls away smoothly.
 *
 * A hard reject/keep rule would leave holes where something moved. The soft
 * weight keeps a little of the moving object instead of a black smear, which is
 * less distracting than a hole.
 *
 * Chroma is averaged plainly: colour noise is far less visible than luma noise,
 * and chroma resolution is half in both axes, so the extra bookkeeping buys
 * nothing a viewer would notice.
 */
class Stacker(private val width: Int, private val height: Int) {

    private val chromaWidth = (width + 1) / 2
    private val chromaHeight = (height + 1) / 2

    private val samples = mutableListOf<Sample>()

    private class Sample(val frame: YuvFrame, val dx: Int, val dy: Int)

    val frameCount: Int get() = samples.size

    fun add(frame: YuvFrame) {
        require(frame.width == width && frame.height == height) {
            "Frame size ${frame.width}x${frame.height} does not match stacker $width x $height"
        }
        val shift = if (samples.isEmpty()) {
            Align.Shift(0, 0)
        } else {
            val reference = samples.first().frame
            Align.estimate(reference.luma, frame.luma, width, height)
        }
        samples.add(Sample(frame, shift.dx, shift.dy))
    }

    /**
     * @param gain exposure lift applied before the tone curve, 1.0 = untouched
     * @param gamma tone curve exponent; above 1 lifts the shadows
     * @param saturation 1.0 = untouched
     */
    fun toBitmap(gain: Float = 1.0f, gamma: Float = 1.0f, saturation: Float = 1.0f): Bitmap {
        check(samples.isNotEmpty()) { "No frames were stacked" }

        val pixelCount = width * height
        val mean = FloatArray(pixelCount)
        accumulateLuma(mean, weight = null)

        val inverseCount = 1.0f / samples.size
        for (i in 0 until pixelCount) mean[i] *= inverseCount

        val threshold = ghostThreshold(mean)

        val weightedLuma = FloatArray(pixelCount)
        val weights = FloatArray(pixelCount)
        accumulateLuma(weightedLuma, weight = weights, mean = mean, threshold = threshold)

        val accU = FloatArray(chromaWidth * chromaHeight)
        val accV = FloatArray(chromaWidth * chromaHeight)
        accumulateChroma(accU, accV)

        val pixels = IntArray(pixelCount)
        val lut = buildLut(gain, gamma)

        for (y in 0 until height) {
            val lumaRow = y * width
            val chromaRow = (y / 2) * chromaWidth
            for (x in 0 until width) {
                val i = lumaRow + x
                val w = weights[i]
                val lumaValue = if (w > 0.0001f) weightedLuma[i] / w else mean[i]
                val luma = lut[lumaValue.toInt().coerceIn(0, 255)]

                val ci = chromaRow + (x / 2)
                val u = (accU[ci] * inverseCount) - 128f
                val v = (accV[ci] * inverseCount) - 128f

                var r = luma + 1.402f * v
                var g = luma - 0.344136f * u - 0.714136f * v
                var b = luma + 1.772f * u

                if (saturation != 1.0f) {
                    val grey = 0.299f * r + 0.587f * g + 0.114f * b
                    r = grey + (r - grey) * saturation
                    g = grey + (g - grey) * saturation
                    b = grey + (b - grey) * saturation
                }

                pixels[i] = (0xFF shl 24) or (clamp(r) shl 16) or (clamp(g) shl 8) or clamp(b)
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    /** Plain sum of aligned luma, or a ghost-weighted sum when a mean is supplied. */
    private fun accumulateLuma(
        target: FloatArray,
        weight: FloatArray?,
        mean: FloatArray? = null,
        threshold: Float = 0f,
    ) {
        for (sample in samples) {
            val frame = sample.frame
            for (y in 0 until height) {
                val srcRow = (y + sample.dy).coerceIn(0, height - 1) * width
                val dstRow = y * width
                for (x in 0 until width) {
                    val srcX = (x + sample.dx).coerceIn(0, width - 1)
                    val value = (frame.luma[srcRow + srcX].toInt() and 0xFF).toFloat()
                    val i = dstRow + x

                    if (mean == null || weight == null) {
                        target[i] += value
                    } else {
                        val distance = abs(value - mean[i]) / threshold
                        val w = 1f / (1f + distance * distance)
                        target[i] += value * w
                        weight[i] += w
                    }
                }
            }
        }
    }

    private fun accumulateChroma(accU: FloatArray, accV: FloatArray) {
        for (sample in samples) {
            val frame = sample.frame
            for (y in 0 until chromaHeight) {
                val srcRow = ((y * 2 + sample.dy) / 2).coerceIn(0, chromaHeight - 1) * chromaWidth
                val dstRow = y * chromaWidth
                for (x in 0 until chromaWidth) {
                    val srcX = ((x * 2 + sample.dx) / 2).coerceIn(0, chromaWidth - 1)
                    accU[dstRow + x] += (frame.chromaU[srcRow + srcX].toInt() and 0xFF).toFloat()
                    accV[dstRow + x] += (frame.chromaV[srcRow + srcX].toInt() and 0xFF).toFloat()
                }
            }
        }
    }

    /**
     * Estimates how far a sample must sit from the mean before it counts as
     * motion rather than noise.
     *
     * Measured as the mean absolute deviation on a subsample of pixels: cheap,
     * and it adapts to the actual noise of this capture instead of assuming a
     * fixed number. Floor of 6 code values stops a perfectly clean scene from
     * rejecting everything.
     */
    private fun ghostThreshold(mean: FloatArray): Float {
        var sum = 0f
        var counted = 0
        for (sample in samples) {
            val frame = sample.frame
            var y = 0
            while (y < height) {
                val srcRow = (y + sample.dy).coerceIn(0, height - 1) * width
                var x = 0
                while (x < width) {
                    val srcX = (x + sample.dx).coerceIn(0, width - 1)
                    val value = (frame.luma[srcRow + srcX].toInt() and 0xFF).toFloat()
                    sum += abs(value - mean[y * width + x])
                    counted++
                    x += 7
                }
                y += 7
            }
        }
        val deviation = if (counted == 0) 0f else sum / counted
        return (deviation * 3.5f).coerceAtLeast(6f)
    }

    private fun buildLut(gain: Float, gamma: Float): IntArray {
        val lut = IntArray(256)
        for (i in 0 until 256) {
            val normalised = (i / 255f) * gain
            val clamped = normalised.coerceIn(0f, 1f)
            val curved = if (gamma == 1.0f) clamped else Math.pow(clamped.toDouble(), 1.0 / gamma).toFloat()
            lut[i] = (curved * 255f).toInt().coerceIn(0, 255)
        }
        return lut
    }

    private fun clamp(value: Float): Int {
        val v = value.toInt()
        return if (v < 0) 0 else if (v > 255) 255 else v
    }
}
