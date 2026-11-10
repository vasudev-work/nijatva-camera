package com.nijatva.camera.camera

import android.graphics.Bitmap
import android.media.Image

/**
 * A YUV_420_888 frame copied into tightly packed planes.
 *
 * ImageReader hands back planes with arbitrary row and pixel strides; every
 * piece of processing in this app works on packed arrays instead, so the stride
 * handling happens exactly once, here.
 */
class YuvFrame(
    val width: Int,
    val height: Int,
    val luma: ByteArray,
    val chromaU: ByteArray,
    val chromaV: ByteArray,
) {
    val chromaWidth: Int = (width + 1) / 2
    val chromaHeight: Int = (height + 1) / 2

    fun toBitmap(): Bitmap {
        val pixels = IntArray(width * height)
        val cw = chromaWidth
        for (y in 0 until height) {
            val lumaRow = y * width
            val chromaRow = (y / 2) * cw
            for (x in 0 until width) {
                val yv = luma[lumaRow + x].toInt() and 0xFF
                val ci = chromaRow + (x / 2)
                val u = (chromaU[ci].toInt() and 0xFF) - 128
                val v = (chromaV[ci].toInt() and 0xFF) - 128

                val r = yv + 1.402f * v
                val g = yv - 0.344136f * u - 0.714136f * v
                val b = yv + 1.772f * u

                pixels[lumaRow + x] = (0xFF shl 24) or (clamp(r) shl 16) or (clamp(g) shl 8) or clamp(b)
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun clamp(value: Float): Int {
        val v = value.toInt()
        return if (v < 0) 0 else if (v > 255) 255 else v
    }

    companion object {
        fun from(image: Image): YuvFrame {
            val width = image.width
            val height = image.height
            val planes = image.planes

            val luma = ByteArray(width * height)
            copyPlane(planes[0], width, height, luma)

            val chromaWidth = (width + 1) / 2
            val chromaHeight = (height + 1) / 2
            val u = ByteArray(chromaWidth * chromaHeight)
            val v = ByteArray(chromaWidth * chromaHeight)
            copyPlane(planes[1], chromaWidth, chromaHeight, u)
            copyPlane(planes[2], chromaWidth, chromaHeight, v)

            return YuvFrame(width, height, luma, u, v)
        }

        private fun copyPlane(plane: Image.Plane, width: Int, height: Int, out: ByteArray) {
            val buffer = plane.buffer
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            val row = ByteArray(rowStride)
            var outIndex = 0

            for (y in 0 until height) {
                val rowStart = y * rowStride
                if (rowStart >= buffer.limit()) break
                buffer.position(rowStart)
                val toRead = minOf(rowStride, buffer.remaining())
                buffer.get(row, 0, toRead)

                if (pixelStride == 1) {
                    System.arraycopy(row, 0, out, outIndex, width)
                    outIndex += width
                } else {
                    var src = 0
                    for (x in 0 until width) {
                        out[outIndex++] = row[src]
                        src += pixelStride
                    }
                }
            }
        }
    }
}
