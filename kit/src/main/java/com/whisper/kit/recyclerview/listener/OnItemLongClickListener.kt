package com.whisper.kit.recyclerview.listener

import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * RecyclerView item 内部 View 长按回调.
 *
 * 回调中的 [view] 是 itemView 内实际命中的 `longClickable && enabled` 目标,
 * [absoluteAdapterPosition] 是该 item 在 RecyclerView 完整 adapter 链中的当前位置.
 * 该接口只提供旁路通知, 不消费原生长按事件, 因此不返回消费结果.
 * 点击监听器是否放弃同一手势只依据目标的公开 View 标志, 不依赖该回调的执行结果.
 *
 * @author whisper
 * @since 2026/09/04
 */
fun interface OnItemLongClickListener {

    /**
     * 处理 item 内部 View 长按事件.
     *
     * @param recyclerView 触发长按的 RecyclerView.
     * @param view itemView 内实际命中的可长按目标.
     * @param absoluteAdapterPosition [view] 所属 item 的 absolute adapter position;
     * 使用 ConcatAdapter 时该值不是子 Adapter 的 binding adapter position.
     */
    fun onItemLongClick(
        recyclerView: RecyclerView,
        view: View,
        absoluteAdapterPosition: Int,
    )
}
