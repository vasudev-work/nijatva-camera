#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <cstdint>
#include <cmath>
#include <vector>

#define LOG_TAG "GcamDevelop"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

constexpr int kLutSize = 4096;

// Maps a linear value in [0,1] to sRGB-ish gamma, via a lookup table so the
// per-pixel cost is a single array read instead of a pow() call.
struct GammaLut {
    uint8_t table[kLutSize + 1];
    GammaLut() {
        for (int i = 0; i <= kLutSize; ++i) {
            double v = static_cast<double>(i) / kLutSize;
            double g = std::pow(v, 1.0 / 2.2) * 255.0;
            table[i] = static_cast<uint8_t>(std::clamp(g + 0.5, 0.0, 255.0));
        }
    }
    uint8_t apply(float v) const {
        int idx = static_cast<int>(std::clamp(v, 0.0f, 1.0f) * kLutSize + 0.5f);
        return table[idx];
    }
};

const GammaLut kGamma;

}  // namespace

/**
 * Develops one RAW_SENSOR frame into packed ARGB_8888 pixels.
 *
 * Assumes an RGGB Bayer pattern (the most common one). Check
 * SENSOR_INFO_COLOR_FILTER_ARRANGEMENT for the phone before relying on it.
 *
 * rowStrideBytes comes from Image.planes[0].rowStride; it can be larger than width*2.
 * wbGains is {r, g, b} with g normalised to 1.0 (see RawDeveloper.kt).
 */
extern "C" JNIEXPORT jintArray JNICALL
Java_com_nijatva_camera_processor_GcamEngine_nativeDevelopRaw(
        JNIEnv *env,
        jobject /* thiz */,
        jobject buffer,
        jint width,
        jint height,
        jint rowStrideBytes,
        jfloat blackLevel,
        jfloat whiteLevel,
        jfloatArray wbGains) {

    if (buffer == nullptr || wbGains == nullptr) return nullptr;
    if (width < 4 || height < 4 || rowStrideBytes < width * 2 || (rowStrideBytes & 1)) {
        LOGE("Bad RAW geometry %dx%d stride %d", width, height, rowStrideBytes);
        return nullptr;
    }

    auto *base = static_cast<const uint8_t *>(env->GetDirectBufferAddress(buffer));
    jlong capacity = env->GetDirectBufferCapacity(buffer);
    if (base == nullptr || capacity < static_cast<jlong>(rowStrideBytes) * height) {
        LOGE("RAW buffer is not direct or too small");
        return nullptr;
    }

    jfloat *wb = env->GetFloatArrayElements(wbGains, nullptr);
    const float wbR = wb[0], wbG = wb[1], wbB = wb[2];
    env->ReleaseFloatArrayElements(wbGains, wb, JNI_ABORT);

    const int strideU16 = rowStrideBytes / 2;
    const float range = std::max(1.0f, whiteLevel - blackLevel);

    // Normalised linear sample, black level removed, clamped to the image edges.
    auto sample = [&](int x, int y) -> float {
        x = std::clamp(x, 0, width - 1);
        y = std::clamp(y, 0, height - 1);
        auto raw = reinterpret_cast<const uint16_t *>(base)[y * strideU16 + x];
        return std::max(0.0f, (static_cast<float>(raw) - blackLevel) / range);
    };

    std::vector<jint> out(static_cast<size_t>(width) * height);

    for (int y = 0; y < height; ++y) {
        const bool evenRow = (y & 1) == 0;
        for (int x = 0; x < width; ++x) {
            const bool evenCol = (x & 1) == 0;
            float r, g, b;

            if (evenRow && evenCol) {            // R site
                r = sample(x, y);
                g = (sample(x - 1, y) + sample(x + 1, y) + sample(x, y - 1) + sample(x, y + 1)) * 0.25f;
                b = (sample(x - 1, y - 1) + sample(x + 1, y - 1) +
                     sample(x - 1, y + 1) + sample(x + 1, y + 1)) * 0.25f;
            } else if (!evenRow && !evenCol) {   // B site
                b = sample(x, y);
                g = (sample(x - 1, y) + sample(x + 1, y) + sample(x, y - 1) + sample(x, y + 1)) * 0.25f;
                r = (sample(x - 1, y - 1) + sample(x + 1, y - 1) +
                     sample(x - 1, y + 1) + sample(x + 1, y + 1)) * 0.25f;
            } else if (evenRow) {                // G site on an R row: R left/right, B above/below
                g = sample(x, y);
                r = (sample(x - 1, y) + sample(x + 1, y)) * 0.5f;
                b = (sample(x, y - 1) + sample(x, y + 1)) * 0.5f;
            } else {                             // G site on a B row: B left/right, R above/below
                g = sample(x, y);
                b = (sample(x - 1, y) + sample(x + 1, y)) * 0.5f;
                r = (sample(x, y - 1) + sample(x, y + 1)) * 0.5f;
            }

            r = std::min(1.0f, r * wbR);
            g = std::min(1.0f, g * wbG);
            b = std::min(1.0f, b * wbB);

            const uint32_t pr = kGamma.apply(r);
            const uint32_t pg = kGamma.apply(g);
            const uint32_t pb = kGamma.apply(b);
            out[static_cast<size_t>(y) * width + x] =
                    static_cast<jint>(0xFF000000u | (pr << 16) | (pg << 8) | pb);
        }
    }

    jintArray result = env->NewIntArray(static_cast<jsize>(out.size()));
    if (result != nullptr) {
        env->SetIntArrayRegion(result, 0, static_cast<jsize>(out.size()), out.data());
    }
    return result;
}
