package com.whisper.kit.recyclerview.listener

import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * RecyclerView 长按手势命中分发器.
 *
 * 长按复用 DOWN 锁定目标和变换后坐标验证, 只负责分发旁路通知, 不消费事件.
 */
internal class OnDispatchLongClickGestureListener(
    recyclerView: RecyclerView,
    filter: GestureTargetFilter?,
    private val listener: OnItemLongClickListener?,
) : OnDispatchTargetGestureListener(recyclerView, filter) {

    override fun onLongPress(e: MotionEvent) {
        dispatchGestureTargetAtLastPoint()
    }

    override fun onGestureTarget(targetView: View, absoluteAdapterPosition: Int) {
        listener?.onItemLongClick(recyclerView, targetView, absoluteAdapterPosition)
    }
}
