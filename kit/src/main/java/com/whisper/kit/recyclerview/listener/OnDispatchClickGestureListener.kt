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
    private val recyclerView: RecyclerView,
    filter: ItemViewFilter?,
    private val listener: OnItemClickListener?,
) : OnDispatchGestureListener(filter) {

    override fun onSingleTapUp(e: MotionEvent): Boolean {
        val view: View = findChildViewOnPoint(recyclerView, e.x, e.y) ?: return false
        val position: Int = recyclerView.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) return false

        // 将 RecyclerView 坐标转换到 itemView 的本地坐标系, 保留 item 自身 matrix 影响.
        val point: FloatArray = transformPointToChildLocal(recyclerView, view, e.x, e.y)
        val localX: Float = point[0]
        val localY: Float = point[1]
        val target: View = findViewOnPoint(view, localX, localY) ?: return false
        listener?.onItemClick(recyclerView, target, position)
        return false
    }
}
