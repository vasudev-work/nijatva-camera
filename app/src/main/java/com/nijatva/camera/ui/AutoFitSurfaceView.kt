package com.nijatva.camera.ui

import android.content.Context
import android.util.AttributeSet
import android.view.SurfaceView
import kotlin.math.roundToInt

/**
 * A SurfaceView that keeps a fixed aspect ratio inside whatever space it is
 * given. Without this the preview stretches: SurfaceView does not letterbox on
 * its own, and a stretched viewfinder is the first thing that makes a camera app
 * feel wrong.
 */
class AutoFitSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : SurfaceView(context, attrs, defStyle) {

    private var aspectRatio = 4f / 3f

    fun setAspectRatio(width: Int, height: Int) {
        require(width > 0 && height > 0) { "Preview size must be positive" }
        aspectRatio = width.toFloat() / height.toFloat()
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)

        if (width == 0 || height == 0) return

        val viewAspect = width.toFloat() / height.toFloat()
        if (viewAspect > aspectRatio) {
            setMeasuredDimension((height * aspectRatio).roundToInt(), height)
        } else {
            setMeasuredDimension(width, (width / aspectRatio).roundToInt())
        }
    }
}
