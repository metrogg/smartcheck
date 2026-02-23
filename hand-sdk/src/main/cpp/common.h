#ifndef COMMON_H
#define COMMON_H

#include <vector>
#include <string>
#include <opencv2/opencv.hpp>

/**
 * 边界框
 */
struct BBox {
    float x1;
    float y1;
    float x2;
    float y2;
    float score;
};

/**
 * 关键点
 */
struct KeyPoint {
    float x;
    float y;
};

/**
 * 异物检测结果
 */
struct ForeignResult {
    std::vector<KeyPoint> keypoints;
    int class_id;
    std::string class_name;
    BBox box;  // 添加 box
    std::vector<BBox> boxes;
    std::vector<int> class_ids;
    std::vector<std::string> class_names;
    std::vector<float> scores;
};

/**
 * 手部检测最终结果
 */
struct HandResult {
    int id;
    BBox box;
    float score;
    std::vector<KeyPoint> keyPoints;
    bool hasForeignObject;
    std::string label;
};

/**
 * 裁剪并放大手部区域
 */
cv::Mat cropAndScale(uint8_t* pixels, int width, int height, const BBox& box, float scale_factor);

#endif // COMMON_H
