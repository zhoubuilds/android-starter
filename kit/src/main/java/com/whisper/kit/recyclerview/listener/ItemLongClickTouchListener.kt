package com.whisper.kit.recyclerview.listener

import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * 统一分发 RecyclerView item 内部 View 长按的触摸监听器.
 *
 * 该监听器只回调 DOWN 时命中的 `longClickable && enabled` 目标. MOVE 和终止事件沿用
 * [ItemClickTouchListener] 的目标锁定与取消语义. 它是完全无侵入的旁路通知工具,
 * 不消费事件, 不影响 View 原生 OnLongClickListener 或上下文菜单.
 * 同一 RecyclerView 同时安装点击监听器时, 超时当下仍满足 `longClickable && enabled` 的
 * 点击目标会放弃旁路点击, 长按通知不需要也不能通过 Boolean 决定该结果.
 *
 * 命中和遮挡只依据标准 View 触摸标志, 无法探测自定义 OnTouchListener 的消费结果或
 * TouchDelegate 扩展区域. 无障碍、键盘和代码直接调用 `performLongClick()` 也不在观察范围内.
 * [recyclerView] 必须与最终调用 [RecyclerView.addOnItemTouchListener] 的实例相同; 直接使用
 * 公开构造器时, 该绑定关系由调用方保证.
 * 子 View 调用 `requestDisallowInterceptTouchEvent(true)` 后, RecyclerView 可能不再向旁路监听器提供
 * 完整事件序列; 本监听器会清理已锁定目标, 让当前手势安全退化为不回调. `false` 不影响当前目标.
 *
 * 该监听器只保证实际收到完整 DOWN 至 UP/CANCEL 事件序列时的手势识别. 在 DOWN 后调用
 * [RecyclerView.removeOnItemTouchListener], 或由其它消费型 [RecyclerView.OnItemTouchListener]
 * 中途接管事件时, RecyclerView 不会向本监听器补发 CANCEL, 已排队的长按仍可能回调;
 * 这些用法不在支持范围内. 监听器应在当前手势结束后移除, 并避免与可能中途消费同一事件流的监听器组合使用.
 *
 * @author whisper
 * @since 2026/09/04
 */
class ItemLongClickTouchListener(
    recyclerView: RecyclerView,
    listener: OnItemLongClickListener?,
) : RecyclerView.OnItemTouchListener {

    /**
     * DOWN 目标锁定和长按回调实现.
     */
    private val dispatchGestureListener: OnDispatchLongClickGestureListener =
        OnDispatchLongClickGestureListener(
            recyclerView = recyclerView,
            filter = { view: View -> view.isLongClickable && view.isEnabled },
            listener = listener,
        )

    /**
     * 长按手势检测器.
     */
    private val gestureDetector: GestureDetector = GestureDetector(
        recyclerView.context,
        dispatchGestureListener,
    ).apply {
        setOnDoubleTapListener(null)
    }

    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
        dispatchGestureListener.onMotionEvent(e)
        try {
            if (dispatchGestureListener.shouldDispatchToGestureDetector(e)) {
                gestureDetector.onTouchEvent(e)
            }
        } finally {
            dispatchGestureListener.onMotionEventFinished(e)
        }
        return false
    }

    override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
    }

    override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        if (disallowIntercept) {
            dispatchGestureListener.clearGestureTarget()
        }
    }
}
