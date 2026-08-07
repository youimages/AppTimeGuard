package com.timewarden.app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 持久化各 App 的时间限制、监控开关以及"今日已锁定"状态。
 *
 * 使用 SharedPreferences 存储，结构简单，适合本应用规模。
 */
class LimitRepository private constructor(private val sp: SharedPreferences) {

    companion object {
        private const val SP_NAME = "app_time_guard"
        private const val KEY_MONITORING = "monitoring_enabled"
        private const val KEY_LIMITS = "limits_json"
        private const val KEY_LOCKED = "locked_json"
        private const val KEY_LOCK_DATE = "locked_date"

        @Volatile
        private var instance: LimitRepository? = null

        fun get(context: Context): LimitRepository {
            return instance ?: synchronized(this) {
                instance ?: LimitRepository(
                    context.applicationContext.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
                ).also { instance = it }
            }
        }
    }

    // ---------- 监控开关 ----------

    fun isMonitoringEnabled(): Boolean = sp.getBoolean(KEY_MONITORING, false)

    fun setMonitoringEnabled(enabled: Boolean) {
        sp.edit().putBoolean(KEY_MONITORING, enabled).apply()
    }

    // ---------- 时间限制 ----------

    /** 获取所有已设置限制的 App 列表 */
    fun getAllLimits(): List<AppLimit> {
        val raw = sp.getString(KEY_LIMITS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                AppLimit(
                    packageName = o.getString("pkg"),
                    label = o.getString("label"),
                    limitMinutes = o.getInt("minutes"),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun getLimit(packageName: String): AppLimit? =
        getAllLimits().firstOrNull { it.packageName == packageName }

    /** 设置或更新某个 App 的限制（minutes=0 表示删除限制） */
    fun setLimit(limit: AppLimit) {
        val list = getAllLimits().filterNot { it.packageName == limit.packageName }.toMutableList()
        if (limit.limitMinutes > 0) list.add(limit)
        sp.edit().putString(KEY_LIMITS, toJson(list)).apply()
    }

    fun removeLimit(packageName: String) {
        sp.edit().putString(
            KEY_LIMITS,
            toJson(getAllLimits().filterNot { it.packageName == packageName })
        ).apply()
    }

    // ---------- 今日已锁定 ----------

    private fun todayStr(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    /** 如果记录的日期不是今天，清空已锁定集合 */
    private fun ensureToday() {
        val saved = sp.getString(KEY_LOCK_DATE, null)
        val today = todayStr()
        if (saved != today) {
            sp.edit()
                .putString(KEY_LOCK_DATE, today)
                .putString(KEY_LOCKED, JSONArray().toString())
                .apply()
        }
    }

    /** 标记某 App 今日已达限制（被锁定），避免一天内反复弹覆盖层 */
    fun markLockedToday(packageName: String) {
        ensureToday()
        val set = lockedTodaySet().toMutableSet()
        if (set.add(packageName)) {
            sp.edit().putString(KEY_LOCKED, JSONArray(set).toString()).apply()
        }
    }

    fun isLockedToday(packageName: String): Boolean {
        ensureToday()
        return lockedTodaySet().contains(packageName)
    }

    private fun lockedTodaySet(): Set<String> {
        val raw = sp.getString(KEY_LOCKED, null) ?: return emptySet()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { it.toString() }.toSet()
        }.getOrDefault(emptySet())
    }

    // ---------- 序列化辅助 ----------

    private fun toJson(list: List<AppLimit>): String {
        val arr = JSONArray()
        list.forEach { l ->
            arr.put(JSONObject().apply {
                put("pkg", l.packageName)
                put("label", l.label)
                put("minutes", l.limitMinutes)
            })
        }
        return arr.toString()
    }
}
