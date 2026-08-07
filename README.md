# AppTimeGuard · 应用使用时长监控与锁定

监控其他 App 的使用时长，达到设定上限后**强制锁定**，无法继续使用，次日 0 点自动重置。

支持 **Android** 与 **iOS** 双平台，技术方案因系统机制不同而不同：

| 平台 | 技术方案 | 拦截方式 |
|------|---------|---------|
| Android | `UsageStatsManager` + 前台服务 + 全屏覆盖 Activity | 弹出全屏锁屏覆盖层 |
| iOS | `FamilyControls` + `DeviceActivity` + `ManagedSettings` | 系统级 Shield 屏蔽遮罩 |

---

## 一、Android 版

**目录**：`AppTimeGuard/`

### 技术方案

1. **`UsageStatsManager`** — 获取各 App 当日累计使用时长（需用户授权"使用情况访问"）
2. **前台服务 `UsageMonitorService`** — 每 3 秒轮询当前前台 App，累计时长，达上限时拉起锁屏覆盖
3. **`LockOverlayActivity`** — 全屏覆盖层，拦截返回键，强制回到桌面
4. **`LimitRepository`** — SharedPreferences 持久化各 App 限制与"今日已锁定"状态（按日期自动重置）

### 项目结构

```
AppTimeGuard/
├── settings.gradle / build.gradle / gradle.properties
├── gradle/wrapper/gradle-wrapper.properties
└── app/
    ├── build.gradle
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/timewarden/app/
        │   ├── AppTimeGuardApp.kt              # Application
        │   ├── data/
        │   │   ├── AppLimit.kt                 # 限制数据类
        │   │   └── LimitRepository.kt          # 持久化存储
        │   ├── receiver/
        │   │   └── BootReceiver.kt             # 开机自启
        │   ├── service/
        │   │   └── UsageMonitorService.kt      # 前台监控服务（核心）
        │   ├── ui/
        │   │   ├── MainActivity.kt             # 主界面
        │   │   ├── LockOverlayActivity.kt      # 全屏锁屏覆盖
        │   │   └── AppListAdapter.kt           # 应用列表适配器
        │   └── util/
        │       ├── UsageStatsHelper.kt         # 使用时长查询
        │       └── PermissionHelper.kt         # 权限检查与跳转
        └── res/
            ├── layout/ (activity_main, activity_lock_overlay, item_app)
            ├── values/ (strings, colors, themes)
            ├── drawable/ (ic_shield, ic_launcher_foreground)
            └── mipmap-anydpi-v26/ (自适应图标)
```

### 编译步骤

1. 安装 **Android Studio**（Hedgehog 2023.1.1 或更高）
2. `File → Open` 选择 `AppTimeGuard/` 目录
3. 等待 Gradle Sync 完成（首次会自动下载依赖）
4. 连接 Android 手机（开启 USB 调试）或启动模拟器
5. 点击 ▶ Run，编译生成 APK 并安装

### 首次使用权限申请

App 首次启动后会提示缺少权限，点击「去授权」依次授予：

1. **使用情况访问权限** — `设置 → 应用 → 特殊应用访问 → 使用情况访问 → AppTimeGuard → 允许`
2. **悬浮窗权限** — `设置 → 应用 → AppTimeGuard → 显示在其他应用上层 → 允许`
3. **通知权限** — 首次开关时系统弹窗授权（Android 13+）

> Android 14+ 前台服务类型为 `specialUse`，已在 Manifest 中声明。

### 使用方法

1. 打开 App，打开顶部「监控总开关」
2. 在应用列表中点击任意 App，输入每日使用上限（分钟）
3. 当该 App 当日使用时长达到上限，会自动弹出全屏锁屏覆盖，无法继续使用
4. 次日 0 点自动重置，可重新使用

---

## 二、iOS 版

**目录**：`AppTimeGuardiOS/`

### 技术方案

iOS 沙盒严格，第三方 App 无法直接读取其他 App 使用时长。但 iOS 15+ 苹果官方提供 **Screen Time API**：

1. **`FamilyControls`** — `FamilyActivityPicker` 让用户选择要限制的 App
2. **`DeviceActivity`** — 设置每日监控计划 + 使用时长阈值事件，系统到阈值时回调扩展
3. **`ManagedSettings`** — 到阈值后设置 `shield`，被屏蔽的 App 显示遮罩无法打开

### 项目结构

```
AppTimeGuardiOS/
├── Shared/
│   └── SharedStore.swift                    # App Groups 共享存储（主App+Extension共用）
├── AppTimeGuard/                            # 主 App
│   ├── AppTimeGuardApp.swift                # @main 入口
│   ├── ContentView.swift                    # SwiftUI 主界面
│   ├── MonitorManager.swift                 # 监控管理（核心）
│   ├── Info.plist
│   └── AppTimeGuard.entitlements
└── DeviceActivityMonitor/                   # 监控扩展
    ├── DeviceActivityMonitorExtension.swift # 到阈值时屏蔽应用（核心）
    ├── Info.plist
    └── DeviceActivityMonitorExtension.entitlements
```

### 在 Xcode 中创建项目

由于 Xcode 项目文件（`.pbxproj`）需由 Xcode 生成，按以下步骤操作：

1. **创建主 App 项目**
   - Xcode → `File → New → Project → iOS → App`
   - Product Name: `AppTimeGuard`，Interface: SwiftUI，Language: Swift
   - 保存后，将本仓库 `AppTimeGuard/` 下的 `.swift` 文件拖入项目替换

2. **添加监控扩展 Target**
   - `File → New → Target → iOS → Device Activity Monitor Extension`
   - Product Name: `DeviceActivityMonitor`
   - 将本仓库 `DeviceActivityMonitor/DeviceActivityMonitorExtension.swift` 拖入替换

3. **添加共享文件**
   - 将 `Shared/SharedStore.swift` 拖入项目，**Target 勾选同时添加到 AppTimeGuard 和 DeviceActivityMonitor**

4. **配置 Entitlements**
   - 两个 Target 的 Signing & Capabilities 中均添加：
     - **App Groups** → 新建 `group.com.timewarden.app`
     - **Family Controls** capability
   - 将仓库中的 `.entitlements` 文件内容对齐

5. **配置 Info.plist**
   - 用仓库中的 `Info.plist` 覆盖自动生成的

### 限制与说明

- **Family Controls capability** 需要在 [Apple Developer Portal](https://developer.apple.com) 为 App ID 启用
- **必须真机测试**：模拟器上 Screen Time API 功能受限
- **最低系统**：iOS 15.0+
- 默认 Shield 界面为系统样式；如需自定义「已达时间上限」提示，可额外添加 `ShieldConfiguration` 扩展

### 使用方法

1. 打开 App，开启「启用监控」
2. 点击「选择要限制的应用」，勾选要限制的 App
3. 用 Stepper 设置每日上限（分钟）
4. 系统会自动监控；达到上限后选中的 App 会被屏蔽，显示遮罩无法打开
5. 次日 0 点自动解除屏蔽

---

## 三、双平台能力对比

| 能力 | Android | iOS |
|------|---------|-----|
| 读取其他 App 使用时长 | UsageStatsManager | DeviceActivity 系统回调 |
| 后台持续监控 | 前台服务 + 轮询 | 系统托管，无需常驻 |
| 锁定方式 | 全屏覆盖 Activity | 系统 Shield 遮罩 |
| 跨日重置 | 按日期判断 | DeviceActivitySchedule |
| 所需权限 | 使用情况访问 + 悬浮窗 | Family Controls + App Groups |
| 可绕过性 | 较低（需关权限才能绕过） | 极低（系统级，需改系统时间） |

---

## 四、免责声明

本应用用于**自我时间管理**。Android 版需用户主动授权，iOS 版基于官方 Screen Time API，均符合各平台安全规范。请勿用于强制监控他人设备。
