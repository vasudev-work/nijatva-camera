package com.nijatva.camera.grade

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.exp

/**
 * Colour and tone for merged photos (Night, HDR, Astro). Pure functions over ARGB
 * pixels. The order is white balance, tone curve, local contrast in the midtones,
 * then saturation. Chroma is scaled around luma, so highlights do not turn grey.
 *
 * Memory: the local-contrast blur runs on a 4x downsampled luma plane, so a 12 MP
 * frame needs one float plane for luma, not several full-size ones.
 */
object ColorGrade {

    data class Look(
        /** Midtone local contrast. 0 turns it off. */
        val clarity: Float = 0.18f,
        /** Chroma multiplier. 1 keeps the colours as they were. */
        val saturation: Float = 1.08f,
        /** Where the highlight shoulder starts, in 0..1. Higher keeps more highlight range. */
        val shoulder: Float = 0.92f,
        /** Blur radius on the downsampled plane, in small pixels (about 4 px at full size per step). */
        val blurRadius: Int = 2,
    )

    private const val DOWN = 4
    private const val LUT_SIZE = 1024

    /** Converts the bitmap and returns a new mutable ARGB_8888 bitmap. The input is not changed. */
    fun applyTo(src: Bitmap, look: Look = Look()): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        apply(px, w, h, look)
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Grades the pixels in place and returns the same array. */
    fun apply(px: IntArray, w: Int, h: Int, look: Look = Look()): IntArray {
        whiteBalance(px)

        val n = px.size
        val luma = FloatArray(n)
        for (i in 0 until n) luma[i] = lumaOf(px[i])

        val sw = (w + DOWN - 1) / DOWN
        val sh = (h + DOWN - 1) / DOWN
        val small = downsample(luma, w, h, sw, sh)
        val smallBlur = boxBlur(small, sw, sh, look.blurRadius)

        val curve = buildCurve(look.shoulder)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                val p = px[i]
                val r0 = ((p shr 16) and 0xFF) / 255f
                val g0 = ((p shr 8) and 0xFF) / 255f
                val b0 = (p and 0xFF) / 255f
                val y0 = luma[i]

                val l = lut(curve, y0)
                val blur = lut(curve, smallBlur[(y / DOWN).coerceAtMost(sh - 1) * sw + (x / DOWN).coerceAtMost(sw - 1)])
                val midtone = 1f - abs(2f * l - 1f)
                val lc = (l + look.clarity * midtone * (l - blur)).coerceIn(0f, 1f)

                // Scale chroma (colour minus luma) around the new luma.
                val s = look.saturation
                val r = (lc + s * (r0 - y0)).coerceIn(0f, 1f)
                val g = (lc + s * (g0 - y0)).coerceIn(0f, 1f)
                val b = (lc + s * (b0 - y0)).coerceIn(0f, 1f)

                px[i] = (p and 0xFF000000.toInt()) or
                    ((r * 255f + 0.5f).toInt() shl 16) or
                    ((g * 255f + 0.5f).toInt() shl 8) or
                    (b * 255f + 0.5f).toInt()
            }
        }
        return px
    }

    /**
     * Grey-world white balance. Each channel is scaled so the frame's average colour
     * becomes neutral, but gains are clamped so a strong scene colour is not erased.
     */
    fun whiteBalance(px: IntArray) {
        var sr = 0L
        var sg = 0L
        var sb = 0L
        var count = 0
        var i = 0
        while (i < px.size) {
            val p = px[i]
            sr += (p shr 16) and 0xFF
            sg += (p shr 8) and 0xFF
            sb += p and 0xFF
            count++
            i += 4
        }
        if (count == 0) return
        val mr = sr.toFloat() / count
        val mg = sg.toFloat() / count
        val mb = sb.toFloat() / count
        val avg = (mr + mg + mb) / 3f
        if (mr < 1f || mg < 1f || mb < 1f) return
        val gr = (avg / mr).coerceIn(0.7f, 1.4f)
        val gg = (avg / mg).coerceIn(0.7f, 1.4f)
        val gb = (avg / mb).coerceIn(0.7f, 1.4f)

        for (j in px.indices) {
            val p = px[j]
            val r = (((p shr 16) and 0xFF) * gr + 0.5f).toInt().coerceIn(0, 255)
            val g = (((p shr 8) and 0xFF) * gg + 0.5f).toInt().coerceIn(0, 255)
            val b = ((p and 0xFF) * gb + 0.5f).toInt().coerceIn(0, 255)
            px[j] = (p and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
        }
    }

    /**
     * Tone curve: a gentle S for midtone contrast, then a soft shoulder above the knee
     * so highlights roll off and never reach a hard clip. Output stays below 1.
     */
    internal fun toneCurve(x: Float, knee: Float): Float {
        val s = x * x * (3f - 2f * x)
        val c = x * 0.75f + s * 0.25f
        return if (c <= knee) c else knee + (1f - knee) * (1f - exp(-(c - knee) / (1f - knee)))
    }

    private fun buildCurve(knee: Float): FloatArray =
        FloatArray(LUT_SIZE) { toneCurve(it / (LUT_SIZE - 1f), knee) }

    private fun lut(curve: FloatArray, v: Float): Float {
        val idx = (v.coerceIn(0f, 1f) * (LUT_SIZE - 1) + 0.5f).toInt()
        return curve[idx]
    }

    private fun lumaOf(p: Int): Float {
        val r = ((p shr 16) and 0xFF) / 255f
        val g = ((p shr 8) and 0xFF) / 255f
        val b = (p and 0xFF) / 255f
        return 0.299f * r + 0.587f * g + 0.114f * b
    }

    /** Mean of each DOWN x DOWN block. Edge blocks average whatever pixels exist. */
    private fun downsample(src: FloatArray, w: Int, h: Int, sw: Int, sh: Int): FloatArray {
        val out = FloatArray(sw * sh)
        for (sy in 0 until sh) {
            for (sx in 0 until sw) {
                var sum = 0f
                var count = 0
                for (y in sy * DOWN until minOf(h, sy * DOWN + DOWN)) {
                    for (x in sx * DOWN until minOf(w, sx * DOWN + DOWN)) {
                        sum += src[y * w + x]
                        count++
                    }
                }
                out[sy * sw + sx] = if (count > 0) sum / count else 0f
            }
        }
        return out
    }

    /** Separable box blur with edge clamping. Each pass keeps a running sum, so it is O(pixels). */
    internal fun boxBlur(src: FloatArray, w: Int, h: Int, r: Int): FloatArray {
        val d = 2 * r + 1
        val tmp = FloatArray(src.size)
        val out = FloatArray(src.size)

        for (y in 0 until h) {
            val row = y * w
            var acc = 0f
            for (k in -r..r) acc += src[row + k.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                tmp[row + x] = acc / d
                acc += src[row + (x + r + 1).coerceAtMost(w - 1)] - src[row + (x - r).coerceAtLeast(0)]
            }
        }

        for (x in 0 until w) {
            var acc = 0f
            for (k in -r..r) acc += tmp[k.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) {
                out[y * w + x] = acc / d
                acc += tmp[(y + r + 1).coerceAtMost(h - 1) * w + x] - tmp[(y - r).coerceAtLeast(0) * w + x]
            }
        }
        return out
    }
}
