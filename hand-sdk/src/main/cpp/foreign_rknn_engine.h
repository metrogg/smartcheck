#ifndef FOREIGN_RKNN_ENGINE_H
#define FOREIGN_RKNN_ENGINE_H

#include <jni.h>
#include <vector>
#include <string>
#include <opencv2/opencv.hpp>
#include "rknn_api.h"
#include "common.h"

/**
 * 异物/关键点检测 RKNN 引擎
 * 模型输出: (1, 53, 8400) -> [x, y, w, h, score, kp0_x, kp0_y, ..., kp20_x, kp20_y]
 */
class ForeignRknnEngine {
public:
    ForeignRknnEngine();
    ~ForeignRknnEngine();
    
    /**
     * 从 Android assets 加载模型
     */
    int loadModelFromAssets(JNIEnv* env, jobject assetManager, const char* model_name);
    
    /**
     * 执行异物检测
     * @param hand_crop 手部裁剪图像
     * @return 检测结果（关键点 + 类别）
     */
    ForeignResult detect(const cv::Mat& hand_crop);
    
private:
    rknn_context ctx_;
    rknn_input_output_num io_num_;
    rknn_tensor_attr* input_attrs_;
    rknn_tensor_attr* output_attrs_;
    
    int model_width_;
    int model_height_;
    int model_channel_;
    
    /**
     * 后处理：解码关键点 + 类别
     */
    ForeignResult postprocess(float* output, int crop_width, int crop_height);
};

#endif // FOREIGN_RKNN_ENGINE_H
