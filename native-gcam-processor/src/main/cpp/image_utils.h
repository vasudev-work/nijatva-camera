#pragma once
#include <cstdint>

// Shared constants and helpers for the RAW pipeline.
// Demosaic, alignment and tone-mapping stages will be added here.

struct RawFrameInfo {
    const uint16_t* data;  // 16-bit Bayer RAW (RAW_SENSOR)
    int width;
    int height;
};
