package com.nijatva.camera.camera

import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.util.Range
import android.util.Size

/**
 * The subset of CameraCharacteristics this app cares about, plus the derived
 * answers to "can this phone actually do what I am about to ask it to do".
 *
 * Read this first on any new device: the hardware level and the RAW capability
 * decide whether night stacking and RAW capture are possible at all, and no
 * amount of app code changes that.
 */
class DeviceCapabilities(
    val cameraId: String,
    val facing: Int,
    val hardwareLevel: Int,
    val supportsRaw: Boolean,
    val supportsManualSensor: Boolean,
    val sensorSize: Size,
    val activeArray: Rect,
    val jpegSizes: List<Size>,
    val yuvSizes: List<Size>,
    val rawSizes: List<Size>,
    val raw10Sizes: List<Size>,
    val fpsRanges: List<Range<Int>>,
    val focusModes: IntArray,
    val oisModes: IntArray,
    val isoRange: Range<Int>?,
    val evRange: Range<Int>?,
    val physicalWidthMm: Float,
    val blackLevel: Float,
    val whiteLevel: Int,
    val exposureRange: Range<Long>?,
    val focalLength: Float,
    val aperture: Float,
) {
    val levelName: String = when (hardwareLevel) {
        CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
        CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
        CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
        CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
        CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
        else -> "UNKNOWN"
    }

    /** Manual ISO/shutter control needs FULL or better. */
    val supportsManualControls: Boolean
        get() = hardwareLevel == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL ||
            hardwareLevel == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_3

    /** Night stacking works on anything that yields YUV frames; quality improves with control. */
    val supportsNightStacking: Boolean
        get() = yuvSizes.isNotEmpty() && supportsManualControls

    val supportsRawCapture: Boolean
        get() = supportsRaw && hardwareLevel >= CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_3

    /** True when the lens offers hardware optical stabilisation. */
    val supportsOis: Boolean
        get() = oisModes.contains(CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON)

    /** True when the sensor can deliver packed 10-bit RAW, which halves DNG size. */
    val supportsRaw10: Boolean
        get() = raw10Sizes.isNotEmpty()

    val supportsAutoFocus: Boolean
        get() = focusModes.contains(CameraMetadata.CONTROL_AF_MODE_AUTO)

    /** Aspect ratio of the sensor's pixel array; 4:3 on almost every phone. */
    val sensorAspect: Float
        get() = if (sensorSize.height > 0) sensorSize.width.toFloat() / sensorSize.height else 4f / 3f

    /**
     * Largest size at or under [maxPixels] that matches the sensor aspect.
     *
     * Matching the aspect matters: the biggest JPEG a camera advertises is often
     * 16:9, and picking it silently crops the top and bottom of a 4:3 sensor.
     */
    fun yuvSizeWithin(maxPixels: Long, aspect: Float = sensorAspect): Size? =
        pick(yuvSizes.filter { it.width.toLong() * it.height <= maxPixels }, aspect)

    fun preferredJpegSize(aspect: Float = sensorAspect): Size? = pick(jpegSizes, aspect)

    fun preferredRawSize(): Size? = rawSizes.maxByOrNull { it.width.toLong() * it.height }

    private fun pick(sizes: List<Size>, aspect: Float): Size? {
        if (sizes.isEmpty()) return null
        val matching = sizes.filter { size ->
            size.height > 0 && kotlin.math.abs(size.width.toFloat() / size.height - aspect) < 0.04f
        }
        return (matching.ifEmpty { sizes }).maxByOrNull { it.width.toLong() * it.height }
    }

    fun diagnostics(): String = buildString {
        appendLine("Camera $cameraId (${if (facing == CameraCharacteristics.LENS_FACING_FRONT) "front" else "back"})")
        appendLine("Hardware level: $levelName")
        appendLine("RAW: ${if (supportsRaw) "yes" else "no"}   manual sensor: ${if (supportsManualSensor) "yes" else "no"}")
        appendLine("Sensor: ${sensorSize.width} x ${sensorSize.height}")
        appendLine("Lens: ${"%.2f".format(focalLength)} mm, f/${"%.1f".format(aperture)}")
        appendLine("ISO range: ${isoRange ?: "unknown"}")
        appendLine("EV range: ${evRange ?: "none"}")
        appendLine("Black level: ${"%.1f".format(blackLevel)}   white level: $whiteLevel")
        exposureRange?.let { appendLine("Exposure: ${it.lower / 1000} us to ${it.upper / 1_000_000} s") }
        appendLine("FPS ranges: ${fpsRanges.joinToString { "${it.lower}-${it.upper}" }}")
        appendLine("Largest JPEG: ${jpegSizes.maxByOrNull { it.width.toLong() * it.height } ?: "none"}")
        appendLine("Largest YUV: ${yuvSizes.maxByOrNull { it.width.toLong() * it.height } ?: "none"}")
        append("Night stacking: ${if (supportsNightStacking) "supported" else "not supported"}")
        appendLine()
        append("RAW capture: ${if (supportsRawCapture) "supported" else "not supported"}")
        appendLine()
        append("OIS: ${if (supportsOis) "available" else "not available"}   RAW10: ${if (supportsRaw10) "yes" else "no"}")
    }

    companion object {
        fun from(cameraId: String, characteristics: CameraCharacteristics): DeviceCapabilities {
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val caps = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: IntArray(0)

            return DeviceCapabilities(
                cameraId = cameraId,
                facing = characteristics.get(CameraCharacteristics.LENS_FACING) ?: CameraCharacteristics.LENS_FACING_BACK,
                hardwareLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
                    ?: CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED,
                supportsRaw = caps.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW),
                supportsManualSensor = caps.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR),
                sensorSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE) ?: Size(0, 0),
                activeArray = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_RECT) ?: Rect(0, 0, 1, 1),
                jpegSizes = map?.getOutputSizes(android.graphics.ImageFormat.JPEG)?.toList() ?: emptyList(),
                yuvSizes = map?.getOutputSizes(android.graphics.ImageFormat.YUV_420_888)?.toList() ?: emptyList(),
                rawSizes = map?.getOutputSizes(android.graphics.ImageFormat.RAW_SENSOR)?.toList() ?: emptyList(),
                raw10Sizes = map?.getOutputSizes(android.graphics.ImageFormat.RAW10)?.toList() ?: emptyList(),
                fpsRanges = map?.getHighSpeedVideoFpsRanges()?.toList() ?: emptyList(),
                focusModes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: IntArray(0),
                oisModes = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
                    ?: IntArray(0),
                isoRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE),
                evRange = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE),
                physicalWidthMm = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)?.width ?: 0f,
                blackLevel = characteristics.get(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN)
                    ?.getOffsetForIndex(0, 0)?.toFloat() ?: 0f,
                whiteLevel = characteristics.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL) ?: 1023,
                exposureRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE),
                focalLength = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull() ?: 0f,
                aperture = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)?.firstOrNull() ?: 0f,
            )
        }
    }
}
