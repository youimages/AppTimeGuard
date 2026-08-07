package com.timewarden.app.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.timewarden.app.R

/**
 * 全屏锁屏覆盖 Activity。
 *
 * 当某 App 当日使用时长达到限制时由监控服务拉起，
 * 覆盖在屏幕最上层，提示用户已达上限，并提供"返回桌面"按钮。
 *
 * - 拦截返回键：按下返回键不会关闭覆盖层，而是回到桌面。
 * - 主题为透明全屏，盖在所有应用之上。
 */
class LockOverlayActivity : Activity() {

    private var pkg: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 关键窗口属性：盖在所有应用之上、锁屏时可见、保持屏幕亮
        window.apply {
            addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            }
        }

        setContentView(R.layout.activity_lock_overlay)

        val label = intent.getStringExtra(EXTRA_LABEL) ?: "该应用"
        pkg = intent.getStringExtra(EXTRA_PACKAGE)
        val limitMinutes = intent.getIntExtra(EXTRA_LIMIT_MIN, 0)

        findViewById<TextView>(R.id.tvAppName).text = label
        findViewById<TextView>(R.id.tvMessage).text = buildString {
            append("「$label」今日使用时长")
            if (limitMinutes > 0) append("已达 $limitMinutes 分钟上限")
            else append("已达上限")
            append("\n请明日再使用，或返回桌面做点别的事吧")
        }

        findViewById<Button>(R.id.btnGoHome).setOnClickListener {
            goHome()
        }
    }

    /** 拦截返回键：不关闭覆盖层，而是回到桌面 */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        goHome()
    }

    private fun goHome() {
        val home = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(home)
        finish()
    }

    companion object {
        private const val EXTRA_PACKAGE = "extra_package"
        private const val EXTRA_LABEL = "extra_label"
        private const val EXTRA_LIMIT_MIN = "extra_limit_min"

        fun launch(context: Context, packageName: String, label: String, limitMinutes: Int = 0) {
            val intent = Intent(context, LockOverlayActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
                putExtra(EXTRA_PACKAGE, packageName)
                putExtra(EXTRA_LABEL, label)
                putExtra(EXTRA_LIMIT_MIN, limitMinutes)
            }
            context.startActivity(intent)
        }
    }
}
