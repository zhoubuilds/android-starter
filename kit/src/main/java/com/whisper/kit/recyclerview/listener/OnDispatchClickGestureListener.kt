package com.whisper.kit.recyclerview.listener

import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.RecyclerView


/**
 *
 *
 * Created by whisper on 2024/11/20
 */
class OnDispatchClickGestureListener(
    private val _recyclerView: RecyclerView,
    filter: ItemViewFilter?,
    private val _listener: OnItemClickListener?
) : OnDispatchGestureListener(filter) {

    override fun onSingleTapUp(e: MotionEvent): Boolean {
        val view: View = findChildViewOnPoint(_recyclerView, e.x, e.y) ?: return false
        val position: Int = _recyclerView.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) return false
        // 将 RecyclerView 坐标转换到 item view 的本地坐标系.
        // 这里和 Android ViewGroup 分发触摸事件一样, 会应用 item 自身 matrix 的逆变换.
        val point = transformPointToChildLocal(_recyclerView, view, e.x, e.y)
        val localX = point[0]
        val localY = point[1]
        val target = findViewOnPoint(view, localX, localY) ?: return false
        _listener?.onItemClick(_recyclerView, target, position)
        // 这是一个旁路点击通知工具, 不消费事件, 不改变 View 原本的触摸/点击行为.
        // 同一批 View 如果还设置了原生 OnClickListener, 调用方会同时收到两套点击通知.
        return false
    }

}
