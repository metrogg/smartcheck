#include <jni.h>

#include <android/bitmap.h>
#include <android/log.h>

#include <cerrno>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <fstream>
#include <memory>
#include <stdexcept>
#include <string>
#include <vector>

#include <sys/stat.h>

#include "seeta/Common/Struct.h"
#include "seeta/FaceDetector.h"
#include "seeta/FaceLandmarker.h"
#include "seeta/FaceRecognizer.h"
#include "seeta/FaceAntiSpoofing.h"
#include "seeta/FaceTracker.h"

#define LOG_TAG "FaceSdkJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static bool g_initialized = false;
static std::unique_ptr<seeta::FaceDetector> g_face_detector;
static std::unique_ptr<seeta::FaceLandmarker> g_face_landmarker;
static std::unique_ptr<seeta::FaceRecognizer> g_face_recognizer;
static std::unique_ptr<seeta::FaceAntiSpoofing> g_face_antispoofing;
static std::unique_ptr<seeta::FaceTracker> g_face_tracker;
static int g_landmark_num = 0;

static std::string g_fd_model_path;
static std::string g_lm_model_path;
static std::string g_fr_model_path;
static std::string g_fas_model_path1;
static std::string g_fas_model_path2;
static const char *g_fd_models[2] = {nullptr, nullptr};
static const char *g_lm_models[2] = {nullptr, nullptr};
static const char *g_fr_models[2] = {nullptr, nullptr};
static const char *g_fas_models[2] = {nullptr, nullptr};
static SeetaModelSetting g_fd_setting{SEETA_DEVICE_CPU, 0, nullptr};
static SeetaModelSetting g_lm_setting{SEETA_DEVICE_CPU, 0, nullptr};
static SeetaModelSetting g_fr_setting{SEETA_DEVICE_CPU, 0, nullptr};
static SeetaModelSetting g_fas_setting{SEETA_DEVICE_CPU, 0, nullptr};

static long long checked_stat_size_or_throw(const char *path, const char *tag)
{
    struct stat st;
    if (stat(path, &st) != 0)
    {
        LOGE("nativeInit stat(%s) failed path=%s errno=%d(%s)", tag, path, errno, strerror(errno));
        throw std::runtime_error("model not accessible");
    }
    const auto size = static_cast<long long>(st.st_size);
    LOGI("nativeInit %s model size=%lld path=%s", tag, size, path);
    if (size <= 0)
    {
        LOGE("nativeInit %s model size invalid: %lld path=%s", tag, size, path);
        throw std::runtime_error("model size invalid");
    }
    return size;
}

static void validate_probe_size_or_throw(
    const char *path,
    const char *tag,
    long long stat_size,
    long long probe_size,
    long long ifs_size)
{
    if (probe_size <= 0 || ifs_size <= 0)
    {
        LOGE(
            "nativeInit %s probe size invalid. stat=%lld probe=%lld ifstream=%lld path=%s",
            tag,
            stat_size,
            probe_size,
            ifs_size,
            path);
        throw std::runtime_error("model probe size invalid");
    }
    if (probe_size != stat_size || ifs_size != stat_size)
    {
        LOGE(
            "nativeInit %s size mismatch. stat=%lld probe=%lld ifstream=%lld path=%s",
            tag,
            stat_size,
            probe_size,
            ifs_size,
            path);
        throw std::runtime_error("model size mismatch");
    }
}

static bool probe_file_seek(const char *path, long long *out_size, int *out_errno)
{
    if (out_size)
        *out_size = -1;
    if (out_errno)
        *out_errno = 0;
    if (!path)
    {
        if (out_errno)
            *out_errno = EINVAL;
        return false;
    }

    FILE *fp = std::fopen(path, "rb");
    if (!fp)
    {
        if (out_errno)
            *out_errno = errno;
        return false;
    }

    if (std::fseek(fp, 0, SEEK_END) != 0)
    {
        if (out_errno)
            *out_errno = errno;
        std::fclose(fp);
        return false;
    }
    const auto pos = std::ftell(fp);
    if (pos < 0)
    {
        if (out_errno)
            *out_errno = errno;
        std::fclose(fp);
        return false;
    }
    if (out_size)
        *out_size = static_cast<long long>(pos);
    std::fclose(fp);
    return true;
}

static bool probe_file_ifstream(const char *path, long long *out_size)
{
    if (out_size)
        *out_size = -1;
    if (!path)
        return false;

    std::ifstream in(path, std::ios::in | std::ios::binary);
    if (!in.is_open())
    {
        return false;
    }
    in.seekg(0, std::ios::end);
    const auto end = in.tellg();
    if (end < 0)
    {
        return false;
    }
    if (out_size)
        *out_size = static_cast<long long>(end);
    return true;
}

static std::vector<uint8_t> bitmapToBgr(JNIEnv *env, jobject bitmap, AndroidBitmapInfo &info)
{
    std::vector<uint8_t> bgr;

    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS)
    {
        LOGE("AndroidBitmap_getInfo failed");
        return bgr;
    }

    void *pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS)
    {
        LOGE("AndroidBitmap_lockPixels failed");
        return bgr;
    }

    const int width = static_cast<int>(info.width);
    const int height = static_cast<int>(info.height);
    bgr.resize(static_cast<size_t>(width) * static_cast<size_t>(height) * 3);

    const uint8_t *base = reinterpret_cast<const uint8_t *>(pixels);

    if (info.format == ANDROID_BITMAP_FORMAT_RGBA_8888)
    {
        for (int y = 0; y < height; ++y)
        {
            const uint8_t *row = base + y * info.stride;
            uint8_t *dst = bgr.data() + static_cast<size_t>(y) * static_cast<size_t>(width) * 3;
            for (int x = 0; x < width; ++x)
            {
                const uint8_t *px = row + x * 4;
                const uint8_t r = px[0];
                const uint8_t g = px[1];
                const uint8_t b = px[2];
                dst[x * 3 + 0] = b;
                dst[x * 3 + 1] = g;
                dst[x * 3 + 2] = r;
            }
        }
    }
    else if (info.format == ANDROID_BITMAP_FORMAT_RGB_565)
    {
        for (int y = 0; y < height; ++y)
        {
            const uint16_t *row = reinterpret_cast<const uint16_t *>(base + y * info.stride);
            uint8_t *dst = bgr.data() + static_cast<size_t>(y) * static_cast<size_t>(width) * 3;
            for (int x = 0; x < width; ++x)
            {
                const uint16_t p = row[x];
                const uint8_t r = static_cast<uint8_t>(((p >> 11) & 0x1F) * 255 / 31);
                const uint8_t g = static_cast<uint8_t>(((p >> 5) & 0x3F) * 255 / 63);
                const uint8_t b = static_cast<uint8_t>((p & 0x1F) * 255 / 31);
                dst[x * 3 + 0] = b;
                dst[x * 3 + 1] = g;
                dst[x * 3 + 2] = r;
            }
        }
    }
    else
    {
        LOGE("Unsupported bitmap format: %d", info.format);
        bgr.clear();
    }

    AndroidBitmap_unlockPixels(env, bitmap);
    return bgr;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_smartcheck_sdk_face_FaceSdk_nativeInit(
    JNIEnv *env,
    jobject /* thiz */,
    jstring fd_model_path,
    jstring lm_model_path,
    jstring fr_model_path,
    jstring fas_model_path1,
    jstring fas_model_path2)
{
    if (fd_model_path == nullptr || lm_model_path == nullptr || fr_model_path == nullptr)
    {
        LOGE("nativeInit invalid model paths");
        return -1;
    }

    const char *fdPath = env->GetStringUTFChars(fd_model_path, nullptr);
    const char *lmPath = env->GetStringUTFChars(lm_model_path, nullptr);
    const char *frPath = env->GetStringUTFChars(fr_model_path, nullptr);
    const char *fasPath1 = nullptr;
    const char *fasPath2 = nullptr;
    if (fas_model_path1 != nullptr)
    {
        fasPath1 = env->GetStringUTFChars(fas_model_path1, nullptr);
    }
    if (fas_model_path2 != nullptr)
    {
        fasPath2 = env->GetStringUTFChars(fas_model_path2, nullptr);
    }

    if (!fdPath || !lmPath || !frPath)
    {
        LOGE("nativeInit GetStringUTFChars failed");
        if (fdPath)
            env->ReleaseStringUTFChars(fd_model_path, fdPath);
        if (lmPath)
            env->ReleaseStringUTFChars(lm_model_path, lmPath);
        if (frPath)
            env->ReleaseStringUTFChars(fr_model_path, frPath);
        return -2;
    }

    g_initialized = false;
    g_face_detector.reset();
    g_face_landmarker.reset();
    g_face_recognizer.reset();
    g_face_antispoofing.reset();
    g_face_tracker.reset();
    g_landmark_num = 0;

    try
    {
        const long long fd_stat_size = checked_stat_size_or_throw(fdPath, "fd");

        long long probe_size = -1;
        int probe_errno = 0;
        if (!probe_file_seek(fdPath, &probe_size, &probe_errno))
        {
            LOGE("nativeInit probe(fd) failed path=%s errno=%d(%s)", fdPath, probe_errno, strerror(probe_errno));
        }
        else
        {
            LOGI("nativeInit probe(fd) ok size=%lld path=%s", probe_size, fdPath);
        }

        long long ifs_size = -1;
        if (!probe_file_ifstream(fdPath, &ifs_size))
        {
            LOGE("nativeInit ifstream(fd) failed path=%s", fdPath);
        }
        else
        {
            LOGI("nativeInit ifstream(fd) ok size=%lld path=%s", ifs_size, fdPath);
        }

        validate_probe_size_or_throw(fdPath, "fd", fd_stat_size, probe_size, ifs_size);

        const long long lm_stat_size = checked_stat_size_or_throw(lmPath, "lm");

        probe_size = -1;
        probe_errno = 0;
        if (!probe_file_seek(lmPath, &probe_size, &probe_errno))
        {
            LOGE("nativeInit probe(lm) failed path=%s errno=%d(%s)", lmPath, probe_errno, strerror(probe_errno));
        }
        else
        {
            LOGI("nativeInit probe(lm) ok size=%lld path=%s", probe_size, lmPath);
        }

        ifs_size = -1;
        if (!probe_file_ifstream(lmPath, &ifs_size))
        {
            LOGE("nativeInit ifstream(lm) failed path=%s", lmPath);
        }
        else
        {
            LOGI("nativeInit ifstream(lm) ok size=%lld path=%s", ifs_size, lmPath);
        }

        validate_probe_size_or_throw(lmPath, "lm", lm_stat_size, probe_size, ifs_size);

        const long long fr_stat_size = checked_stat_size_or_throw(frPath, "fr");

        probe_size = -1;
        probe_errno = 0;
        if (!probe_file_seek(frPath, &probe_size, &probe_errno))
        {
            LOGE("nativeInit probe(fr) failed path=%s errno=%d(%s)", frPath, probe_errno, strerror(probe_errno));
        }
        else
        {
            LOGI("nativeInit probe(fr) ok size=%lld path=%s", probe_size, frPath);
        }

        ifs_size = -1;
        if (!probe_file_ifstream(frPath, &ifs_size))
        {
            LOGE("nativeInit ifstream(fr) failed path=%s", frPath);
        }
        else
        {
            LOGI("nativeInit ifstream(fr) ok size=%lld path=%s", ifs_size, frPath);
        }

        validate_probe_size_or_throw(frPath, "fr", fr_stat_size, probe_size, ifs_size);

        // Validate and probe FAS models (two models: local + global)
        if (fasPath1 != nullptr && fasPath2 != nullptr)
        {
            // Probe fasPath1
            const long long fas1_stat_size = checked_stat_size_or_throw(fasPath1, "fas1");
            probe_size = -1;
            probe_errno = 0;
            if (!probe_file_seek(fasPath1, &probe_size, &probe_errno))
            {
                LOGE("nativeInit probe(fas1) failed path=%s errno=%d(%s)", fasPath1, probe_errno, strerror(probe_errno));
            }
            else
            {
                LOGI("nativeInit probe(fas1) ok size=%lld path=%s", probe_size, fasPath1);
            }

            ifs_size = -1;
            if (!probe_file_ifstream(fasPath1, &ifs_size))
            {
                LOGE("nativeInit ifstream(fas1) failed path=%s", fasPath1);
            }
            else
            {
                LOGI("nativeInit ifstream(fas1) ok size=%lld path=%s", ifs_size, fasPath1);
            }

            validate_probe_size_or_throw(fasPath1, "fas1", fas1_stat_size, probe_size, ifs_size);

            // Probe fasPath2
            const long long fas2_stat_size = checked_stat_size_or_throw(fasPath2, "fas2");
            probe_size = -1;
            probe_errno = 0;
            if (!probe_file_seek(fasPath2, &probe_size, &probe_errno))
            {
                LOGE("nativeInit probe(fas2) failed path=%s errno=%d(%s)", fasPath2, probe_errno, strerror(probe_errno));
            }
            else
            {
                LOGI("nativeInit probe(fas2) ok size=%lld path=%s", probe_size, fasPath2);
            }

            ifs_size = -1;
            if (!probe_file_ifstream(fasPath2, &ifs_size))
            {
                LOGE("nativeInit ifstream(fas2) failed path=%s", fasPath2);
            }
            else
            {
                LOGI("nativeInit ifstream(fas2) ok size=%lld path=%s", ifs_size, fasPath2);
            }

            validate_probe_size_or_throw(fasPath2, "fas2", fas2_stat_size, probe_size, ifs_size);

            g_fas_model_path1 = fasPath1;
            g_fas_model_path2 = fasPath2;
        }

        g_fd_model_path = fdPath;
        g_lm_model_path = lmPath;
        g_fr_model_path = frPath;

        g_fd_models[0] = g_fd_model_path.c_str();
        g_fd_models[1] = nullptr;
        g_lm_models[0] = g_lm_model_path.c_str();
        g_lm_models[1] = nullptr;
        g_fr_models[0] = g_fr_model_path.c_str();
        g_fr_models[1] = nullptr;

        g_fd_setting.device = SEETA_DEVICE_CPU;
        g_fd_setting.id = 0;
        g_fd_setting.model = g_fd_models;
        g_lm_setting.device = SEETA_DEVICE_CPU;
        g_lm_setting.id = 0;
        g_lm_setting.model = g_lm_models;
        g_fr_setting.device = SEETA_DEVICE_CPU;
        g_fr_setting.id = 0;
        g_fr_setting.model = g_fr_models;

        LOGI(
            "nativeInit settings: fd(device=%d id=%d model0=%s) lm(device=%d id=%d model0=%s) fr(device=%d id=%d model0=%s)",
            static_cast<int>(g_fd_setting.device),
            g_fd_setting.id,
            g_fd_setting.model && g_fd_setting.model[0] ? g_fd_setting.model[0] : "(null)",
            static_cast<int>(g_lm_setting.device),
            g_lm_setting.id,
            g_lm_setting.model && g_lm_setting.model[0] ? g_lm_setting.model[0] : "(null)",
            static_cast<int>(g_fr_setting.device),
            g_fr_setting.id,
            g_fr_setting.model && g_fr_setting.model[0] ? g_fr_setting.model[0] : "(null)");

        LOGI(
            "nativeInit creating FaceDetector... core=auto sizeof(SeetaModelSetting)=%zu",
            sizeof(SeetaModelSetting));
        g_face_detector = std::make_unique<seeta::FaceDetector>(g_fd_setting);
        LOGI("nativeInit FaceDetector ok");

        LOGI("nativeInit creating FaceLandmarker...");
        g_face_landmarker = std::make_unique<seeta::FaceLandmarker>(g_lm_setting);
        LOGI("nativeInit FaceLandmarker ok");

        LOGI("nativeInit creating FaceRecognizer...");
        g_face_recognizer = std::make_unique<seeta::FaceRecognizer>(g_fr_setting);
        LOGI("nativeInit FaceRecognizer ok");

        // Initialize FaceAntiSpoofing (liveness detection) with two models
        if (!g_fas_model_path1.empty() && !g_fas_model_path2.empty())
        {
            LOGI("nativeInit creating FaceAntiSpoofing with two models...");
            g_fas_models[0] = g_fas_model_path1.c_str();
            g_fas_models[1] = g_fas_model_path2.c_str();
            g_fas_setting.device = SEETA_DEVICE_CPU;
            g_fas_setting.id = 0;
            g_fas_setting.model = g_fas_models;
            g_face_antispoofing = std::make_unique<seeta::FaceAntiSpoofing>(g_fas_setting);

            // Set threshold as per official example: clarity=0.3, reality=0.8
            g_face_antispoofing->SetThreshold(0.3f, 0.8f);

            LOGI("nativeInit FaceAntiSpoofing ok");
        }

        // Initialize FaceTracker
        LOGI("nativeInit creating FaceTracker...");
        g_face_tracker = std::make_unique<seeta::FaceTracker>(g_fd_setting, 640, 480);
        LOGI("nativeInit FaceTracker ok");

        g_face_detector->set(seeta::FaceDetector::PROPERTY_MIN_FACE_SIZE, 40);

        g_landmark_num = g_face_landmarker->number();
        g_initialized = true;
        LOGI("nativeInit ok. fd=%s lm=%s fr=%s landmarks=%d", fdPath, lmPath, frPath, g_landmark_num);
    }
    catch (const std::exception &e)
    {
        LOGE("nativeInit exception: %s", e.what());
        g_face_detector.reset();
        g_face_landmarker.reset();
        g_face_recognizer.reset();
        g_face_antispoofing.reset();
        g_face_tracker.reset();
        g_initialized = false;
        g_landmark_num = 0;
    }
    catch (...)
    {
        LOGE("nativeInit unknown exception");
        g_face_detector.reset();
        g_face_landmarker.reset();
        g_face_recognizer.reset();
        g_face_antispoofing.reset();
        g_face_tracker.reset();
        g_initialized = false;
        g_landmark_num = 0;
    }

    env->ReleaseStringUTFChars(fd_model_path, fdPath);
    env->ReleaseStringUTFChars(lm_model_path, lmPath);
    env->ReleaseStringUTFChars(fr_model_path, frPath);
    if (fas_model_path1 != nullptr && fasPath1 != nullptr)
    {
        env->ReleaseStringUTFChars(fas_model_path1, fasPath1);
    }
    if (fas_model_path2 != nullptr && fasPath2 != nullptr)
    {
        env->ReleaseStringUTFChars(fas_model_path2, fasPath2);
    }

    return g_initialized ? 0 : -3;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_smartcheck_sdk_face_FaceSdk_nativeDetect(
    JNIEnv *env,
    jobject /* thiz */,
    jobject bitmap)
{
    if (!g_initialized)
    {
        LOGE("nativeDetect called before init");
        return nullptr;
    }

    if (!g_face_detector || !g_face_landmarker)
    {
        LOGE("nativeDetect detector not ready");
        return nullptr;
    }

    if (bitmap == nullptr)
    {
        return nullptr;
    }

    AndroidBitmapInfo info;
    auto bgr = bitmapToBgr(env, bitmap, info);
    if (bgr.empty())
    {
        return nullptr;
    }

    SeetaImageData image;
    image.width = static_cast<int>(info.width);
    image.height = static_cast<int>(info.height);
    image.channels = 3;
    image.data = bgr.data();

    const SeetaFaceInfoArray faces = g_face_detector->detect(image);

    jclass faceInfoClass = env->FindClass("com/smartcheck/sdk/face/FaceInfo");
    jclass rectFClass = env->FindClass("android/graphics/RectF");
    jclass pointFClass = env->FindClass("android/graphics/PointF");
    jclass arrayListClass = env->FindClass("java/util/ArrayList");
    if (!faceInfoClass || !rectFClass || !pointFClass || !arrayListClass)
    {
        LOGE("nativeDetect FindClass failed");
        return nullptr;
    }

    jmethodID rectFCtor = env->GetMethodID(rectFClass, "<init>", "(FFFF)V");
    jmethodID pointFCtor = env->GetMethodID(pointFClass, "<init>", "(FF)V");
    jmethodID arrayListCtor = env->GetMethodID(arrayListClass, "<init>", "()V");
    jmethodID arrayListAdd = env->GetMethodID(arrayListClass, "add", "(Ljava/lang/Object;)Z");
    jmethodID faceInfoCtor = env->GetMethodID(faceInfoClass, "<init>", "(ILandroid/graphics/RectF;FLjava/util/List;)V");
    if (!rectFCtor || !pointFCtor || !arrayListCtor || !arrayListAdd || !faceInfoCtor)
    {
        LOGE("nativeDetect GetMethodID failed");
        return nullptr;
    }

    jobjectArray result = env->NewObjectArray(faces.size, faceInfoClass, nullptr);
    if (!result)
    {
        return nullptr;
    }

    const int landmarkCount = g_landmark_num > 0 ? g_landmark_num : 5;
    std::vector<SeetaPointF> points(static_cast<size_t>(landmarkCount));

    for (int i = 0; i < faces.size; ++i)
    {
        const SeetaFaceInfo &f = faces.data[i];

        const float left = static_cast<float>(f.pos.x);
        const float top = static_cast<float>(f.pos.y);
        const float right = static_cast<float>(f.pos.x + f.pos.width);
        const float bottom = static_cast<float>(f.pos.y + f.pos.height);
        jobject rectObj = env->NewObject(rectFClass, rectFCtor, left, top, right, bottom);
        if (env->ExceptionCheck() || !rectObj)
        {
            env->ExceptionClear();
            continue;
        }

        jobject landmarksList = env->NewObject(arrayListClass, arrayListCtor);
        if (env->ExceptionCheck() || !landmarksList)
        {
            env->ExceptionClear();
            env->DeleteLocalRef(rectObj);
            continue;
        }

        g_face_landmarker->mark(image, f.pos, points.data());
        for (int k = 0; k < landmarkCount; ++k)
        {
            jobject pObj = env->NewObject(
                pointFClass,
                pointFCtor,
                static_cast<float>(points[static_cast<size_t>(k)].x),
                static_cast<float>(points[static_cast<size_t>(k)].y));
            if (env->ExceptionCheck() || !pObj)
            {
                env->ExceptionClear();
                continue;
            }
            env->CallBooleanMethod(landmarksList, arrayListAdd, pObj);
            if (env->ExceptionCheck())
            {
                env->ExceptionClear();
            }
            env->DeleteLocalRef(pObj);
        }

        jobject faceObj = env->NewObject(faceInfoClass, faceInfoCtor, i, rectObj, f.score, landmarksList);
        if (!env->ExceptionCheck() && faceObj)
        {
            env->SetObjectArrayElement(result, i, faceObj);
        }
        else
        {
            env->ExceptionClear();
        }

        if (faceObj)
            env->DeleteLocalRef(faceObj);
        env->DeleteLocalRef(landmarksList);
        env->DeleteLocalRef(rectObj);
    }

    return result;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_smartcheck_sdk_face_FaceSdk_nativeExtractFeature(
    JNIEnv *env,
    jobject /* thiz */,
    jobject bitmap)
{
    if (!g_initialized)
    {
        LOGE("nativeExtractFeature called before init");
        return nullptr;
    }

    if (!g_face_detector || !g_face_landmarker || !g_face_recognizer)
    {
        LOGE("nativeExtractFeature recognizer not ready");
        return nullptr;
    }

    if (bitmap == nullptr)
    {
        return nullptr;
    }

    AndroidBitmapInfo info;
    auto bgr = bitmapToBgr(env, bitmap, info);
    if (bgr.empty())
    {
        return nullptr;
    }

    SeetaImageData image;
    image.width = static_cast<int>(info.width);
    image.height = static_cast<int>(info.height);
    image.channels = 3;
    image.data = bgr.data();

    const SeetaFaceInfoArray faces = g_face_detector->detect(image);
    if (faces.size <= 0)
    {
        return nullptr;
    }

    int bestIndex = 0;
    int bestArea = faces.data[0].pos.width * faces.data[0].pos.height;
    for (int i = 1; i < faces.size; ++i)
    {
        const int area = faces.data[i].pos.width * faces.data[i].pos.height;
        if (area > bestArea)
        {
            bestArea = area;
            bestIndex = i;
        }
    }

    const int landmarkCount = g_landmark_num > 0 ? g_landmark_num : 5;
    std::vector<SeetaPointF> points(static_cast<size_t>(landmarkCount));
    g_face_landmarker->mark(image, faces.data[bestIndex].pos, points.data());

    const int featureSize = g_face_recognizer->GetExtractFeatureSize();
    if (featureSize <= 0)
    {
        LOGE("nativeExtractFeature invalid feature size: %d", featureSize);
        return nullptr;
    }

    std::vector<float> feature(static_cast<size_t>(featureSize));
    const bool ok = g_face_recognizer->Extract(image, points.data(), feature.data());
    if (!ok)
    {
        LOGE("nativeExtractFeature Extract failed");
        return nullptr;
    }

    jfloatArray out = env->NewFloatArray(featureSize);
    if (!out)
    {
        return nullptr;
    }

    env->SetFloatArrayRegion(out, 0, featureSize, feature.data());
    if (env->ExceptionCheck())
    {
        env->ExceptionClear();
        return nullptr;
    }

    return out;
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_smartcheck_sdk_face_FaceSdk_nativeCalculateSimilarity(
    JNIEnv *env,
    jobject /* thiz */,
    jfloatArray feature1,
    jfloatArray feature2)
{
    if (!g_initialized)
    {
        LOGE("nativeCalculateSimilarity called before init");
        return 0.0f;
    }

    if (!g_face_recognizer)
    {
        LOGE("nativeCalculateSimilarity recognizer not ready");
        return 0.0f;
    }

    if (!feature1 || !feature2)
    {
        return 0.0f;
    }

    const jsize len1 = env->GetArrayLength(feature1);
    const jsize len2 = env->GetArrayLength(feature2);
    if (len1 <= 0 || len2 <= 0 || len1 != len2)
    {
        LOGE("nativeCalculateSimilarity feature length mismatch: %d vs %d", static_cast<int>(len1), static_cast<int>(len2));
        return 0.0f;
    }

    const int expected = g_face_recognizer->GetExtractFeatureSize();
    if (expected > 0 && len1 != expected)
    {
        LOGE("nativeCalculateSimilarity feature length unexpected: %d (expected %d)", static_cast<int>(len1), expected);
        return 0.0f;
    }

    jfloat *f1 = env->GetFloatArrayElements(feature1, nullptr);
    jfloat *f2 = env->GetFloatArrayElements(feature2, nullptr);
    if (!f1 || !f2)
    {
        if (f1)
            env->ReleaseFloatArrayElements(feature1, f1, JNI_ABORT);
        if (f2)
            env->ReleaseFloatArrayElements(feature2, f2, JNI_ABORT);
        return 0.0f;
    }

    const float sim = g_face_recognizer->CalculateSimilarity(f1, f2);

    env->ReleaseFloatArrayElements(feature1, f1, JNI_ABORT);
    env->ReleaseFloatArrayElements(feature2, f2, JNI_ABORT);

    return sim;
}

extern "C" JNIEXPORT void JNICALL
Java_com_smartcheck_sdk_face_FaceSdk_nativeRelease(
    JNIEnv *env,
    jobject /* thiz */)
{
    (void)env;
    g_initialized = false;
    g_face_detector.reset();
    g_face_landmarker.reset();
    g_face_recognizer.reset();
    g_face_antispoofing.reset();
    g_face_tracker.reset();
    g_landmark_num = 0;
    LOGI("nativeRelease ok");
}

extern "C" JNIEXPORT jint JNICALL
Java_com_smartcheck_sdk_face_FaceSdk_nativeDetectLiveness(
    JNIEnv *env,
    jobject /* thiz */,
    jobject bitmap,
    jfloatArray jpoints)
{
    if (!g_initialized)
    {
        LOGE("nativeDetectLiveness called before init");
        return -1;
    }

    if (!g_face_antispoofing)
    {
        LOGE("nativeDetectLiveness antispoofing not initialized");
        return -2;
    }

    if (bitmap == nullptr)
    {
        return -3;
    }

    AndroidBitmapInfo info;
    auto bgr = bitmapToBgr(env, bitmap, info);
    if (bgr.empty())
    {
        return -4;
    }

    SeetaImageData image;
    image.width = static_cast<int>(info.width);
    image.height = static_cast<int>(info.height);
    image.channels = 3;
    image.data = bgr.data();

    // Detect face first
    const SeetaFaceInfoArray faces = g_face_detector->detect(image);
    if (faces.size <= 0)
    {
        LOGE("nativeDetectLiveness no face detected");
        return -5;
    }

    // Use the largest face
    int bestIndex = 0;
    int bestArea = faces.data[0].pos.width * faces.data[0].pos.height;
    for (int i = 1; i < faces.size; ++i)
    {
        const int area = faces.data[i].pos.width * faces.data[i].pos.height;
        if (area > bestArea)
        {
            bestArea = area;
            bestIndex = i;
        }
    }

    const SeetaRect &face = faces.data[bestIndex].pos;

    // Get landmarks
    const int landmarkCount = g_landmark_num > 0 ? g_landmark_num : 5;
    std::vector<SeetaPointF> points(static_cast<size_t>(landmarkCount));
    g_face_landmarker->mark(image, face, points.data());

    // Predict liveness
    seeta::FaceAntiSpoofing::Status status = g_face_antispoofing->Predict(image, face, points.data());

    LOGI("nativeDetectLiveness status=%d (0=REAL, 1=SPOOF, 2=FUZZY, 3=DETECTING)", static_cast<int>(status));

    return static_cast<int>(status);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_smartcheck_sdk_face_FaceSdk_nativeTrack(
    JNIEnv *env,
    jobject /* thiz */,
    jobject bitmap)
{
    if (!g_initialized)
    {
        LOGE("nativeTrack called before init");
        return nullptr;
    }

    if (!g_face_tracker || !g_face_detector)
    {
        LOGE("nativeTrack tracker not ready");
        return nullptr;
    }

    if (bitmap == nullptr)
    {
        return nullptr;
    }

    AndroidBitmapInfo info;
    auto bgr = bitmapToBgr(env, bitmap, info);
    if (bgr.empty())
    {
        return nullptr;
    }

    SeetaImageData image;
    image.width = static_cast<int>(info.width);
    image.height = static_cast<int>(info.height);
    image.channels = 3;
    image.data = bgr.data();

    // Set video size before tracking to match the actual frame size
    g_face_tracker->SetVideoSize(image.width, image.height);

    // Track faces
    const SeetaTrackingFaceInfoArray tracked_faces = g_face_tracker->Track(image);

    // Create result array
    jclass faceInfoClass = env->FindClass("com/smartcheck/sdk/face/FaceInfo");
    jclass rectFClass = env->FindClass("android/graphics/RectF");
    jclass arrayListClass = env->FindClass("java/util/ArrayList");
    if (!faceInfoClass || !rectFClass || !arrayListClass)
    {
        LOGE("nativeTrack FindClass failed");
        return nullptr;
    }

    jmethodID rectFCtor = env->GetMethodID(rectFClass, "<init>", "(FFFF)V");
    jmethodID arrayListCtor = env->GetMethodID(arrayListClass, "<init>", "()V");
    jmethodID arrayListAdd = env->GetMethodID(arrayListClass, "add", "(Ljava/lang/Object;)Z");
    jmethodID faceInfoCtor = env->GetMethodID(faceInfoClass, "<init>", "(ILandroid/graphics/RectF;FLjava/util/List;)V");
    if (!rectFCtor || !arrayListCtor || !arrayListAdd || !faceInfoCtor)
    {
        LOGE("nativeTrack GetMethodID failed");
        return nullptr;
    }

    jobjectArray result = env->NewObjectArray(tracked_faces.size, faceInfoClass, nullptr);
    if (!result)
    {
        return nullptr;
    }

    for (int i = 0; i < tracked_faces.size; ++i)
    {
        const SeetaTrackingFaceInfo &f = tracked_faces.data[i];

        const float left = static_cast<float>(f.pos.x);
        const float top = static_cast<float>(f.pos.y);
        const float right = static_cast<float>(f.pos.x + f.pos.width);
        const float bottom = static_cast<float>(f.pos.y + f.pos.height);

        // Mirror X coordinates for front camera (前置摄像头需要镜像)
        const float mirrored_left = static_cast<float>(image.width) - right;
        const float mirrored_right = static_cast<float>(image.width) - left;

        jobject rectObj = env->NewObject(rectFClass, rectFCtor, mirrored_left, top, mirrored_right, bottom);

        // Create empty landmarks list for tracking result
        jobject landmarksList = env->NewObject(arrayListClass, arrayListCtor);

        // Use tracking PID as id, score from detection
        jobject faceObj = env->NewObject(faceInfoClass, faceInfoCtor, f.PID, rectObj, f.score, landmarksList);
        if (!env->ExceptionCheck() && faceObj)
        {
            env->SetObjectArrayElement(result, i, faceObj);
        }

        if (faceObj)
            env->DeleteLocalRef(faceObj);
        env->DeleteLocalRef(landmarksList);
        env->DeleteLocalRef(rectObj);
    }

    LOGI("nativeTrack detected %d tracked faces", tracked_faces.size);
    return result;
}
