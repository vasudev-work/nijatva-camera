#include "pipeline_core.h"
#include "gcam_constants.h"

#include <algorithm>
#include <chrono>
#include <cmath>
#include <cstdio>
#include <limits>

namespace gcam {
namespace {

using Plane = std::vector<float>;
using Clock = std::chrono::steady_clock;

double msSince(Clock::time_point t) {
    return std::chrono::duration<double, std::milli>(Clock::now() - t).count();
}

// Normalised linear value of one Bayer site, black removed.
inline float rawNorm(const FrameInput& f, int x, int y) {
    const float range = std::max(1.0f, f.meta.white - f.meta.black);
    const float v = static_cast<float>(f.raw[static_cast<size_t>(y) * f.meta.strideU16 + x]);
    return std::max(0.0f, v - f.meta.black) / range;
}

// Half-resolution green: average of the two G sites in each 2x2 RGGB cell.
Plane greenHalf(const FrameInput& f, int gw, int gh) {
    Plane g(static_cast<size_t>(gw) * gh);
    for (int j = 0; j < gh; ++j)
        for (int i = 0; i < gw; ++i) {
            const int x0 = 2 * i, y0 = 2 * j;
            g[static_cast<size_t>(j) * gw + i] =
                0.5f * (rawNorm(f, x0 + 1, y0) + rawNorm(f, x0, y0 + 1));
        }
    return g;
}

struct Level {
    Plane p;
    int w = 0, h = 0;
};

std::vector<Level> buildPyramid(Plane base, int w, int h, int levels) {
    std::vector<Level> pyr(levels);
    pyr[0].p = std::move(base);
    pyr[0].w = w;
    pyr[0].h = h;
    for (int l = 1; l < levels; ++l) {
        const Level& s = pyr[l - 1];
        Level& d = pyr[l];
        d.w = s.w / 2;
        d.h = s.h / 2;
        d.p.resize(static_cast<size_t>(d.w) * d.h);
        for (int j = 0; j < d.h; ++j)
            for (int i = 0; i < d.w; ++i) {
                const size_t r0 = static_cast<size_t>(2 * j) * s.w + 2 * i;
                const size_t r1 = r0 + s.w;
                d.p[static_cast<size_t>(j) * d.w + i] =
                    0.25f * (s.p[r0] + s.p[r0 + 1] + s.p[r1] + s.p[r1 + 1]);
            }
    }
    return pyr;
}

struct Shift {
    int dx = 0, dy = 0;
};

// ref(x,y) is matched against alt(x+dx, y+dy). Only shifts with at least half overlap count.
Shift searchLevel(const Level& R, const Level& A, int cx, int cy, int radius, bool l1) {
    Shift best{cx, cy};
    double bestCost = std::numeric_limits<double>::max();
    for (int dy = cy - radius; dy <= cy + radius; ++dy)
        for (int dx = cx - radius; dx <= cx + radius; ++dx) {
            const int x0 = std::max(0, -dx), x1 = std::min(R.w, R.w - dx);
            const int y0 = std::max(0, -dy), y1 = std::min(R.h, R.h - dy);
            if (x1 - x0 < R.w / 2 || y1 - y0 < R.h / 2) continue;
            double cost = 0;
            long count = 0;
            for (int y = y0; y < y1; ++y)
                for (int x = x0; x < x1; ++x) {
                    const double d = R.p[static_cast<size_t>(y) * R.w + x] -
                                     A.p[static_cast<size_t>(y + dy) * A.w + (x + dx)];
                    cost += l1 ? std::fabs(d) : d * d;
                    ++count;
                }
            cost /= static_cast<double>(count);
            if (cost < bestCost) {
                bestCost = cost;
                best = {dx, dy};
            }
        }
    return best;
}

// Coarse-to-fine global translation. Returns the shift in half-resolution pixels.
Shift alignFrame(const std::vector<Level>& refPyr, const std::vector<Level>& altPyr) {
    const int L = static_cast<int>(refPyr.size());
    int cx = 0, cy = 0;
    for (int l = L - 1; l >= 0; --l) {
        if (l < L - 1) {
            cx *= 2;
            cy *= 2;
        }
        const int radius = (l == 0) ? kFineSearchRadius : kCoarseSearchRadius;
        const Shift s = searchLevel(refPyr[l], altPyr[l], cx, cy, radius, l == 0);
        cx = s.dx;
        cy = s.dy;
    }
    return {cx, cy};
}

// Gain that brings alt's green mean onto ref's, over the overlap, ignoring clipped and black pixels.
float exposureRatio(const Level& R, const Level& A, int dx, int dy) {
    const int x0 = std::max(0, -dx), x1 = std::min(R.w, R.w - dx);
    const int y0 = std::max(0, -dy), y1 = std::min(R.h, R.h - dy);
    double sr = 0, sa = 0;
    long n = 0;
    for (int y = y0; y < y1; ++y)
        for (int x = x0; x < x1; ++x) {
            const float r = R.p[static_cast<size_t>(y) * R.w + x];
            const float a = A.p[static_cast<size_t>(y + dy) * A.w + (x + dx)];
            if (r < kExposureMeanLow || r > kExposureMeanHigh) continue;
            if (a < kExposureMeanLow || a > kExposureMeanHigh) continue;
            sr += r;
            sa += a;
            ++n;
        }
    if (n == 0 || sa <= 0) return 1.0f;
    const float ratio = static_cast<float>(sr / sa);
    return std::clamp(ratio, kExposureRatioMin, kExposureRatioMax);
}

float medianSmall(float* v, int n) {
    for (int i = 1; i < n; ++i) {
        const float key = v[i];
        int j = i - 1;
        while (j >= 0 && v[j] > key) {
            v[j + 1] = v[j];
            --j;
        }
        v[j + 1] = key;
    }
    return v[n / 2];
}

// Stage 4, option A: per-pixel inverse-variance average with a median-based ghost guard.
// Output is a normalised Bayer mosaic in reference coordinates.
Plane mergeBurst(const std::vector<FrameInput>& frames, const std::vector<int>& sx,
                 const std::vector<int>& sy, const std::vector<float>& ratio, int ref,
                 int width, int height) {
    const int n = static_cast<int>(frames.size());
    Plane out(static_cast<size_t>(width) * height);
    float vals[kMaxFrames], vars[kMaxFrames], tmp[kMaxFrames];
    bool isRef[kMaxFrames];

    for (int y = 0; y < height; ++y)
        for (int x = 0; x < width; ++x) {
            int m = 0;
            for (int z = 0; z < n; ++z) {
                const int xs = x + sx[z], ys = y + sy[z];
                if (xs < 0 || ys < 0 || xs >= width || ys >= height) continue;
                const FrameInput& f = frames[z];
                const float vr = rawNorm(f, xs, ys);
                if (vr >= kClipLevel) continue;  // clipped: carries no information
                const float range = std::max(1.0f, f.meta.white - f.meta.black);
                const float dn = vr * range + f.meta.black;
                const float g = ratio[z];
                const float varDn = std::max(1.0f, f.meta.noiseA * dn + f.meta.noiseB);
                vals[m] = vr * g;
                vars[m] = varDn / (range * range) * g * g;
                isRef[m] = (z == ref);
                tmp[m] = vals[m];
                ++m;
            }

            if (m == 0) {  // every sample clipped: keep the reference's clipped value
                out[static_cast<size_t>(y) * width + x] = rawNorm(frames[ref], x, y);
                continue;
            }

            const float med = medianSmall(tmp, m);
            double sumW = 0, sumWV = 0;
            float refVal = med;
            bool haveRef = false;
            for (int k = 0; k < m; ++k) {
                if (isRef[k]) {
                    refVal = vals[k];
                    haveRef = true;
                }
                if (!isRef[k] && std::fabs(vals[k] - med) > kGhostSigma * std::sqrt(vars[k]))
                    continue;  // misaligned or moving content
                const double w = 1.0 / vars[k];
                sumW += w;
                sumWV += w * vals[k];
            }
            out[static_cast<size_t>(y) * width + x] =
                (sumW > 0) ? static_cast<float>(sumWV / sumW)
                           : (haveRef ? refVal : med);
        }
    return out;
}

// Stage 5 bilinear demosaic, stage 6 white balance, stage 7 colour matrix,
// stage 8b filmic curve, then sRGB encoding. Done per pixel on unclamped values, so
// highlights above 1.0 after WB survive into the matrix and the curve.
constexpr float kHableA = 0.15f, kHableB = 0.50f, kHableC = 0.10f;
constexpr float kHableD = 0.20f, kHableE = 0.02f, kHableF = 0.30f;

inline float hableRaw(float x) {
    return (x * (kHableA * x + kHableC * kHableB) + kHableD * kHableE) /
           (x * (kHableA * x + kHableB) + kHableD * kHableF);
}
const float kHableY0 = hableRaw(0.0f);  // equals E/F
const float kHableY1 = hableRaw(1.0f);

// Exposure normalisation for the tone curve.
//
// Hable's curve lifts the midtones: 18% scene-linear comes out at 0.22
// display-linear, which is 0.505 in sRGB - about half a stop brighter than a
// correctly exposed grey card (0.18 linear, 0.46 sRGB). A single gamma on the
// curve's input pulls the midtones back to where they belong and leaves white
// alone, because 1^gamma is still 1.
constexpr float kToneGamma = 1.12f;

// The curve is a pure function of one variable, so it is tabulated once: a pow()
// and a division per channel per pixel is far too much for a 12 MP frame.
const std::vector<float>& toneLut() {
    static const std::vector<float> lut = [] {
        std::vector<float> t(4097);
        for (int i = 0; i <= 4096; ++i) {
            const float xg = std::pow(i / 4096.0f, kToneGamma);
            t[i] = std::clamp((hableRaw(xg) - kHableY0) / (kHableY1 - kHableY0), 0.0f, 1.0f);
        }
        return t;
    }();
    return lut;
}

// Linear scene value -> display-linear value in [0,1], Hable shoulder normalised to
// white and mid-grey held at 18%.
float toneCurve(float L) {
    const float x = std::max(0.0f, L - kBlackPoint) / (kWhitePoint - kBlackPoint);
    if (x >= 1.0f) return 1.0f;
    const float p = x * 4096.0f;
    const int i = static_cast<int>(p);
    const float f = p - static_cast<float>(i);
    const std::vector<float>& t = toneLut();
    return t[i] + (t[i + 1] - t[i]) * f;
}

float srgbEncode(float v) {
    return v <= 0.0031308f ? 12.92f * v : 1.055f * std::pow(v, 1.0f / 2.4f) - 0.055f;
}

// sRGB encode as a 16-bit lookup table: index is the linear value scaled to 0..65535.
const std::vector<uint16_t>& srgb16Lut() {
    static const std::vector<uint16_t> lut = [] {
        std::vector<uint16_t> t(65536);
        for (int i = 0; i < 65536; ++i) {
            const float v = std::clamp(srgbEncode(i / 65535.0f), 0.0f, 1.0f);
            t[i] = static_cast<uint16_t>(v * 65535.0f + 0.5f);
        }
        return t;
    }();
    return lut;
}

// Row-normalise the matrix so a neutral input stays neutral, if its rows are off.
void normaliseCcm(const float in[9], float out[9]) {
    for (int r = 0; r < 3; ++r) {
        const float sum = in[3 * r] + in[3 * r + 1] + in[3 * r + 2];
        const bool fix = std::fabs(sum) > 1e-3f && std::fabs(sum - 1.0f) > 0.01f;
        for (int k = 0; k < 3; ++k)
            out[3 * r + k] = fix ? in[3 * r + k] / sum : in[3 * r + k];
    }
}

// Applies the matrix. Where a channel goes negative (out-of-gamut colour), pull the colour
// toward its luma instead of clipping, which is what avoids magenta shadows.
void applyCcm(const float m[9], float& r, float& g, float& b) {
    float c[3] = {m[0] * r + m[1] * g + m[2] * b,
                  m[3] * r + m[4] * g + m[5] * b,
                  m[6] * r + m[7] * g + m[8] * b};
    if (c[0] < 0 || c[1] < 0 || c[2] < 0) {
        const float y = 0.2126f * c[0] + 0.7152f * c[1] + 0.0722f * c[2];
        float t = 1.0f;
        for (int k = 0; k < 3; ++k)
            if (c[k] < 0 && y - c[k] > 0) t = std::min(t, y / (y - c[k]));
        t = std::max(t, 0.0f);
        for (int k = 0; k < 3; ++k) c[k] = y + t * (c[k] - y);
    }
    r = c[0];
    g = c[1];
    b = c[2];
}

void demosaicWb(const Plane& m, int width, int height, const FrameMeta& wb,
                std::vector<uint16_t>& rgb) {
    auto s = [&](int x, int y) -> float {
        x = std::clamp(x, 0, width - 1);
        y = std::clamp(y, 0, height - 1);
        return m[static_cast<size_t>(y) * width + x];
    };
    float ccm[9];
    normaliseCcm(wb.ccm, ccm);
    const std::vector<uint16_t>& lut = srgb16Lut();

    rgb.assign(static_cast<size_t>(width) * height * 3, 0);
    for (int y = 0; y < height; ++y) {
        const bool evenRow = (y & 1) == 0;
        for (int x = 0; x < width; ++x) {
            const bool evenCol = (x & 1) == 0;
            float r, g, b;
            if (evenRow && evenCol) {
                r = s(x, y);
                g = 0.25f * (s(x - 1, y) + s(x + 1, y) + s(x, y - 1) + s(x, y + 1));
                b = 0.25f * (s(x - 1, y - 1) + s(x + 1, y - 1) + s(x - 1, y + 1) + s(x + 1, y + 1));
            } else if (!evenRow && !evenCol) {
                b = s(x, y);
                g = 0.25f * (s(x - 1, y) + s(x + 1, y) + s(x, y - 1) + s(x, y + 1));
                r = 0.25f * (s(x - 1, y - 1) + s(x + 1, y - 1) + s(x - 1, y + 1) + s(x + 1, y + 1));
            } else if (evenRow) {
                g = s(x, y);
                r = 0.5f * (s(x - 1, y) + s(x + 1, y));
                b = 0.5f * (s(x, y - 1) + s(x, y + 1));
            } else {
                g = s(x, y);
                b = 0.5f * (s(x - 1, y) + s(x + 1, y));
                r = 0.5f * (s(x, y - 1) + s(x, y + 1));
            }
            // Stage 6: white balance (linear, unclamped).
            r *= wb.wbR;
            g *= wb.wbG;
            b *= wb.wbB;
            // Stage 7: colour matrix with negative-channel desaturation.
            applyCcm(ccm, r, g, b);
            // Stage 8b: filmic shoulder, then sRGB encoding via LUT.
            const float tr = toneCurve(r), tg = toneCurve(g), tb = toneCurve(b);
            const size_t o = (static_cast<size_t>(y) * width + x) * 3;
            rgb[o + 0] = lut[static_cast<size_t>(tr * 65535.0f + 0.5f)];
            rgb[o + 1] = lut[static_cast<size_t>(tg * 65535.0f + 0.5f)];
            rgb[o + 2] = lut[static_cast<size_t>(tb * 65535.0f + 0.5f)];
        }
    }
}

// Minimal baseline TIFF: 16-bit RGB, uncompressed, little-endian.
bool writeTiff16Rgb(const std::string& path, const std::vector<uint16_t>& rgb, int w, int h) {
    std::vector<uint8_t> hdr;
    auto u16 = [&](uint16_t v) { hdr.push_back(v & 0xFF); hdr.push_back(v >> 8); };
    auto u32 = [&](uint32_t v) { for (int i = 0; i < 4; ++i) hdr.push_back((v >> (8 * i)) & 0xFF); };

    const uint32_t entries = 10;
    const uint32_t ifdOffset = 8;
    const uint32_t bpsOffset = ifdOffset + 2 + entries * 12 + 4;   // 6 bytes of BitsPerSample
    const uint32_t dataOffset = bpsOffset + 6;
    const uint32_t dataBytes = static_cast<uint32_t>(rgb.size() * 2);

    hdr.insert(hdr.end(), {'I', 'I', 42, 0});
    u32(ifdOffset);
    u16(static_cast<uint16_t>(entries));
    auto entry = [&](uint16_t tag, uint16_t type, uint32_t count, uint32_t value) {
        u16(tag); u16(type); u32(count);
        if (type == 3) { u16(static_cast<uint16_t>(value)); u16(0); }  // SHORT, left-justified
        else u32(value);
    };
    entry(256, 4, 1, w);             // ImageWidth
    entry(257, 4, 1, h);             // ImageLength
    entry(258, 3, 3, bpsOffset);     // BitsPerSample (3 values, stored at bpsOffset)
    entry(259, 3, 1, 1);             // Compression: none
    entry(262, 3, 1, 2);             // Photometric: RGB
    entry(273, 4, 1, dataOffset);    // StripOffsets
    entry(277, 3, 1, 3);             // SamplesPerPixel
    entry(278, 4, 1, h);             // RowsPerStrip
    entry(279, 4, 1, dataBytes);     // StripByteCounts
    entry(284, 3, 1, 1);             // PlanarConfig: chunky
    u32(0);                          // next IFD
    u16(16); u16(16); u16(16);       // BitsPerSample values

    FILE* fp = std::fopen(path.c_str(), "wb");
    if (!fp) return false;
    bool ok = std::fwrite(hdr.data(), 1, hdr.size(), fp) == hdr.size();
    std::vector<uint8_t> chunk(static_cast<size_t>(w) * 3 * 2);
    for (int y = 0; ok && y < h; ++y) {
        const uint16_t* row = rgb.data() + static_cast<size_t>(y) * w * 3;
        for (size_t i = 0; i < chunk.size() / 2; ++i) {
            chunk[2 * i] = row[i] & 0xFF;
            chunk[2 * i + 1] = row[i] >> 8;
        }
        ok = std::fwrite(chunk.data(), 1, chunk.size(), fp) == chunk.size();
    }
    return std::fclose(fp) == 0 && ok;
}

}  // namespace

bool developBurst(const std::vector<FrameInput>& frames, int width, int height,
                  const std::string& tiffPath, BurstStats* stats) {
    const int n = static_cast<int>(frames.size());
    if (n < 1 || n > kMaxFrames) return false;
    if (width < 32 || height < 32 || (width & 1) || (height & 1)) return false;
    for (const auto& f : frames)
        if (!f.raw || f.meta.strideU16 < width) return false;

    BurstStats st;
    st.shiftX.assign(n, 0);
    st.shiftY.assign(n, 0);
    st.exposureRatio.assign(n, 1.0f);

    const int gw = width / 2, gh = height / 2;
    int levels = 1;
    while (levels < kPyramidLevels && (gw >> levels) >= 16 && (gh >> levels) >= 16) ++levels;

    // Stage 1: reference = sharpest of the first few frames (green gradient energy).
    auto t = Clock::now();
    int ref = 0;
    double bestEnergy = -1;
    for (int i = 0; i < std::min(n, kReferenceCandidates); ++i) {
        const Plane g = greenHalf(frames[i], gw, gh);
        double e = 0;
        for (int y = 0; y < gh; ++y)
            for (int x = 0; x < gw; ++x) {
                const size_t k = static_cast<size_t>(y) * gw + x;
                if (x + 1 < gw) e += std::fabs(g[k + 1] - g[k]);
                if (y + 1 < gh) e += std::fabs(g[k + gw] - g[k]);
            }
        e /= static_cast<double>(gw) * gh;
        if (e > bestEnergy) {
            bestEnergy = e;
            ref = i;
        }
    }
    st.referenceIndex = ref;
    const std::vector<Level> refPyr = buildPyramid(greenHalf(frames[ref], gw, gh), gw, gh, levels);

    // Stages 2-3: alignment and exposure match against the reference.
    std::vector<float> ratio(n, 1.0f);
    for (int i = 0; i < n; ++i) {
        if (i == ref) continue;
        const std::vector<Level> altPyr = buildPyramid(greenHalf(frames[i], gw, gh), gw, gh, levels);
        const Shift s = alignFrame(refPyr, altPyr);
        ratio[i] = exposureRatio(refPyr[0], altPyr[0], s.dx, s.dy);
        st.shiftX[i] = 2 * s.dx;   // half-res shift -> full-res, always even, so CFA phase is kept
        st.shiftY[i] = 2 * s.dy;
    }
    st.exposureRatio = ratio;
    st.alignMs = msSince(t);

    // Stage 4: merge in linear Bayer space.
    t = Clock::now();
    const Plane merged = mergeBurst(frames, st.shiftX, st.shiftY, ratio, ref, width, height);
    st.mergeMs = msSince(t);

    // Stages 5-6: demosaic and white balance.
    t = Clock::now();
    std::vector<uint16_t> rgb;
    demosaicWb(merged, width, height, frames[ref].meta, rgb);
    st.demosaicMs = msSince(t);

    // Output.
    t = Clock::now();
    const bool ok = writeTiff16Rgb(tiffPath, rgb, width, height);
    st.writeMs = msSince(t);

    if (stats) *stats = st;
    return ok;
}

}  // namespace gcam
