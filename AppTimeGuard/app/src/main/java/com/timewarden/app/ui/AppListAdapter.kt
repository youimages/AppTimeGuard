package com.timewarden.app.ui

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.timewarden.app.R

/** 列表项 App 信息 */
data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable,
    val limitMinutes: Int = 0,
)

/**
 * 已安装 App 列表适配器。
 * 点击列表项回调 [onAppClick]，用于设置/修改时间限制。
 */
class AppListAdapter(
    private var items: List<AppInfo> = emptyList(),
    private val onAppClick: (AppInfo) -> Unit,
) : RecyclerView.Adapter<AppListAdapter.VH>() {

    fun submit(list: List<AppInfo>) {
        items = list
        notifyDataSetChanged()
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.ivAppIcon)
        val name: TextView = view.findViewById(R.id.tvAppName)
        val pkg: TextView = view.findViewById(R.id.tvAppPkg)
        val limit: TextView = view.findViewById(R.id.tvAppLimit)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val info = items[position]
        holder.icon.setImageDrawable(info.icon)
        holder.name.text = info.label
        holder.pkg.text = info.packageName
        holder.limit.text = if (info.limitMinutes > 0) {
            "每日限额：${info.limitMinutes} 分钟"
        } else {
            "未设置限制"
        }
        holder.itemView.setOnClickListener { onAppClick(info) }
    }

    override fun getItemCount(): Int = items.size
}
