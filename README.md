# SmartCheck AI - 智能晨检系统

基于 Android 工业主板的 AIoT 晨检设备，集成人脸识别（SeetaFace6）、红外测温、手部异物检测三大功能。

## 项目结构

```
smartcheck/
├── app/                          # 主应用模块
│   ├── src/main/
│   │   ├── java/com/smartcheck/app/
│   │   │   ├── api/              # 网络层
│   │   │   │   ├── ApiService.kt       # HTTP API
│   │   │   │   ├── KtorServerManager.kt # 内嵌 Ktor 服务器
│   │   │   │   ├── JwtUtil.kt          # JWT 工具
│   │   │   │   └── model/               # API 模型
│   │   │   ├── data/              # 数据层实现
│   │   │   │   ├── db/            # Room 数据库
│   │   │   │   ├── repository/    # Repository 实现
│   │   │   │   └── serial/         # 串口通信
│   │   │   ├── domain/            # 领域层
│   │   │   │   ├── model/         # 领域模型
│   │   │   │   ├── repository/    # Repository 接口
│   │   │   │   └── usecase/       # 用例
│   │   │   ├── utils/             # 工具类
│   │   │   │   ├── FileLoggingTree.kt # 文件日志
│   │   │   │   ├── DeviceInfo.kt      # 设备信息
│   │   │   │   └── DeviceAuth.kt      # 设备认证
│   │   │   ├── voice/             # 语音播报
│   │   │   ├── viewmodel/         # ViewModel 层
│   │   │   ├── ui/                # UI 层 (Compose)
│   │   │   ├── di/                # Hilt 依赖注入
│   │   │   ├── App.kt             # Application
│   │   │   └── MainActivity.kt    # 主 Activity
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
│
├── hand-sdk/                     # 手部异物检测 SDK（RKNN + OpenCV + JNI）
│   ├── src/main/
│   │   ├── java/com/smartcheck/sdk/
│   │   │   └── HandDetector.kt    # Kotlin API
│   │   ├── cpp/                   # C++ JNI + RKNN 推理
│   │   ├── assets/                # RKNN 模型
│   │   └── jniLibs/               # RKNN Runtime
│   └── build.gradle.kts
│
├── face-sdk/                     # 人脸识别 SDK（SeetaFace6）
│   ├── src/main/
│   │   ├── java/com/smartcheck/sdk/face/
│   │   │   ├── FaceSdk.kt         # Kotlin API
│   │   │   └── FaceInfo.kt        # 人脸信息模型
│   │   ├── cpp/                   # C++ JNI 封装
│   │   ├── assets/                # SeetaFace6 模型
│   │   └── jniLibs/               # SeetaFace6 动态库
│   └── build.gradle.kts
│
├── settings.gradle.kts
├── build.gradle.kts
├── AGENTS.md                     # Agent 开发规范
├── SETUP_GUIDE.md                # 环境配置指南
└── README.md
```

## 核心功能

### 1. 状态机（CheckState）

严格的线性状态流转：

```
IDLE → FACE_PASS → TEMP_MEASURING → HAND_CHECKING → ALL_PASS
         ↓              ↓                  ↓
      (失败)        TEMP_FAIL         HAND_FAIL
```

### 2. 人脸识别（face-sdk / SeetaFace6）

- **SDK**: SeetaFace6 Android
- **功能模块**:
  - 人脸检测 (FaceDetector)
  - 关键点定位 (FaceLandmarker)
  - 特征提取与比对 (FaceRecognizer)
  - 活体检测 (FaceAntiSpoofingX)
  - 口罩检测 (MaskDetector)
  - 性别/年龄估计
- **模型文件**: `fd_2_00.dat`, `pd_2_00_pts5.dat`, `fr_2_10.dat`, `fas_first/second.csta` 等

### 3. 手部检测（hand-sdk / RKNN）

- **推理框架**: RKNN + OpenCV + JNI
- **功能模块**:
  - 手部检测（整图）
  - 手掌/手背异物检测
- **模型文件**: `hand_check_rk3566.rknn`, `yiwu_check_rk3566.rknn`
- **注意**: 需要 Rockchip NPU 环境（RK3566 系列），模拟器不支持

### 4. 硬件抽象

- 红外测温模块（接口已定义，待接入真实串口）
- 蜂鸣器控制
- 语音播报（TTS）

### 5. 设备激活与认证

- 激活码管理（服务器端 + 设备端）
- 内网穿透部署（natapp）
- JWT 身份验证

### 6. 数据管理

- 本地 Room 数据库
- 晨检记录持久化
- 报表导出（CSV）
- 数据上传（可选）

## 技术栈

- **语言**: Kotlin
- **UI 框架**: Jetpack Compose + Material3
- **架构**: MVVM + Clean Architecture + Repository
- **依赖注入**: Hilt
- **数据库**: Room
- **相机**: CameraX
- **网络**: Retrofit + OkHttp + Ktor（内嵌服务器）
- **异步**: Kotlin Coroutines + Flow
- **日志**: Timber + 文件日志轮转

## 开发状态

### ✅ 已完成

| 模块 | 状态 |
|------|------|
| 多模块工程骨架 | ✅ |
| face-sdk (SeetaFace6) | ✅ |
| hand-sdk (RKNN) | ✅ |
| 状态机核心逻辑 | ✅ |
| 用户管理 (CRUD) | ✅ |
| 晨检记录管理 | ✅ |
| 数据导出 (CSV) | ✅ |
| 设备激活验证 | ✅ |
| 登录认证 | ✅ |
| 语音播报 | ✅ |
| 日志轮转 | ✅ |
| 硬件通信框架 | ✅ (串口待接入真实设备) |
| UI 界面 (Home + Admin) | ✅ |
| CameraX 集成 | ✅ |
| Hilt 依赖注入 | ✅ |

### ⚠️ 待完善

- 真实串口通信实现（当前为框架，需接入 android-serialport-api）
- 生产环境 HTTPS 改造

## 构建与运行

### 前置要求

- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 17
- Android SDK 34
- Android NDK (用于 native 编译)
- CMake 3.22.1

### 构建命令

```bash
# 清理
./gradlew clean

# 构建 Debug APK
./gradlew assembleDebug

# 构建 Release APK
./gradlew assembleRelease

# 构建 SDK AAR
./gradlew :hand-sdk:assembleDebug :face-sdk:assembleDebug
```

### 注意事项

- 需要授予相机权限
- 手部检测依赖 RKNN（需要 Rockchip NPU 真机）
- SeetaFace6 需要 `fr_2_10.dat` 模型文件（Git LFS 管理）

## 激活服务器

项目包含激活验证服务器：

```bash
# 运行服务器
python auth_server.py

# 服务器默认端口 80
# 部署后修改 App.kt 中的 ACTIVATION_URL
```

## 文档

- `AGENTS.md` - Agent 开发规范
- `SETUP_GUIDE.md` - 环境配置指南
- `docs/` - 完整技术文档目录

## 许可证

本项目仅供学习和研究使用。

## 作者

单兵开发者
