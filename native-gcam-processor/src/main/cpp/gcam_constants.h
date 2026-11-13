#pragma once
// Starting points from GCAM-RAW-PIPELINE.md (Appendix). Not truths: tune on real photos.
namespace gcam {
constexpr int   kMaxFrames           = 16;
constexpr int   kPyramidLevels       = 4;
constexpr int   kTileSize            = 16;    // reserved for per-tile alignment (v2)
constexpr int   kCoarseSearchRadius  = 4;     // half-res px, L2
constexpr int   kFineSearchRadius    = 1;     // half-res px, L1
constexpr int   kReferenceCandidates = 3;     // sharpest of the first three
constexpr float kExposureRatioMin    = 0.25f;
constexpr float kExposureRatioMax    = 4.0f;
constexpr float kExposureMeanLow     = 0.02f; // ignore clipped/black pixels in means
constexpr float kExposureMeanHigh    = 0.98f;
constexpr float kClipLevel           = 0.98f; // normalised; at/above this is clipped
constexpr float kGhostSigma          = 3.0f;  // reject beyond 3 sigma from median
constexpr float kBlackPoint          = 0.002f;
constexpr float kWhitePoint          = 1.0f;
constexpr float kClarityAmount       = 0.18f;
constexpr float kClarityRadius       = 2.0f;
constexpr float kSaturation          = 1.10f;
constexpr float kSkinSaturation      = 0.60f;
constexpr float kSharpenAmount       = 0.45f;
constexpr float kSharpenRadius       = 1.0f;
constexpr float kSharpenSigmaMul     = 2.5f;
constexpr int   kJpegQuality         = 96;
}  // namespace gcam
