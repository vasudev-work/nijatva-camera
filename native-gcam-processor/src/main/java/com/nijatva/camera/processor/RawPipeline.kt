package com.nijatva.camera.processor

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import java.io.File

/**
 * Runs the native burst pipeline on RAW_SENSOR images and writes a 16-bit linear TIFF.
 * Images must come from the same camera and be RAW_SENSOR, with the frame's own
 * capture result and characteristics.
 */
class RawPipeline(private val engine: GcamEngine = GcamEngine()) {

    data class Result(
        val referenceIndex: Int,
        val shiftsX: IntArray,
        val shiftsY: IntArray,
        val exposureRatios: FloatArray,
        val alignMs: Float,
        val mergeMs: Float,
        val demosaicMs: Float,
        val writeMs: Float
    )

    /**
     * Caller keeps ownership of the images and closes them afterwards.
     * Returns null if the native side rejects the input or the write fails.
     */
    fun process(
        images: List<Image>,
        results: List<TotalCaptureResult>,
        characteristics: CameraCharacteristics,
        outputTiff: File
    ): Result? {
        require(images.size == results.size && images.isNotEmpty()) { "One result per image" }
        require(images.size <= 16) { "At most 16 frames" }

        val width = images[0].width
        val height = images[0].height
        require(images.all { it.width == width && it.height == height }) { "All frames must match size" }

        val buffers = Array(images.size) { i ->
            images[i].planes[0].buffer.also { require(it.isDirect) { "Image plane must be direct" } }
        }
        val meta = FloatArray(images.size * META_STRIDE)
        val black = RawDeveloper.blackLevel(characteristics)
        val white = RawDeveloper.whiteLevel(characteristics)
        images.forEachIndexed { i, image ->
            val res = results[i]
            val wb = RawDeveloper.whiteBalanceGains(res)
            val stride = image.planes[0].rowStride
            require(stride % 2 == 0) { "RowStride must be even for 16-bit RAW" }
            val base = i * META_STRIDE
            meta[base + 0] = (res.get(android.hardware.camera2.CaptureResult.SENSOR_SENSITIVITY) ?: 100).toFloat()
            meta[base + 1] = (res.get(android.hardware.camera2.CaptureResult.SENSOR_EXPOSURE_TIME) ?: 0L).toFloat()
            meta[base + 2] = wb[0]
            meta[base + 3] = wb[1]
            meta[base + 4] = wb[2]
            meta[base + 5] = black
            meta[base + 6] = white
            // Placeholder noise model: var(DN) = A*DN + B. Replace with measured values (Part 4).
            meta[base + 7] = NOISE_A_PLACEHOLDER
            meta[base + 8] = NOISE_B_PLACEHOLDER
            meta[base + 9] = stride.toFloat()
            // COLOR_CORRECTION_TRANSFORM, row-major, camera RGB -> linear sRGB.
            val ccm = res.get(android.hardware.camera2.CaptureResult.COLOR_CORRECTION_TRANSFORM)
            for (row in 0..2) for (col in 0..2) {
                meta[base + 10 + row * 3 + col] = ccm?.let {
                    val e = it.getElement(col, row)
                    e.numerator.toFloat() / e.denominator
                } ?: if (row == col) 1f else 0f
            }
        }

        val out = engine.nativeProcessRawFramesV2(
            buffers, meta, width, height, outputTiff.absolutePath
        ) ?: return null

        val n = images.size
        return Result(
            referenceIndex = out[0].toInt(),
            shiftsX = IntArray(n) { out[5 + it * 3].toInt() },
            shiftsY = IntArray(n) { out[5 + it * 3 + 1].toInt() },
            exposureRatios = FloatArray(n) { out[5 + it * 3 + 2] },
            alignMs = out[1],
            mergeMs = out[2],
            demosaicMs = out[3],
            writeMs = out[4]
        )
    }

    companion object {
        private const val META_STRIDE = 19
        // Placeholders only. The noise model must be measured per device and ISO.
        private const val NOISE_A_PLACEHOLDER = 0.0f
        private const val NOISE_B_PLACEHOLDER = 9.0f
    }
}
