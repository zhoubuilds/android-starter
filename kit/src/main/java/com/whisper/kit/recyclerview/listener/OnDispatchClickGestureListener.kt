package com.whisper.kit.recyclerview.listener

import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * RecyclerView 单击手势命中分发器.
 *
 * 该类先命中被点击的 itemView, 再在 itemView 内查找最深层的可点击目标 View.
 * 它只负责分发旁路点击通知, 不消费事件, 不改变 View 原本的触摸和点击行为.
 *
 * @author whisper
 * @since 2026/07/30
 */
internal class OnDispatchClickGestureListener(
    recyclerView: RecyclerView,
    filter: GestureTargetFilter?,
    private val listener: OnItemClickListener?,
) : OnDispatchTargetGestureListener(recyclerView, filter) {

    override fun onSingleTapUp(e: MotionEvent): Boolean {
        dispatchGestureTarget(e)
        return false
    }

    override fun onLongPress(e: MotionEvent) {
        // 无法无侵入地取得 performLongClick() 结果, 以超时当下的公开 View 标志决定归属.
        if (isGestureTargetLongClickableAndEnabled()) {
            clearGestureTarget()
        }
    }

    /**
     * 补充分发 GestureDetector 进入长按状态后不再产生的单击抬起回调.
     *
     * 普通单击已经在 [onSingleTapUp] 清理目标, longClickable 目标已经在长按超时时清理;
     * 因此这里只可能分发超时时未被长按占用且通过 ACTION_UP 最终校验的目标.
     */
    fun dispatchGestureTargetOnUp(e: MotionEvent) {
        if (e.actionMasked == MotionEvent.ACTION_UP) {
            dispatchGestureTarget(e)
        }
    }

    override fun onGestureTarget(targetView: View, absoluteAdapterPosition: Int) {
        listener?.onItemClick(recyclerView, targetView, absoluteAdapterPosition)
    }
}
