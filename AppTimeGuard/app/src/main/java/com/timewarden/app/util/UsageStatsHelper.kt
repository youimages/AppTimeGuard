package com.timewarden.app.util

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.util.Log

/**
 * 封装 [UsageStatsManager]，提供：
 * 1. 当日某 App 的累计使用时长（毫秒）
 * 2. 当前前台 App 包名
 *
 * 需要用户在系统设置中授予"使用情况访问"权限。
 */
class UsageStatsHelper(context: Context) {

    private val usm: UsageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    /**
     * 获取某包名从今天 0 点到现在的累计使用时长（毫秒）。
     * 原理：查询今日所有 MOVE_TO_FOREGROUND / MOVE_TO_BACKGROUND 事件，
     * 配对计算每段前台停留时长并累加。
     */
    fun getTodayUsageMillis(packageName: String): Long {
        val now = System.currentTimeMillis()
        val startOfDay = startOfTodayMillis()
        return calcUsageMillis(startOfDay, now, packageName)
    }

    /**
     * 获取当前处于前台的 App 包名。
     * 优先用事件流取最后一个 MOVE_TO_FOREGROUND。
     */
    fun getCurrentForegroundPackage(): String? {
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 60_000, now) ?: return null
        val event = UsageEvents.Event()
        var lastFg: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastFg = event.packageName
            }
        }
        return lastFg
    }

    /**
     * 计算指定时间段内某 App 的前台停留总时长。
     */
    private fun calcUsageMillis(start: Long, end: Long, packageName: String): Long {
        val events = usm.queryEvents(start, end) ?: return 0L
        val event = UsageEvents.Event()
        var total = 0L
        var lastFgTime = 0L
        var inForeground = false
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.packageName != packageName) continue
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    lastFgTime = event.timeStamp
                    inForeground = true
                }
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    if (inForeground) {
                        total += event.timeStamp - lastFgTime
                        inForeground = false
                    }
                }
            }
        }
        // 若当前仍在前台，补上到 end 的时长
        if (inForeground) {
            total += end - lastFgTime
        }
        return total
    }

    private fun startOfTodayMillis(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    companion object {
        private const val TAG = "UsageStatsHelper"
    }
}
