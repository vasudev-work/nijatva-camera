# Reading the BSG MGC config collection

Notes on `WonITKorea/BSG_MGC_Configs`, and what is - and is not - in it.

## What the repository actually contains

50 files. Most are `SharedPreferences` dumps from Google Camera 8.1.x (BSG MGC
builds): a flat `<map>` of 150 to 170 `<string name="...">` entries each. They are
grouped by phone: LG, Nokia, OnePlus, Realme, Redmi, Samsung, Sony, Xiaomi, plus a
`Universal Configs` folder. The README says most were archived from a Telegram
config channel.

Three kinds of key appear:

| Kind | Example keys | Portable to another phone? |
|---|---|---|
| GCam UI state | `pref_camera_picturesize_back_key`, `pref_volume_key_action` | Only inside GCam |
| GCam feature switches | `pref_camera_raw_output_key`, `pref_astro_key`, `lib_curve_key` | Only inside GCam |
| libpatcher tuning | `lib_luma_l1..l4`, `lib_chroma_*`, `lib_spat`, `lib_sharpness`, `lib_contrast` | No - arbitrary units for GCam's pipeline |

## Realme coverage

Two phones: Realme 6 Pro and Realme 8. There is no RMX3998 entry, and no C-series
or Narzo-series config anywhere in the repository.

## The noise model is empty

Every config carries the libpatcher noise-model keys -
`ptchr_noisemodel_key`, `ptchr_noisemodelunblock_key`, `ptchr_ndenoisea1..a4`,
`ptchr_ndenoiseb1..b4`, `ptchr_ndenoisec1..c3`, `ptchr_lutnoise1/2`,
`ptchr_lumanoise_key`, `ptchr_noisesuppression_key`. Across all of them, the value
is `OFF`.

That matters, because a noise model is the one thing in a config that is real
calibration rather than taste. It encodes how much noise a sensor produces at a
given ISO, which is what lets a pipeline decide "this deviation is noise, average
it away" versus "this deviation is detail, keep it". Nobody in this repository
filled it in.

## Verdict for this project

Nothing here is usable for the Nijatva app, and nothing here is usable for an
RMX3998 GCam setup either:

- The tuning values are GCam-internal units, tuned by ear on other sensors.
  Copying `lib_spat` or `lib_sharpness` across phones produces colour casts and
  waxy detail, which is exactly why every device has its own file.
- There is no config for this device.
- The calibration data that would transfer - black level, white level, noise
  model, AWB gains - is either absent or switched off.

## What a real calibration looks like

Two of these values the phone reports directly, and the app now shows them in the
Info dialog:

- `SENSOR_BLACK_LEVEL_PATTERN` - the offset a sensor records with no light. Every
  raw value sits on top of it; ignore it and shadow maths drifts.
- `SENSOR_INFO_WHITE_LEVEL` - the saturation point.

The rest has to be measured on the device:

1. **Noise model.** Cover the lens, shoot 16 frames at each of several ISOs, take
   the per-pixel standard deviation in a central crop. Plot variance against
   signal and fit `variance = a * signal + b`. That pair is the whole noise model.
2. **Fixed-pattern noise.** Average the same dark frames; anything left is
   spatial noise that can be subtracted rather than averaged.
3. **Lens shading.** Shoot a plain white wall, divide by the mean.

Those numbers belong to this sensor on this phone. They cannot be downloaded, and
that is the point.

## If you still want the GCam route

Use `Universal Configs/` from that repository, expect to tune `lib_luma_l1..l4`,
`lib_spat` and `lib_sharpness` by eye for your sensor, and confirm first that the
Camera2 probe shows LEVEL_3 with RAW - it does on RMX3998, so a port is viable.
That path and this app are alternatives, not steps in one plan.
