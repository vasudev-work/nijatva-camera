# liblightcycle.so - what it is

Analysis of the 3.1 MB native library, and why it is not a shortcut to better
photos.

## Identification

| Property | Value |
|---|---|
| Format | ELF64, aarch64, stripped, `DYN` shared object |
| Size | 3,205,800 bytes (code section about 775 KB) |
| SONAME | `liblightcycle.so` |
| Dependencies | `libc`, `libm`, `liblog`, `libGLESv2`, `libdl` only |
| Toolchain | Android clang 3.8 (2016 era), GCC 4.9 comment |
| JNI class | `com.google.android.apps.lightcycle.panorama.LightCycleNative` |
| Exported JNI functions | 50 |

LightCycle was Google's internal code name for **Photo Sphere**. The library is
the panorama stitching engine from the Google Gallery/Camera app, the same code
that later shipped inside the Glass photosphere package.

## What it does, from its own symbols

The 50 JNI entry points describe a panorama capture session, not an image
processing pipeline:

- Capture modes: `ResetForPhotoSphereCapture`, `ResetForHorizontalCapture`,
  `ResetForVerticalCapture`, `ResetForWideCapture`, `ResetForFisheyeCapture`
- Alignment and stitching: `AddImage`, `AlignNextImage`, `UndoAddImage`,
  `ProcessFrame`, `PreviewStitch`, `StitchPanorama`, `StereographicProject`
- Motion sensing: `StartGyroCalibration`, `EndGyroCalibration`,
  `GetHeadingRadians`, `SetFilteredRotation`, `MovingTooFast`,
  `PhotoSkippedTooFast`, `DeviceOrientationStatus`
- Capture guidance: `InitTargets`, `GetTargets`, `TargetHit`,
  `GetTargetInRange`, `SetTargetHitAngleRadians`
- GPU plumbing: `CreateFrameTexture`, `InitFrameTexture`, `UpdateFrameTexture`
- Output: `SetOutputResolutionLarge/Medium/Small`, `CreateThumbnailImage`,
  `CalibrateFieldOfViewDeg`

Internal strings confirm the architecture: `pixel_mapper`, `image_adjuster`,
`blending_masks_`, `dilated_bounds_`, `projection_masks_`. That is the classic
Google panorama pipeline: project each frame onto a sphere or cylinder, build
overlap masks, feather the seams, adjust per-image exposure before blending.

## What it is not

There is no HDR+ fusion, no night mode, no super resolution, and no multi-frame
denoising in this library. It aligns and blends panorama frames. For the problem
this project exists to solve - a noisy, clipped, soft image in low light - it does
nothing at all.

## Can it be dropped into an app

Technically, it is unusually self-contained: it needs only GLES2, libc, libm and
liblog, with no vendor HAL dependency, which is why the same binary ran across
many devices. But it is not usable as a component:

1. **The Java side is missing.** Every call goes through
   `com.google.android.apps.lightcycle.panorama.LightCycleNative`, a 50-method
   contract with no documentation. The session state machine - what order to call
   `Init`, `InitTargets`, `CreateNewStitchingSession`, `ProcessFrame`,
   `FinishCapture` in, and what each expects back - has to be reverse engineered
   from a stripped binary.
2. **Licensing.** This is proprietary Google code extracted from a Google APK.
   Bundling it in another app is redistribution of closed-source software,
   regardless of whether it loads.
3. **It solves a different problem.** Even a perfect integration gives panorama
   stitching, not image quality.

## The usable idea

If panorama is wanted in Nijatva, the architecture above is the right one and it
does not need this binary:

1. Capture frames as the user pans, with the gyroscope giving an initial estimate
   of the rotation between them.
2. Refine that estimate by image alignment - `Align.estimate` in this app already
   does translation-only registration on downsampled luma.
3. Project each frame onto a cylinder using the estimated yaw.
4. Feather the overlap: weight falls from 1 at the centre of a frame to 0 at its
   edge, then normalise.
5. Compensate per-frame exposure before blending, or the seams show as brightness
   steps.

Steps 2 and 4 are already in this codebase in simpler form; step 1 needs the
`TYPE_ROTATION_VECTOR` sensor, step 3 is a coordinate transform, step 5 is a mean
match on overlap. That is a real week of work, not a dropped-in binary.

## Attribution

Photo Sphere and LightCycle are Google trademarks and products. This note records
a technical reading of a file supplied by the device owner; nothing from that
library is included in this project.
