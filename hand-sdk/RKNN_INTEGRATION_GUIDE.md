# RKNN 集成指南

## 概述

本文档说明如何将 RKNN Runtime 和 OpenCV 集成到 `hand-sdk` 模块中，以实现手部检测和异物检测功能。

---

## 1. 准备 RKNN Runtime 库

### 1.1 下载 RKNN-Toolkit2

从 Rockchip 官方仓库下载 RKNN-Toolkit2：

```bash
git clone https://github.com/rockchip-linux/rknn-toolkit2.git
```

### 1.2 提取 librknnrt.so

在 `rknn-toolkit2/rknpu2/runtime/Android/` 目录下，找到对应架构的 `librknnrt.so`：

- `arm64-v8a/librknnrt.so`
- `armeabi-v7a/librknnrt.so`

### 1.3 复制到项目

将 `librknnrt.so` 复制到以下目录：

```
SmartCheck_AI/hand-sdk/src/main/jniLibs/
├── arm64-v8a/
│   └── librknnrt.so
└── armeabi-v7a/
    └── librknnrt.so
```

### 1.4 复制 RKNN API 头文件

将 `rknn-toolkit2/rknpu2/runtime/Linux/librknn_api/include/rknn_api.h` 复制到：

```
SmartCheck_AI/hand-sdk/src/main/cpp/rknn_api/rknn_api.h
```

**注意**：当前项目中已有一个占位符头文件，请用官方头文件替换。

---

## 2. 准备 OpenCV Android SDK

### 2.1 下载 OpenCV

从 OpenCV 官网下载 Android SDK：

https://opencv.org/releases/

推荐版本：**OpenCV 4.8.0** 或更高

### 2.2 解压并放置

将下载的 `opencv-android-sdk` 解压到项目根目录或其他位置，例如：

```
D:/opencv-android-sdk/
```

### 2.3 修改 CMakeLists.txt

编辑 `hand-sdk/src/main/cpp/CMakeLists.txt`，将 `OpenCV_DIR` 路径修改为实际路径：

```cmake
# 当前占位符路径
set(OpenCV_DIR "${CMAKE_SOURCE_DIR}/../../../../../opencv-android-sdk/sdk/native/jni")

# 修改为实际路径，例如：
set(OpenCV_DIR "D:/opencv-android-sdk/sdk/native/jni")
```

---

## 3. 配置 CMakeLists.txt

### 3.1 更新 RKNN 库路径

编辑 `hand-sdk/src/main/cpp/CMakeLists.txt`，确认 RKNN 库路径正确：

```cmake
set(RKNN_LIB_DIR "${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}")
```

这会自动根据编译架构（`arm64-v8a` 或 `armeabi-v7a`）选择对应的 `librknnrt.so`。

### 3.2 验证链接配置

确保以下库正确链接：

```cmake
target_link_libraries(hand_detector
    ${log-lib}
    ${jnigraphics-lib}
    ${OpenCV_LIBS}
    ${RKNN_LIB_DIR}/librknnrt.so
)
```

---

## 4. 编译项目

### 4.1 清理并重新构建

在 Android Studio 中：

1. **Clean Project**: `Build > Clean Project`
2. **Rebuild Project**: `Build > Rebuild Project`

### 4.2 检查编译输出

查看 `Build` 窗口，确认：

- CMake 配置成功
- OpenCV 找到并链接
- `libhand_detector.so` 生成成功

### 4.3 常见错误

#### 错误 1: `rknn_api.h: No such file or directory`

**解决**：确认 `rknn_api.h` 已复制到 `hand-sdk/src/main/cpp/rknn_api/` 目录。

#### 错误 2: `librknnrt.so: cannot open shared object file`

**解决**：确认 `librknnrt.so` 已复制到 `hand-sdk/src/main/jniLibs/{arch}/` 目录。

#### 错误 3: `OpenCV not found`

**解决**：检查 `CMakeLists.txt` 中的 `OpenCV_DIR` 路径是否正确。

---

## 5. 运行时验证

### 5.1 初始化检测器

在 `MainActivity` 或 `MainViewModel` 中调用：

```kotlin
val ret = HandDetector.init(context)
if (ret == 0) {
    Log.d("App", "HandDetector initialized successfully")
} else {
    Log.e("App", "Failed to initialize HandDetector")
}
```

### 5.2 执行检测

```kotlin
val bitmap: Bitmap = ... // 从相机获取
val results = HandDetector.detect(bitmap)

results.forEach { handInfo ->
    Log.d("App", "Hand ${handInfo.id}: ${handInfo.label}, score=${handInfo.score}")
}
```

### 5.3 查看日志

使用 `adb logcat` 或 Android Studio Logcat 查看：

```
HandDetectorJNI: nativeInit called
HandRknnEngine: Model size: 12345678 bytes
HandRknnEngine: Hand model loaded successfully
ForeignRknnEngine: Foreign model loaded successfully
HandDetectorJNI: Models loaded successfully
HandDetector: HandDetector initialized successfully
```

---

## 6. 目录结构总览

```
SmartCheck_AI/
├── hand-sdk/
│   ├── src/
│   │   ├── main/
│   │   │   ├── assets/
│   │   │   │   ├── hand_check_rk3566.rknn
│   │   │   │   └── yiwu_check_rk3566.rknn
│   │   │   ├── cpp/
│   │   │   │   ├── CMakeLists.txt
│   │   │   │   ├── hand_detector_jni.cpp
│   │   │   │   ├── hand_rknn_engine.h
│   │   │   │   ├── hand_rknn_engine.cpp
│   │   │   │   ├── foreign_rknn_engine.h
│   │   │   │   ├── foreign_rknn_engine.cpp
│   │   │   │   ├── common.h
│   │   │   │   └── rknn_api/
│   │   │   │       └── rknn_api.h (从 RKNN-Toolkit2 获取)
│   │   │   ├── jniLibs/
│   │   │   │   ├── arm64-v8a/
│   │   │   │   │   └── librknnrt.so
│   │   │   │   └── armeabi-v7a/
│   │   │   │       └── librknnrt.so
│   │   │   └── java/com/smartcheck/sdk/
│   │   │       └── HandDetector.kt
│   │   └── build.gradle.kts
│   └── RKNN_INTEGRATION_GUIDE.md (本文档)
└── opencv-android-sdk/ (外部依赖，需自行下载)
```

---

## 7. 下一步

- **调整后处理逻辑**：根据实际模型输出调整 `postprocess()` 函数
- **优化性能**：使用 RKNN 的 Zero-Copy 模式减少内存拷贝
- **添加类别映射**：根据 `yiwu_check_rk3566.rknn` 的类别输出，完善异物判断逻辑
- **多线程推理**：使用 Kotlin Coroutines 在后台线程执行推理

---

## 8. 参考资料

- [RKNN-Toolkit2 官方文档](https://github.com/rockchip-linux/rknn-toolkit2)
- [OpenCV Android SDK](https://opencv.org/releases/)
- [Android NDK 开发指南](https://developer.android.com/ndk)
