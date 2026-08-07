//
//  MonitorManager.swift
//  AppTimeGuard
//
//  核心：用 FamilyControls 选择应用、DeviceActivity 设阈值监控、ManagedSettings 屏蔽。
//

import Foundation
import FamilyControls
import ManagedSettings
import DeviceActivity

@MainActor
final class MonitorManager: ObservableObject {

    static let shared = MonitorManager()

    /// 选中的应用/分类（FamilyActivityPicker 双向绑定）
    @Published var selection = FamilyActivitySelection()

    /// 每日时间上限（分钟）
    @Published var limitMinutes: Int = 30

    /// 监控是否开启
    @Published var isMonitoring: Bool = false

    /// 屏蔽管理器：用命名的 store，便于扩展单独操作同一份屏蔽配置
    private let store = ManagedSettingsStore(named: .init("AppTimeGuardStore"))

    /// DeviceActivity 中心
    private let center = DeviceActivityCenter()

    /// 事件名称
    private let thresholdEventName = DeviceActivityEvent.Name("dailyLimitThreshold")

    init() {
        // 从共享存储恢复状态
        selection = SharedStore.loadSelection()
        limitMinutes = SharedStore.limitMinutes
        isMonitoring = SharedStore.isMonitoring
    }

    // MARK: - 启动 / 停止监控

    func startMonitoring() {
        guard !selection.applicationTokens.isEmpty else { return }

        // 保存到共享存储（供 Extension 读取）
        SharedStore.saveSelection(selection)
        SharedStore.limitMinutes = limitMinutes
        SharedStore.isMonitoring = true

        // 每日计划：0:00 ~ 23:59
        let schedule = DeviceActivitySchedule(
            intervalStart: DateComponents(hour: 0, minute: 0),
            intervalEnd: DateComponents(hour: 23, minute: 59),
            repeats: true
        )

        // 阈值事件：选中的应用累计使用 limitMinutes 分钟后触发
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
            try center.startMonitoring(schedule, events: events)
            isMonitoring = true
        } catch {
            isMonitoring = false
            SharedStore.isMonitoring = false
            print("启动监控失败: \(error)")
        }
    }

    func stopMonitoring() {
        center.stopMonitoring()
        clearShield()
        isMonitoring = false
        SharedStore.isMonitoring = false
    }

    // MARK: - 屏蔽控制

    /// 屏蔽选中的应用（达到阈值后由 Extension 调用，这里也暴露供主 App 调试）
    func shieldApplications() {
        store.shield.applications = selection.applicationTokens
        store.shield.applicationCategories = .specific(selection.categoryTokens)
    }

    /// 取消屏蔽
    func clearShield() {
        store.clearAllSettings()
    }
}
