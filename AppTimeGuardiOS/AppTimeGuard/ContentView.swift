//
//  ContentView.swift
//  AppTimeGuard
//

import SwiftUI
import FamilyControls

struct ContentView: View {
    @StateObject private var manager = MonitorManager.shared
    @State private var showPicker = false

    var body: some View {
        NavigationStack {
            Form {
                // MARK: - 监控开关
                Section {
                    Toggle("启用监控", isOn: $manager.isMonitoring)
                        .onChange(of: manager.isMonitoring) { _, enabled in
                            if enabled {
                                manager.startMonitoring()
                            } else {
                                manager.stopMonitoring()
                            }
                        }
                } header: {
                    Text("监控")
                } footer: {
                    Text("开启后，选中的应用达到每日上限将自动锁定，次日 0 点重置。")
                }

                // MARK: - 应用选择
                Section {
                    Button {
                        showPicker = true
                    } label: {
                        HStack {
                            Text("选择要限制的应用")
                            Spacer()
                            Text("\(manager.selection.applicationTokens.count) 个")
                                .foregroundStyle(.secondary)
                        }
                    }
                } header: {
                    Text("受监控应用")
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
                    .onChange(of: manager.limitMinutes) { _, _ in
                        // 修改阈值后若正在监控，重启以应用新阈值
                        if manager.isMonitoring {
                            manager.startMonitoring()
                        }
                    }
                } header: {
                    Text("时间限额")
                }

                // MARK: - 手动测试屏蔽
                Section {
                    Button("立即测试屏蔽效果") {
                        manager.shieldApplications()
                    }
                    Button("取消屏蔽") {
                        manager.clearShield()
                    }
                } header: {
                    Text("调试")
                } footer: {
                    Text("可手动触发屏蔽，预览被锁定时的界面效果。")
                }
            }
            .navigationTitle("时长守护")
            .familyActivityPicker(
                isPresented: $showPicker,
                selection: $manager.selection
            )
        }
    }
}
