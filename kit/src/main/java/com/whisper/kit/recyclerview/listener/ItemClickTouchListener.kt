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
 * 目标在 ACTION_DOWN 时确定; 后续事件只验证原目标, ACTION_UP 不会重新选择其它 View.
 *
 * 该监听器是旁路通知工具, 不消费事件, 不改变子 View 原本的触摸和点击行为.
 * 如果同一批 View 同时设置了原生 OnClickListener, 调用方会收到两套点击通知.
 * 每个有效的 DOWN/UP 都按普通 View 点击独立回调, 快速双击不会合并或延迟单击.
 * 按住超过平台长按超时后, 监听器在超时回调当下读取目标状态. 目标满足
 * `longClickable && enabled` 时立即且永久取消当前旁路点击; 该行为不要求安装
 * [ItemLongClickTouchListener], 也无法区分原生 `performLongClick()` 最终返回 `true` 或 `false`.
 * 其它目标会保留到 ACTION_UP, 届时仍需通过 clickable、enabled、挂载、窗口焦点、位置和
 * 命中边界的最终校验才会回调. 超时后的属性变化不会重新执行长按占用判断.
 * 该机制只观察传递给 RecyclerView 的 [MotionEvent], 不会收到无障碍、键盘或代码直接
 * 调用 `performClick()` 产生的非指针点击.
 * 命中和遮挡只依据标准 View 触摸标志. 无法无侵入地探测自定义 OnTouchListener 的消费结果
 * 或父 View 的 TouchDelegate; 依赖这些机制的业务交互必须继续使用原生触摸链路.
 * [recyclerView] 必须与最终调用 [RecyclerView.addOnItemTouchListener] 的实例相同; 直接使用
 * 公开构造器时, 该绑定关系由调用方保证.
 * 子 View 调用 `requestDisallowInterceptTouchEvent(true)` 后, RecyclerView 可能不再向旁路监听器提供
 * 完整事件序列; 本监听器会清理已锁定目标, 让当前手势安全退化为不回调. `false` 不影响当前目标.
 *
 * 该监听器只保证实际收到完整 DOWN 至 UP/CANCEL 事件序列时的手势识别. 在 DOWN 后调用
 * [RecyclerView.removeOnItemTouchListener], 或由其它消费型 [RecyclerView.OnItemTouchListener]
 * 中途接管事件时, RecyclerView 不会向本监听器补发 CANCEL; 这些用法不在支持范围内.
 * 监听器应在当前手势结束后移除, 并避免与可能中途消费同一事件流的监听器组合使用.
 *
 * @author whisper
 * @since 2026/07/30
 */
class ItemClickTouchListener(
    recyclerView: RecyclerView,
    listener: OnItemClickListener?,
) : RecyclerView.OnItemTouchListener {

    /**
     * DOWN 目标锁定和单击回调实现.
     */
    private val dispatchGestureListener: OnDispatchClickGestureListener =
        OnDispatchClickGestureListener(
            recyclerView = recyclerView,
            filter = { view: View -> view.isClickable && view.isEnabled },
            listener = listener,
        )

    /**
     * 单击手势检测器.
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
            // GestureDetector 进入长按状态后不会再调用 onSingleTapUp, 由锁定目标决定是否补发.
            dispatchGestureListener.dispatchGestureTargetOnUp(e)
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
