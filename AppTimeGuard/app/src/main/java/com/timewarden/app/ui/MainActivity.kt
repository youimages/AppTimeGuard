package com.timewarden.app.ui

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.timewarden.app.R
import com.timewarden.app.data.AppLimit
import com.timewarden.app.data.LimitRepository
import com.timewarden.app.service.UsageMonitorService
import com.timewarden.app.util.PermissionHelper

/**
 * 主界面：
 * - 检查并引导授权（使用情况访问 + 悬浮窗）
 * - 监控总开关
 * - 已安装 App 列表，点击设置每日时间上限
 */
class MainActivity : AppCompatActivity() {

    private lateinit var repo: LimitRepository
    private lateinit var adapter: AppListAdapter

    private lateinit var tvPermissionHint: TextView
    private lateinit var btnGrant: Button
    private lateinit var switchMonitor: Switch
    private lateinit var rvList: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repo = LimitRepository.get(this)

        tvPermissionHint = findViewById(R.id.tvPermissionHint)
        btnGrant = findViewById(R.id.btnGrantPermission)
        switchMonitor = findViewById(R.id.switchMonitor)
        rvList = findViewById(R.id.rvAppList)

        adapter = AppListAdapter { info -> showLimitDialog(info) }
        rvList.layoutManager = LinearLayoutManager(this)
        rvList.adapter = adapter

        btnGrant.setOnClickListener {
            // 优先跳转使用情况访问，其次悬浮窗
            if (!PermissionHelper.hasUsageAccess(this)) {
                PermissionHelper.openUsageAccessSettings(this)
            } else if (!PermissionHelper.hasOverlayPermission(this)) {
                PermissionHelper.openOverlaySettings(this)
            }
        }

        switchMonitor.setOnCheckedChangeListener { _, checked ->
            repo.setMonitoringEnabled(checked)
            if (checked) {
                val missing = PermissionHelper.missingPermissionDesc(this)
                if (missing != null) {
                    Toast.makeText(this, "请先授权：$missing", Toast.LENGTH_LONG).show()
                    switchMonitor.isChecked = false
                    repo.setMonitoringEnabled(false)
                    PermissionHelper.openUsageAccessSettings(this)
                } else {
                    UsageMonitorService.start(this)
                    Toast.makeText(this, "监控已开启", Toast.LENGTH_SHORT).show()
                }
            } else {
                UsageMonitorService.stop(this)
                Toast.makeText(this, "监控已关闭", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionState()
        switchMonitor.isChecked = repo.isMonitoringEnabled()
        loadInstalledApps()
    }

    /** 刷新权限提示区域 */
    private fun refreshPermissionState() {
        val missing = PermissionHelper.missingPermissionDesc(this)
        if (missing == null) {
            tvPermissionHint.text = "所需权限已全部就绪 ✓"
            tvPermissionHint.visibility = View.VISIBLE
            btnGrant.visibility = View.GONE
        } else {
            tvPermissionHint.text = "缺少权限：$missing\n点击右侧按钮前往系统设置授权"
            tvPermissionHint.visibility = View.VISIBLE
            btnGrant.visibility = View.VISIBLE
        }
    }

    /** 异步加载已安装 App 列表，并合并已设限制 */
    private fun loadInstalledApps() {
        Thread {
            val pm = packageManager
            val apps = pm.getInstalledApplications(0)
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                .map {
                    AppInfo(
                        packageName = it.packageName,
                        label = pm.getApplicationLabel(it).toString(),
                        icon = pm.getApplicationIcon(it),
                    )
                }
                .sortedBy { it.label.lowercase() }

            val limits = repo.getAllLimits()
            val merged = apps.map { a ->
                a.copy(limitMinutes = limits.firstOrNull { it.packageName == a.packageName }?.limitMinutes ?: 0)
            }
            runOnUiThread { adapter.submit(merged) }
        }.start()
    }

    /** 弹出对话框设置某 App 每日时间上限（分钟） */
    private fun showLimitDialog(info: AppInfo) {
        val edit = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "输入分钟数，0 = 取消限制"
            setText(if (info.limitMinutes > 0) info.limitMinutes.toString() else "")
        }
        AlertDialog.Builder(this)
            .setTitle("「${info.label}」每日使用上限")
            .setView(edit)
            .setPositiveButton("保存") { _, _ ->
                val min = edit.text.toString().toIntOrNull() ?: 0
                repo.setLimit(AppLimit(info.packageName, info.label, min))
                Toast.makeText(this, if (min > 0) "已设置 ${min} 分钟" else "已取消限制", Toast.LENGTH_SHORT).show()
                loadInstalledApps()
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
