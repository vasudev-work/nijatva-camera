package com.nijatva.camera.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class OrientationTest {
    @Test fun backCameraNaturalPortrait() = assertEquals(90, Orientation.jpegRotation(90, 0, false))
    @Test fun backCameraLandscapeNeedsNoTurn() = assertEquals(0, Orientation.jpegRotation(90, 90, false))
    @Test fun backCameraUpsideDown() = assertEquals(270, Orientation.jpegRotation(90, 180, false))
    @Test fun frontCameraLandscape() = assertEquals(0, Orientation.jpegRotation(270, 90, true))
    @Test fun wrapsAroundFullTurn() = assertEquals(180, Orientation.jpegRotation(0, 180, false))
}
