# SmartCheck AI - 智能晨检系统

基于 Android 工业主板的 AIoT 晨检设备，集成人脸识别、红外测温、手部异物检测三大功能。

## 项目结构

```
SmartCheck_AI/
├── app/                          # 主应用模块
│   ├── src/main/
│   │   ├── java/com/smartcheck/app/
│   │   │   ├── App.kt           # Application 入口
│   │   │   ├── MainActivity.kt   # 主 Activity
│   │   │   ├── data/            # 数据层
│   │   │   │   ├── db/          # Room 数据库
│   │   │   │   │   ├── AppDatabase.kt
│   │   │   │   │   ├── UserEntity.kt
│   │   │   │   │   ├── RecordEntity.kt
│   │   │   │   │   ├── UserDao.kt
│   │   │   │   │   └── RecordDao.kt
│   │   │   │   ├── repository/  # Repository 层
│   │   │   │   │   ├── UserRepository.kt
│   │   │   │   │   ├── RecordRepository.kt
│   │   │   │   │   └── HardwareRepository.kt
│   │   │   │   └── serial/      # 串口通信
│   │   │   │       └── SerialPortManager.kt
│   │   │   ├── ml/              # 人脸识别
│   │   │   │   ├── FaceEngine.kt
│   │   │   │   └── MockFaceEngine.kt
│   │   │   ├── viewmodel/       # ViewModel 层
│   │   │   │   ├── CheckState.kt
│   │   │   │   ├── MainViewModel.kt
│   │   │   │   └── AdminViewModel.kt
│   │   │   ├── ui/              # UI 层
│   │   │   │   ├── theme/       # 主题
│   │   │   │   ├── components/  # 组件
│   │   │   │   │   ├── CameraPreview.kt
│   │   │   │   │   └── ResultCard.kt
│   │   │   │   ├── screens/     # 页面
│   │   │   │   │   ├── HomeScreen.kt
│   │   │   │   │   └── AdminScreen.kt
│   │   │   │   └── navigation/  # 导航
│   │   │   │       └── AppNavigation.kt
│   │   │   └── di/              # 依赖注入
│   │   │       └── AppModule.kt
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
│
├── hand-sdk/                     # 手部异物检测 SDK 模块（RKNN + OpenCV + JNI）
│   ├── src/main/
│   │   ├── java/com/smartcheck/sdk/
│   │   │   └── HandDetector.kt  # Kotlin API（JNI 调用）
│   │   ├── cpp/                 # C++ 推理实现（CMake + JNI）
│   │   │   ├── CMakeLists.txt
│   │   │   ├── hand_detector_jni.cpp
│   │   │   ├── hand_rknn_engine.*
│   │   │   ├── foreign_rknn_engine.*
│   │   │   └── rknn_api/rknn_api.h
│   │   ├── assets/              # RKNN 模型
│   │   │   ├── hand_check_rk3566.rknn
│   │   │   └── yiwu_check_rk3566.rknn
│   │   ├── jniLibs/             # RKNN Runtime
│   │   │   ├── arm64-v8a/librknnrt.so
│   │   │   └── armeabi-v7a/librknnrt.so
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
│
├── settings.gradle.kts
├── build.gradle.kts
└── README.md
```

## 核心功能模块

### 1. 状态机（CheckState）

严格的线性状态流转：

```
IDLE → FACE_PASS → TEMP_MEASURING → HAND_CHECKING → ALL_PASS
         ↓              ↓                  ↓
      (失败)        TEMP_FAIL         HAND_FAIL
```

### 2. 人脸识别（FaceEngine）

- **当前实现**：MockFaceEngine（模拟识别）
- **未来集成**：SeetaFace2 C++ SDK
  - 人脸检测
  - 关键点定位
  - 特征提取与比对
  - 活体检测

### 3. 手部检测（HandDetector）

- **当前实现**：RKNN + OpenCV + JNI（已集成）
  - 模型文件（assets）：`hand_check_rk3566.rknn`、`yiwu_check_rk3566.rknn`
  - RKNN Runtime（jniLibs）：`librknnrt.so`
  - OpenCV（third_party）：用于前处理（颜色空间转换、Letterbox、裁剪）
  - 推理链路：整图手部检测 → 裁剪放大（1.5x）→ 异物/关键点检测
- **后续可选**：模型加密（`.rknn.enc`）与授权校验
- **注意**：RKNN 推理需要在支持 Rockchip NPU 的 ARM 真机上验证，模拟器不支持

### 4. 硬件通信（SerialPortManager）

- 红外测温模块
- 蜂鸣器控制

### 5. 数据持久化（Room）

- 用户信息（UserEntity）
- 晨检记录（RecordEntity）

## 技术栈

- **语言**：Kotlin
- **UI 框架**：Jetpack Compose + Material3
- **架构**：MVVM + Repository
- **依赖注入**：Hilt
- **数据库**：Room
- **相机**：CameraX
- **异步**：Kotlin Coroutines + Flow
- **日志**：Timber

## 开发状态

### ✅ 已完成

- [x] 多模块工程骨架
- [x] hand-sdk 手部异物检测（RKNN + OpenCV + JNI）
- [x] 状态机核心逻辑
- [x] FaceEngine 接口 + Mock 实现
- [x] 硬件通信层框架
- [x] Room 数据库设计
- [x] UI 界面（HomeScreen + AdminScreen）
- [x] CameraX 集成
- [x] Hilt 依赖注入配置

### 🚧 待完成

- [ ] SeetaFace2 C++ 集成
- [ ] 真实串口通信实现
- [ ] 用户管理功能
- [ ] 数据导出功能

## 构建与运行

### 前置要求

- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 17
- Android SDK 34
- Gradle 8.2+

### 构建步骤

1. 克隆项目到本地
2. 用 Android Studio 打开 `SmartCheck_AI` 目录
3. 等待 Gradle 同步完成
4. 连接 Android 设备或启动模拟器
5. 点击 Run 按钮

### 注意事项

- 需要授予相机权限
- 人脸识别与串口通信当前仍为 Mock（用于跑通业务流程）
- 手部异物检测依赖 RKNN（需要物理设备 + Rockchip NPU 环境），模拟器不支持

## 后续集成指南

### 集成 SeetaFace2

推荐参考 `hand-sdk` 的组织方式进行集成：

1. 选择集成方式：
   - 方案 A（推荐）：新增独立 `face-sdk` 模块（类似 `hand-sdk`），对外提供 Kotlin API
   - 方案 B：直接在 `app/src/main/cpp/` 下添加 JNI 封装
2. 编译 SeetaFace2 为 Android `.so`（放入 `jniLibs/{abi}`）
3. 放置 SeetaFace2 模型文件到 `assets/` 并通过 `AssetManager` 加载
4. 实现 `SeetaFaceEngine` 替换 `MockFaceEngine`，并在 `AppModule.kt` 中切换依赖注入
5. 将人脸特征向量写入 `UserEntity.faceEmbedding`，并补齐注册/管理流程

### 集成 RKNN 手部检测

该模块已完成集成（`hand-sdk`）。

验证/配置要点：

1. 确保 `hand-sdk/src/main/assets/` 下存在两个 `.rknn` 模型
2. 确保 `hand-sdk/src/main/jniLibs/{arm64-v8a,armeabi-v7a}/librknnrt.so` 存在
3. 确保 `third_party/OpenCV-android-sdk/` 路径正确（CMake 会按相对路径查找）
4. 参考 `SETUP_GUIDE.md` 进行环境检查

### 集成真实串口

1. 添加 android-serialport-api 依赖
2. 在 `SerialPortManager.kt` 中实现真实串口读写
3. 根据测温模块协议解析数据

## 许可证

本项目仅供学习和研究使用。

## 作者

单兵开发者
