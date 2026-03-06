#include "foreign_rknn_engine.h"
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <android/log.h>
#include <cmath>
#include <algorithm>

#define TAG "ForeignRknnEngine"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

#define MODEL_INPUT_SIZE 640
#define CONF_THRESHOLD 0.55f
#define BAND_AID_THRESHOLD 0.65f
#define NMS_THRESHOLD 0.45f
#define NUM_KEYPOINTS 21

// Sigmoid 激活函数
static inline float sigmoid(float x) {
    return 1.0f / (1.0f + expf(-x));
}

struct ForeignCandidate {
    BBox box;
    int class_id;
    float score;
    int idx;
};

static inline float iou(const BBox& a, const BBox& b) {
    float inter_x1 = std::max(a.x1, b.x1);
    float inter_y1 = std::max(a.y1, b.y1);
    float inter_x2 = std::min(a.x2, b.x2);
    float inter_y2 = std::min(a.y2, b.y2);

    float inter_w = std::max(0.0f, inter_x2 - inter_x1);
    float inter_h = std::max(0.0f, inter_y2 - inter_y1);
    float inter_area = inter_w * inter_h;
    float area_a = std::max(0.0f, a.x2 - a.x1) * std::max(0.0f, a.y2 - a.y1);
    float area_b = std::max(0.0f, b.x2 - b.x1) * std::max(0.0f, b.y2 - b.y1);
    float denom = area_a + area_b - inter_area;
    return denom > 0.0f ? (inter_area / denom) : 0.0f;
}

static std::vector<ForeignCandidate> nms_class_aware(std::vector<ForeignCandidate>& candidates, float iou_threshold) {
    if (candidates.empty()) return {};

    std::sort(candidates.begin(), candidates.end(), [](const ForeignCandidate& a, const ForeignCandidate& b) {
        return a.score > b.score;
    });

    std::vector<ForeignCandidate> result;
    std::vector<bool> suppressed(candidates.size(), false);

    for (size_t i = 0; i < candidates.size(); i++) {
        if (suppressed[i]) continue;
        result.push_back(candidates[i]);
        for (size_t j = i + 1; j < candidates.size(); j++) {
            if (suppressed[j]) continue;
            if (candidates[i].class_id != candidates[j].class_id) continue;
            if (iou(candidates[i].box, candidates[j].box) > iou_threshold) {
                suppressed[j] = true;
            }
        }
    }

    return result;
}

ForeignRknnEngine::ForeignRknnEngine() 
    : ctx_(0), input_attrs_(nullptr), output_attrs_(nullptr),
      model_width_(MODEL_INPUT_SIZE), model_height_(MODEL_INPUT_SIZE), model_channel_(3) {
}

ForeignRknnEngine::~ForeignRknnEngine() {
    if (ctx_ > 0) {
        rknn_destroy(ctx_);
    }
    if (input_attrs_) {
        delete[] input_attrs_;
    }
    if (output_attrs_) {
        delete[] output_attrs_;
    }
}

int ForeignRknnEngine::loadModelFromAssets(JNIEnv* env, jobject assetManager, const char* model_name) {
    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);
    AAsset* asset = AAssetManager_open(mgr, model_name, AASSET_MODE_BUFFER);
    
    if (!asset) {
        LOGE("Failed to open asset: %s", model_name);
        return -1;
    }
    
    size_t model_size = AAsset_getLength(asset);
    void* model_data = malloc(model_size);
    AAsset_read(asset, model_data, model_size);
    AAsset_close(asset);
    
    LOGD("Model size: %zu bytes", model_size);
    
    int ret = rknn_init(&ctx_, model_data, model_size, 0, nullptr);
    free(model_data);
    
    if (ret < 0) {
        LOGE("rknn_init failed: %d", ret);
        return -1;
    }
    
    ret = rknn_query(ctx_, RKNN_QUERY_IN_OUT_NUM, &io_num_, sizeof(io_num_));
    if (ret < 0) {
        LOGE("rknn_query io_num failed: %d", ret);
        return -1;
    }
    
    LOGD("Model input num: %d, output num: %d", io_num_.n_input, io_num_.n_output);
    
    input_attrs_ = new rknn_tensor_attr[io_num_.n_input];
    memset(input_attrs_, 0, sizeof(rknn_tensor_attr) * io_num_.n_input);
    for (uint32_t i = 0; i < io_num_.n_input; i++) {
        input_attrs_[i].index = i;
        ret = rknn_query(ctx_, RKNN_QUERY_INPUT_ATTR, &(input_attrs_[i]), sizeof(rknn_tensor_attr));
        if (ret < 0) {
            LOGE("rknn_query input attr %d failed: %d", i, ret);
            return -1;
        }
    }
    
    output_attrs_ = new rknn_tensor_attr[io_num_.n_output];
    memset(output_attrs_, 0, sizeof(rknn_tensor_attr) * io_num_.n_output);
    for (uint32_t i = 0; i < io_num_.n_output; i++) {
        output_attrs_[i].index = i;
        ret = rknn_query(ctx_, RKNN_QUERY_OUTPUT_ATTR, &(output_attrs_[i]), sizeof(rknn_tensor_attr));
        if (ret < 0) {
            LOGE("rknn_query output attr %d failed: %d", i, ret);
            return -1;
        }
        LOGD("Output %d: n_dims=%d, dims=[%d,%d,%d,%d], n_elems=%d, size=%d", i,
             output_attrs_[i].n_dims,
             output_attrs_[i].dims[0], output_attrs_[i].dims[1], 
             output_attrs_[i].dims[2], output_attrs_[i].dims[3],
             output_attrs_[i].n_elems, output_attrs_[i].size);
    }
    
    LOGD("Foreign model loaded successfully");
    return 0;
}

ForeignResult ForeignRknnEngine::detect(const cv::Mat& hand_crop) {
    ForeignResult result;
    result.class_id = -1; // 默认正常
    result.class_name = "Normal";
    result.box.x1 = 0.0f;
    result.box.y1 = 0.0f;
    result.box.x2 = 0.0f;
    result.box.y2 = 0.0f;
    result.box.score = 0.0f;
    
    if (hand_crop.empty()) {
        LOGE("Empty hand crop");
        return result;
    }
    
    // 1. 前处理 - 使用 UINT8 输入
    uint8_t* input_data = new uint8_t[model_width_ * model_height_ * model_channel_];
    
    // Letterbox resize (保持宽高比)
    cv::Mat rgb;
    // hand_crop 可能是 BGR (从 hand_detector_jni 传入) 或者 RGB?
    if (hand_crop.channels() == 4) {
        cv::cvtColor(hand_crop, rgb, cv::COLOR_RGBA2RGB);
    } else {
        // 假设是 BGR (OpenCV 默认) 转 RGB
        cv::cvtColor(hand_crop, rgb, cv::COLOR_BGR2RGB);
    }
    
    float scale = std::min(
        static_cast<float>(model_width_) / hand_crop.cols,
        static_cast<float>(model_height_) / hand_crop.rows
    );
    
    int new_w = static_cast<int>(hand_crop.cols * scale);
    int new_h = static_cast<int>(hand_crop.rows * scale);
    
    cv::Mat resized;
    cv::resize(rgb, resized, cv::Size(new_w, new_h));
    
    // 创建 640x640 的画布，填充灰色 (114, 114, 114)
    cv::Mat canvas(model_height_, model_width_, CV_8UC3, cv::Scalar(114, 114, 114));
    
    // 居中放置
    int offset_x = (model_width_ - new_w) / 2;
    int offset_y = (model_height_ - new_h) / 2;
    resized.copyTo(canvas(cv::Rect(offset_x, offset_y, new_w, new_h)));
    
    // 复制数据
    memcpy(input_data, canvas.data, model_width_ * model_height_ * model_channel_);
    
    // 2. 设置输入 - 使用 RKNN_TENSOR_UINT8 类型
    rknn_input inputs[1];
    memset(inputs, 0, sizeof(inputs));
    inputs[0].index = 0;
    inputs[0].type = RKNN_TENSOR_UINT8;
    inputs[0].size = model_width_ * model_height_ * model_channel_;
    inputs[0].fmt = RKNN_TENSOR_NHWC;
    inputs[0].pass_through = 0;  // 自动处理 mean/std
    inputs[0].buf = input_data;
    
    int ret = rknn_inputs_set(ctx_, io_num_.n_input, inputs);
    if (ret < 0) {
        LOGE("rknn_inputs_set failed: %d", ret);
        delete[] input_data;
        return result;
    }
    
    // 3. 执行推理
    ret = rknn_run(ctx_, nullptr);
    if (ret < 0) {
        LOGE("rknn_run failed: %d", ret);
        delete[] input_data;
        return result;
    }
    
    // 4. 获取输出 - 使用 want_float=1 让 RKNN 自动反量化
    rknn_output outputs[1];
    memset(outputs, 0, sizeof(outputs));
    outputs[0].want_float = 1;  // 让 RKNN 自动反量化为 float
    outputs[0].is_prealloc = 0;
    
    ret = rknn_outputs_get(ctx_, io_num_.n_output, outputs, nullptr);
    if (ret < 0) {
        LOGE("rknn_outputs_get failed: %d", ret);
        delete[] input_data;
        return result;
    }
    
    // 5. 直接使用浮点输出
    const int num_boxes = 8400;
    const int num_features = 53;
    float* float_output = (float*)outputs[0].buf;
    
    LOGD("Output buffer size: %d", outputs[0].size);
    
    // 调试: 打印输出的前几个值
    LOGD("Foreign output [53, 8400]:");
    for (int c = 0; c < 10; c++) {
        float* ptr = float_output + c * num_boxes;
        LOGD("Channel %d (first 5): %.4f, %.4f, %.4f, %.4f, %.4f", 
             c, ptr[0], ptr[1], ptr[2], ptr[3], ptr[4]);
    }
    
    // 检查各通道的最大值
    float* objectness_ch = float_output + 4 * num_boxes;
    float max_objectness = 0.0f;
    for (int i = 0; i < num_boxes; i++) {
        if (objectness_ch[i] > max_objectness) max_objectness = objectness_ch[i];
    }
    LOGD("Objectness channel max: %.4f", max_objectness);
    
    const int class_start = 4;
    const int num_classes = 7;

    // 检查类别通道
    for (int c = 0; c < num_classes; c++) {
        float* class_ch = float_output + (class_start + c) * num_boxes;
        float max_class = 0.0f;
        for (int i = 0; i < num_boxes; i++) {
            if (class_ch[i] > max_class) max_class = class_ch[i];
        }
        LOGD("Class %d channel max: %.4f", c, max_class);
    }
    
    result = postprocess(float_output, hand_crop.cols, hand_crop.rows);
    
    // 6. 清理
    rknn_outputs_release(ctx_, io_num_.n_output, outputs);
    delete[] input_data;
    
    return result;
}

ForeignResult ForeignRknnEngine::postprocess(float* output, int crop_width, int crop_height) {
    // 输出格式: (1, 53, 8400)
    // YOLOv8-pose 输出:
    // 0-3: bbox (x, y, w, h)
    // 4: objectness score
    // 5-11: 7 个类别概率 (band_aid, bracelet, ring, watch, toilet_paper, foreign, hand)
    const int num_boxes = 8400;
    const int num_features = 53;
    const int num_classes = 7;
    const int class_start = 4;
    const int hand_class_id = 6;
    
    ForeignResult result;
    result.class_id = -1;
    result.class_name = "Normal";
    result.box.x1 = 0.0f;
    result.box.y1 = 0.0f;
    result.box.x2 = 0.0f;
    result.box.y2 = 0.0f;
    result.box.score = 0.0f;
    
    // 计算缩放比例
    float scale = std::min(
        static_cast<float>(model_width_) / crop_width,
        static_cast<float>(model_height_) / crop_height
    );
    int offset_x = (model_width_ - static_cast<int>(crop_width * scale)) / 2;
    int offset_y = (model_height_ - static_cast<int>(crop_height * scale)) / 2;
    
    const char* class_names[] = {"band_aid", "bracelet", "ring", "watch", "toilet_paper", "foreign", "hand"};

    float sample_max = -1e9f;
    float sample_min = 1e9f;
    int sample_step = 13;
    int sample_count = 0;
    for (int i = 0; i < num_boxes && sample_count < 200; i += sample_step, sample_count++) {
        for (int c = 0; c < num_classes; c++) {
            float v = output[num_boxes * (class_start + c) + i];
            sample_max = std::max(sample_max, v);
            sample_min = std::min(sample_min, v);
        }
    }
    const bool need_sigmoid = (sample_max > 1.0f) || (sample_min < 0.0f);

    int best_hand_idx = -1;
    float best_hand_score = 0.0f;

    std::vector<ForeignCandidate> candidates;
    candidates.reserve(64);

    for (int i = 0; i < num_boxes; i++) {
        float hand_prob_raw = output[num_boxes * (class_start + hand_class_id) + i];
        float hand_prob = need_sigmoid ? sigmoid(hand_prob_raw) : hand_prob_raw;
        if (hand_prob > best_hand_score) {
            best_hand_score = hand_prob;
            best_hand_idx = i;
        }

        float max_class_prob = 0.0f;
        int max_class_id = -1;

        for (int c = 0; c < num_classes - 1; c++) {
            float class_prob_raw = output[num_boxes * (class_start + c) + i];
            float class_prob = need_sigmoid ? sigmoid(class_prob_raw) : class_prob_raw;
            if (class_prob > max_class_prob) {
                max_class_prob = class_prob;
                max_class_id = c;
            }
        }

        float score = max_class_prob;
        
        float threshold = CONF_THRESHOLD;
        if (max_class_id == 0) {
            threshold = BAND_AID_THRESHOLD;
        }
        
        if (score < threshold) continue;

        float cx = output[num_boxes * 0 + i];
        float cy = output[num_boxes * 1 + i];
        float w = output[num_boxes * 2 + i];
        float h = output[num_boxes * 3 + i];

        // 反向映射到裁剪图坐标
        cx = (cx - offset_x) / scale;
        cy = (cy - offset_y) / scale;
        w = w / scale;
        h = h / scale;

        BBox box;
        box.x1 = std::max(0.0f, cx - w / 2);
        box.y1 = std::max(0.0f, cy - h / 2);
        box.x2 = std::min((float)crop_width, cx + w / 2);
        box.y2 = std::min((float)crop_height, cy + h / 2);
        box.score = score;

        if (box.x2 <= box.x1 || box.y2 <= box.y1) continue;
        if ((box.x2 - box.x1) < 4.0f || (box.y2 - box.y1) < 4.0f) continue;

        ForeignCandidate cand;
        cand.box = box;
        cand.class_id = max_class_id;
        cand.score = score;
        cand.idx = i;
        candidates.push_back(cand);
    }

    std::vector<ForeignCandidate> kept = nms_class_aware(candidates, NMS_THRESHOLD);
    const int max_keep = 20;
    if ((int)kept.size() > max_keep) {
        kept.resize(max_keep);
    }

    result.boxes.reserve(kept.size());
    result.class_ids.reserve(kept.size());
    result.class_names.reserve(kept.size());
    result.scores.reserve(kept.size());

    for (const auto& det : kept) {
        result.boxes.push_back(det.box);
        result.class_ids.push_back(det.class_id);
        result.class_names.push_back(class_names[det.class_id]);
        result.scores.push_back(det.score);
    }

    if (!kept.empty()) {
        result.class_id = kept[0].class_id;
        result.class_name = class_names[result.class_id];
        result.box = kept[0].box;
        result.box.score = kept[0].score;
    } else {
        LOGD("No detection above threshold");
    }

    int kp_idx = -1;
    if (best_hand_idx >= 0) {
        kp_idx = best_hand_idx;
    } else if (!kept.empty()) {
        kp_idx = kept[0].idx;
    }

    const int kp_start = class_start + num_classes;
    if (kp_idx >= 0) {
        for (int k = 0; k < NUM_KEYPOINTS; k++) {
            float kx = output[num_boxes * (kp_start + k * 2) + kp_idx];
            float ky = output[num_boxes * (kp_start + k * 2 + 1) + kp_idx];

            kx = (kx - offset_x) / scale;
            ky = (ky - offset_y) / scale;

            kx = std::max(0.0f, std::min(kx, static_cast<float>(crop_width)));
            ky = std::max(0.0f, std::min(ky, static_cast<float>(crop_height)));

            KeyPoint kp;
            kp.x = kx;
            kp.y = ky;
            result.keypoints.push_back(kp);
        }
    }

    return result;
}
