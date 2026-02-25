# SmartCheck UI 组件清单与交互状态

本文件列出页面级组件清单与核心交互状态，便于开发阶段拆分与扩展。

## 1. 页面组件清单

### 1.1 登录页

- LoginHeroBackground（可配置背景图）
- LoginTitle（可配置标题）
- LoginCard
  - AccountInput
  - PasswordInput（显示/隐藏）
  - LoginButton
  - ErrorMessage
- VersionBadge（只读版本信息）

### 1.2 首页（主入口）

- TopBar
  - WelcomeTitle（食堂名称动态）
  - UserAvatar
  - SettingsButton
- StepGuideCard（晨检步骤说明，配置化）
- EntryGrid
  - EntryButton（我要晨检/员工管理/晨检记录/报表导出）

### 1.3 我要晨检

- ProgressStepper（可配置步骤）
- CameraPanel
  - FaceCameraView（阶段1）
  - HandCameraView（阶段2）
- ResultInfoCard
  - UserAvatar
  - UserName
  - TemperatureChip
  - HealthCertStatusChip（含预警）
- HandResultPanel
  - HandImageFront
  - HandImageBack
  - ForeignObjectBadge
- SymptomConfirmCard / Dialog（身体不适确认）
- SubmitBar
  - SubmitButton
  - VoiceIndicator（预留）

### 1.4 员工管理

- SearchBar（姓名/编号）
- EmployeeList（分页）
  - EmployeeListItem（头像、姓名、健康证剩余天数、状态）
- PaginationBar
- FloatingAddButton（新增员工）

### 1.5 员工详情（可编辑）

- DetailHeader（姓名/编号）
- InfoSection（身份证等）
- FacePhotoSection
- HealthCertSection（证件图、起止日期、剩余天数）
- EditActionBar（编辑/保存/取消）

### 1.6 新增员工

- EmployeeForm
  - NameInput / IdInput / IdCardInput
  - FaceCaptureButton
  - HealthCertCaptureButton
  - DateRangePicker
  - RemainingDaysPreview
- SubmitButton

### 1.7 晨检记录

- FilterBar
  - DateFilter
  - NameFilter
  - HandStatusFilter
  - HealthCertFilter
  - SymptomFilter
- RecordList
  - RecordItem（状态与字段）
- RecordDetailDrawer / Dialog（查看/编辑）
- ExportButton

### 1.8 报表导出

- ExportFilters（复用 FilterBar）
- ExportAction
  - ExportButton
  - ExportStatusToast

### 1.9 设置

- AdminProfileForm（头像/管理员名）
- AccountForm（账号/密码）
- CanteenForm（食堂名称）
- LoginPageConfigForm（登录标题/背景图）
- VersionInfo（只读）

## 2. 交互状态表（核心流程）

### 2.1 我要晨检流程状态机

- Idle（待机）
- FaceDetecting（刷脸识别中）
- FaceSuccess（人脸识别成功）
- TempMeasuring（体温获取中）
- TempSuccess（体温正常）
- HandFrontDetecting（手部正面）
- HandFrontDone
- HandBackDetecting（手部反面）
- HandBackDone
- SymptomConfirm（不适确认）
- SubmitReady
- Submitting
- SubmitSuccess
- SubmitFail

### 2.2 异常/提示状态

- NoFace / NoHand / CameraError / PermissionDenied
- HealthCertExpiring（<7天）
- HealthCertExpired

## 3. 可扩展/可升级预留

- ProgressStepper 可配置，新增步骤仅改配置。
- 状态机以“枚举 + 事件”驱动，便于插入语音提示/扫码等步骤。
- 员工数据源抽象 Repository，便于接入云端同步或第三方系统。
- 导出服务抽象，支持 Excel/PDF/CSV 切换。
- CameraPanel 支持双摄/单摄/外接摄像头。
- Theme Token 化（颜色/字体/间距/圆角），便于品牌升级或不同屏幕适配。
