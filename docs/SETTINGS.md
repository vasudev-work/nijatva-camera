# Settings: what each one does, and what is missing

The settings sheet follows the reference layout: a bottom sheet titled "Photo
settings" with General and Pro tabs, an outlined "More settings" button, and an
Advanced page behind it. Rows are a title, a subtitle and one control.

## General

| Setting | Values | What it actually changes |
|---|---|---|
| Flash | Off / Auto / On | `CONTROL_AE_MODE` - `ON_AUTO_FLASH` for Auto, `ON_ALWAYS_FLASH` for On |
| HDR+ | off / HDR+ / enhanced | off = single capture, HDR+ = 3-exposure fusion, enhanced = same with a longer hold |
| Timer | Off / 3 s / 10 s | Countdown before the shutter fires |
| Ratio | Full image (4:3) / Wide (16:9) | Which JPEG size the session is built for; 4:3 uses the whole sensor |
| Grid type | None / 3x3 / 4x4 / Golden | Composition lines on the viewfinder |
| Framing hints | On / Off | Level indicator, driven by the gyroscope roll angle |

## Pro

| Setting | What it does |
|---|---|
| Lens | Auto or manual lens choice |
| Quick access controls | Tap the viewfinder to move the AF/AE metering point |
| RAW + JPEG | In RAW mode, also write a JPEG from the same session |
| Save selfie as previewed | Mirrors front-camera output, matching what the preview shows |

## Advanced

| Setting | What it does |
|---|---|
| JPEG quality | 85 to 100, applied to merged, stitched and DNG-companion output |
| RAW bit depth | 10-bit packs four pixels into five bytes (half the DNG size) or 16-bit |
| Optical stabilization (OIS) | `LENS_OPTICAL_STABILIZATION_MODE` on or off, only offered when the lens supports it |
| Vibration on shutter | Haptic tick on capture |
| RAW/JPEG control | Same as the Pro tab switch, exposed here as the reference does |
| Timer light | Holds the torch on during the countdown |
| Rich colour in photos | Tags the output Display P3 instead of sRGB |
| Framing hints | Level indicator while framing |

Volume up or down also fires the shutter, as on every camera app.

## Deliberately absent

The reference app's Advanced page has switches this app does not implement. They
are listed here rather than shown as toggles that quietly do nothing:

| Reference setting | Why it is absent |
|---|---|
| Activate HDRnet | Google's neural HDR replacement; the network and the code are not ours to ship |
| Top Shot | Requires buffering frames and scoring them for "best moment"; needs a scorer we do not have |
| Social media depth features | Needs a depth stream; this camera exposes none |
| Time Lapse for Astrophotography | Needs long-exposure stacking over minutes plus a timer-lapse encoder |
| Ultra HDR | Needs a gain map written alongside the JPEG (`Gainmap`, API 34); not implemented |
| Show dirty lens warning | Would need a flat-field estimate to detect smearing; guessing would be worse than silence |
| Camera sounds | This app plays no shutter sound, so there is nothing to switch |
| Save location | Would need a location permission the app does not request |
| Store videos efficiently (HEVC) | There is no video pipeline yet |

## Where the state lives

`settings/Settings.kt` holds the data class and the SharedPreferences store.
Enums are persisted by name, so reordering an enum cannot turn a saved "Auto"
into something else on the next launch.
