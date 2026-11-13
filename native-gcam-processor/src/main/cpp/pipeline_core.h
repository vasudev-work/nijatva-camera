#pragma once
#include <cstdint>
#include <string>
#include <vector>

namespace gcam {

struct FrameMeta {
    float iso = 100.f;
    float exposureNs = 0.f;
    float wbR = 1.f, wbG = 1.f, wbB = 1.f;   // normalised so wbG == 1
    float black = 0.f, white = 1023.f;        // raw DN
    float noiseA = 0.f, noiseB = 9.f;         // noise model: var(DN) = A * DN + B
    int strideU16 = 0;                        // row stride in uint16 elements
    // COLOR_CORRECTION_TRANSFORM as a row-major 3x3, camera RGB -> linear sRGB.
    float ccm[9] = {1, 0, 0, 0, 1, 0, 0, 0, 1};
};

struct FrameInput {
    const uint16_t* raw = nullptr;            // RGGB Bayer, row-major, 16-bit container
    FrameMeta meta;
};

struct BurstStats {
    int referenceIndex = 0;
    std::vector<int> shiftX, shiftY;          // full-res shift of each frame vs reference
    std::vector<float> exposureRatio;         // gain applied to each frame before merge
    double alignMs = 0, mergeMs = 0, demosaicMs = 0, writeMs = 0;
};

// Runs stages 0-4 (+ bilinear demosaic and WB) and writes a 16-bit linear RGB TIFF.
// Returns false on invalid input or I/O failure.
bool developBurst(const std::vector<FrameInput>& frames, int width, int height,
                  const std::string& tiffPath, BurstStats* stats);

}  // namespace gcam
