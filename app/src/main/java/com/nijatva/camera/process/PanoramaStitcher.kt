package com.nijatva.camera.process

import android.graphics.Bitmap
import com.nijatva.camera.camera.YuvFrame
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Horizontal panorama stitching.
 *
 * Each frame is placed by the yaw angle the gyroscope reported when it was taken,
 * converted to pixels through the focal length in pixels. Cylindrical placement
 * (x = f * yaw) keeps straight lines straight across the seam, which a rectilinear
 * placement does not once the sweep passes about 40 degrees.
 *
 * Blending is a plain feathered composite, one frame over the next, weighted by
 * distance from the frame edge. Sequential compositing needs a single output
 * buffer instead of one accumulator per frame, which is the difference between
 * 35 MB and 150 MB on a 6 megapixel panorama.
 *
 * Not implemented: vertical stitching, roll correction, exposure equalisation
 * between frames, and true spherical projection. A sweep that drifts up or down
 * will show a step at the seam. See docs/PANORAMA.md.
 */
class PanoramaStitcher(private val focalPixels: Float) {

    class Frame(val frame: YuvFrame, val yawRadians: Float)

    private val frames = mutableListOf<Frame>()

    val count: Int get() = frames.size

    fun add(frame: YuvFrame, yawRadians: Float) {
        frames.add(Frame(frame, yawRadians))
    }

    fun clear() = frames.clear()

    fun stitch(): Bitmap {
        check(frames.size >= 2) { "A panorama needs at least two frames" }

        val minYaw = frames.minOf { it.yawRadians }
        val maxYaw = frames.maxOf { it.yawRadians }

        val firstFrame = frames.first().frame
        val frameWidth = firstFrame.width
        val frameHeight = firstFrame.height

        val spanPixels = ((maxYaw - minYaw) * focalPixels).toInt()
        val outWidth = (spanPixels + frameWidth).coerceAtLeast(frameWidth)
        val outHeight = frameHeight

        val out = IntArray(outWidth * outHeight)
        val feather = (frameWidth * 0.18f).coerceAtLeast(8f)

        for (entry in frames) {
            val offsetX = ((entry.yawRadians - minYaw) * focalPixels).toInt()
            composite(entry.frame, out, outWidth, outHeight, offsetX, feather)
        }

        return Bitmap.createBitmap(out, outWidth, outHeight, Bitmap.Config.ARGB_8888)
    }

    private fun composite(
        frame: YuvFrame,
        out: IntArray,
        outWidth: Int,
        outHeight: Int,
        offsetX: Int,
        feather: Float,
    ) {
        val height = min(frame.height, outHeight)
        val chromaWidth = frame.chromaWidth

        for (y in 0 until height) {
            val lumaRow = y * frame.width
            val chromaRow = (y / 2) * chromaWidth
            val outRow = y * outWidth

            for (x in 0 until frame.width) {
                val destX = x + offsetX
                if (destX < 0 || destX >= outWidth) continue

                // Weight falls to zero at the frame edge so the next frame's
                // content takes over gradually instead of at a visible line.
                val edge = min(x.toFloat(), (frame.width - 1 - x).toFloat())
                val alpha = (edge / feather).coerceIn(0f, 1f)

                val ci = chromaRow + (x / 2)
                val lumaValue = (frame.luma[lumaRow + x].toInt() and 0xFF).toFloat()
                val u = (frame.chromaU[ci].toInt() and 0xFF) - 128
                val v = (frame.chromaV[ci].toInt() and 0xFF) - 128

                val r = clamp(lumaValue + 1.402f * v)
                val g = clamp(lumaValue - 0.344136f * u - 0.714136f * v)
                val b = clamp(lumaValue + 1.772f * u)

                val destIndex = outRow + destX
                val existing = out[destIndex]

                out[destIndex] = if (existing == 0) {
                    (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                } else {
                    val er = (existing shr 16) and 0xFF
                    val eg = (existing shr 8) and 0xFF
                    val eb = existing and 0xFF
                    val nr = (er * (1f - alpha) + r * alpha).toInt()
                    val ng = (eg * (1f - alpha) + g * alpha).toInt()
                    val nb = (eb * (1f - alpha) + b * alpha).toInt()
                    (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
                }
            }
        }
    }

    private fun clamp(value: Float): Int {
        val v = value.toInt()
        return if (v < 0) 0 else if (v > 255) 255 else v
    }

    companion object {
        /** Focal length in pixels from physical sensor width. */
        fun focalPixels(focalLengthMm: Float, sensorWidthMm: Float, imageWidthPx: Int): Float {
            if (sensorWidthMm <= 0f || focalLengthMm <= 0f) return imageWidthPx * 0.85f
            return (focalLengthMm / sensorWidthMm) * imageWidthPx
        }
    }
}
