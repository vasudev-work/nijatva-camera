package com.nijatva.camera.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The edition rule: which modes the strip and the grid offer in each build.
 *
 * This is pure logic, so it is tested on the JVM rather than by installing the
 * app and looking at it.
 */
class ModesTest {

    @Test
    fun liteStripOffersPhotoNightAndPro() {
        assertEquals(listOf(Mode.PHOTO, Mode.NIGHT, Mode.PRO), Modes.stripModes(lite = true))
    }

    @Test
    fun fullStripOffersPhotoVideoNightProAstro() {
        assertEquals(
            listOf(Mode.PHOTO, Mode.VIDEO, Mode.NIGHT, Mode.PRO, Mode.ASTRO),
            Modes.stripModes(lite = false),
        )
    }

    @Test
    fun liteGridIsTheSameThreeModes() {
        assertEquals(Modes.stripModes(lite = true), Modes.gridModes(lite = true))
    }

    @Test
    fun fullGridCoversEveryModeTheAppHas() {
        val grid = Modes.gridModes(lite = false)
        assertEquals(Mode.values().size, grid.size)
        assertEquals(grid.size, grid.toSet().size)
    }

    @Test
    fun liteNeverOffersVideoAstroPanoramaOrRaw() {
        val lite = Modes.gridModes(lite = true)
        assertFalse(Mode.VIDEO in lite)
        assertFalse(Mode.ASTRO in lite)
        assertFalse(Mode.PANORAMA in lite)
        assertFalse(Mode.RAW in lite)
    }

    @Test
    fun theStripIsAlwaysASubsetOfTheGrid() {
        listOf(true, false).forEach { lite ->
            val grid = Modes.gridModes(lite).toSet()
            Modes.stripModes(lite).forEach { mode ->
                assertTrue("$mode is on the strip but not in the grid", mode in grid)
            }
        }
    }

    @Test
    fun everyModeHasALabelAndAnIcon() {
        Mode.values().forEach { mode ->
            assertNotEquals("no label for $mode", 0, Modes.labelRes(mode))
            assertNotEquals("no icon for $mode", 0, Modes.iconRes(mode))
        }
    }
}
