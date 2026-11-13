package com.nijatva.camera.processor

import java.nio.ByteBuffer

class GcamEngine {

    companion object {
        init {
            System.loadLibrary("gcamprocessor")
        }
    }

    /**
     * @param buffers direct ByteBuffers holding 16-bit Bayer RAW data (RAW_SENSOR)
     */
    external fun nativeProcessRawFrames(
        buffers: Array<ByteBuffer>,
        width: Int,
        height: Int,
        outputPath: String
    ): Boolean

    /**
     * Develops one RAW plane to packed ARGB_8888 pixels (see raw_develop.cpp).
     * Returns null if the native side rejects the input.
     */
    external fun nativeDevelopRaw(
        buffer: ByteBuffer,
        width: Int,
        height: Int,
        rowStrideBytes: Int,
        blackLevel: Float,
        whiteLevel: Float,
        wbGains: FloatArray
    ): IntArray?

    /**
     * Full burst pipeline (see pipeline_core.cpp). meta is 19 floats per frame:
     * [iso, exposureNs, wbR, wbG, wbB, black, white, noiseA, noiseB, strideBytes, ccm 3x3].
     * Returns [referenceIndex, alignMs, mergeMs, demosaicMs, writeMs,
     *          then per frame: shiftX, shiftY, exposureRatio], or null on failure.
     */
    external fun nativeProcessRawFramesV2(
        buffers: Array<ByteBuffer>,
        meta: FloatArray,
        width: Int,
        height: Int,
        outputPath: String
    ): FloatArray?

    fun processFrames(
        buffers: List<ByteBuffer>,
        width: Int,
        height: Int,
        outputPath: String
    ): Boolean {
        require(buffers.all { it.isDirect }) { "RAW buffers must be direct ByteBuffers" }
        return nativeProcessRawFrames(buffers.toTypedArray(), width, height, outputPath)
    }
}
