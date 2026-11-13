// Synthetic burst test: no phone needed.
// Checks (1) alignment recovers known shifts and (2) the merged output carries less
// noise than a single frame. Noise is measured against the same pipeline run on
// noise-free frames, so demosaic bias on the texture cancels out.
#include "../src/main/cpp/pipeline_core.h"

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <random>

namespace {

std::vector<double> readTiffRgb(const char* path, int w, int h) {
    FILE* fp = std::fopen(path, "rb");
    if (!fp) return {};
    std::fseek(fp, 0, SEEK_END);
    const long size = std::ftell(fp);
    std::vector<uint8_t> data(size);
    std::fseek(fp, 0, SEEK_SET);
    const size_t got = std::fread(data.data(), 1, size, fp);
    std::fclose(fp);
    if (got != data.size()) return {};
    const size_t off = data.size() - static_cast<size_t>(w) * h * 3 * 2;
    std::vector<double> px(static_cast<size_t>(w) * h * 3);
    for (size_t i = 0; i < px.size(); ++i)
        px[i] = (data[off + 2 * i] | (data[off + 2 * i + 1] << 8)) / 65535.0;
    return px;
}

}  // namespace

int main() {
    const int W = 256, H = 192, margin = 16;
    const float black = 64, white = 1023, range = white - black;
    std::vector<float> scene(W * H);
    for (int y = 0; y < H; ++y)
        for (int x = 0; x < W; ++x) {
            float s = 0.5f + 0.25f * std::sin(x * 0.09f) * std::cos(y * 0.07f)
                    + 0.15f * std::sin((x + y) * 0.21f)
                    + 0.10f * ((((x * 73 + y * 151) % 97) / 97.0f) - 0.5f);
            scene[y * W + x] = std::fmin(1.0f, std::fmax(0.0f, s));
        }
    auto sceneAt = [&](int x, int y) {
        x = std::max(0, std::min(W - 1, x));
        y = std::max(0, std::min(H - 1, y));
        return scene[y * W + x];
    };

    const int shX[] = {0, 4, -6, 2, -2, 6}, shY[] = {0, -2, 2, 6, -4, 0};
    const int N = 6;
    const float noiseSigma = 3.0f;
    std::mt19937 rng(42);
    std::normal_distribution<float> gauss(0.0f, noiseSigma);

    auto makeFrames = [&](bool withNoise, std::vector<std::vector<uint16_t>>& bufs) {
        bufs.assign(N, std::vector<uint16_t>(W * H));
        for (int z = 0; z < N; ++z)
            for (int y = 0; y < H; ++y)
                for (int x = 0; x < W; ++x) {
                    float v = black + 800.0f * sceneAt(x - shX[z], y - shY[z]);
                    if (withNoise) v += gauss(rng);
                    v = std::fmin(white, std::fmax(0.0f, v));
                    bufs[z][y * W + x] = static_cast<uint16_t>(v + 0.5f);
                }
        std::vector<gcam::FrameInput> fs(N);
        for (int z = 0; z < N; ++z) {
            fs[z].raw = bufs[z].data();
            fs[z].meta.black = black;
            fs[z].meta.white = white;
            fs[z].meta.noiseA = 0.0f;
            fs[z].meta.noiseB = noiseSigma * noiseSigma;
            fs[z].meta.strideU16 = W;
        }
        return fs;
    };

    // Noise-free reference run gives the pipeline's own output for a perfect burst.
    std::vector<std::vector<uint16_t>> cleanBufs, noisyBufs;
    auto cleanFrames = makeFrames(false, cleanBufs);
    gcam::BurstStats cleanStats;
    if (!gcam::developBurst(cleanFrames, W, H, "/tmp/clean.tif", &cleanStats)) {
        std::printf("FAIL: clean burst\n");
        return 1;
    }
    const std::vector<double> cleanOut = readTiffRgb("/tmp/clean.tif", W, H);

    auto noisyFrames = makeFrames(true, noisyBufs);
    gcam::BurstStats st;
    if (!gcam::developBurst(noisyFrames, W, H, "/tmp/burst.tif", &st)) {
        std::printf("FAIL: noisy burst\n");
        return 1;
    }
    const int ref = st.referenceIndex;
    std::printf("reference frame: %d\n", ref);

    bool shiftsOk = true;
    for (int z = 0; z < N; ++z) {
        const int expX = shX[z] - shX[ref], expY = shY[z] - shY[ref];
        const bool ok = st.shiftX[z] == expX && st.shiftY[z] == expY;
        shiftsOk &= ok;
        std::printf("frame %d: recovered (%d,%d) expected (%d,%d) %s\n", z,
                    st.shiftX[z], st.shiftY[z], expX, expY, ok ? "OK" : "MISMATCH");
    }
    std::printf("timings ms: align %.1f merge %.1f demosaic %.1f write %.1f\n",
                st.alignMs, st.mergeMs, st.demosaicMs, st.writeMs);

    // Single noisy frame through the same path, same reference frame.
    std::vector<gcam::FrameInput> single{noisyFrames[ref]};
    gcam::BurstStats sst;
    gcam::developBurst(single, W, H, "/tmp/single.tif", &sst);
    const std::vector<double> singleOut = readTiffRgb("/tmp/single.tif", W, H);
    const std::vector<double> burstOut = readTiffRgb("/tmp/burst.tif", W, H);

    auto rmse = [&](const std::vector<double>& a, const std::vector<double>& b) {
        double se = 0;
        long cnt = 0;
        for (int y = margin; y < H - margin; ++y)
            for (int x = margin; x < W - margin; ++x)
                for (int c = 0; c < 3; ++c) {
                    const size_t i = (static_cast<size_t>(y) * W + x) * 3 + c;
                    const double d = a[i] - b[i];
                    se += d * d;
                    ++cnt;
                }
        return std::sqrt(se / cnt) * range;  // DN units
    };

    const double rSingle = rmse(singleOut, cleanOut);
    const double rBurst = rmse(burstOut, cleanOut);
    std::printf("noise RMSE single: %.2f DN, burst of %d: %.2f DN, ratio %.2f (ideal ~%.2f)\n",
                rSingle, N, rBurst, rBurst / rSingle, 1.0 / std::sqrt(N));

    const bool noiseOk = rBurst < 0.75 * rSingle;
    const bool pass = shiftsOk && noiseOk;
    std::printf("%s\n", pass ? "PASS" : "FAIL");
    return pass ? 0 : 1;
}
