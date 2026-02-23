cmake_minimum_required(VERSION 3.22.1)

project("hand_detector")

# 设置 C++ 标准
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 添加源文件
add_library(hand_detector SHARED
    hand_detector_jni.cpp
    hand_rknn_engine.cpp
    foreign_rknn_engine.cpp
)

# 查找依赖库
find_library(log-lib log)
find_library(jnigraphics-lib jnigraphics)

# OpenCV 配置（项目内部自包含）
# OpenCV SDK 放在项目根目录的 third_party 文件夹中
# CMAKE_CURRENT_SOURCE_DIR = hand-sdk/src/main/cpp
# 向上 4 层到达项目根目录 SmartCheck_AI
get_filename_component(PROJECT_ROOT_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../.." ABSOLUTE)
set(OpenCV_DIR "${PROJECT_ROOT_DIR}/third_party/OpenCV-android-sdk/sdk/native/jni/abi-${ANDROID_ABI}")
message(STATUS "CMAKE_CURRENT_SOURCE_DIR: ${CMAKE_CURRENT_SOURCE_DIR}")
message(STATUS "PROJECT_ROOT_DIR: ${PROJECT_ROOT_DIR}")
message(STATUS "OpenCV_DIR: ${OpenCV_DIR}")
message(STATUS "ANDROID_ABI: ${ANDROID_ABI}")
find_package(OpenCV REQUIRED)

# RKNN Runtime 库路径（需要从 Rockchip SDK 获取）
# 请将以下路径替换为实际的 librknnrt.so 路径
set(RKNN_LIB_DIR "${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}")

# 添加 RKNN 头文件路径
include_directories(
    ${CMAKE_SOURCE_DIR}
    ${CMAKE_SOURCE_DIR}/rknn_api
    ${OpenCV_INCLUDE_DIRS}
)

# 链接库
target_link_libraries(hand_detector
    ${log-lib}
    ${jnigraphics-lib}
    ${OpenCV_LIBS}
    ${RKNN_LIB_DIR}/librknnrt.so
)
