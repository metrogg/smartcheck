#include <jni.h>
#include <android/log.h>
#include <android/bitmap.h>
#include <string>
#include <vector>
#include <algorithm>
#include "hand_rknn_engine.h"
#include "foreign_rknn_engine.h"

#define TAG "HandDetectorJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// 全局引擎实例
static HandRknnEngine *g_hand_engine = nullptr;
static ForeignRknnEngine *g_foreign_engine = nullptr;

extern "C"
{

    /**
     * 初始化 RKNN 模型
     * @param env JNI 环境
     * @param thiz HandDetector 对象
     * @param assetManager Android AssetManager
     * @param handModelName 手部检测模型文件名
     * @param foreignModelName 异物检测模型文件名
     * @return 0=成功, -1=失败
     */
    JNIEXPORT jint JNICALL
    Java_com_smartcheck_sdk_HandDetector_nativeInit(
        JNIEnv *env,
        jobject thiz,
        jobject assetManager,
        jstring handModelName,
        jstring foreignModelName)
    {
        LOGD("nativeInit called");

        const char *hand_model = env->GetStringUTFChars(handModelName, nullptr);
        const char *foreign_model = env->GetStringUTFChars(foreignModelName, nullptr);

        try
        {
            // 创建引擎实例
            g_hand_engine = new HandRknnEngine();
            g_foreign_engine = new ForeignRknnEngine();

            // 从 assets 加载模型
            int ret = g_hand_engine->loadModelFromAssets(env, assetManager, hand_model);
            if (ret != 0)
            {
                LOGE("Failed to load hand model: %s", hand_model);
                env->ReleaseStringUTFChars(handModelName, hand_model);
                env->ReleaseStringUTFChars(foreignModelName, foreign_model);
                return -1;
            }

            ret = g_foreign_engine->loadModelFromAssets(env, assetManager, foreign_model);
            if (ret != 0)
            {
                LOGE("Failed to load foreign model: %s", foreign_model);
                env->ReleaseStringUTFChars(handModelName, hand_model);
                env->ReleaseStringUTFChars(foreignModelName, foreign_model);
                return -1;
            }

            LOGD("Models loaded successfully");
            env->ReleaseStringUTFChars(handModelName, hand_model);
            env->ReleaseStringUTFChars(foreignModelName, foreign_model);
            return 0;
        }
        catch (const std::exception &e)
        {
            LOGE("Exception in nativeInit: %s", e.what());
            env->ReleaseStringUTFChars(handModelName, hand_model);
            env->ReleaseStringUTFChars(foreignModelName, foreign_model);
            return -1;
        }
    }

    /**
     * 执行手部 + 异物检测
     * @param env JNI 环境
     * @param thiz HandDetector 对象
     * @param bitmap 输入图像
     * @return HandInfo 数组
     */
    JNIEXPORT jobjectArray JNICALL
    Java_com_smartcheck_sdk_HandDetector_nativeDetect(
        JNIEnv *env,
        jobject thiz,
        jobject bitmap)
    {
        if (!g_hand_engine || !g_foreign_engine)
        {
            LOGE("Engines not initialized");
            return nullptr;
        }

        // 1. 从 Bitmap 提取像素数据
        AndroidBitmapInfo info;
        void *pixels;

        if (AndroidBitmap_getInfo(env, bitmap, &info) < 0)
        {
            LOGE("Failed to get bitmap info");
            return nullptr;
        }

        if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0)
        {
            LOGE("Failed to lock bitmap pixels");
            return nullptr;
        }

        // 2. 手部检测
        std::vector<BBox> hand_boxes = g_hand_engine->detect(
            (uint8_t *)pixels,
            info.width,
            info.height);

        // 限制最大检测数量为 2 只手（正常人只有 2 只手）
        const size_t MAX_HANDS = 2;

        // 按置信度排序，保留最高的 MAX_HANDS 个
        if (hand_boxes.size() > MAX_HANDS)
        {
            std::sort(hand_boxes.begin(), hand_boxes.end(),
                      [](const BBox &a, const BBox &b)
                      { return a.score > b.score; });
        }

        size_t num_hands = std::min(hand_boxes.size(), MAX_HANDS);

        // 只在手部数量变化时记录日志，减少冗余
        static size_t last_hand_count = static_cast<size_t>(-1);
        if (hand_boxes.size() != last_hand_count)
        {
            if (hand_boxes.size() > MAX_HANDS)
            {
                LOGD("手部检测: %zu -> %zu (按置信度筛选)", hand_boxes.size(), MAX_HANDS);
            }
            else
            {
                LOGD("手部检测: %zu 只手", hand_boxes.size());
            }
            last_hand_count = hand_boxes.size();
        }

        // 3. 对每个手部框做异物检测
        struct HandJniResult
        {
            HandResult hand;
            ForeignResult foreign;
        };
        std::vector<HandJniResult> results;
        for (size_t i = 0; i < num_hands; i++)
        {
            const auto &hand_box = hand_boxes[i];

            // 裁剪 + 放大 1.5x
            cv::Mat hand_crop = cropAndScale(
                (uint8_t *)pixels,
                info.width,
                info.height,
                hand_box,
                1.5f);

            // 异物检测
            ForeignResult foreign_result = g_foreign_engine->detect(hand_crop);

            HandResult hand_result;
            hand_result.id = static_cast<int>(i);
            hand_result.box = hand_box; // 始终使用手部框（全图显示）
            hand_result.hasForeignObject = !foreign_result.boxes.empty();
            if (!foreign_result.boxes.empty() && !foreign_result.class_names.empty() && !foreign_result.scores.empty())
            {
                // Overall label/score: top-1 foreign object
                hand_result.label = foreign_result.class_names[0];
                hand_result.score = foreign_result.scores[0];
            }
            else
            {
                hand_result.label = foreign_result.class_name;
                hand_result.score = hand_box.score;
            }

            // Keypoints only (relative to crop)
            hand_result.keyPoints = foreign_result.keypoints;

            HandJniResult merged;
            merged.hand = hand_result;
            merged.foreign = std::move(foreign_result);
            results.push_back(std::move(merged));
        }

        AndroidBitmap_unlockPixels(env, bitmap);

        // 4. 转换成 Java HandInfo 数组
        jclass handInfoClass = env->FindClass("com/smartcheck/sdk/HandInfo");
        jmethodID constructor = env->GetMethodID(
            handInfoClass,
            "<init>",
            "(ILandroid/graphics/RectF;FLjava/util/List;ZLjava/lang/String;Ljava/util/List;)V");

        jobjectArray resultArray = env->NewObjectArray(
            results.size(),
            handInfoClass,
            nullptr);

        jclass rectFClass = env->FindClass("android/graphics/RectF");
        jmethodID rectFConstructor = env->GetMethodID(rectFClass, "<init>", "(FFFF)V");

        jclass pointFClass = env->FindClass("android/graphics/PointF");
        jmethodID pointFConstructor = env->GetMethodID(pointFClass, "<init>", "(FF)V");

        jclass arrayListClass = env->FindClass("java/util/ArrayList");
        jmethodID arrayListConstructor = env->GetMethodID(arrayListClass, "<init>", "()V");
        jmethodID arrayListAdd = env->GetMethodID(arrayListClass, "add", "(Ljava/lang/Object;)Z");

        // ForeignObjectInfo
        jclass foreignInfoClass = env->FindClass("com/smartcheck/sdk/ForeignObjectInfo");
        jmethodID foreignInfoCtor = nullptr;
        if (foreignInfoClass)
        {
            foreignInfoCtor = env->GetMethodID(
                foreignInfoClass,
                "<init>",
                "(Landroid/graphics/RectF;FLjava/lang/String;)V");
        }

        for (size_t i = 0; i < results.size(); i++)
        {
            const auto &result = results[i].hand;
            const auto &foreign = results[i].foreign;

            // 创建 RectF
            jobject rectF = env->NewObject(
                rectFClass,
                rectFConstructor,
                result.box.x1,
                result.box.y1,
                result.box.x2,
                result.box.y2);

            // 创建 keyPoints List
            jobject keyPointsList = env->NewObject(arrayListClass, arrayListConstructor);
            for (const auto &kp : result.keyPoints)
            {
                jobject pointF = env->NewObject(pointFClass, pointFConstructor, kp.x, kp.y);
                env->CallBooleanMethod(keyPointsList, arrayListAdd, pointF);
                env->DeleteLocalRef(pointF);
            }

            // 创建 foreignObjects List
            jobject foreignObjectsList = env->NewObject(arrayListClass, arrayListConstructor);
            if (foreignInfoClass && foreignInfoCtor)
            {
                const size_t n = std::min(foreign.boxes.size(), foreign.class_names.size());
                for (size_t j = 0; j < n; j++)
                {
                    const auto &b = foreign.boxes[j];
                    const float score = (j < foreign.scores.size()) ? foreign.scores[j] : b.score;
                    jobject fRect = env->NewObject(
                        rectFClass,
                        rectFConstructor,
                        b.x1,
                        b.y1,
                        b.x2,
                        b.y2);
                    jstring fLabel = env->NewStringUTF(foreign.class_names[j].c_str());
                    jobject fInfo = env->NewObject(
                        foreignInfoClass,
                        foreignInfoCtor,
                        fRect,
                        score,
                        fLabel);
                    env->CallBooleanMethod(foreignObjectsList, arrayListAdd, fInfo);
                    env->DeleteLocalRef(fRect);
                    env->DeleteLocalRef(fLabel);
                    env->DeleteLocalRef(fInfo);
                }
            }

            // 创建 label String
            jstring labelStr = env->NewStringUTF(result.label.c_str());

            // 创建 HandInfo 对象
            jobject handInfo = env->NewObject(
                handInfoClass,
                constructor,
                result.id,
                rectF,
                result.score,
                keyPointsList,
                result.hasForeignObject,
                labelStr,
                foreignObjectsList);

            env->SetObjectArrayElement(resultArray, i, handInfo);

            // 清理局部引用
            env->DeleteLocalRef(rectF);
            env->DeleteLocalRef(keyPointsList);
            env->DeleteLocalRef(foreignObjectsList);
            env->DeleteLocalRef(labelStr);
            env->DeleteLocalRef(handInfo);
        }

        env->DeleteLocalRef(handInfoClass);
        env->DeleteLocalRef(rectFClass);
        env->DeleteLocalRef(pointFClass);
        env->DeleteLocalRef(arrayListClass);
        if (foreignInfoClass)
        {
            env->DeleteLocalRef(foreignInfoClass);
        }

        return resultArray;
    }

    /**
     * 释放资源
     */
    JNIEXPORT void JNICALL
    Java_com_smartcheck_sdk_HandDetector_nativeRelease(
        JNIEnv *env,
        jobject thiz)
    {
        LOGD("nativeRelease called");

        if (g_hand_engine)
        {
            delete g_hand_engine;
            g_hand_engine = nullptr;
        }

        if (g_foreign_engine)
        {
            delete g_foreign_engine;
            g_foreign_engine = nullptr;
        }
    }

} // extern "C"

/**
 * 裁剪并放大手部区域
 */
cv::Mat cropAndScale(
    uint8_t *pixels,
    int width,
    int height,
    const BBox &box,
    float scale_factor)
{
    // 计算中心点
    int cx = (box.x1 + box.x2) / 2;
    int cy = (box.y1 + box.y2) / 2;

    // 计算放大后的尺寸
    int w = box.x2 - box.x1;
    int h = box.y2 - box.y1;
    int new_w = static_cast<int>(w * scale_factor);
    int new_h = static_cast<int>(h * scale_factor);

    // 计算新的边界（确保不越界）
    int nx1 = std::max(0, cx - new_w / 2);
    int ny1 = std::max(0, cy - new_h / 2);
    int nx2 = std::min(width, cx + new_w / 2);
    int ny2 = std::min(height, cy + new_h / 2);

    // 从 RGBA 转换为 RGB Mat
    cv::Mat src(height, width, CV_8UC4, pixels);
    cv::Mat rgb;
    cv::cvtColor(src, rgb, cv::COLOR_RGBA2BGR);

    // 裁剪
    cv::Rect roi(nx1, ny1, nx2 - nx1, ny2 - ny1);
    cv::Mat cropped = rgb(roi).clone();

    return cropped;
}
