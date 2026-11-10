# Panorama and the guidance overlay

## What the mode does

Switch to **Sphere** and the app captures frames automatically as you turn. The
gyroscope decides when: every 8 degrees of yaw, if the phone is roughly level, one
frame is taken. The shutter button adds a frame by hand, and the right-hand button
finishes and stitches.

The overlay is a field of target marks that fill in as the sweep progresses, with
a centre frame showing the region each new frame will cover. It is a flat grid,
not a distorted sphere: what the user needs to know is "turn this far, keep it
level", and a grid communicates that faster than a fisheye projection.

## How the stitch works

1. Each frame is filed with the yaw the gyroscope reported when it was grabbed.
2. Focal length in pixels comes from `LENS_INFO_AVAILABLE_FOCAL_LENGTHS` and
   `SENSOR_INFO_PHYSICAL_SIZE`: `f_px = (f_mm / sensor_width_mm) * image_width_px`.
3. Horizontal position is `x = f_px * yaw`. That is a cylindrical placement:
   straight lines stay straight across the seam, which a rectilinear placement
   stops doing past about 40 degrees of sweep.
4. Frames are composited in order, each weighted by distance from its own edge,
   so the seam is a gradient rather than a line.

Sequential compositing is what keeps this inside a phone's memory: one output
buffer, not one accumulator per frame. A six-megapixel panorama costs about
35 MB, where per-channel float accumulators would cost roughly 150 MB.

## What is not implemented

Be clear about this before trusting the output:

- **No vertical stitching.** Pitch is ignored, so a sweep that drifts up or down
  shows a step at the seam. The gyro already reports pitch; wiring it in means
  offsetting `y` by `f_px * tan(pitch_delta)` and growing the output height.
- **No roll correction.** Holding the phone tilted rotates each frame against the
  last one; frame-to-frame alignment would fix it.
- **No exposure equalisation.** Frames taken while panning past a window come out
  brighter or darker, and the feather blend cannot hide a brightness step. The
  fix is a mean match over the overlap region before compositing.
- **Not a true 360 sphere.** This produces a wide horizontal panorama. A real
  Photo Sphere needs rows, spherical projection and a viewer.
- **No seam finding.** Placement trusts the gyro entirely; image alignment is
  available in `Align` but is not applied here.

## Why not use Google's liblightcycle.so

It was analysed in `LIGHTCYCLE_SO.md`. It is proprietary, its 50-method JNI
contract is undocumented, and it cannot be redistributed. The architecture above
is the same idea built from scratch.
