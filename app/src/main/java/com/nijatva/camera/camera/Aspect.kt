package com.nijatva.camera.camera

/**
 * Capture aspect ratio.
 *
 * The sensor is 4:3, so 4:3 uses the whole active area. 16:9 is a centre crop of
 * the same sensor - it fills a tall phone screen without letterboxing, and it
 * loses the top and bottom of the frame. Neither is "better"; the phone's screen
 * is 20:9, so both modes will letterbox a little.
 */
enum class Aspect(val ratio: Float, val label: String) {
    RATIO_4_3(4f / 3f, "4:3"),
    RATIO_16_9(16f / 9f, "16:9"),
    ;

    fun next(): Aspect = if (this == RATIO_4_3) RATIO_16_9 else RATIO_4_3
}
