package com.timewarden.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import com.timewarden.app.R
import com.timewarden.app.data.LimitRepository
import com.timewarden.app.ui.LockOverlayActivity
import com.timewarden.app.util.UsageStatsHelper

/**
 * 前台监控服务。
 *
 * 每 [POLL_INTERVAL_MS] 轮询一次当前前台 App：
 * - 若该 App 设有时间限制，读取当日累计使用时长；
 * - 若已达到限制且今日尚未锁定，则拉起 [LockOverlayActivity] 覆盖屏幕，并标记今日已锁定。
 *
 * 通过 wake lock 保证 CPU 在息屏时仍可轮询（可选）。
 */
class UsageMonitorService : Service() {

    private lateinit var usageHelper: UsageStatsHelper
    private lateinit var repo: LimitRepository
    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null

    private val pollRunnable = object : Runnable {
        override fun run() {
            checkCurrentApp()
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        usageHelper = UsageStatsHelper(this)
        repo = LimitRepository.get(this)
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        handler.post(pollRunnable)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(pollRunnable)
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---------- 核心检查逻辑 ----------

    private fun checkCurrentApp() {
        val fgPkg = usageHelper.getCurrentForegroundPackage() ?: return
        // 不限制自己
        if (fgPkg == packageName) return

        val limit = repo.getLimit(fgPkg) ?: return
        if (limit.limitMinutes <= 0) return

        // 已标记今日已锁定，不再处理（除非用户次日再触发）
        if (repo.isLockedToday(fgPkg)) return

        val usedMillis = usageHelper.getTodayUsageMillis(fgPkg)
        val limitMillis = limit.limitMinutes * 60_000L
        if (usedMillis >= limitMillis) {
            repo.markLockedToday(fgPkg)
            LockOverlayActivity.launch(this, fgPkg, limit.label)
        }
    }

    // ---------- 前台通知 ----------

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "使用时长监控",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持监控服务在前台运行"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun startForegroundCompat() {
        val notification: Notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("AppTimeGuard 正在监控")
                .setContentText("使用时长守护运行中")
                .setSmallIcon(R.drawable.ic_shield)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("AppTimeGuard 正在监控")
                .setContentText("使用时长守护运行中")
                .setSmallIcon(R.drawable.ic_shield)
                .setOngoing(true)
                .build()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    // ---------- WakeLock（可选，保证息屏仍轮询） ----------

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AppTimeGuard::Monitor")
        wakeLock?.setReferenceCounted(false)
        wakeLock?.acquire(WAKE_LOCK_TIMEOUT_MS)
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    companion object {
        private const val CHANNEL_ID = "monitor_channel"
        private const val NOTIF_ID = 1001
        private const val POLL_INTERVAL_MS = 3_000L      // 每 3 秒轮询一次
        private const val WAKE_LOCK_TIMEOUT_MS = 24L * 60 * 60 * 1000 // 24 小时

        fun start(context: Context) {
            val intent = Intent(context, UsageMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, UsageMonitorService::class.java))
        }
    }
}
