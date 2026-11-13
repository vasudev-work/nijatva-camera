#include <jni.h>
#include <android/log.h>
#include <vector>
#include "gcam_constants.h"
#include "pipeline_core.h"

#define LOG_TAG "GcamPipeline"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Per-frame metadata layout, 19 floats per frame, built by RawPipeline.kt:
// [iso, exposureNs, wbR, wbG, wbB, black, white, noiseA, noiseB, strideBytes, ccm[0..8]]
constexpr int kMetaStride = 19;

/**
 * Returns [referenceIndex, alignMs, mergeMs, demosaicMs, writeMs,
 *          then per frame: shiftX, shiftY, exposureRatio], or null on failure.
 * The result is also written as a 16-bit TIFF to outputPath.
 */
extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_nijatva_camera_processor_GcamEngine_nativeProcessRawFramesV2(
        JNIEnv *env,
        jobject /* thiz */,
        jobjectArray buffers,
        jfloatArray meta,
        jint width,
        jint height,
        jstring outputPath) {

    if (buffers == nullptr || meta == nullptr || outputPath == nullptr) return nullptr;
    const jsize n = env->GetArrayLength(buffers);
    if (n < 1 || n > gcam::kMaxFrames) return nullptr;
    if (env->GetArrayLength(meta) < n * kMetaStride) return nullptr;

    std::vector<gcam::FrameInput> frames(n);
    jfloat *m = env->GetFloatArrayElements(meta, nullptr);
    for (jsize i = 0; i < n; ++i) {
        jobject buf = env->GetObjectArrayElement(buffers, i);
        auto *ptr = static_cast<const uint16_t *>(env->GetDirectBufferAddress(buf));
        env->DeleteLocalRef(buf);
        const float *f = m + i * kMetaStride;
        if (ptr == nullptr) {
            LOGE("Frame %d is not a direct buffer", i);
            env->ReleaseFloatArrayElements(meta, m, JNI_ABORT);
            return nullptr;
        }
        frames[i].raw = ptr;
        frames[i].meta.iso = f[0];
        frames[i].meta.exposureNs = f[1];
        frames[i].meta.wbR = f[2];
        frames[i].meta.wbG = f[3];
        frames[i].meta.wbB = f[4];
        frames[i].meta.black = f[5];
        frames[i].meta.white = f[6];
        frames[i].meta.noiseA = f[7];
        frames[i].meta.noiseB = f[8];
        frames[i].meta.strideU16 = static_cast<int>(f[9]) / 2;
        for (int k = 0; k < 9; ++k) frames[i].meta.ccm[k] = f[10 + k];
    }
    env->ReleaseFloatArrayElements(meta, m, JNI_ABORT);

    const char *path = env->GetStringUTFChars(outputPath, nullptr);
    gcam::BurstStats st;
    const bool ok = gcam::developBurst(frames, width, height, path, &st);
    env->ReleaseStringUTFChars(outputPath, path);
    if (!ok) return nullptr;

    std::vector<jfloat> out;
    out.reserve(5 + 3 * n);
    out.push_back(static_cast<jfloat>(st.referenceIndex));
    out.push_back(static_cast<jfloat>(st.alignMs));
    out.push_back(static_cast<jfloat>(st.mergeMs));
    out.push_back(static_cast<jfloat>(st.demosaicMs));
    out.push_back(static_cast<jfloat>(st.writeMs));
    for (jsize i = 0; i < n; ++i) {
        out.push_back(static_cast<jfloat>(st.shiftX[i]));
        out.push_back(static_cast<jfloat>(st.shiftY[i]));
        out.push_back(st.exposureRatio[i]);
    }
    jfloatArray result = env->NewFloatArray(static_cast<jsize>(out.size()));
    if (result) env->SetFloatArrayRegion(result, 0, static_cast<jsize>(out.size()), out.data());
    return result;
}
