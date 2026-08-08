//
//  MonitorManager.swift
//  AppTimeGuard
//
//  核心：用 FamilyControls 选择应用、DeviceActivity 设阈值监控、ManagedSettings 屏蔽。
//  已做优雅降级：免费账号（无 FamilyControls 能力）或未授权时，UI 与设置流程照常可用，
//  仅"实际屏蔽其他 App"不可用，并给出明确提示，不再静默崩溃。
//

import Foundation
import FamilyControls
import ManagedSettings
import DeviceActivity
import UserNotifications

@MainActor
final class MonitorManager: ObservableObject {

    static let shared = MonitorManager()

    /// 选中的应用/分类（FamilyActivityPicker 双向绑定）
    @Published var selection = FamilyActivitySelection()

    /// 每日时间上限（分钟）
    @Published var limitMinutes: Int = 30

    /// 监控是否开启
    @Published var isMonitoring: Bool = false

    /// FamilyControls 授权状态（免费账号 / 未开启能力时为 .denied 或 .notDetermined）
    @Published var authorizationStatus: FamilyControls.AuthorizationStatus = .notDetermined

    /// 屏蔽管理器：用命名的 store，便于扩展单独操作同一份屏蔽配置
    private let store = ManagedSettingsStore(named: .init("AppTimeGuardStore"))

    /// DeviceActivity 中心
    private let center = DeviceActivityCenter()

    /// 事件名称
    private let thresholdEventName = DeviceActivityEvent.Name("dailyLimitThreshold")

    /// 当前是否已具备 FamilyControls 能力（决定屏蔽功能是否可用）
    var isFamilyControlsAvailable: Bool {
        authorizationStatus == .approved
    }

    init() {
        selection = SharedStore.loadSelection()
        limitMinutes = SharedStore.limitMinutes
        isMonitoring = SharedStore.isMonitoring
        refreshAuthorization()
    }

    // MARK: - 授权

    /// 刷新当前授权状态（免费账号未开启能力时通常为 .denied）
    func refreshAuthorization() {
        authorizationStatus = AuthorizationCenter.shared.authorizationStatus
    }

    /// 请求 FamilyControls 授权（仅在已配置该能力的账号下才会成功）
    func requestAuthorization() async {
        do {
            try await AuthorizationCenter.shared.requestAuthorization(for: .individual)
            authorizationStatus = AuthorizationCenter.shared.authorizationStatus
        } catch {
            authorizationStatus = .denied
            print("FamilyControls 授权失败（免费账号或未开启能力）: \(error)")
        }
    }

    // MARK: - 启动 / 停止监控

    func startMonitoring() {
        guard !selection.applicationTokens.isEmpty else { return }

        // 免费账号 / 未授权：标记不可用，不启动系统级监控，避免崩溃
        guard isFamilyControlsAvailable else {
            print("FamilyControls 未授权，无法启动系统级监控（测试模式）")
            return
        }

        // 保存到共享存储（供 Extension 读取）
        SharedStore.saveSelection(selection)
        SharedStore.limitMinutes = limitMinutes
        SharedStore.isMonitoring = true

        let schedule = DeviceActivitySchedule(
            intervalStart: DateComponents(hour: 0, minute: 0),
            intervalEnd: DateComponents(hour: 23, minute: 59),
            repeats: true
        )

        let event = DeviceActivityEvent(
            applications: selection.applicationTokens,
            categories: selection.categoryTokens,
            webDomains: selection.webDomainTokens,
            threshold: DateComponents(minute: limitMinutes)
        )
        let events: [DeviceActivityEvent.Name: DeviceActivityEvent] = [
            thresholdEventName: event
        ]

        do {
            try center.startMonitoring(DeviceActivityName("dailyLimit"), during: schedule, events: events)
            isMonitoring = true
        } catch {
            isMonitoring = false
            SharedStore.isMonitoring = false
            print("启动监控失败: \(error)")
        }
    }

    func stopMonitoring() {
        guard isFamilyControlsAvailable else {
            isMonitoring = false
            SharedStore.isMonitoring = false
            return
        }
        center.stopMonitoring()
        clearShield()
        isMonitoring = false
        SharedStore.isMonitoring = false
    }

    // MARK: - 屏蔽控制

    /// 屏蔽选中的应用（达到阈值后由 Extension 调用，这里也暴露供主 App 调试）
    func shieldApplications() {
        guard isFamilyControlsAvailable else {
            print("FamilyControls 未授权，无法屏蔽（测试模式）")
            return
        }
        store.shield.applications = selection.applicationTokens
        store.shield.applicationCategories = .specific(selection.categoryTokens)
    }

    /// 取消屏蔽
    func clearShield() {
        guard isFamilyControlsAvailable else { return }
        store.clearAllSettings()
    }

    // MARK: - 软守护（免费账号也可用：本地通知 + 倒计时，不真正拦截其他 App）

    /// 软守护剩余秒数（前台倒计时展示）
    @Published var remainingSeconds: Int = 0

    private var countdownTimer: Timer?

    /// 请求本地通知授权
    func requestNotificationAuth() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { granted, _ in
            print("通知授权 granted: \(granted)")
        }
    }

    /// 安排 N 分钟后的本地通知（即使 App 被杀死也能提醒）
    func scheduleSoftNotification(minutes: Int) {
        let center = UNUserNotificationCenter.current()
        center.removeAllPendingNotificationRequests()
        let content = UNMutableNotificationContent()
        content.title = "时长守护提醒"
        content.body = "你设置的守护时间已到，请放下手机休息一下。"
        content.sound = .default
        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: TimeInterval(max(1, minutes * 60)), repeats: false)
        let request = UNNotificationRequest(identifier: "softGuard", content: content, trigger: trigger)
        center.add(request)
    }

    /// 取消已安排的本地通知
    func cancelSoftNotification() {
        UNUserNotificationCenter.current().removeAllPendingNotificationRequests()
    }

    /// 启动前台倒计时（仅用于界面展示，真正提醒靠本地通知）
    func startCountdown(seconds: Int) {
        remainingSeconds = seconds
        countdownTimer?.invalidate()
        countdownTimer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] t in
            guard let self else { return }
            if self.remainingSeconds > 0 {
                self.remainingSeconds -= 1
            } else {
                t.invalidate()
            }
        }
    }

    /// 停止倒计时
    func stopCountdown() {
        countdownTimer?.invalidate()
        remainingSeconds = 0
    }

    /// 软守护：开始（通知 + 倒计时）
    func startSoftGuard() {
        scheduleSoftNotification(minutes: limitMinutes)
        startCountdown(seconds: limitMinutes * 60)
    }

    /// 软守护：停止
    func stopSoftGuard() {
        cancelSoftNotification()
        stopCountdown()
    }
}
