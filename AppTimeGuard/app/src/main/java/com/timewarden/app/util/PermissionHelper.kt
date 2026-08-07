package com.timewarden.app.util

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * 权限检查与引导跳转：
 * 1. 使用情况访问权限（获取其他 App 使用时长）
 * 2. 悬浮窗权限（全屏锁屏覆盖）
 * 3. 通知权限（Android 13+ 前台服务通知）
 */
object PermissionHelper {

    /** 检查是否有"使用情况访问"权限 */
    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** 跳转到"使用情况访问"系统设置页 */
    fun openUsageAccessSettings(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** 检查是否有悬浮窗权限 */
    fun hasOverlayPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true

    /** 跳转到悬浮窗权限设置页 */
    fun openOverlaySettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
        }
    }

    /**
     * 检查是否具备全部运行所需权限。
     * @return 缺失权限的可读说明，null 表示全部就绪
     */
    fun missingPermissionDesc(context: Context): String? {
        if (!hasUsageAccess(context)) return "使用情况访问权限"
        if (!hasOverlayPermission(context)) return "悬浮窗权限"
        return null
    }
}
