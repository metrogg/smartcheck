#include "hand_rknn_engine.h"
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <android/log.h>
#include <opencv2/opencv.hpp>
#include <cmath>
#include <algorithm>

#define TAG "HandRknnEngine"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// 模型输入尺寸
#define MODEL_INPUT_SIZE 640

// 检测阈值
#define CONF_THRESHOLD 0.5f
#define NMS_THRESHOLD 0.35f

// Sigmoid 激活函数
static inline float sigmoid(float x)
{
    return 1.0f / (1.0f + expf(-x));
}

HandRknnEngine::HandRknnEngine()
    : ctx_(0), input_attrs_(nullptr), output_attrs_(nullptr),
      model_width_(MODEL_INPUT_SIZE), model_height_(MODEL_INPUT_SIZE), model_channel_(3)
{
}

HandRknnEngine::~HandRknnEngine()
{
    if (ctx_ > 0)
    {
        rknn_destroy(ctx_);
    }
    if (input_attrs_)
    {
        delete[] input_attrs_;
    }
    if (output_attrs_)
    {
        delete[] output_attrs_;
    }
}

int HandRknnEngine::loadModelFromAssets(JNIEnv *env, jobject assetManager, const char *model_name)
{
    AAssetManager *mgr = AAssetManager_fromJava(env, assetManager);
    AAsset *asset = AAssetManager_open(mgr, model_name, AASSET_MODE_BUFFER);

    if (!asset)
    {
        LOGE("Failed to open asset: %s", model_name);
        return -1;
    }

    size_t model_size = AAsset_getLength(asset);
    void *model_data = malloc(model_size);
    AAsset_read(asset, model_data, model_size);
    AAsset_close(asset);

    LOGD("Model size: %zu bytes", model_size);

    // 初始化 RKNN
    int ret = rknn_init(&ctx_, model_data, model_size, 0, nullptr);
    free(model_data);

    if (ret < 0)
    {
        LOGE("rknn_init failed: %d", ret);
        return -1;
    }

    // 查询模型输入输出信息
    ret = rknn_query(ctx_, RKNN_QUERY_IN_OUT_NUM, &io_num_, sizeof(io_num_));
    if (ret < 0)
    {
        LOGE("rknn_query io_num failed: %d", ret);
        return -1;
    }

    LOGD("Model input num: %d, output num: %d", io_num_.n_input, io_num_.n_output);

    // 查询输入属性
    input_attrs_ = new rknn_tensor_attr[io_num_.n_input];
    memset(input_attrs_, 0, sizeof(rknn_tensor_attr) * io_num_.n_input);
    for (uint32_t i = 0; i < io_num_.n_input; i++)
    {
        input_attrs_[i].index = i;
        ret = rknn_query(ctx_, RKNN_QUERY_INPUT_ATTR, &(input_attrs_[i]), sizeof(rknn_tensor_attr));
        if (ret < 0)
        {
            LOGE("rknn_query input attr %d failed: %d", i, ret);
            return -1;
        }
        LOGD("Input %d: n_dims=%d, dims=[%d,%d,%d,%d], type=%d, fmt=%d", i,
             input_attrs_[i].n_dims,
             input_attrs_[i].dims[0], input_attrs_[i].dims[1],
             input_attrs_[i].dims[2], input_attrs_[i].dims[3],
             input_attrs_[i].type, input_attrs_[i].fmt);
    }

    // 查询输出属性
    output_attrs_ = new rknn_tensor_attr[io_num_.n_output];
    memset(output_attrs_, 0, sizeof(rknn_tensor_attr) * io_num_.n_output);
    for (uint32_t i = 0; i < io_num_.n_output; i++)
    {
        output_attrs_[i].index = i;
        ret = rknn_query(ctx_, RKNN_QUERY_OUTPUT_ATTR, &(output_attrs_[i]), sizeof(rknn_tensor_attr));
        if (ret < 0)
        {
            LOGE("rknn_query output attr %d failed: %d", i, ret);
            return -1;
        }
        LOGD("Output %d: n_dims=%d, dims=[%d,%d,%d,%d], n_elems=%d, size=%d, type=%d, qnt_type=%d, zp=%d, scale=%.6f", i,
             output_attrs_[i].n_dims,
             output_attrs_[i].dims[0], output_attrs_[i].dims[1],
             output_attrs_[i].dims[2], output_attrs_[i].dims[3],
             output_attrs_[i].n_elems, output_attrs_[i].size,
             output_attrs_[i].type, output_attrs_[i].qnt_type,
             output_attrs_[i].zp, output_attrs_[i].scale);
    }

    LOGD("Hand model loaded successfully");
    return 0;
}

std::vector<BBox> HandRknnEngine::detect(uint8_t *pixels, int width, int height)
{
    // 1. 前处理 - 使用 UINT8 输入
    uint8_t *input_data = new uint8_t[model_width_ * model_height_ * model_channel_];

    // Letterbox resize (保持宽高比)
    cv::Mat src(height, width, CV_8UC4, pixels);
    cv::Mat rgb;
    cv::cvtColor(src, rgb, cv::COLOR_RGBA2RGB); // Android Bitmap 是 RGBA

    float scale = std::min(
        static_cast<float>(model_width_) / width,
        static_cast<float>(model_height_) / height);

    int new_w = static_cast<int>(width * scale);
    int new_h = static_cast<int>(height * scale);

    cv::Mat resized;
    cv::resize(rgb, resized, cv::Size(new_w, new_h));

    // 创建 640x640 的画布，填充灰色 (114, 114, 114)
    cv::Mat canvas(model_height_, model_width_, CV_8UC3, cv::Scalar(114, 114, 114));

    // 居中放置
    int offset_x = (model_width_ - new_w) / 2;
    int offset_y = (model_height_ - new_h) / 2;
    resized.copyTo(canvas(cv::Rect(offset_x, offset_y, new_w, new_h)));

    // 复制数据 (HWC)
    memcpy(input_data, canvas.data, model_width_ * model_height_ * model_channel_);

    LOGD("Input prepared (UINT8), size: %d", model_width_ * model_height_ * model_channel_);

    // 2. 设置输入 - 使用 RKNN_TENSOR_UINT8 类型
    rknn_input inputs[1];
    memset(inputs, 0, sizeof(inputs));
    inputs[0].index = 0;
    inputs[0].type = RKNN_TENSOR_UINT8;
    inputs[0].size = model_width_ * model_height_ * model_channel_;
    inputs[0].fmt = RKNN_TENSOR_NHWC;
    inputs[0].pass_through = 0; // 让 RKNN 自动处理 mean/std
    inputs[0].buf = input_data;

    int ret = rknn_inputs_set(ctx_, io_num_.n_input, inputs);
    if (ret < 0)
    {
        LOGE("rknn_inputs_set failed: %d", ret);
        delete[] input_data;
        return {};
    }

    // 3. 执行推理
    ret = rknn_run(ctx_, nullptr);
    if (ret < 0)
    {
        LOGE("rknn_run failed: %d", ret);
        delete[] input_data;
        return {};
    }

    // 4. 获取输出 - 使用 want_float=1 让 RKNN 自动反量化
    rknn_output outputs[1];
    memset(outputs, 0, sizeof(outputs));
    outputs[0].want_float = 1; // 让 RKNN 自动反量化为 float
    outputs[0].is_prealloc = 0;

    ret = rknn_outputs_get(ctx_, io_num_.n_output, outputs, nullptr);
    if (ret < 0)
    {
        LOGE("rknn_outputs_get failed: %d", ret);
        delete[] input_data;
        return {};
    }

    // 5. 直接使用浮点输出
    const int num_boxes = 8400;
    const int num_channels = 5;
    float *float_output = (float *)outputs[0].buf;

    LOGD("Output buffer size: %zu, expected: %zu", outputs[0].size, num_boxes * num_channels * sizeof(float));

    // 调试: 打印输出的前几个值
    std::vector<BBox> boxes = postprocess(float_output, width, height);

    // 6. 清理
    rknn_outputs_release(ctx_, io_num_.n_output, outputs);
    delete[] input_data;

    // 只在检测到框时记录详细日志
    if (!boxes.empty())
    {
        LOGD("手部检测: %zu 个候选框", boxes.size());
    }
    return boxes;
}

std::vector<BBox> HandRknnEngine::postprocess(float *output, int orig_width, int orig_height)
{
    // 模型输出格式: [1, 5, 8400] -> 展平后为 [5, 8400]
    // 每个通道: [cx, cy, w, h, score] 各有 8400 个值
    const int num_boxes = 8400;

    std::vector<BBox> boxes;

    // 计算缩放比例（用于反向映射到原图）
    float scale = std::min(
        static_cast<float>(model_width_) / orig_width,
        static_cast<float>(model_height_) / orig_height);
    int offset_x = (model_width_ - static_cast<int>(orig_width * scale)) / 2;
    int offset_y = (model_height_ - static_cast<int>(orig_height * scale)) / 2;

    // 指向每个通道的指针 (格式: [5, 8400])
    float *cx_ptr = output;                    // 中心 x
    float *cy_ptr = output + num_boxes;        // 中心 y
    float *w_ptr = output + num_boxes * 2;     // 宽度
    float *h_ptr = output + num_boxes * 3;     // 高度
    float *score_ptr = output + num_boxes * 4; // 置信度

    // 调试: 输出前3个框的原始值
    float max_score_raw = 0.0f;
    float max_score = 0.0f;
    for (int i = 0; i < std::min(3, num_boxes); i++)
    {
        float score = score_ptr[i]; // 已经是概率值，不需要 sigmoid
        max_score = std::max(max_score, score);
        LOGD("Box[%d]: cx=%.1f, cy=%.1f, w=%.1f, h=%.1f, score=%.4f",
             i, cx_ptr[i], cy_ptr[i], w_ptr[i], h_ptr[i], score);
    }

    // 找到最大 score
    for (int i = 0; i < num_boxes; i++)
    {
        float score = score_ptr[i];
        if (score > max_score)
        {
            max_score = score;
        }
    }
    LOGD("Max score: %.4f, threshold: %.2f", max_score, CONF_THRESHOLD);

    // 解析所有框
    int valid_count = 0;
    for (int i = 0; i < num_boxes; i++)
    {
        float score = score_ptr[i]; // 已经是概率值，不需要 sigmoid

        if (score < CONF_THRESHOLD)
        {
            continue;
        }

        float cx = cx_ptr[i];
        float cy = cy_ptr[i];
        float w = w_ptr[i];
        float h = h_ptr[i];

        // 检查尺寸是否合理
        if (w <= 0 || h <= 0 || w > model_width_ || h > model_height_)
        {
            continue;
        }

        // xywh -> xyxy (模型坐标系)
        float x1 = cx - w / 2.0f;
        float y1 = cy - h / 2.0f;
        float x2 = cx + w / 2.0f;
        float y2 = cy + h / 2.0f;

        // 反向映射到原图坐标
        x1 = (x1 - offset_x) / scale;
        y1 = (y1 - offset_y) / scale;
        x2 = (x2 - offset_x) / scale;
        y2 = (y2 - offset_y) / scale;

        // 边界裁剪
        x1 = std::max(0.0f, std::min(x1, static_cast<float>(orig_width)));
        y1 = std::max(0.0f, std::min(y1, static_cast<float>(orig_height)));
        x2 = std::max(0.0f, std::min(x2, static_cast<float>(orig_width)));
        y2 = std::max(0.0f, std::min(y2, static_cast<float>(orig_height)));

        // 检查框大小是否合理
        if (x2 - x1 < 10 || y2 - y1 < 10)
        {
            continue;
        }

        BBox box;
        box.x1 = x1;
        box.y1 = y1;
        box.x2 = x2;
        box.y2 = y2;
        box.score = score;

        boxes.push_back(box);
        valid_count++;
    }

    LOGD("Valid boxes before NMS: %d", valid_count);

    // NMS
    std::vector<BBox> result = nms(boxes, NMS_THRESHOLD);
    LOGD("Boxes after NMS: %zu", result.size());

    return result;
}

std::vector<BBox> HandRknnEngine::nms(std::vector<BBox> &boxes, float iou_threshold)
{
    if (boxes.empty())
    {
        return {};
    }

    // 按 score 降序排序
    std::sort(boxes.begin(), boxes.end(), [](const BBox &a, const BBox &b)
              { return a.score > b.score; });

    std::vector<BBox> result;
    std::vector<bool> suppressed(boxes.size(), false);

    for (size_t i = 0; i < boxes.size(); i++)
    {
        if (suppressed[i])
        {
            continue;
        }

        result.push_back(boxes[i]);

        for (size_t j = i + 1; j < boxes.size(); j++)
        {
            if (suppressed[j])
            {
                continue;
            }

            // 计算 IoU
            float inter_x1 = std::max(boxes[i].x1, boxes[j].x1);
            float inter_y1 = std::max(boxes[i].y1, boxes[j].y1);
            float inter_x2 = std::min(boxes[i].x2, boxes[j].x2);
            float inter_y2 = std::min(boxes[i].y2, boxes[j].y2);

            float inter_area = std::max(0.0f, inter_x2 - inter_x1) *
                               std::max(0.0f, inter_y2 - inter_y1);

            float area_i = (boxes[i].x2 - boxes[i].x1) * (boxes[i].y2 - boxes[i].y1);
            float area_j = (boxes[j].x2 - boxes[j].x1) * (boxes[j].y2 - boxes[j].y1);

            float iou = inter_area / (area_i + area_j - inter_area);

            if (iou > iou_threshold)
            {
                suppressed[j] = true;
            }
        }
    }

    return result;
}
