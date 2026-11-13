package com.nijatva.camera.ui

import com.nijatva.camera.settings.AccentChoice
import com.nijatva.camera.settings.ThemeChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The appearance mapping: which surface each theme picks, and which colour each
 * accent resolves to. Pure arithmetic, so it runs on the JVM.
 */
class PaletteTest {

    @Test
    fun blackThemeIsTheSiteBlack() {
        Palette.apply(ThemeChoice.BLACK, AccentChoice.SAFFRON, darkSystem = false)

        assertEquals(0xFF0B0B0C.toInt(), Palette.background)
        assertFalse(Palette.isLight)
    }

    @Test
    fun lightThemeGivesLightSurfaces() {
        Palette.apply(ThemeChoice.LIGHT, AccentChoice.SAFFRON, darkSystem = true)

        assertTrue(Palette.isLight)
        assertEquals(0xFFF6F5F2.toInt(), Palette.background)
        assertEquals(0xFF121214.toInt(), Palette.onSurface)
    }

    @Test
    fun systemThemeFollowsTheSystem() {
        Palette.apply(ThemeChoice.SYSTEM, AccentChoice.SAFFRON, darkSystem = true)
        assertFalse(Palette.isLight)

        Palette.apply(ThemeChoice.SYSTEM, AccentChoice.SAFFRON, darkSystem = false)
        assertTrue(Palette.isLight)
    }

    @Test
    fun accentsAreTheThreeTheSiteShips() {
        assertEquals(0xFF1E7A46.toInt(), AccentChoice.GREEN.argb)
        assertEquals(0xFFD98431.toInt(), AccentChoice.SAFFRON.argb)
        assertEquals(0xFF3F45A8.toInt(), AccentChoice.INDIGO.argb)
    }

    @Test
    fun accentIsAppliedWhateverTheTheme() {
        Palette.apply(ThemeChoice.LIGHT, AccentChoice.INDIGO, darkSystem = false)

        assertEquals(0xFF3F45A8.toInt(), Palette.accent)
        assertEquals(0xFF3F45A8.toInt(), Palette.accentSoft(255) or 0xFF000000.toInt())
    }

    @Test
    fun softAccentKeepsTheColourAndSetsAlpha() {
        Palette.apply(ThemeChoice.BLACK, AccentChoice.GREEN, darkSystem = true)

        val soft = Palette.accentSoft(alpha = 40)
        assertEquals(40, (soft ushr 24) and 0xFF)
        assertEquals(AccentChoice.GREEN.argb and 0x00FFFFFF, soft and 0x00FFFFFF)
    }
}
