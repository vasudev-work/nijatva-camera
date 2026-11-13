#include <jni.h>
#include <android/log.h>
#include <vector>
#include "image_utils.h"

#define LOG_TAG "GcamEngineCPP"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jboolean JNICALL
Java_com_nijatva_camera_processor_GcamEngine_nativeProcessRawFrames(
        JNIEnv *env,
        jobject /* thiz */,
        jobjectArray buffers,
        jint width,
        jint height,
        jstring outputPath) {

    if (buffers == nullptr || width <= 0 || height <= 0) {
        LOGE("Invalid arguments");
        return JNI_FALSE;
    }

    const jsize count = env->GetArrayLength(buffers);
    std::vector<RawFrameInfo> frames;
    frames.reserve(count);

    for (jsize i = 0; i < count; ++i) {
        jobject buf = env->GetObjectArrayElement(buffers, i);
        // Buffers must be direct ByteBuffers so the native address is stable.
        auto *ptr = static_cast<uint16_t *>(env->GetDirectBufferAddress(buf));
        jlong capacity = env->GetDirectBufferCapacity(buf);
        env->DeleteLocalRef(buf);

        if (ptr == nullptr || capacity < (jlong) width * height * 2) {
            LOGE("Frame %d is not a valid direct RAW buffer", i);
            return JNI_FALSE;
        }
        frames.push_back({ptr, width, height});
    }

    const char *path = env->GetStringUTFChars(outputPath, nullptr);
    LOGI("Received %d RAW frames (%dx%d), output: %s", (int) frames.size(), width, height, path);

    // TODO: align frames -> merge -> demosaic -> white balance -> CCM -> tone map -> JPEG encode.
    // Not implemented yet; this stage only validates input and reports success.

    env->ReleaseStringUTFChars(outputPath, path);
    return JNI_TRUE;
}
