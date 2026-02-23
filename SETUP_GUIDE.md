# SmartCheck AI 项目配置指南

## 重要提示

在编译和运行项目之前，请确保完成以下**必需步骤**：

### 必需文件清单

- [ ] RKNN 模型文件（放在 `hand-sdk/src/main/assets/`）
  - `hand_check_rk3566.rknn` - 手部检测模型
  - `yiwu_check_rk3566.rknn` - 异物检测模型
- [ ] RKNN Runtime 库（已包含在 `hand-sdk/src/main/jniLibs/`）
- [ ] RKNN API 头文件（需替换 `hand-sdk/src/main/cpp/rknn_api/rknn_api.h`）
- [ ] OpenCV Android SDK（已包含在 `third_party/OpenCV-android-sdk/`）

---

## 项目结构

```
SmartCheck_AI/                        # 主项目（完全自包含）
├── app/
├── hand-sdk/
│   ├── src/main/cpp/
│   │   └── CMakeLists.txt            # 使用相对路径引用 OpenCV
│   ├── src/main/jniLibs/
│   │   ├── arm64-v8a/librknnrt.so
│   │   └── armeabi-v7a/librknnrt.so
│   └── src/main/assets/
│       ├── hand_check_rk3566.rknn
│       └── yiwu_check_rk3566.rknn
├── third_party/
│   └── OpenCV-android-sdk/           # OpenCV SDK（项目内部）
│       └── sdk/native/jni/
└── ...
```

**关键点**：所有依赖都在项目内部，克隆即用。

---

## 环境配置步骤

### 1. 放置 OpenCV SDK

1. 从 https://opencv.org/releases/ 下载 **OpenCV-4.8.0-android-sdk.zip**（或更新版本）
2. 在项目根目录创建 `third_party` 文件夹
3. 解压 OpenCV 到 `third_party/` 下，使其结构为：

   ```
   SmartCheck_AI/
   └── third_party/
       └── OpenCV-android-sdk/
           └── sdk/
               └── native/
                   └── jni/
                       ├── abi-armeabi-v7a/
                       ├── abi-arm64-v8a/
                       └── ...
   ```

### 2. 放置 RKNN Runtime 库

1. 从 https://github.com/rockchip-linux/rknn-toolkit2 下载 RKNN-Toolkit2
2. 找到 `rknpu2/runtime/Android/` 下的 `librknnrt.so`
3. 复制到项目中：

   ```
   SmartCheck_AI/hand-sdk/src/main/jniLibs/
   ├── arm64-v8a/
   │   └── librknnrt.so
   └── armeabi-v7a/
       └── librknnrt.so
   ```

### 3. 放置 RKNN API 头文件

1. 从 RKNN-Toolkit2 找到 `rknpu2/runtime/Linux/librknn_api/include/rknn_api.h`
2. 复制到项目中：

   ```
   SmartCheck_AI/hand-sdk/src/main/cpp/rknn_api/rknn_api.h
   ```

---

## 编译验证

完成上述配置后，在 Android Studio 中：

1. **Sync Project with Gradle Files**
2. **Build > Rebuild Project**
3. 检查 `Build` 窗口输出，确认：
   - CMake 配置成功
   - OpenCV 找到
   - `libhand_detector.so` 生成成功

---

## 相对路径说明

`CMakeLists.txt` 中使用的相对路径：

```cmake
get_filename_component(PROJECT_ROOT_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../.." ABSOLUTE)
set(OpenCV_DIR "${PROJECT_ROOT_DIR}/third_party/OpenCV-android-sdk/sdk/native/jni/abi-${ANDROID_ABI}")
```

路径解析：

- `${CMAKE_CURRENT_SOURCE_DIR}` = `SmartCheck_AI/hand-sdk/src/main/cpp/`
- `../../../..` = 向上 4 层 = `SmartCheck_AI/`
- `${ANDROID_ABI}` = `arm64-v8a` / `armeabi-v7a`
- 最终路径 = `SmartCheck_AI/third_party/OpenCV-android-sdk/sdk/native/jni/abi-${ANDROID_ABI}`

**这样做的好处**：
- ✅ 项目完全自包含，所有依赖都在内部
- ✅ 克隆项目后直接可用，无需额外配置
- ✅ 版本锁定，避免 OpenCV 版本不一致
- ✅ 便于 Git 管理（可选择是否提交 third_party）

---

## 常见问题

### Q: 编译时找不到 OpenCV

**A**: 检查以下几点：

1. `OpenCV-android-sdk` 是否在 `SmartCheck_AI/third_party/` 目录下
2. 目录名是否完全一致（大小写敏感）
3. 内部结构是否正确（应该有 `sdk/native/jni/` 这几层）

### Q: 编译时找不到 librknnrt.so

**A**: 检查以下几点：

1. `librknnrt.so` 是否在 `hand-sdk/src/main/jniLibs/{arch}/` 下
2. 是否同时放置了 `arm64-v8a` 和 `armeabi-v7a` 两个版本

### Q: 编译时找不到 rknn_api.h

**A**: 检查以下几点：

1. 是否已将官方 `rknn_api.h` 放到 `hand-sdk/src/main/cpp/rknn_api/` 目录
2. 文件名是否完全一致

---

## 项目分发流程

当你要把项目分发给他人时：

### 方案 1：完整打包（推荐）

直接打包整个项目，包含 OpenCV：

```
SmartCheck_AI/
├── app/
├── hand-sdk/
│   ├── src/main/jniLibs/     ✓ 包含 librknnrt.so
│   ├── src/main/cpp/
│   │   └── rknn_api/         ✓ 包含 rknn_api.h
│   └── src/main/assets/      ✓ 包含 .rknn 模型
├── third_party/
│   └── OpenCV-android-sdk/   ✓ 包含 OpenCV
└── ...
```

**优点**：他人克隆后直接编译，零配置。

### 方案 2：轻量打包

不包含 OpenCV（减小体积），提供说明文档：

1. 打包项目（不含 `third_party/`）
2. 提供 `SETUP_GUIDE.md`
3. 他人自行下载 OpenCV 放到 `third_party/OpenCV-android-sdk/`

**优点**：体积小，适合 Git 仓库。

---

## 后续优化建议

如果项目需要更灵活的配置，可以考虑：

1. **使用 CMake 变量**：在 `build.gradle.kts` 中通过 `-D` 参数传递路径
2. **创建 `local.properties`**：让用户配置本地依赖路径
3. **使用 Gradle 依赖管理**：通过 Maven 或其他包管理工具管理 OpenCV

但对于当前项目，相对路径已经足够简洁和易用。

---

## 快速检查清单

- [ ] `third_party/OpenCV-android-sdk/sdk/native/jni/` 存在
- [ ] `hand-sdk/src/main/jniLibs/arm64-v8a/librknnrt.so` 存在
- [ ] `hand-sdk/src/main/jniLibs/armeabi-v7a/librknnrt.so` 存在
- [ ] `hand-sdk/src/main/cpp/rknn_api/rknn_api.h` 存在（官方版本）
- [ ] `hand-sdk/src/main/assets/hand_check_rk3566.rknn` 存在
- [ ] `hand-sdk/src/main/assets/yiwu_check_rk3566.rknn` 存在
- [ ] Android Studio Sync 成功
- [ ] Rebuild Project 成功

全部打勾后，项目就可以编译运行了！
