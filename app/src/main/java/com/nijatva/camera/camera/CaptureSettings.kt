package com.nijatva.camera.camera

/**
 * Everything the user can override about a capture. Null means "let the camera
 * decide", which is the default in every automatic mode.
 */
data class CaptureSettings(
    val iso: Int? = null,
    val exposureNs: Long? = null,
    val focusDiopters: Float? = null,
    val evSteps: Int? = null,
    val wbKelvin: Int? = null,
    val zoom: Float = 1.0f,
    val torchOn: Boolean = false,
    val autoFlash: Boolean = false,
    val flashAlways: Boolean = false,
    val oisOn: Boolean? = null,
) {
    val isManual: Boolean get() = iso != null || exposureNs != null || focusDiopters != null

    fun withIso(value: Int?): CaptureSettings = copy(iso = value)
    fun withExposure(value: Long?): CaptureSettings = copy(exposureNs = value)
    fun withFocus(value: Float?): CaptureSettings = copy(focusDiopters = value)
    fun withEv(value: Int?): CaptureSettings = copy(evSteps = value)
    fun withWhiteBalance(value: Int?): CaptureSettings = copy(wbKelvin = value)
}
