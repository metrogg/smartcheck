#ifndef HAND_RKNN_ENGINE_H
#define HAND_RKNN_ENGINE_H

#include <jni.h>
#include <vector>
#include <string>
#include "rknn_api.h"
#include "common.h"

/**
 * 手部检测 RKNN 引擎
 * 模型输出: (1, 5, 8400) -> [x, y, w, h, score]
 */
class HandRknnEngine {
public:
    HandRknnEngine();
    ~HandRknnEngine();
    
    /**
     * 从 Android assets 加载模型
     */
    int loadModelFromAssets(JNIEnv* env, jobject assetManager, const char* model_name);
    
    /**
     * 执行手部检测
     * @param pixels RGBA 像素数据
     * @param width 图像宽度
     * @param height 图像高度
     * @return 检测到的手部框列表
     */
    std::vector<BBox> detect(uint8_t* pixels, int width, int height);
    
private:
    rknn_context ctx_;
    rknn_input_output_num io_num_;
    rknn_tensor_attr* input_attrs_;
    rknn_tensor_attr* output_attrs_;
    
    int model_width_;
    int model_height_;
    int model_channel_;
    
    /**
     * 后处理：解码 + NMS
     */
    std::vector<BBox> postprocess(float* output, int orig_width, int orig_height);
    
    /**
     * NMS 去重
     */
    std::vector<BBox> nms(std::vector<BBox>& boxes, float iou_threshold);
};

#endif // HAND_RKNN_ENGINE_H
