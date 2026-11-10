package com.nijatva.camera.camera

/**
 * Capture modes. Each mode builds a different surface set and capture plan, so
 * switching modes rebuilds the capture session.
 */
enum class Mode(val label: String) {
    PHOTO("Photo"),
    NIGHT("Night"),
    HDR("HDR"),
    PRO("Pro"),
    PANORAMA("Sphere"),
    RAW("RAW"),
    ;

    /** Modes that need the YUV reader and on-device fusion. */
    val isComputational: Boolean get() = this == NIGHT || this == HDR
}
