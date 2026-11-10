# Open-source camera options, checked

A list of "open source GCam alternatives" circulates widely, with several claims
that do not survive checking. This note records what each project actually is and
what, if anything, is worth taking from it.

## Verdict table

| Project | What it actually is | Licence | Useful here? |
|---|---|---|---|
| GCam Go / BSG / Arnova ports | Patched builds of Google's closed-source app | None - closed | No. Not open source in any part. |
| Open Camera | Mature open-source Camera2 app by Mark Harman | GPLv3 | As a feature reference only |
| GrapheneOS Camera | Modern Kotlin CameraX app with Zero Shutter Lag | MIT | Yes - read its ZSL wiring |
| Focal | CyanogenMod-era camera app, unmaintained since 2015 | GPLv3 | No. Historical interest only |
| LibRaw | C++ raw decode/demosaic library | LGPL / CDDL | No. DngCreator already writes DNG |
| OpenCV | General computer-vision library | Apache 2.0 | Optional, ~20 MB, for alignment only |
| CameraX vendor extensions | OEM HDR / night / bokeh / retouch via CameraX | Apache 2.0 | Only where the OEM exposes them |

## Corrections worth knowing

**GCam ports are not open source.** BSG and Arnova modify Google's closed APK.
There is no open-source framework being built on; the work is smali patching,
which is the same category as Morphe patches. Celso Azevedo's site is a
distribution hub, not a source repository.

**Open Camera's repository is not on that GitHub link.** It is Mark Harman's
project, hosted on SourceForge, and it is GPLv3. Its "HDR" is exposure bracketing
with a merge step, not HDR+.

**CameraX vendor extensions are conditional.** `CameraX` exposes HDR, night,
bokeh and face-retouch extensions only where the OEM implements them and publishes
them to third-party apps. Many ColorOS and realme builds do not. Code that assumes
they exist will silently fall back to a plain capture.

## The one thing worth taking: Zero Shutter Lag

GrapheneOS Camera's capture path is worth reading for one reason. It sets
`ImageCapture.CAPTURE_MODE_ZERO_SHUTTER_LAG` and gates the setting on
`cameraInfo.isZslSupported`.

CameraX implements that mode with a ring buffer holding the three most recent
frames; on shutter press it reprocesses the buffered frame whose timestamp is
closest to the press. It requires `PRIVATE` reprocessing support and silently
falls back to `CAPTURE_MODE_MINIMIZE_LATENCY` where that is missing. It cannot be
combined with flash on or auto, and not with camera extensions.

This matters for RMX3998 because the camera advertises `PRIVATE_REPROCESSING`, so
ZSL is viable on that hardware. It also explains why the stock camera feels
faster than any third-party app: the stock app is using exactly this buffering,
and an app that does not use it is always a frame or two behind the button press.

## Why Nijatva does not just become a CameraX app

CameraX would give ZSL, lifecycle handling and simpler code, but it hides the
things this app exists for: RAW output, YUV streams for stacking, and per-frame
manual sensor control. CameraX exposes manual control partially and reprocessing
not at all.

The pragmatic split is a second prototype: a small CameraX app that does ZSL and
nothing else, to confirm the mode works on this phone and to measure the latency
gain. If it works, the ring-buffer idea can be rebuilt in Camera2 inside Nijatva,
or Nijatva's photo path can delegate to CameraX while the computational modes stay
on Camera2.

## Licensing note

GrapheneOS Camera is MIT: reusing code requires keeping its copyright notice and
permission text. Open Camera is GPLv3, so nothing can be copied from it into a
permissively licensed app without making the whole app GPLv3. Reading either for
ideas is unrestricted; copying text is not.
