# Native RAW processor module

`native-gcam-processor` holds the RAW burst work that does not belong on the Java
heap: alignment, merging, demosaic, colour and tone, all in C++.

## What is in it

| File | Role |
|---|---|
| `cpp/pipeline_core.cpp` | Stages 0-8b: normalise, pick the reference frame, align, exposure match, merge, demosaic, white balance, colour matrix, tone curve. |
| `cpp/pipeline_core.h` | `FrameMeta`, `FrameInput`, `BurstStats`, `developBurst()`. |
| `cpp/gcam_constants.h` | Every tunable, in one place, with the tuning note attached. |
| `cpp/pipeline_jni.cpp` | JNI entry `nativeProcessRawFramesV2`: takes the buffers plus a flat metadata array, returns per-frame shifts, exposure ratios and stage timings. |
| `cpp/raw_develop.cpp` | Single-frame path used by `RawDeveloper.kt`. |
| `cpp/processor.cpp` | The original v1 entry point, kept so older callers still link. |
| `GcamEngine.kt` | JNI declarations. |
| `RawPipeline.kt` | Builds the metadata array from `TotalCaptureResult` and calls the burst entry point. |
| `RawBurstController.kt` | Captures a RAW burst of N frames with their capture results. |
| `RawCameraController.kt`, `RawCaptureSetup.kt`, `DngSaver.kt` | Single-frame RAW capture and DNG output. |
| `RawDeveloper.kt` | One RAW frame to a `Bitmap`/JPEG through the native path. |
| `tests/` | Two host tests, no phone needed. See `tests/README.md`. |

## What runs today

1. Black-level subtraction and normalisation per frame.
2. Reference frame = sharpest of the first three, by green gradient energy.
3. Coarse-to-fine alignment on a half-resolution green pyramid, four levels, L2 at
   coarse levels, L1 at the finest, global translation.
4. Exposure match to the reference over the aligned overlap, clipped and black
   pixels excluded, ratio clamped to 0.25-4.0.
5. Merge: per-pixel inverse-variance average from the noise model, with a
   median-based ghost guard at 3 sigma, and clipped pixels dropped.
6. Bilinear demosaic.
7. White balance from the capture's `COLOR_CORRECTION_GAINS`.
8. 3x3 `COLOR_CORRECTION_TRANSFORM` with negative-channel desaturation.
9. Hable shoulder normalised to white and gamma 1.12 so an 18% grey card lands at
   0.46 sRGB, then sRGB encoding through a 16-bit LUT.
10. 16-bit TIFF out.

## What is not in it yet

- The Wiener merge in the frequency domain (stage 4, option B). The current merge
  is the inverse-variance average, which the synthetic test shows already reaches
  the 1/sqrt(N) noise floor.
- Malvar-He-Cutler demosaic (bilinear now), false-colour suppression.
- Synthetic-exposure fusion and local contrast: `ColorGrade` does those on the
  Kotlin side today.
- Sharpening, chroma denoise, dithering, JPEG encode - the pipeline writes TIFF.
- Per-device noise model. `RawPipeline.kt` still passes placeholder `A` and `B`
  (`0` and `9`); measure them per ISO before trusting any of this on a phone.
- Nothing calls the pipeline from a capture mode yet.

## Build

NDK 27.2.12479018 and CMake 3.22.1, both pinned in `build.gradle`. arm64-v8a and
x86_64 only: the working set of a 12 MP float pipeline does not fit a 32-bit
address space.
