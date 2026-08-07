//
//  SharedStore.swift
//  AppTimeGuard (主 App) 与 DeviceActivityMonitorExtension 共享
//
//  ⚠️ 在 Xcode 中需将本文件同时勾选添加到两个 Target：
//     1) AppTimeGuard  2) DeviceActivityMonitorExtension
//

import Foundation
import FamilyControls

/// 通过 App Groups 在主 App 与监控扩展之间共享数据。
/// 存储：应用选择、每日时间阈值、监控开关。
struct SharedStore {

    /// App Group identifier，需与 entitlements 中一致并在 Apple Developer 后台创建。
    static let appGroup = "group.com.timewarden.app"

    static var defaults: UserDefaults {
        UserDefaults(suiteName: appGroup) ?? .standard
    }

    // MARK: - 应用选择

    static func saveSelection(_ selection: FamilyActivitySelection) {
        if let data = try? JSONEncoder().encode(selection) {
            defaults.set(data, forKey: Keys.selection)
        }
    }

    static func loadSelection() -> FamilyActivitySelection {
        guard let data = defaults.data(forKey: Keys.selection),
              let decoded = try? JSONDecoder().decode(FamilyActivitySelection.self, from: data)
        else { return FamilyActivitySelection() }
        return decoded
    }

    // MARK: - 时间阈值（分钟）

    static var limitMinutes: Int {
        get {
            let v = defaults.integer(forKey: Keys.limitMinutes)
            return v == 0 ? 30 : v
        }
        set { defaults.set(newValue, forKey: Keys.limitMinutes) }
    }

    // MARK: - 监控开关

    static var isMonitoring: Bool {
        get { defaults.bool(forKey: Keys.isMonitoring) }
        set { defaults.set(newValue, forKey: Keys.isMonitoring) }
    }

    private enum Keys {
        static let selection = "selection"
        static let limitMinutes = "limitMinutes"
        static let isMonitoring = "isMonitoring"
    }
}
