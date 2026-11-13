# Changelog

## Phase 1 (unreleased)

- **Upright merged photos.** Night and HDR output is rotated to the device
  orientation before saving. Single JPEGs get the Camera2 `JPEG_ORIENTATION`.
  Camera2's rule lives in `camera/Orientation.kt`.
- **Merged photos get EXIF.** Make, Model, Software and Orientation are written
  by `ImageSaver.tagMerged`.
- **Last-shot thumbnail.** The corner thumbnail shows the saved photo. Tap it to
  open the photo in the gallery.
- **Night hold-still ring.** In Night mode the shutter ring is green when the
  phone is steady and amber while it moves. `camera/StillnessTracker.kt` checks
  motion over about 750 ms.
- **Settings export, import and reset.** `SettingsStore.exportJson`,
  `importJson` and `reset`. Import clamps out-of-range values. Not yet wired to
  a button.
- **Unit tests** for the rotation math and the stillness logic
  (`app/src/test`). They run on the JVM and need no phone.


## Phase 2, part 1: video

- **Video mode (full edition).** H.264 MP4 at up to 1080p, 30 fps, 16:9,
  picture only. The shutter starts and stops a take. `video/VideoRecorder.kt`
  wraps MediaRecorder. The engine adds its surface to the session in
  `createSession` and to the repeating request only while recording.
- **Recording UI.** The shutter ring turns red and the status line shows
  `REC mm:ss` while recording.
- **Safe stop.** Leaving the app or switching modes ends a take cleanly. The
  file is moved into the gallery, and a fresh session is built for the next take.
- Lite edition does not get video.

Not yet: audio, zero shutter lag, GPU fusion, astro mode, histogram, focus
peaking, presets UI, panorama orientation fix.

## Phase 2, part 2: histogram and focus peaking (Photo and Pro)

- **Histogram.** A 256-bin brightness histogram in the top-right of the
  viewfinder. Amber bars mark clipped shadows and highlights. Settings > Pro >
  Viewfinder analysis.
- **Focus peaking.** Red overlay on sharp edges, rotated to match the upright
  preview. Same settings section.
- **Analysis stream.** A low-resolution YUV stream (about 640 x 480) runs next to
  the preview in Photo and Pro, only on full or level-3 devices. Every third frame
  is measured. Work is skipped while both options are off.
- **Pure logic in `analysis/FrameAnalysis.kt`,** with JVM tests in
  `FrameAnalysisTest`.

Known limits: peaking is not mirrored for the front camera, and the fixed
threshold (40) may need tuning per scene.

## Phase 2, part 3: video audio

- **Microphone audio in video.** Mono AAC, 128 kbps, 44.1 kHz, camcorder source.
  Settings > Pro > Video > Record audio, on by default.
- **Permission flow.** The first record press asks for `RECORD_AUDIO`. Granting it
  turns audio on; tap record again to start. Denying it records picture only.
- The session is rebuilt once when audio is turned on, since the recorder is
  prepared with the session.

## Phase 3: astro mode (full edition)

- **Astro mode.** Six 4-second exposures at ISO 3200, aligned and stacked with
  the existing Stacker. Ghost rejection removes outliers such as planes and
  meteors. A stack takes about 30 seconds.
- **Tripod gate.** The shutter refuses to start while the gyro reports movement.
  If the device has no gyro, the check is skipped.
- **Frame duration.** Exposures longer than 33 ms set `SENSOR_FRAME_DURATION` so
  the HAL does not shorten them. Night mode is unaffected below that limit.

Not yet: exposure and frame count as settings, movement abort during a stack,
and tone-curve tuning on a real night sky. The 4 s and ISO 3200 values are
starting points.

## Phase 3, part 2: astro settings, movement abort, presets

- **Astro settings.** Exposure per frame (2, 4 or 8 s) and frame count (4, 6 or
  10) in Settings > Pro > Astro. Changing them in Astro mode rebuilds the session.
- **Movement abort.** If the phone keeps moving for two seconds during an astro
  stack, the stack is cancelled, the preview comes back, and nothing is saved.
- **Presets and backup.** "Presets and backup" in the settings sheet saves,
  loads and deletes named presets, shares the settings as JSON, and resets to
  defaults. Reset keeps saved presets.

## Phase 3, part 3: settings import

- **Import settings from a JSON file** in "Presets and backup". The system file
  picker opens, the file is read (up to 64 KB), and the settings are applied and
  saved. A file that is not a settings file is refused with a message.

## Colour grading for merged photos

- **`grade/ColorGrade.kt`** runs on Night, HDR and Astro output before it is saved.
  Steps: grey-world white balance (gains clamped to 0.7 to 1.4), a tone curve with
  a soft highlight shoulder, midtone local contrast from a blur on a 4x downsampled
  plane, and a saturation boost (1.08) applied to chroma only.
- Memory: the blur runs on a quarter-size luma plane, so a 12 MP frame does not
  allocate several full-size float planes.
- Single-frame JPEGs from the camera are not regraded, since the HAL already
  processes them.
- Look values are starting points. They were chosen by reasoning, not by looking
  at real photos, so tune them on a few real shots before trusting them.

## Phase 4: real alignment and exposure matching

- **Coarse-to-fine alignment.** `camera/Align.kt` now searches three pyramid
  levels (1/32, 1/16, 1/8) instead of one. The coarse level reaches +/-96 pixels
  of full-resolution drift, so a bigger handshake is registered instead of
  clipped, and every level is a box average rather than a point sample, which
  stops brickwork, foliage and fabric from aliasing into a false match.
  `AlignPyramid` builds the reference levels once per burst and `Stacker` reuses
  them.
- **Per-frame exposure match.** `camera/ExposureMatch.kt` measures the mid-tone
  mean ratio over the overlap, after the alignment shift, and scales each frame
  onto the first - luma and chroma - before it is fused. Auto-exposure drift no
  longer averages a night stack to mid grey. Clipped highlights and crushed
  shadows are skipped because they carry no brightness information.
- **Panorama seam brightness.** `process/PanoramaStitcher.kt` matches every
  frame's exposure to the first one, so panning out of shade or past a window no
  longer leaves a hard step at the seam.
- **Tests.** `AlignTest` covers shift recovery at both small and large drift,
  identical frames and the small-frame fallback; `ExposureMatchTest` covers ratio
  recovery in both directions, clipped-pixel handling, the no-overlap fallback
  and flat frames.

Not modelled yet: rotation between frames, GPU fusion, zero shutter lag.

## Native RAW processor module

- **`native-gcam-processor`.** A new library module with a JNI bridge
  (`GcamEngine`), Camera2 RAW capture helpers (`RawCaptureSetup`,
  `RawCameraController`) and a DNG writer (`DngSaver`). CMake builds
  `libgcamprocessor.so` for arm64-v8a and x86_64; the app depends on the module,
  so both build together.
- The C++ side validates the incoming RAW frames - direct buffers, capacity for
  `width * height * 2` bytes - and returns success. The align, merge, demosaic,
  white balance, colour matrix, tone map and JPEG encode stages are still a TODO
  in `processor.cpp`, so nothing calls the module from a capture mode yet.
