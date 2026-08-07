package com.timewarden.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.timewarden.app.service.UsageMonitorService
import com.timewarden.app.data.LimitRepository

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            // 仅在监控开关开启时自启服务
            val repo = LimitRepository.get(context)
            if (repo.isMonitoringEnabled()) {
                UsageMonitorService.start(context)
            }
        }
    }
}
