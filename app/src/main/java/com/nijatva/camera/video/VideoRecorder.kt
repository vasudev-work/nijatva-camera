package com.nijatva.camera.video

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Size
import android.view.Surface
import java.io.File

/**
 * Encodes the camera's frames into an MP4 through a MediaRecorder surface.
 *
 * The surface is part of the capture session from the moment the session is
 * built, but it only receives frames between start() and stop(). A MediaRecorder
 * cannot be restarted after stop(), so the engine builds a fresh one for every
 * take.
 *
 * Audio is optional and mono AAC. The caller must hold RECORD_AUDIO before
 * passing withAudio = true.
 */
class VideoRecorder(
    context: Context,
    output: File,
    size: Size,
    fps: Int,
    orientationHint: Int,
    withAudio: Boolean = false,
) {
    private val recorder: MediaRecorder = createRecorder(context)

    /** Valid after construction, when prepare() has run. */
    val surface: Surface
        get() = recorder.surface

    init {
        try {
            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            // CAMCORDER is the source tuned for video; it needs RECORD_AUDIO granted.
            if (withAudio) recorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            if (withAudio) {
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                recorder.setAudioEncodingBitRate(128_000)
                recorder.setAudioSamplingRate(44_100)
                recorder.setAudioChannels(1)
            }
            recorder.setVideoSize(size.width, size.height)
            recorder.setVideoFrameRate(fps)
            recorder.setVideoEncodingBitRate(bitrateFor(size, fps))
            recorder.setOrientationHint(orientationHint)
            recorder.setOutputFile(output.absolutePath)
            recorder.prepare()
        } catch (t: Throwable) {
            recorder.release()
            throw t
        }
    }

    fun start() = recorder.start()

    /** Finalises the file. Throws if the take is too short to be valid. */
    fun stop() = recorder.stop()

    fun release() = recorder.release()

    private companion object {
        fun createRecorder(context: Context): MediaRecorder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

        /** Roughly 0.12 bits per pixel per frame: 1080p30 lands near 7.5 Mbps. */
        fun bitrateFor(size: Size, fps: Int): Int =
            (size.width.toLong() * size.height * fps * 0.12).toInt().coerceIn(4_000_000, 40_000_000)
    }
}
