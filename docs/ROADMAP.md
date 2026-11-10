# Roadmap: from a working camera to a computational one

The A-Z GCam feature list is a good inventory. This turns it into something
buildable on one phone, by one person, in stages. Every tier is judged against
what RMX3998 can actually do, not against what a Pixel can.

## The hardware ceiling first

Measured from the device itself, not from a spec sheet:

| | RMX3998 (realme 12x 5G) |
|---|---|
| Camera level | LEVEL_3, RAW, manual sensor, private + YUV reprocessing |
| Largest JPEG a third-party app gets | 3264 x 2448 (8 MP) |
| Largest YUV / preview stream | 4064 x 3048 (12.4 MP) |
| Frame rate | 30 fps ceiling, no 60 fps range offered |
| Cameras exposed to apps | 2 (main, front) |
| GPU | OpenGL ES 3.2 |

Three consequences that shape everything below:

1. **15-frame RAW bursts are not possible here.** RAW10 at 12.4 MP is ~15.5 MB
   a frame; fifteen of them is 232 MB before any processing, on a phone whose
   app heap is a fraction of that. Five to eight frames is the real budget.
2. **60 fps, ultrawide and macro are not reachable.** Not by this app, and not
   by patching the stock camera either - the HAL does not offer them.
3. **8 MP is the JPEG ceiling** for any third-party app. Resolution gains are
   not available; noise, dynamic range and colour are.

## Tier 1 - already in this repository

| Feature | State | Where |
|---|---|---|
| HDR+ style multi-frame fusion | 3-exposure bracket, Mertens weights | `process/ExposureFusion.kt` |
| Night stacking | 8 frames, registered, ghost-rejected | `camera/Stacker.kt` |
| Ghosting reduction | soft per-pixel weights around the mean | `camera/Stacker.kt` |
| RAW / DNG | 10-bit or 16-bit, `DngCreator` | `camera/CameraEngine.kt` |
| Manual ISO, shutter, focus, WB, EV | thermometer panel, 5 tools | `ui/ProPanel.kt` |
| Grid lines, horizon level | thirds / quarters / golden + gyro level | `ui/GridOverlay.kt` |
| Panorama | gyro-triggered, cylindrical, feathered | `process/PanoramaStitcher.kt` |
| Config-free operation | reads capabilities at runtime | `camera/DeviceCapabilities.kt` |

## Tier 2 - feasible next, in the order I would do them

| Feature | What it takes | Why it is worth it |
|---|---|---|
| **GPU fusion** | Move `Stacker` and `ExposureFusion` to a fragment shader (GLES 3.2 is present). Frames arrive as textures, merge in one pass. | A 5 MP merge is ~2 s on CPU today. On the GPU it is tens of milliseconds, which is the difference between "wait for it" and "instant", and it makes 12 MP stacking possible at all. |
| **Zero shutter lag** | CameraX `CAPTURE_MODE_ZERO_SHUTTER_LAG`, or a Camera2 ring buffer with `PRIVATE_REPROCESSING` (the device advertises it). | Removes the 200-400 ms between press and frame. Every stock camera has this; it is why they feel faster. |
| **Real alignment** | Replace translation-only SSD with a coarse-to-fine pyramid on luma, ±32 px at 1/32 scale. | Handles rotation and larger shake; today a tilted frame smears. |
| **Per-frame exposure match** | Mean-match each frame over the overlap before fusion. | Kills the brightness step at panorama seams and in HDR stacks. |
| **Astrophotography** | 16-30 stacked frames at 1-2 s each, dark-frame offset from `SENSOR_BLACK_LEVEL_PATTERN`. | The device has the control for it. Needs a tripod and patience, not new hardware. |

## Tier 3 - possible but expensive

| Feature | The catch |
|---|---|
| Portrait / semantic segmentation | Needs a TFLite model (Fast-SCNN or similar, 2-5 MB) plus alpha matting. Feasible at preview resolution; per-frame at 8 MP on a Dimensity 6100+ is roughly 200-400 ms, so it belongs after the capture, not during. |
| Super Res Zoom | Real multi-frame super-resolution needs sub-pixel alignment accuracy below 0.5 px and a known point-spread function. Doable in a limited 2x form; a general version is a research project. |
| Log video / EIS+OIS fusion | A separate video pipeline (`MediaCodec` + `MediaMuxer`). Nothing in this repository helps; it is a second app's worth of work. |
| Ultra HDR gain maps | Needs `Gainmap` (API 34) written beside the JPEG. Small feature, no user-visible win on a 8 MP sensor. |

## Tier 4 - not reachable, and no amount of code changes that

| Feature | Why |
|---|---|
| HDRnet, Real Tone, Unblur, Top Shot | Google's trained models and colour science. Not published, not licensable. |
| Audio zoom | Needs multiple microphones with per-channel capture and beamforming the HAL does not expose. |
| Dark current subtraction | Not what GCam does; it models black level and noise instead. A real dark-frame calibration would need a controlled capture and would drift with sensor temperature. |
| 60 fps / ultrawide / macro | Not exposed by this device's HAL. |

## What I would do next, concretely

1. GPU fusion (Tier 2, first row). Biggest measurable win on this hardware.
2. Zero shutter lag after that.
3. Then stop adding features and shoot a thousand photos, because a camera's
   quality is decided by its failures, not its feature list.

## On the "beat Apple and Samsung" framing

That is not a plan, it is a wish. Those pipelines are thousands of engineer-years
of per-sensor tuning on hardware nobody else can buy. What one developer can do -
and what this repository is doing - is take a specific phone whose stock camera
leaves hardware unused, and get more out of that hardware than the vendor did.
That is a real goal with a finish line.
