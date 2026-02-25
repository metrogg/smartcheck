# SmartCheck UI 落地实施清单

本文件描述将设计文档落地到当前代码库的具体改造计划，包含页面替换、导航调整、组件新增与数据结构变更建议。

## 1. 现有页面处理策略

移除/隐藏测试页面（保留代码可选）：

- `app/src/main/java/com/smartcheck/app/ui/screens/FaceTestScreen.kt`
- `app/src/main/java/com/smartcheck/app/ui/screens/HandTestScreen.kt`
- `app/src/main/java/com/smartcheck/app/ui/screens/HandCheckScreen.kt`

保留并改造的页面：

- `app/src/main/java/com/smartcheck/app/ui/screens/HomeScreen.kt` -> 改造为“我要晨检”页面
- `app/src/main/java/com/smartcheck/app/ui/screens/AdminScreen.kt` -> 改造为“晨检记录”页面
- `app/src/main/java/com/smartcheck/app/ui/screens/AdminLoginScreen.kt` -> 保留并改造为“登录页”
- `app/src/main/java/com/smartcheck/app/ui/screens/EmployeeEnrollScreen.kt` -> 改造为“新增员工”页面

新增页面（建议新增文件）：

- `app/src/main/java/com/smartcheck/app/ui/screens/DashboardScreen.kt`（首页/主入口）
- `app/src/main/java/com/smartcheck/app/ui/screens/EmployeeListScreen.kt`（员工管理列表）
- `app/src/main/java/com/smartcheck/app/ui/screens/EmployeeDetailScreen.kt`（员工详情/编辑）
- `app/src/main/java/com/smartcheck/app/ui/screens/RecordsScreen.kt`（晨检记录列表/筛选/查看/编辑）
- `app/src/main/java/com/smartcheck/app/ui/screens/ReportExportScreen.kt`（报表导出）
- `app/src/main/java/com/smartcheck/app/ui/screens/SettingsScreen.kt`（管理员设置）

## 2. 导航结构调整

目标：移除测试页入口，以业务页为主。

建议路由：

- `login`
- `dashboard`
- `check`
- `employees`
- `employee_detail/{id}`
- `employee_new`
- `records`
- `export`
- `settings`

改动位置：

- `app/src/main/java/com/smartcheck/app/ui/navigation/AppNavigation.kt`
  - startDestination 改为 `login`
  - 移除 `face_test` / `hand_test` / `hand_check`
  - 增加 dashboard / check / employees / employee_detail / employee_new / records / export / settings

## 3. 组件与主题改造清单

新增通用组件：

- `StepGuideCard`（首页步骤说明）
- `EntryButton`（首页功能入口按钮）
- `ProgressStepper`（晨检流程进度条）
- `HealthCertStatusChip`（健康证状态）
- `SymptomConfirmCard`（不适确认组件）
- `PaginationBar`（员工列表分页）

改造现有组件：

- `ResultCard` -> 适配“我要晨检”信息展示
- `DualCameraPreview` -> 保持用于人脸/手部预览
- `HandOverlay` / `FaceOverlay` -> 继续用于识别标注

主题/样式：

- `app/src/main/java/com/smartcheck/app/ui/theme/Color.kt`
- `app/src/main/java/com/smartcheck/app/ui/theme/Type.kt`
- `app/src/main/java/com/smartcheck/app/ui/theme/Theme.kt`

建议：引入 token 化风格（颜色、字体、间距、圆角），便于后续品牌升级。

## 4. ViewModel 与状态调整建议

新增 ViewModel：

- `DashboardViewModel`（首页欢迎语/头像/入口权限）
- `EmployeeListViewModel`（分页、搜索）
- `EmployeeDetailViewModel`（详情/编辑）
- `RecordsViewModel`（现有可扩展筛选字段）
- `SettingsViewModel`（管理员设置项）

改造现有 ViewModel：

- `MainViewModel` -> 对应“我要晨检”完整状态机（刷脸验温、手部双面、不适确认、提交）
- `EmployeeEnrollViewModel` -> 对应“新增员工”流程
- `AdminAuthViewModel` -> 登录入口

新增状态枚举：

- `CheckStep`：Face -> HandFront -> HandBack -> Symptom -> Done
- `HealthCertStatus`：Valid / Expiring / Expired

## 5. 数据结构与存储建议

晨检记录新增字段（用于筛选与展示）：

- handStatus（手部情况）
- healthCertStatus（健康证状态）
- symptomFlags（身体不适项）

员工信息新增字段：

- idCardNumber（身份证）
- healthCertImagePath（健康证图）
- healthCertStartDate / healthCertEndDate

说明：字段变更需同步 Room Entity/DAO/迁移脚本。

## 6. 分阶段实施建议

阶段 1：结构搭建

- 导航调整
- 新增页面空壳
- 旧测试页移除入口

阶段 2：核心流程

- 完成“我要晨检”页面与状态机
- 进度条/流程切换/自动回首页

阶段 3：员工管理

- 列表分页
- 详情/编辑
- 新增员工完整流程

阶段 4：记录与导出

- 晨检记录筛选
- 查看/编辑
- 报表导出

阶段 5：设置

- 管理员信息、账号密码、食堂名称、登录页标题/背景图配置

## 7. 可扩展性落地要点

- 核心流程使用“状态机驱动”，避免 UI 里硬编码流程顺序。
- 配置型步骤文案与顺序，通过配置对象统一管理。
- Report 导出封装为独立服务，未来切换格式无需改 UI。
- 相机类型与硬件适配抽象为策略层，方便替换硬件。
