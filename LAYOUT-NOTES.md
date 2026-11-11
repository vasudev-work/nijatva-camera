# Nijatva Camera — layout redesign (UI only)

Deliverable notes for the layout brief. Nothing in the camera, capture, stacking,
video, analysis or grading paths was changed.

## New files

| File | What it is |
|---|---|
| `app/src/main/java/com/nijatva/camera/ui/ModeStrip.kt` | Scrolling mode strip: amber bold selected mode, neighbours white at 55%, snapping to the nearest name after a swipe, fading edges, MORE at the end. Reports taps only; the activity owns mode changes. |
| `app/src/main/java/com/nijatva/camera/ui/ModesGrid.kt` | The MORE screen: full-screen overlay, gradient background, 3-column (4 on tablets) grid of icon + caption tiles, and the same mode strip repeated at the bottom with MORE highlighted. |
| `app/src/main/java/com/nijatva/camera/ui/ZoomPill.kt` | The zoom capsule: dark translucent pill, active step in amber. Only added to the screen when the device reports more than one zoom step. |
| `app/src/main/java/com/nijatva/camera/camera/Modes.kt` | Pure edition rule: which modes the strip and the grid offer for Full and Lite, plus the label and icon for each mode. |
| `app/src/test/java/com/nijatva/camera/camera/ModesTest.kt` | 7 JVM tests for that rule (Lite list, Full list, strip ⊆ grid, every mode labelled and drawn). |
| `app/src/main/res/drawable/ic_mode_*.xml` (8 files) | Mode glyphs drawn for this app as vectors: photo, video, night, pro, astro, hdr, panorama, raw. |
| `app/src/main/res/values-hi/strings.xml` | Hindi strings for every string added here. |

## Changed files

| File | Change |
|---|---|
| `app/src/main/java/com/nijatva/camera/MainActivity.kt` | Bottom chrome rebuilt: mode strip, zoom pill, 56dp thumbnail, 74dp shutter inside a 92dp outer ring, 56dp flip button, 96dp control row. MORE opens the grid. `selectMode()` now refuses a change during recording with "Stop recording first" and drives the strip and grid instead of the old text chips. Grid/HDR toggles moved to the right edge. Control row reparents to the right edge in landscape. Old `modeTab()`, `zoomPill()`, `updateZoomChips()` removed. |
| `app/src/main/java/com/nijatva/camera/camera/DeviceCapabilities.kt` | Read-only: reads `SCALER_AVAILABLE_MAX_DIGITAL_ZOOM` and exposes `supportsZoom`, so the zoom pill only appears where it can do something. No capture behaviour changed. |
| `app/src/main/res/values/strings.xml` | Mode labels, MORE/CLOSE, zoom labels, "Stop recording first", REC format. |

## Reference element → decision

| Reference element | Decision | Why |
|---|---|---|
| Flash toggle (top left) | Built | Existing flash control, restyled to sit in the 56dp top bar. |
| Hamburger (top right) | Built | Opens the existing settings sheet. |
| Scan icon (top centre) | Hidden | No matching feature. |
| Look / colour button (right, upper) | Hidden | `ColorGrade.Look` exists but has no picker UI yet. |
| Portrait button (right, lower) | Hidden | No portrait mode. |
| Zoom pill | Built, conditional | Shown only when `supportsZoom`; 1x / 2x from the existing crop zoom, no lens switching added. |
| Mode strip | Built | Full: PHOTO, VIDEO, NIGHT, PRO, ASTRO, MORE. Lite: PHOTO, NIGHT, PRO, MORE. |
| MORE grid: PRO, PANO, HDR, NIGHT, ASTRO | Built | All exist as `Mode` values. |
| MORE grid: HI-RES | Built as RAW | `Mode.RAW` is the closest real capability; no fake hi-res. |
| MORE grid: FILM, SLO-MO, TIME-LAPSE, DUAL-VIEW, TEXT SCANNER, TILT-SHIFT | Omitted | Not in the app; the device cannot do slo-mo (30 fps ceiling) or dual-view (one camera at a time). |
| Shutter | Restyled only | Same click path, same recording/astro ring colours. |
| Thumbnail | Restyled only | Same `openLastShot()`, rounded clip kept. |
| Camera flip | Restyled only | Same `engine.switchCamera()`. |
| Edit pencil (MORE) | Hidden | No mode reordering. |
| Green status dot | Not added | Android draws its own camera-in-use indicator. |

## Build and test output

```
:app:compileFullDebugKotlin      BUILD SUCCESSFUL
:app:testFullDebugUnitTest       BUILD SUCCESSFUL — 38 tests, 0 failures
                                 (7 of them new: ModesTest)
:app:assembleFullDebug           BUILD SUCCESSFUL
:app:assembleLiteDebug           BUILD SUCCESSFUL
```

No test failed, so there is no failure message to report.

## Not done, and why

- **Screenshots are missing.** This environment has no device or emulator, so
  portrait, MORE, landscape and tablet screenshots could not be captured. The two
  debug APKs are attached so the screens can be photographed on the phone.
- **Tablet settings side panel (rule 8) is not implemented.** `SettingsSheet` is a
  `Dialog`, so an inline side panel is a structural change to that class rather
  than a layout tweak. The MORE grid does use 4 columns from `sw600dp`.
- **Strings are only partly externalised.** Everything this layout adds is in
  `strings.xml` (English) and `values-hi/strings.xml` (Hindi). The pre-existing
  strings inside `MainActivity` and `SettingsSheets` are still hardcoded; moving
  them is a mechanical pass over ~150 strings that would have touched behaviour
  files this brief asked me not to change.
- **Peaking overlay alignment in landscape is unchanged.** The overlay is a
  full-screen `FIT_XY` image over a cover-cropped preview, so it does not follow
  the crop; that is pre-existing and fixing it is a capture-path change.

## Assumptions

- Device: realme RMX3998 (LEVEL_3, RAW). If it reports `maxDigitalZoom < 1.5` the
  zoom pill stays hidden.
- Editions: `BuildConfig.LITE` is the only switch; Lite gets exactly PHOTO, NIGHT
  and PRO in both the strip and the grid.
- Rotation: the manifest handles `orientation|screenSize|keyboardHidden`, so the
  activity is not recreated; the control row is reparented instead and the grid is
  rebuilt for the new column count.
