# Capture pipeline

## Photo and Pro

```
CameraDevice --TEMPLATE_STILL_CAPTURE--> JPEG ImageReader --> MediaStore
```

The camera's own JPEG bytes are written unchanged. No decode, no re-encode: the
OEM encoder is already tuned for that sensor, and re-compressing at quality 98
would cost detail for nothing.

## Night

```
ImageReader (YUV_420_888, <= 8 MP, 8 frames)
   |
   +-- frame 0 -> reference
   +-- frame N -> Align.estimate(reference, frame) -> (dx, dy)
                     |
                     +--> Stacker.accumulate (clamped shift)
   |
Stacker.toBitmap(gain, gamma, saturation) -> JPEG
```

Why this works: sensor noise is random per frame, the scene is not. Averaging N
registered frames divides random noise by sqrt(N) while the signal stays put.
Eight frames is roughly a 3x noise improvement - about two stops of usable
shadow detail, at the cost of a two-second hold-still.

Sizing: the YUV reader is capped at 8 MP. At 12 MP a single YUV frame is ~18 MB
and eight of them plus accumulators will hit the heap ceiling on a 6 GB phone.

Exposure choice: `min(maxExposure / 8, 40 ms)` at ISO up to 3200. Deliberately
conservative - a longer exposure raises the odds the whole stack is ruined by
hand shake, and the stacker cannot fix that.

## HDR

```
Step 1: exposure x4,  skip 2 frames, keep 1
Step 2: exposure x1,  skip 2 frames, keep 1
Step 3: exposure /4,  skip 2 frames, keep 1
   |
   +--> YuvFrame.toBitmap() x3 (capped at 4 MP)
   +--> ExposureFusion.fuse() -> JPEG
```

Skipped frames matter: after `setRepeatingRequest` the first frames still carry
the previous exposure while 3A settles. Keeping them produces a stack with two
identical exposures and one wrong one, which fuses into a muddy result.

Weights per pixel: contrast (Laplacian magnitude), saturation (RGB deviation from
mean), and well-exposedness (Gaussian around 0.5 per channel). A bright sky
pixel scores low on well-exposedness in the long exposure, so the short exposure
wins there; a shadow pixel scores low in the short exposure, so the long one
wins. That is the whole trick.

This is not HDR+. There is no radiance map and no motion compensation between the
three frames, so a moving subject will ghost. On a still scene it behaves well.

## Threading

- One `HandlerThread` owns every Camera2 call: open, session, requests, image
  callbacks.
- Fusion and stacking run on a single-thread executor so a two-second merge does
  not stall the preview.
- `busy` is volatile and gates capture, mode switch and settings changes, because
  changing a repeating request mid-bracket silently corrupts the stack.

## Mode to surface map

| Mode | Surfaces | Session rebuild on switch |
|---|---|---|
| Photo, Pro | preview + JPEG | yes |
| Night | preview + YUV (<= 8 MP) | yes |
| HDR | preview + YUV (<= 4 MP) | yes |
| RAW | preview + RAW_SENSOR | yes |

Every mode rebuilds the session. It costs about 200 ms and removes a whole class
of "which reader is alive right now" bugs.

## Next: ghost rejection

For each pixel, accumulate sum and sum-of-squares across the stack, then:

```
mean  = sum / n
sigma = sqrt(sq/n - mean^2)
out   = sigma < threshold ? mean : reference
```

That keeps the noise benefit in static regions and falls back to the sharp
reference frame where something moved. It needs one extra float array per plane,
which is why it is not in v1 on an 8 MP capture.
