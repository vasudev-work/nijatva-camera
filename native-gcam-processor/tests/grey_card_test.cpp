// Grey-card check from GCAM-RAW-PIPELINE.md, step 2.
// A flat card with a colour cast goes through WB, the matrix (identity here) and the
// filmic curve. After WB it must come out neutral (R = G = B), and the value must match
// the analytic curve + sRGB encoding for a linear 0.18 grey.
#include "../src/main/cpp/pipeline_core.h"
#include <cmath>
#include <cstdint>
#include <vector>
#include <cstdio>

namespace {
float hableRaw(float x) {
    const float A = 0.15f, B = 0.5f, C = 0.1f, D = 0.2f, E = 0.02f, F = 0.3f;
    return (x * (A * x + C * B) + D * E) / (x * (A * x + B) + D * F);
}
float srgb(float v) { return v <= 0.0031308f ? 12.92f * v : 1.055f * std::pow(v, 1 / 2.4f) - 0.055f; }
}  // namespace

int main() {
    const int W = 64, H = 64;
    const float black = 64, white = 1023, range = white - black;
    // Raw channel levels of the card as the sensor sees it (normalised), warm cast.
    const float cardR = 0.12f, cardG = 0.18f, cardB = 0.10f;

    std::vector<uint16_t> raw(W * H);
    for (int y = 0; y < H; ++y)
        for (int x = 0; x < W; ++x) {
            const bool evenRow = (y & 1) == 0, evenCol = (x & 1) == 0;
            float v;
            if (evenRow && evenCol) v = cardR;          // R site
            else if (!evenRow && !evenCol) v = cardB;   // B site
            else v = cardG;                             // G sites
            raw[y * W + x] = static_cast<uint16_t>(black + v * range + 0.5f);
        }

    gcam::FrameInput f;
    f.raw = raw.data();
    f.meta.black = black;
    f.meta.white = white;
    f.meta.strideU16 = W;
    // Gains that neutralise the card: R and B scaled up to match G.
    f.meta.wbR = cardG / cardR;
    f.meta.wbG = 1.0f;
    f.meta.wbB = cardG / cardB;

    gcam::BurstStats st;
    if (!gcam::developBurst({f}, W, H, "/tmp/grey.tif", &st)) {
        std::printf("FAIL: developBurst\n");
        return 1;
    }

    // Read the centre pixel back from the TIFF.
    FILE* fp = std::fopen("/tmp/grey.tif", "rb");
    std::fseek(fp, 0, SEEK_END);
    const long size = std::ftell(fp);
    std::vector<uint8_t> data(size);
    std::fseek(fp, 0, SEEK_SET);
    if (std::fread(data.data(), 1, size, fp) != data.size()) { std::fclose(fp); return 1; }
    std::fclose(fp);
    const size_t off = data.size() - static_cast<size_t>(W) * H * 6;
    const size_t o = off + (static_cast<size_t>(H / 2) * W + W / 2) * 6;
    auto px = [&](size_t i) { return (data[o + 2 * i] | (data[o + 2 * i + 1] << 8)) / 65535.0f; };
    const float outR = px(0), outG = px(1), outB = px(2);

    // Analytic expectation for a linear 0.18 grey, including the curve's exposure
    // normalisation (gamma 1.12 on the curve input).
    const float gamma = 1.12f;
    const float x = std::pow((0.18f - 0.002f) / (1.0f - 0.002f), gamma);
    const float y0 = hableRaw(0.0f), y1 = hableRaw(1.0f);
    const float tone = std::fmin(1.0f, std::fmax(0.0f, (hableRaw(x) - y0) / (y1 - y0)));
    const float expected = srgb(tone);
    const float photographic = 0.46f;   // 18% grey as sRGB, the standard reference

    const float neutralErr = std::fmax(std::fabs(outR - outG), std::fabs(outG - outB));
    const float valueErr = std::fabs(outG - expected);
    std::printf("centre RGB (sRGB 0..1): %.4f %.4f %.4f\n", outR, outG, outB);
    std::printf("neutral error (max channel diff): %.5f\n", neutralErr);
    std::printf("analytic grey 0.18 -> sRGB %.4f, measured %.4f, diff %.5f\n",
                expected, outG, valueErr);
    std::printf("photographic 18%% grey target 0.460 sRGB, this curve gives %.3f\n", expected);

    const bool greyOk = std::fabs(outG - photographic) < 0.01f;
    if (!greyOk)
        std::printf("mid-grey is %.3f, off the 0.460 target by %.3f\n", outG, outG - photographic);

    const bool pass = neutralErr < 0.004f && valueErr < 0.004f && greyOk;
    std::printf("%s\n", pass ? "PASS" : "FAIL");
    return pass ? 0 : 1;
}
