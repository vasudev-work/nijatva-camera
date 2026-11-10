package com.nijatva.camera.io

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saves finished images into the shared Pictures/NijatvaCamera folder through
 * MediaStore, so no storage permission is needed on Android 10 and later.
 */
class ImageSaver(private val context: Context) {

    fun saveJpeg(
        bitmap: Bitmap,
        quality: Int = 98,
        label: String = "IMG",
        mirror: Boolean = false,
        wideGamut: Boolean = false,
    ): Uri {
        val prepared = prepare(bitmap, mirror, wideGamut)
        val name = "${label}_${timestamp()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/NijatvaCamera")
            put(MediaStore.Images.Media.IS_PENDING, 1)
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
            put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore refused the insert")

        resolver.openOutputStream(uri)?.use { stream ->
            prepared.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        } ?: error("Could not open $uri for writing")

        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

    /**
     * Writes camera-produced JPEG bytes straight to MediaStore.
     *
     * No re-encode: the bytes already carry the camera's own EXIF and its
     * encoder settings, and re-compressing would throw away detail for nothing.
     */
    fun saveJpegBytes(bytes: ByteArray, label: String = "IMG"): Uri {
        val name = "${label}_${timestamp()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/NijatvaCamera")
            put(MediaStore.Images.Media.IS_PENDING, 1)
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
            put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore refused the insert")

        resolver.openOutputStream(uri)?.use { it.write(bytes) }
            ?: error("Could not open $uri for writing")

        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

    fun saveDng(file: File): Uri {
        val name = "IMG_${timestamp()}.dng"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/x-adobe-dng")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/NijatvaCamera")
            put(MediaStore.Images.Media.IS_PENDING, 1)
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
            put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore refused the insert")

        resolver.openOutputStream(uri)?.use { stream ->
            file.inputStream().use { it.copyTo(stream) }
        } ?: error("Could not open $uri for writing")

        file.delete()

        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

    /** Writes capture metadata into a JPEG already committed to MediaStore. */
    fun writeExif(uri: Uri, iso: Int?, exposureNs: Long?, focalLength: Float?) {
        try {
            context.contentResolver.openFileDescriptor(uri, "rw")?.use { descriptor ->
                val exif = ExifInterface(descriptor.fileDescriptor)
                iso?.let { exif.setAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, it.toString()) }
                exposureNs?.let {
                    exif.setAttribute(ExifInterface.TAG_EXPOSURE_TIME, (it / 1_000_000_000.0).toString())
                }
                focalLength?.let { exif.setAttribute(ExifInterface.TAG_FOCAL_LENGTH, it.toString()) }
                exif.saveAttributes()
            }
        } catch (_: Exception) {
            // Metadata is nice to have; a failed EXIF write must not lose the photo.
        }
    }

    fun openStream(uri: Uri): OutputStream? = context.contentResolver.openOutputStream(uri)

    /**
     * Applies the two save-time transforms the settings offer.
     *
     * Mirroring matters because the front camera preview is mirrored and the
     * saved file is not - which surprises people when they compare the two.
     * Wide gamut tags the bitmap as Display P3, so the encoder writes an ICC
     * profile instead of assuming sRGB.
     */
    private fun prepare(bitmap: Bitmap, mirror: Boolean, wideGamut: Boolean): Bitmap {
        var working = bitmap

        if (mirror) {
            val matrix = android.graphics.Matrix().apply { preScale(-1f, 1f) }
            working = Bitmap.createBitmap(working, 0, 0, working.width, working.height, matrix, true)
        }

        if (wideGamut && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                working.setColorSpace(android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.DISPLAY_P3))
            } catch (_: Throwable) {
                // An immutable or hardware bitmap refuses the change; sRGB output
                // is a fine fallback and must not lose the photo.
            }
        }
        return working
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
}
