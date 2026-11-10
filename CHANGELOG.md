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
