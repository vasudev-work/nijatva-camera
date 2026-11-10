# Device profile: realme RMX3998

Read from a Camera2 API probe dump on the target phone: realme RMX3998,
Android 16 (SDK 36).

## What the phone actually exposes

| | Back (ID 0) | Front (ID 1) |
|---|---|---|
| Hardware level | LEVEL_3 | FULL |
| RAW | yes | yes |
| Manual sensor / post-processing | yes / yes | yes / yes |
| Burst capture | yes | yes |
| YUV / private reprocessing | yes / yes | no |
| Auto focus | yes, all modes except EDOF | no, fixed focus |
| Largest YUV / preview | 4064 x 3048 (12.4 MP) | 3264 x 2448 |
| Largest JPEG | 3840 x 2160 (16:9), 3264 x 2448 (4:3) | 3264 x 2448 |
| FPS ranges | up to 30 | up to 30 |
| Camera IDs exposed | 0 and 1 only | |

## What that means for this app

Good news first: LEVEL_3 with RAW and manual sensor control is the best case for
third-party capture. Night stacking, exposure fusion and DNG all work here, and
the fusion modes are not fighting the HAL for control.

Three limits are worth knowing before blaming the code:

1. **JPEG is capped at about 8 MP for third-party apps.** The YUV stream goes to
   4064 x 3048, but the largest JPEG the app is offered is 3840 x 2160. The stock
   camera can write larger files because it uses vendor paths a third-party app
   never sees. No app-side change recovers that.
2. **30 fps is the ceiling.** Every advertised AE target FPS range tops out at
   [30, 30]. There is no 60 fps range to request, so 4K60 or 1080p60 is not
   reachable from this app - and it also means patching the stock camera to
   "unlock" 60 fps would have nothing to unlock on this hardware.
3. **Only two cameras are exposed.** IDs 0 and 1. If the phone has an ultrawide
   or macro lens, it is hidden from Camera2 and this app cannot see it.

The front camera reports FULL and RAW but has no autofocus modes at all, so any
request that sets an AF mode is invalid there. The engine now checks
`CONTROL_AF_AVAILABLE_MODES` before touching AF, which is why tapping the preview
on the selfie camera only moves the exposure metering.

## Screen and system

6.7 inch panel, 1080 x 2400 (20:9), 408 dpi. Status bar 43 dp, navigation bar
16 dp. Android 16 (API 36), ART 2.1.0, OpenGL ES 3.2.

Two consequences for the app:

- **Edge to edge is enforced.** On Android 15 and later a targetSdk 35 app is
  drawn behind the system bars whether it opts in or not. The UI therefore pads
  the top and bottom bars by the real window insets; without that the mode row
  and the shutter sit under the navigation bar on this phone.
- **The screen is 20:9, the sensor is 4:3.** A 4:3 preview letterboxes heavily on
  this panel. The app exposes a 4:3 / 16:9 capture toggle: 4:3 uses the whole
  sensor, 16:9 is a centre crop that fills more of the screen. Neither fills 20:9
  without cropping the frame, which is why every camera app letterboxes or crops
  here.

OpenGL ES 3.2 is available, which matters for the roadmap: the fusion and
stacking maths currently runs on the CPU in Kotlin. Moving it to a fragment
shader would cut a two second merge to well under a hundred milliseconds.

## Tuning applied for this device

| Setting | Value | Why |
|---|---|---|
| Night capture size | 2560 x 1920 (4.9 MP) | 8 frames at 8 MP peaks near 220 MB with accumulators; 4.9 MP keeps it near 110 MB |
| Night frames | 8 | ~2.8x noise reduction; more frames cost hold-still time without much gain |
| Night exposure cap | 40 ms | Longer raises the odds the whole stack is ruined by hand shake |
| Night ISO cap | 3200 | Above that, highlight clipping eats the benefit of stacking |
| HDR capture size | 2560 x 1920 | 3 bitmaps at 4.9 MP is ~59 MB, safe |
| JPEG / YUV selection | sensor aspect first, then pixels | Otherwise the 16:9 JPEG gets picked and crops the 4:3 sensor |

## Worth building next on this hardware

YUV_REPROCESSING and PRIVATE_REPROCESSING are both available, which enables the
pipeline a real camera app uses: keep a ring buffer of ZSL frames while the user
frames the shot, then reprocess the ones at the moment the shutter was pressed.
That removes shutter lag entirely and gives the stack frames from before the
press instead of after it. It is the single biggest remaining quality jump
available on this phone.
