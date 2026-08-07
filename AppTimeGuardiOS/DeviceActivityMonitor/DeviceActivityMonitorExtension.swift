//
//  DeviceActivityMonitorExtension.swift
//  DeviceActivityMonitorExtension
//
//  当选中的应用累计使用时长达到阈值时，系统回调本扩展，
//  在此设置 ManagedSettingsStore.shield 屏蔽应用。
//

import DeviceActivity
import ManagedSettings
import FamilyControls

/// DeviceActivity 监控扩展。
/// 到达阈值 → 屏蔽；每日间隔开始 → 解除屏蔽。
class DeviceActivityMonitorExtension: DeviceActivityMonitor {

    /// 与主 App 使用同一命名的 store，确保屏蔽状态一致
    private let store = ManagedSettingsStore(named: .init("AppTimeGuardStore"))

    private let thresholdEventName = DeviceActivityEvent.Name("dailyLimitThreshold")

    // MARK: - 达到阈值：屏蔽应用

    override func eventDidReachThreshold(
        _ event: DeviceActivityEvent.Name,
        activity: DeviceActivityName
    ) {
        super.eventDidReachThreshold(event, activity: activity)

        guard event == thresholdEventName else { return }

        // 从共享存储读取用户选择的应用
        let selection = SharedStore.loadSelection()

        // 设置屏蔽：选中的应用将显示遮罩，无法打开
        store.shield.applications = selection.applicationTokens
        store.shield.applicationCategories = .specific(selection.categoryTokens)
    }

    // MARK: - 每日间隔开始：解除屏蔽

    override func intervalDidStart(
        for activity: DeviceActivityName
    ) {
        super.intervalDidStart(for: activity)
        // 新的一天开始，清除昨日屏蔽
        store.clearAllSettings()
    }

    // MARK: - 监控结束

    override func intervalDidEnd(
        for activity: DeviceActivityName
    ) {
        super.intervalDidEnd(for: activity)
        // 监控周期结束，清除屏蔽
        store.clearAllSettings()
    }
}
