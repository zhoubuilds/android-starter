package com.whisper.kit.recyclerview.listener

import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

/**
 * 维护 RecyclerView 手势从 DOWN 到终止事件的目标归属.
 *
 * 完整命中只发生在 DOWN. 后续事件沿原目标父链验证当前坐标, 并在手势失效或终止时
 * 主动释放对 item 和目标 View 的强引用.
 */
internal abstract class OnDispatchTargetGestureListener(
    protected val recyclerView: RecyclerView,
    filter: GestureTargetFilter?,
) : OnDispatchGestureListener(filter) {

    /**
     * 当前手势在 DOWN 阶段命中的 item.
     */
    private var downItemView: View? = null

    /**
     * 当前手势在 DOWN 阶段命中的回调目标.
     */
    private var downTargetView: View? = null

    /**
     * 当前手势 DOWN 事件在 RecyclerView 中的坐标.
     */
    private var downX: Float = 0f
    private var downY: Float = 0f

    /**
     * 最近一次有效指针事件在 RecyclerView 中的坐标.
     */
    private var lastX: Float = 0f
    private var lastY: Float = 0f

    /**
     * 原生 View 点击边界允许的移动容差.
     */
    private val touchSlop: Float =
        ViewConfiguration.get(recyclerView.context).scaledTouchSlop.toFloat()

    final override fun onDown(e: MotionEvent): Boolean {
        clearGestureTarget()
        downX = e.x
        downY = e.y
        val itemView: View = findChildViewOnPoint(recyclerView, e.x, e.y) ?: return false
        if (recyclerView.getChildAdapterPosition(itemView) == RecyclerView.NO_POSITION) return false

        val point: FloatArray = transformPointToChildLocal(recyclerView, itemView, e.x, e.y)
        val targetView: View = findViewOnPoint(itemView, point[0], point[1]) ?: return false
        downItemView = itemView
        downTargetView = targetView
        lastX = e.x
        lastY = e.y
        return false
    }

    final override fun onScroll(
        e1: MotionEvent?,
        e2: MotionEvent,
        distanceX: Float,
        distanceY: Float,
    ): Boolean {
        clearGestureTarget()
        return false
    }

    /**
     * 在 GestureDetector 处理事件前维护 DOWN 锁定目标.
     */
    fun onMotionEvent(e: MotionEvent) {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> clearGestureTarget()
            MotionEvent.ACTION_MOVE -> {
                val itemView: View = downItemView ?: return
                val targetView: View = downTargetView ?: return
                if (isGestureTargetValid(itemView, targetView, e.x, e.y)) {
                    lastX = e.x
                    lastY = e.y
                } else {
                    clearGestureTarget()
                }
            }

            MotionEvent.ACTION_POINTER_DOWN,
            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_OUTSIDE,
            -> clearGestureTarget()
        }
    }

    /**
     * 判断当前事件是否需要交给 GestureDetector 更新手势分类.
     *
     * RecyclerView 只会在 LayoutManager 支持的滚动轴位移超过 touch slop 时开始滚动.
     * 未达到任一可滚动轴阈值的 MOVE 不交给 GestureDetector, 避免其二维距离算法把
     * 交叉轴移动或两轴各自未越界的轻微移动误判为滚动.
     */
    fun shouldDispatchToGestureDetector(e: MotionEvent): Boolean {
        if (e.actionMasked != MotionEvent.ACTION_MOVE) return true

        val layoutManager: RecyclerView.LayoutManager = recyclerView.layoutManager ?: return false
        val exceedsHorizontalSlop: Boolean =
            layoutManager.canScrollHorizontally() && abs(e.x - downX) > touchSlop
        val exceedsVerticalSlop: Boolean =
            layoutManager.canScrollVertically() && abs(e.y - downY) > touchSlop
        return exceedsHorizontalSlop || exceedsVerticalSlop
    }

    /**
     * 在终止事件处理后释放当前手势持有的 View.
     */
    fun onMotionEventFinished(e: MotionEvent) {
        if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) {
            clearGestureTarget()
        }
    }

    /**
     * 主动释放当前手势持有的 item 和目标 View.
     */
    fun clearGestureTarget() {
        downItemView = null
        downTargetView = null
    }

    /**
     * 读取调用当下锁定目标是否仍声明并启用长按能力.
     *
     * 该结果只依据公开 View 标志, 不表示原生长按监听器或上下文菜单实际消费了事件.
     */
    protected fun isGestureTargetLongClickableAndEnabled(): Boolean {
        val targetView: View = downTargetView ?: return false
        return targetView.isLongClickable && targetView.isEnabled
    }

    /**
     * 使用当前事件坐标验证并分发锁定目标.
     */
    protected fun dispatchGestureTarget(e: MotionEvent) {
        dispatchGestureTarget(e.x, e.y)
    }

    /**
     * 使用最近一次有效指针坐标验证并分发锁定目标.
     */
    protected fun dispatchGestureTargetAtLastPoint() {
        dispatchGestureTarget(lastX, lastY)
    }

    /**
     * 接收已经完成最终校验的手势目标.
     */
    protected abstract fun onGestureTarget(targetView: View, absoluteAdapterPosition: Int)

    /**
     * 清理状态后验证原目标并向具体手势实现分发.
     */
    private fun dispatchGestureTarget(localX: Float, localY: Float) {
        val itemView: View = downItemView ?: return
        val targetView: View = downTargetView ?: return
        clearGestureTarget()

        if (!isGestureTargetValid(itemView, targetView, localX, localY)) return
        if (!recyclerView.isAttachedToWindow || !recyclerView.hasWindowFocus()) return
        if (!itemView.isAttachedToWindow || !targetView.isAttachedToWindow) return
        val absoluteAdapterPosition: Int = recyclerView.getChildAdapterPosition(itemView)
        if (absoluteAdapterPosition == RecyclerView.NO_POSITION) return

        onGestureTarget(targetView, absoluteAdapterPosition)
    }

    /**
     * 验证原目标仍属于 DOWN item, 且当前触点仍在该目标的扩展点击边界内.
     */
    private fun isGestureTargetValid(
        itemView: View,
        targetView: View,
        localX: Float,
        localY: Float,
    ): Boolean =
        itemView.parent === recyclerView
            && targetView.isDescendantOrSelfOf(itemView)
            && isViewTarget(targetView)
            && recyclerView.getChildAdapterPosition(itemView) != RecyclerView.NO_POSITION
            && isPointInsideDescendant(
                ancestor = recyclerView,
                descendant = targetView,
                ancestorLocalX = localX,
                ancestorLocalY = localY,
                touchSlop = touchSlop,
            )

    /**
     * 判断当前 View 是否仍位于指定 item 的父链中.
     */
    private fun View.isDescendantOrSelfOf(ancestor: View): Boolean {
        var current: View? = this
        while (current != null) {
            if (current === ancestor) return true
            current = current.parent as? View
        }
        return false
    }
}
