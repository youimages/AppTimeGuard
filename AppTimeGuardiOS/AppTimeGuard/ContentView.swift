//
//  ContentView.swift
//  AppTimeGuard
//

import SwiftUI
import UIKit

struct ContentView: View {
    @StateObject private var manager = MonitorManager.shared

    // 软守护：自定义应用选择（免费账号无法读取系统已装 App，只能手动勾选常用 App 作为提醒对象）
    @State private var selectedApps: [String] = ["微信", "抖音"]
    @State private var customAppName: String = ""
    @State private var showAddSheet = false

    let presetApps = [
        "微信", "抖音", "微博", "小红书", "B站", "快手",
        "淘宝", "王者荣耀", "和平精英", "原神", "QQ", "支付宝"
    ]

    var body: some View {
        NavigationStack {
            Form {
                // MARK: - 软守护模式说明
                Section {
                    Label {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("软守护模式（免费账号可用）")
                                .font(.headline)
                            Text("iOS 第三方 App 无法真正拦截其他 App（需付费开发者账号的屏幕使用时间能力）。本模式通过「本地通知定时提醒 + 全屏遮挡」帮助你自律，并可一键跳转系统设置开启真正的 App 限额。")
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                        }
                    } icon: {
                        Image(systemName: "bell.fill")
                            .foregroundStyle(.blue)
                    }
                }

                // MARK: - 守护开关
                Section {
                    Toggle("开始守护", isOn: $manager.isMonitoring)
                        .onChange(of: manager.isMonitoring) { enabled in
                            if enabled {
                                manager.startSoftGuard()
                            } else {
                                manager.stopSoftGuard()
                            }
                        }
                    if manager.isMonitoring {
                        HStack {
                            Text("剩余时间")
                            Spacer()
                            Text(format(manager.remainingSeconds))
                                .foregroundStyle(.secondary)
                                .monospacedDigit()
                        }
                    }
                } header: {
                    Text("守护")
                } footer: {
                    Text("开启后，到点会弹出本地通知提醒；若 App 在前台则显示全屏遮挡。真正锁死其他 App 请在下方跳转系统设置。")
                }

                // MARK: - 应用选择
                Section {
                    ForEach(selectedApps, id: \.self) { app in
                        HStack {
                            Text(app)
                            Spacer()
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundStyle(.blue)
                        }
                    }
                    .onDelete { indexSet in
                        selectedApps.remove(atOffsets: indexSet)
                    }
                    Button {
                        showAddSheet = true
                    } label: {
                        Label("添加应用", systemImage: "plus.circle")
                    }
                } header: {
                    Text("要守护的应用（提醒用，不真正拦截）")
                } footer: {
                    Text("免费账号下无法读取设备已装 App，请手动勾选常用 App 作为提醒对象。")
                }

                // MARK: - 时间限额
                Section {
                    Stepper(value: $manager.limitMinutes, in: 5...480, step: 5) {
                        HStack {
                            Text("每日上限")
                            Spacer()
                            Text("\(manager.limitMinutes) 分钟")
                                .foregroundStyle(.secondary)
                        }
                    }
                    .onChange(of: manager.limitMinutes) { _ in
                        if manager.isMonitoring {
                            manager.startSoftGuard()
                        }
                    }
                } header: {
                    Text("时间限额")
                }

                // MARK: - 真正锁定（系统能力）
                Section {
                    Button {
                        openScreenTimeSettings()
                    } label: {
                        Label("去系统设置开启真正的 App 限额", systemImage: "lock.shield")
                    }
                    Text("iOS「屏幕使用时间 → App 限额」可真正锁死指定 App，免费且无需开发者账号。点击上方按钮跳转设置，按提示操作即可。")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                } header: {
                    Text("真正锁定（系统能力）")
                }
            }
            .navigationTitle("时长守护")
            .onAppear {
                manager.requestNotificationAuth()
            }
            .sheet(isPresented: $showAddSheet) {
                NavigationStack {
                    List {
                        ForEach(presetApps.filter { !selectedApps.contains($0) }, id: \.self) { app in
                            Button {
                                selectedApps.append(app)
                                showAddSheet = false
                            } label: {
                                Text(app)
                            }
                        }
                        Section("自定义") {
                            TextField("输入应用名称", text: $customAppName)
                            Button("添加") {
                                let name = customAppName.trimmingCharacters(in: .whitespaces)
                                if !name.isEmpty && !selectedApps.contains(name) {
                                    selectedApps.append(name)
                                }
                                customAppName = ""
                                showAddSheet = false
                            }
                        }
                    }
                    .navigationTitle("选择应用")
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) {
                            Button("取消") { showAddSheet = false }
                        }
                    }
                }
            }
            // 全屏遮挡：到点且 App 在前台时显示
            .overlay {
                if manager.isMonitoring && manager.remainingSeconds <= 0 {
                    LockOverlay(onDismiss: {
                        manager.isMonitoring = false
                        manager.stopSoftGuard()
                    })
                }
            }
        }
    }

    private func format(_ seconds: Int) -> String {
        let m = seconds / 60
        let s = seconds % 60
        return String(format: "%02d:%02d", m, s)
    }

    private func openScreenTimeSettings() {
        // 优先尝试直达屏幕使用时间的私有 scheme，失败则回退到设置根页面
        if let url = URL(string: "prefs:root=SCREEN_TIME") ?? URL(string: UIApplication.openSettingsURLString),
           UIApplication.shared.canOpenURL(url) {
            UIApplication.shared.open(url)
        } else if let url = URL(string: UIApplication.openSettingsURLString) {
            UIApplication.shared.open(url)
        }
    }
}

/// 全屏遮挡（软守护到点后的提醒界面）
struct LockOverlay: View {
    let onDismiss: () -> Void

    var body: some View {
        ZStack {
            Color.red.ignoresSafeArea()
            VStack(spacing: 20) {
                Image(systemName: "hand.raised.fill")
                    .font(.system(size: 64))
                    .foregroundStyle(.white)
                Text("时间到")
                    .font(.largeTitle.bold())
                    .foregroundStyle(.white)
                Text("你设置的守护时间已结束，请放下手机休息一下。")
                    .foregroundStyle(.white)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal)
                Button {
                    onDismiss()
                } label: {
                    Text("我知道了")
                        .font(.headline)
                        .foregroundStyle(.red)
                        .padding(.horizontal, 32)
                        .padding(.vertical, 12)
                        .background(.white, in: Capsule())
                }
                .padding(.top, 12)
            }
        }
    }
}
