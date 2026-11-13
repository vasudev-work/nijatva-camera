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
