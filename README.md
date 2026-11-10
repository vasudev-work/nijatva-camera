# Nijatva

A Camera2 camera app for Android (`com.nijatva.camera`), built to do the things a stock OEM camera will
not do on a mid-range phone: manual exposure, RAW, night stacking and exposure
fusion, without any root, Magisk or signature tricks.

Kotlin, no Compose, no CameraX. Plain `android.hardware.camera2` plus programmatic
views, so every step of the capture pipeline is visible and changeable.

## What it does

| Mode | Pipeline | Output |
|---|---|---|
| Photo | Single capture, camera JPEG passed through untouched | Full-res JPEG |
| Night | 8 YUV frames at a long fixed exposure, re-registered, ghost-rejected, averaged | JPEG, up to 5 MP |
| HDR | 3 bracketed exposures (0, +2, -2 EV), Mertens exposure fusion | JPEG, up to 4 MP |
| Pro | Manual ISO, shutter and focus from sliders, single capture | Full-res JPEG |
| RAW | `DngCreator` from the RAW_SENSOR stream | DNG |
| Sphere | Gyro-triggered frames every 8 degrees, cylindrical stitch with feathered seams | Wide panorama JPEG |

Plus: a 4:3 / 16:9 capture toggle, tap to set the AF/AE metering point, pinch to
zoom (crop region, not digital upscale), torch toggle, front/back switch, and an
Info dialog that
dumps the camera's hardware level, RAW support, sensor size and output sizes.

That Info dialog is the first thing to open on a new phone. If the level reads
LEGACY or LIMITED, night and HDR will refuse to run, and that is the phone, not
the app.

## What it deliberately does not do

- No video recording. Video is a separate pipeline (`MediaRecorder` or
  `MediaCodec` plus a surface) and mixing it into this class would make both
  worse. It is the next module, not a flag.
- No beauty, no AI scene detection, no portrait bokeh. Those are vendor
  algorithms shipped inside the OEM camera; nothing here can reproduce them.
- No optical quality improvement. Noise, tone and colour improve through
  stacking and fusion; autofocus speed, lens softness and rolling shutter are
  hardware.

## Build

1. Open the project folder in Android Studio (Ladybug or newer).
2. Android Studio will fetch a Gradle distribution for the wrapper on first sync;
   no `gradlew` binary is committed here.
3. Requires JDK 17, Android SDK 35, AGP 8.7.3, Kotlin 2.0.21.
4. `minSdk` is 29 (Android 10) because the app writes through MediaStore and uses
   scoped-storage paths without any legacy permission.

```
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Only the `CAMERA` permission is requested. Images go to
`Pictures/NijatvaCamera/`.

## Notes on supplied binaries

`docs/LIGHTCYCLE_SO.md` identifies the supplied `liblightcycle.so` as Google's
Photo Sphere stitching engine (code name LightCycle) and explains why it cannot
be dropped into an app: proprietary, undocumented 50-method JNI contract, and it
solves panorama stitching rather than image quality.

## Open-source options

`docs/OPEN_SOURCE_OPTIONS.md` evaluates the usual "open source GCam alternative"
list. Short version: the ports are not open source, Open Camera is GPLv3, and the
one genuinely useful reference is GrapheneOS Camera's MIT-licensed Zero Shutter
Lag wiring, which this phone's `PRIVATE_REPROCESSING` support makes viable.

## Where to change things

| Pro panel layout, tool set, slider mapping | `ui/ProPanel.kt`, `ui/ToolButton.kt`, `ui/ThermometerSlider.kt` |
| Panorama capture cadence and stitch | `MainActivity.panoRunnable`, `process/PanoramaStitcher.kt` |
| Want | File |
|---|---|
| How many night frames, max capture size, exposure and ISO choice | `camera/CameraEngine.kt`, the `companion object` |
| Fusion weights and tone curve | `process/ExposureFusion.kt`, `camera/Stacker.kt` |
| Which sizes and streams a mode uses | `CameraEngine.createSession()` |
| Manual control ranges shown to the user | `MainActivity.onReady()` |
| UI layout | `MainActivity.buildUi()` |

## Settings

A settings sheet (General / Pro tabs) and an Advanced page, matching the
reference layout: flash, HDR+, timer, ratio, grid, framing hints, RAW+JPEG, timer
light and wide-gamut output. `docs/SETTINGS.md` maps every switch to the Camera2
control it changes, and lists the reference settings that are deliberately absent
with the reason for each.

## Tuned for realme RMX3998

This build is configured against a Camera2 probe dump from a realme RMX3998
(Android 16). Back camera is LEVEL_3 with RAW and manual sensor control, so every
mode here works. Two hardware limits are documented in
`docs/DEVICE_PROFILE.md`: JPEG is capped near 8 MP for third-party apps, and the
AE ranges top out at 30 fps, so 60 fps capture is not reachable at all.

The UI is inset-aware, which this phone needs: Android 16 draws the app behind
the status and navigation bars for any targetSdk 35 app, so both bars are padded
by the real window insets.

If you run this on a different phone, open the Info dialog first and read the
level. Then adjust `NIGHT_MAX_PIXELS` and `NIGHT_FRAMES` in `CameraEngine.kt` to
match the RAM you have.

## Honest status

The code has not been compiled in the environment it was written in, because that
environment has no Android SDK. It is written against the documented Camera2 API
and the project structure is complete, but treat the first build as a debugging
session: expect missing imports or a deprecated-API warning to fix, not a
finished app. The capture logic and the maths are the parts worth keeping.

Known gaps, in the order they will bite:

1. **No EXIF on merged images.** Single-shot JPEGs keep the camera's own EXIF;
   merged output is written without it. `ImageSaver.writeExif` exists and is
   only wired for the single-shot path.
2. **Preview framing.** The preview follows the chosen capture aspect, but the
   merged output is a slightly different size, so framing is approximate.
3. **No thumbnail / last-shot review.** Saving works, viewing requires leaving
   the app.
4. **Rotated output.** Orientation is not written into merged JPEGs, so
   landscape shots may appear sideways in some galleries.
5. **No measured noise model.** Ghost rejection estimates its threshold from the
   capture itself. A calibrated per-ISO noise model would separate noise from
   motion more reliably - see `docs/GCAM_CONFIGS.md` for how to measure one.

## Licence

Your code, your call. If any of this ends up in a GPL project, note that the
exposure-fusion and stacking implementations here are independent of any GPL
source.
