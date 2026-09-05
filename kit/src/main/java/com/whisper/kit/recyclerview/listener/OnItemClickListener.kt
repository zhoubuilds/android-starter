package com.whisper.kit.recyclerview.listener

import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * RecyclerView item 内部 View 点击回调.
 *
 * 回调中的 [view] 是 itemView 内实际命中的可点击目标, [absoluteAdapterPosition] 是该 item
 * 在 RecyclerView 完整 adapter 链中的当前位置. 当 RecyclerView 正在移除、移动或刷新 item 导致位置无效时,
 * 监听器不会触发回调.
 * 该接口表示 RecyclerView 收到的指针点击通知, 不等同于 [View.OnClickListener] 的完整输入语义.
 * 未被 `longClickable && enabled` 占用的长按手势也可能在抬起时产生该通知.
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
     * @param absoluteAdapterPosition [view] 所属 item 的 absolute adapter position;
     * 使用 ConcatAdapter 时该值不是子 Adapter 的 binding adapter position.
     */
    fun onItemClick(recyclerView: RecyclerView, view: View, absoluteAdapterPosition: Int)
}
