package com.whisper.kit.recyclerview.listener

import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * RecyclerView item 内部 View 点击回调.
 *
 * 回调中的 [view] 是 itemView 内实际命中的可点击目标, [position] 是该 item
 * 在 adapter 中的当前位置. 当 RecyclerView 正在移除、移动或刷新 item 导致位置无效时,
 * 监听器不会触发回调.
 *
 * @author whisper
 * @since 2026/07/30
 */
fun interface OnItemClickListener {

    /**
     * 处理 item 内部 View 点击事件.
     *
     * @param recyclerView 触发点击的 RecyclerView.
     * @param view itemView 内实际命中的可点击目标.
     * @param position [view] 所属 item 的 adapter 位置.
     */
    fun onItemClick(recyclerView: RecyclerView, view: View, position: Int)
}
