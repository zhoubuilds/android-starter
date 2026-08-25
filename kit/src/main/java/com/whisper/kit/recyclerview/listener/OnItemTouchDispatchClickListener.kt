package com.whisper.kit.recyclerview.listener

import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * 统一分发 RecyclerView item 内部 View 点击的触摸监听器.
 *
 * 将该监听器添加到 RecyclerView 后, 单击事件会被路由到 itemView 内实际命中的
 * `clickable && enabled` 目标 View. 如果没有命中可点击目标, 不会触发回调; 需要让
 * item 空白区域响应点击时, 应将 item 根 View 设置为 clickable.
 *
 * 该监听器是旁路通知工具, 不消费事件, 不改变子 View 原本的触摸和点击行为.
 * 如果同一批 View 同时设置了原生 OnClickListener, 调用方会收到两套点击通知.
 *
 * @author whisper
 * @since 2026/07/30
 */
class OnItemTouchDispatchClickListener(
    recyclerView: RecyclerView,
    listener: OnItemClickListener?,
) : RecyclerView.OnItemTouchListener {

    /**
     * 单击手势检测器.
     */
    private val gestureDetector: GestureDetector = GestureDetector(
        recyclerView.context,
        OnDispatchClickGestureListener(
            recyclerView,
            { view: View -> view.isClickable && view.isEnabled },
            listener,
        ),
    )

    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
        return gestureDetector.onTouchEvent(e)
    }

    override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
    }

    override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
    }
}
