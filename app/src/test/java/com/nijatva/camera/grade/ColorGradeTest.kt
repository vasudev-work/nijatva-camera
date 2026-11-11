package com.nijatva.camera.grade

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ColorGradeTest {

    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    private fun red(p: Int) = (p shr 16) and 0xFF
    private fun green(p: Int) = (p shr 8) and 0xFF
    private fun blue(p: Int) = p and 0xFF

    @Test fun whiteBalanceNeutralisesUniformCast() {
        val px = IntArray(64) { argb(150, 100, 100) }
        ColorGrade.whiteBalance(px)
        val p = px[0]
        assertTrue(abs(red(p) - green(p)) <= 1)
        assertTrue(abs(green(p) - blue(p)) <= 1)
    }

    @Test fun alphaSurvivesGrading() {
        val px = intArrayOf(argb(255, 255, 255), argb(0, 0, 0), argb(200, 40, 10), argb(128, 128, 128))
        ColorGrade.apply(px, 2, 2)
        px.forEach { assertEquals(0xFF, it ushr 24) }
    }

    @Test fun neutralGreyStaysNeutralAndNearItsValue() {
        val px = IntArray(16) { argb(128, 128, 128) }
        ColorGrade.apply(px, 4, 4, ColorGrade.Look(clarity = 0f, saturation = 1f))
        val p = px[5]
        assertEquals(red(p), green(p))
        assertEquals(green(p), blue(p))
        assertTrue(abs(red(p) - 128) <= 4)
    }

    @Test fun highlightsRollOffBelowFullWhite() {
        val knee = 0.92f
        for (i in 0..100) {
            val x = i / 100f
            assertTrue(ColorGrade.toneCurve(x, knee) <= 1f)
        }
        assertEquals(0f, ColorGrade.toneCurve(0f, knee), 1e-6f)
        assertTrue(ColorGrade.toneCurve(1f, knee) < 1f)
    }

    @Test fun toneCurveIsMonotonic() {
        var prev = -1f
        for (i in 0..200) {
            val v = ColorGrade.toneCurve(i / 200f, 0.92f)
            assertTrue(v >= prev)
            prev = v
        }
    }

    @Test fun boxBlurOfFlatPlaneIsFlat() {
        val src = FloatArray(5 * 4) { 0.4f }
        val out = ColorGrade.boxBlur(src, 5, 4, 2)
        out.forEach { assertEquals(0.4f, it, 1e-5f) }
    }
}
