package com.whisper.kit.recyclerview.wheel

import android.graphics.PointF
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IntDef
import androidx.recyclerview.widget.RecyclerView

/**
 * 从 RecyclerView 纵向中心开始布局的滚轮 LayoutManager, 支持有界和循环滚动.
 *
 * 应同时安装 [WheelSnapHelper], 使滚动停止后的中心条目与 RecyclerView 中心对齐.
 * [scrollToPosition] 和 [smoothScrollToPosition] 均表示将目标条目移动到中心位置.
 *
 * @author whisper
 * @since 2026/10/14
 */
class WheelLayoutManager(
    @param:ScrollFeature
    @get:ScrollFeature
    val scrollFeature: Int = SCROLL_FEATURE_LIMITED,
) : RecyclerView.LayoutManager(), RecyclerView.SmoothScroller.ScrollVectorProvider {

    companion object {
        /** Adapter 首尾是滚动边界. */
        const val SCROLL_FEATURE_LIMITED: Int = 0

        /** Adapter 首尾相接并允许循环滚动. */
        const val SCROLL_FEATURE_UNLIMITED: Int = 2
    }

    @IntDef(SCROLL_FEATURE_LIMITED, SCROLL_FEATURE_UNLIMITED)
    @Retention(AnnotationRetention.SOURCE)
    annotation class ScrollFeature

    private var pendingScrollPosition: Int = RecyclerView.NO_POSITION
    private var centerYPosition: Int = RecyclerView.NO_POSITION
    private var onCenterYPositionChangeListener: OnCenterYPositionChangeListener? = null

    override fun generateDefaultLayoutParams(): RecyclerView.LayoutParams = RecyclerView.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )

    override fun onLayoutChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
        val centerY: Int = (height - paddingTop - paddingBottom) / 2
        val centerViewPosition: Int
        val centerViewOffsetY: Int
        if (pendingScrollPosition == RecyclerView.NO_POSITION) {
            val centerView: View? = findCenterChild()
            val position: Int = centerView?.let(::getPosition) ?: RecyclerView.NO_POSITION
            if (position == RecyclerView.NO_POSITION) {
                centerViewPosition = 0
                centerViewOffsetY = 0
            } else {
                val attachedCenterView: View = checkNotNull(centerView)
                centerViewPosition = minOf(position, itemCount - 1)
                centerViewOffsetY =
                    getDecoratedTop(attachedCenterView) +
                    getDecoratedMeasuredHeight(attachedCenterView) / 2 - centerY
            }
        } else {
            centerViewPosition = pendingScrollPosition
            centerViewOffsetY = 0
        }

        if (state.isPreLayout) {
            return
        }
        detachAndScrapAttachedViews(recycler)

        if (itemCount <= 0) {
            return
        }

        val centerView: View = getViewForPositionByCycle(recycler, centerViewPosition)
        addView(centerView)
        measureChildWithMargins(centerView, 0, 0)
        val firstWidth: Int = getDecoratedMeasuredWidth(centerView)
        val firstHeight: Int = getDecoratedMeasuredHeight(centerView)
        val firstLeft: Int = paddingLeft + getLeftDecorationWidth(centerView)
        val firstTop: Int = centerY - firstHeight / 2 + centerViewOffsetY
        val firstRight: Int = firstLeft + firstWidth
        val firstBottom: Int = firstTop + firstHeight
        layoutDecoratedWithMargins(centerView, firstLeft, firstTop, firstRight, firstBottom)

        layoutChild(
            getDecoratedTop(centerView),
            centerViewPosition - 1,
            LAYOUT_START,
            recycler,
        )
        layoutChild(
            getDecoratedBottom(centerView),
            centerViewPosition + 1,
            LAYOUT_END,
            recycler,
        )
        checkIfCenterYPositionChanged(findCenterChildPosition())
    }

    override fun isAutoMeasureEnabled(): Boolean = true

    override fun canScrollVertically(): Boolean = true

    override fun scrollVerticallyBy(
        dy: Int,
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State,
    ): Int {
        val consumed: Int = fill(dy, recycler)
        offsetChildrenVertical(-consumed)
        checkIfCenterYPositionChanged(findCenterChildPosition())
        return consumed
    }

    override fun scrollToPosition(position: Int) {
        pendingScrollPosition = position
        requestLayout()
    }

    override fun onLayoutCompleted(state: RecyclerView.State) {
        super.onLayoutCompleted(state)
        pendingScrollPosition = RecyclerView.NO_POSITION
    }

    override fun supportsPredictiveItemAnimations(): Boolean = true

    override fun computeVerticalScrollExtent(state: RecyclerView.State): Int {
        if (childCount == 0) {
            return 0
        }
        val firstView: View = getChildAt(0) ?: return 0
        val lastView: View = getChildAt(childCount - 1) ?: return 0
        val top: Int = maxOf(paddingTop, getDecoratedTop(firstView))
        val bottom: Int = minOf(height - paddingBottom, getDecoratedBottom(lastView))
        return bottom - top
    }

    override fun computeVerticalScrollRange(state: RecyclerView.State): Int {
        if (childCount == 0) {
            return 0
        }
        val firstView: View = getChildAt(0) ?: return 0
        val lastView: View = getChildAt(childCount - 1) ?: return 0
        return (getDecoratedBottom(lastView) - getDecoratedTop(firstView)) / childCount * itemCount
    }

    override fun computeVerticalScrollOffset(state: RecyclerView.State): Int {
        if (childCount == 0) {
            return 0
        }
        val centerYView: View = findCenterChild() ?: return 0
        val centerYPosition: Int = getPosition(centerYView)
        val firstView: View = getChildAt(0) ?: return 0
        val lastView: View = getChildAt(childCount - 1) ?: return 0
        val averageHeight: Int =
            (getDecoratedBottom(lastView) - getDecoratedTop(firstView)) / childCount
        val centerY: Int = (height - paddingTop - paddingBottom) / 2
        return centerYPosition * averageHeight - (getDecoratedBottom(centerYView) - centerY)
    }

    override fun smoothScrollToPosition(
        recyclerView: RecyclerView,
        state: RecyclerView.State,
        position: Int,
    ) {
        startSmoothScroll(
            WheelSmoothScroller(recyclerView.context).apply {
                targetPosition = position
            },
        )
    }

    override fun computeScrollVectorForPosition(targetPosition: Int): PointF? {
        if (childCount == 0) {
            return null
        }
        val centerView: View = findCenterChild() ?: return null
        val centerChildPosition: Int = getPosition(centerView)
        if (centerChildPosition == RecyclerView.NO_POSITION || centerChildPosition == targetPosition) {
            return null
        }
        val direction: Int = when (scrollFeature) {
            SCROLL_FEATURE_LIMITED -> if (targetPosition > centerChildPosition) 1 else -1
            SCROLL_FEATURE_UNLIMITED -> {
                val up: Int = (centerChildPosition - targetPosition + itemCount) % itemCount
                val down: Int = (targetPosition - centerChildPosition + itemCount) % itemCount
                when {
                    up < down -> -1
                    up > down -> 1
                    else -> if (targetPosition > centerChildPosition) 1 else -1
                }
            }
            else -> 0
        }
        return PointF(0f, direction.toFloat())
    }

    /** 返回当前覆盖 RecyclerView 纵向中心的条目, 没有已布局条目时返回 null. */
    fun findCenterChild(): View? {
        if (childCount == 0) {
            return null
        }
        val centerIndex: Int = childCount / 2
        val centerView: View = getChildAt(centerIndex) ?: return null
        return when (val centerViewCheckResult: Int = checkChildIfInCenterY(centerView)) {
            0 -> centerView
            else -> {
                if (centerViewCheckResult < 0) {
                    findCenterChildAfter(centerIndex)
                } else {
                    findCenterChildBefore(centerIndex)
                }
            }
        }
    }

    /** 返回当前覆盖 RecyclerView 纵向中心的 Adapter position. */
    fun findCenterChildPosition(): Int =
        findCenterChild()?.let(::getPosition) ?: RecyclerView.NO_POSITION

    /** 设置中心条目 position 变化监听器, null 表示移除. */
    fun setOnCenterYPositionChangeListener(listener: OnCenterYPositionChangeListener?) {
        onCenterYPositionChangeListener = listener
    }

    private fun fill(dy: Int, recycler: RecyclerView.Recycler): Int = when {
        dy > 0 -> fillEnd(dy, recycler)
        dy < 0 -> fillStart(dy, recycler)
        else -> 0
    }

    private fun fillEnd(dy: Int, recycler: RecyclerView.Recycler): Int {
        val anchor: View = getChildAt(childCount - 1) ?: return 0
        val anchorPosition: Int = getPosition(anchor)
        var lastCenterY: Int = anchor.top + anchor.height / 2
        var offset: Int = getDecoratedBottom(anchor)
        var position: Int = anchorPosition + 1
        while (
            (scrollFeature == SCROLL_FEATURE_UNLIMITED || position < itemCount) &&
            offset < height - paddingBottom + dy
        ) {
            val view: View = getViewForPositionByCycle(recycler, position)
            addView(view)
            measureChildWithMargins(view, 0, 0)
            val width: Int = getDecoratedMeasuredWidth(view)
            val viewHeight: Int = getDecoratedMeasuredHeight(view)
            val left: Int = paddingLeft + getLeftDecorationWidth(view)
            val top: Int = offset + getTopDecorationHeight(view)
            val right: Int = left + width
            val bottom: Int = top + viewHeight
            layoutDecoratedWithMargins(view, left, top, right, bottom)
            offset += viewHeight
            lastCenterY = top + viewHeight / 2
            recycleStart(offset, anchor, dy, recycler)
            position += 1
        }
        return if (scrollFeature == SCROLL_FEATURE_UNLIMITED) {
            dy
        } else {
            minOf(dy, lastCenterY - (height - paddingTop - paddingBottom) / 2)
        }
    }

    private fun fillStart(dy: Int, recycler: RecyclerView.Recycler): Int {
        val anchor: View = getChildAt(0) ?: return 0
        val anchorPosition: Int = getPosition(anchor)
        var firstCenterY: Int = anchor.top + anchor.height / 2
        var offset: Int = getDecoratedTop(anchor)
        var position: Int = anchorPosition - 1
        while (
            (scrollFeature == SCROLL_FEATURE_UNLIMITED || position >= 0) &&
            offset > paddingTop + dy
        ) {
            val view: View = getViewForPositionByCycle(recycler, position)
            addView(view, 0)
            measureChildWithMargins(view, 0, 0)
            val width: Int = getDecoratedMeasuredWidth(view)
            val viewHeight: Int = getDecoratedMeasuredHeight(view)
            val left: Int = paddingLeft + getLeftDecorationWidth(view)
            val top: Int = offset - viewHeight - getBottomDecorationHeight(view)
            val right: Int = left + width
            val bottom: Int = top + viewHeight
            layoutDecoratedWithMargins(view, left, top, right, bottom)
            offset -= viewHeight
            firstCenterY = top + viewHeight / 2
            recycleEnd(offset, anchor, dy, recycler)
            position -= 1
        }
        return if (scrollFeature == SCROLL_FEATURE_UNLIMITED) {
            dy
        } else {
            maxOf(dy, firstCenterY - (height - paddingTop - paddingBottom) / 2)
        }
    }

    private fun recycleStart(
        offset: Int,
        anchor: View,
        dy: Int,
        recycler: RecyclerView.Recycler,
    ) {
        while (childCount > 0) {
            val first: View = getChildAt(0) ?: break
            if (
                first.bottom < paddingTop +
                minOf(offset - getDecoratedBottom(anchor), dy)
            ) {
                removeAndRecycleView(first, recycler)
            } else {
                break
            }
        }
    }

    private fun recycleEnd(
        offset: Int,
        anchor: View,
        dy: Int,
        recycler: RecyclerView.Recycler,
    ) {
        while (childCount > 0) {
            val last: View = getChildAt(childCount - 1) ?: break
            if (
                last.top > height - paddingBottom +
                maxOf(offset - getDecoratedTop(anchor), dy)
            ) {
                removeAndRecycleView(last, recycler)
            } else {
                break
            }
        }
    }

    private fun layoutChild(
        baseline: Int,
        position: Int,
        layoutDirection: Int,
        recycler: RecyclerView.Recycler,
    ) {
        when (layoutDirection) {
            LAYOUT_START -> layoutChildToStart(baseline, position, recycler)
            LAYOUT_END -> layoutChildToEnd(baseline, position, recycler)
        }
    }

    private fun layoutChildToStart(
        baseline: Int,
        position: Int,
        recycler: RecyclerView.Recycler,
    ) {
        var offset: Int = baseline
        var targetPosition: Int = position
        while (
            (scrollFeature == SCROLL_FEATURE_UNLIMITED || targetPosition >= 0) &&
            offset >= paddingTop
        ) {
            val view: View = getViewForPositionByCycle(recycler, targetPosition)
            addView(view, 0)
            measureChildWithMargins(view, 0, 0)
            val width: Int = getDecoratedMeasuredWidth(view)
            val viewHeight: Int = getDecoratedMeasuredHeight(view)
            val left: Int = paddingLeft + getLeftDecorationWidth(view)
            val top: Int = offset - viewHeight - getBottomDecorationHeight(view)
            val right: Int = left + width
            val bottom: Int = top + viewHeight
            layoutDecoratedWithMargins(view, left, top, right, bottom)
            offset -= viewHeight
            targetPosition -= 1
        }
    }

    private fun layoutChildToEnd(
        baseline: Int,
        position: Int,
        recycler: RecyclerView.Recycler,
    ) {
        var offset: Int = baseline
        var targetPosition: Int = position
        while (
            (scrollFeature == SCROLL_FEATURE_UNLIMITED || targetPosition < itemCount) &&
            offset < height - paddingBottom
        ) {
            val view: View = getViewForPositionByCycle(recycler, targetPosition)
            addView(view)
            measureChildWithMargins(view, 0, 0)
            val width: Int = getDecoratedMeasuredWidth(view)
            val viewHeight: Int = getDecoratedMeasuredHeight(view)
            val left: Int = paddingLeft + getLeftDecorationWidth(view)
            val top: Int = offset + getTopDecorationHeight(view)
            val right: Int = left + width
            val bottom: Int = top + viewHeight
            layoutDecoratedWithMargins(view, left, top, right, bottom)
            offset += viewHeight
            targetPosition += 1
        }
    }

    private fun getViewForPositionByCycle(
        recycler: RecyclerView.Recycler,
        position: Int,
    ): View = recycler.getViewForPosition((position % itemCount + itemCount) % itemCount)

    private fun findCenterChildAfter(centerIndex: Int): View? {
        for (index: Int in centerIndex + 1 until childCount) {
            val view: View? = getChildAt(index)
            val checkResult: Int = view?.let(::checkChildIfInCenterY) ?: 1
            if (checkResult == 0) {
                return view
            }
            if (checkResult > 0) {
                break
            }
        }
        return null
    }

    private fun findCenterChildBefore(centerIndex: Int): View? {
        for (index: Int in centerIndex - 1 downTo 0) {
            val view: View? = getChildAt(index)
            val checkResult: Int = view?.let(::checkChildIfInCenterY) ?: -1
            if (checkResult == 0) {
                return view
            }
            if (checkResult < 0) {
                break
            }
        }
        return null
    }

    private fun checkChildIfInCenterY(view: View): Int {
        val centerY: Int = (height - paddingTop - paddingBottom) / 2
        return when {
            getDecoratedTop(view) > centerY -> 1
            getDecoratedBottom(view) < centerY -> -1
            else -> 0
        }
    }

    private fun checkIfCenterYPositionChanged(position: Int) {
        if (centerYPosition == position) {
            return
        }
        centerYPosition = position
        onCenterYPositionChangeListener?.onCenterYPositionChanged(position)
    }

    /** 中心位置的 Adapter position 发生变化后的通知. */
    fun interface OnCenterYPositionChangeListener {
        fun onCenterYPositionChanged(position: Int)
    }
}

private const val LAYOUT_START: Int = -1
private const val LAYOUT_END: Int = 1
