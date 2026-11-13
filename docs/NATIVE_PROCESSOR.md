# Native RAW processor module

`native-gcam-processor` is a library module that holds the RAW burst work that
does not belong on the Java heap.

## What is in it

| File | Role |
|---|---|
| `GcamEngine.kt` | The JNI bridge. Loads `libgcamprocessor` and hands an array of direct `ByteBuffer`s to C++. |
| `RawCaptureSetup.kt` | Picks the largest `RAW_SENSOR` size and builds the `ImageReader`. |
| `RawCameraController.kt` | Opens the first back camera that reports RAW, takes one frame with noise reduction and edge enhancement off, and writes a DNG. |
| `DngSaver.kt` | `DngCreator` wrapper; needs the same `CameraCharacteristics` and `TotalCaptureResult` the frame came from. |
| `cpp/processor.cpp` | The JNI entry point `nativeProcessRawFrames`. |
| `cpp/image_utils.h` | Shared `RawFrameInfo`. |
| `cpp/CMakeLists.txt` | Builds `libgcamprocessor.so`. |

## State

The Kotlin side is complete for a single-frame RAW capture to DNG. The C++ side
is a stub: it checks that every buffer is a direct buffer with room for
`width * height * 2` bytes, logs the frame count, and returns true. No pixels are
processed and no file is written yet.

The intended pipeline, in the order the stages have to run:

1. Align the frames (the Kotlin `Align` pyramid is the reference behaviour).
2. Merge with ghost rejection, in linear space.
3. Demosaic the merged Bayer frame.
4. White balance from the capture result's gains, then the device colour matrix.
5. Tone map and encode a JPEG.

## Build

Needs an NDK and CMake 3.22.1; both are declared in the module's `build.gradle`
(`ndkVersion`, `externalNativeBuild.cmake.version`). Only arm64-v8a and x86_64
are built, since the working set of a 12 MP float pipeline does not fit a 32-bit
address space.
