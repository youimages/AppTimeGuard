package com.timewarden.app.data

/**
 * 单个 App 的时间限制配置。
 *
 * @param packageName 包名，作为唯一标识
 * @param label 显示名
 * @param limitMinutes 每日允许使用的分钟数，0 表示不限制
 */
data class AppLimit(
    val packageName: String,
    val label: String,
    val limitMinutes: Int,
)
