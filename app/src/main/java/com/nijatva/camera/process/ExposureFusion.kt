package com.nijatva.camera.process

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Exposure fusion in the Mertens sense: blend several differently exposed
 * frames of the same scene into one, weighting each pixel by how much it can be
 * trusted in that frame.
 *
 * This is not HDR+. There is no radiance map, no tone curve fitted to a sensor
 * response, and no motion compensation - it is a weighted average with sensible
 * weights, which is enough to stop skies clipping while keeping a dark
 * foreground readable. It is cheap, runs in a second or two on a mid-range CPU,
 * and behaves predictably, which matters more here than matching Google.
 */
object ExposureFusion {

    private const val WEIGHT_CONTRAST = 1.0f
    private const val WEIGHT_SATURATION = 1.0f
    private const val WEIGHT_EXPOSURE = 1.0f
    private const val EXPOSURE_SIGMA = 0.2f

    fun fuse(frames: List<Bitmap>): Bitmap {
        require(frames.size >= 2) { "Fusion needs at least two frames" }

        val width = frames.minOf { it.width }
        val height = frames.minOf { it.height }
        val scaled = frames.map { if (it.width == width && it.height == height) it else Bitmap.createScaledBitmap(it, width, height, true) }

        val pixelArrays = scaled.map { bmp ->
            IntArray(width * height).also { bmp.getPixels(it, 0, width, 0, 0, width, height) }
        }

        val out = IntArray(width * height)

        for (index in out.indices) {
            var totalWeight = 0f
            var sumR = 0f
            var sumG = 0f
            var sumB = 0f

            for (pixels in pixelArrays) {
                val colour = pixels[index]
                val r = ((colour shr 16) and 0xFF) / 255f
                val g = ((colour shr 8) and 0xFF) / 255f
                val b = (colour and 0xFF) / 255f

                val contrast = laplacian(pixels, index, width, height)
                val saturation = saturation(r, g, b)
                val wellExposed = wellExposedness(r) * wellExposedness(g) * wellExposedness(b)

                val weight = contrast.powSafe(WEIGHT_CONTRAST) *
                    saturation.powSafe(WEIGHT_SATURATION) *
                    wellExposed.powSafe(WEIGHT_EXPOSURE) + 1e-6f

                totalWeight += weight
                sumR += r * weight
                sumG += g * weight
                sumB += b * weight
            }

            val r = (sumR / totalWeight * 255f).toInt().coerceIn(0, 255)
            val g = (sumG / totalWeight * 255f).toInt().coerceIn(0, 255)
            val b = (sumB / totalWeight * 255f).toInt().coerceIn(0, 255)
            out[index] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        return Bitmap.createBitmap(out, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun laplacian(pixels: IntArray, index: Int, width: Int, height: Int): Float {
        val x = index % width
        val y = index / width
        if (x == 0 || y == 0 || x == width - 1 || y == height - 1) return 0f

        val centre = luma(pixels[index])
        val sum = luma(pixels[index - 1]) + luma(pixels[index + 1]) +
            luma(pixels[index - width]) + luma(pixels[index + width])
        return abs(4f * centre - sum)
    }

    private fun luma(colour: Int): Float {
        val r = ((colour shr 16) and 0xFF) / 255f
        val g = ((colour shr 8) and 0xFF) / 255f
        val b = (colour and 0xFF) / 255f
        return 0.299f * r + 0.587f * g + 0.114f * b
    }

    private fun saturation(r: Float, g: Float, b: Float): Float {
        val mean = (r + g + b) / 3f
        return sqrt(((r - mean) * (r - mean) + (g - mean) * (g - mean) + (b - mean) * (b - mean)) / 3f)
    }

    private fun wellExposedness(channel: Float): Float {
        val delta = channel - 0.5f
        return exp(-(delta * delta) / (2f * EXPOSURE_SIGMA * EXPOSURE_SIGMA))
    }

    private fun Float.powSafe(exponent: Float): Float =
        if (this <= 0f) 0f else Math.pow(this.toDouble(), exponent.toDouble()).toFloat()
}
