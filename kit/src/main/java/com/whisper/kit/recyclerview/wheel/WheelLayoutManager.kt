package com.whisper.kit.recyclerview.wheel

import android.graphics.PointF
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IntDef
import androidx.recyclerview.widget.RecyclerView

/**
 * 从 RecyclerView 纵向中心开始布局的滚轮 LayoutManager, 支持有界和条件循环滚动.
 *
 * 应同时安装 [WheelSnapHelper], 使滚动停止后的中心条目与 RecyclerView 中心对齐.
 * [scrollToPosition] 和 [smoothScrollToPosition] 均表示将目标条目移动到中心位置.
 * 循环模式只在每个 Adapter position 最多布局一次即可覆盖布局窗口时生效, 否则自动退化为有界模式.
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
        /**
         * Adapter 首尾是滚动边界.
         */
        const val SCROLL_FEATURE_LIMITED: Int = 0

        /**
         * Adapter 首尾在能够保持 Adapter position 唯一时相接, 否则使用有界滚动.
         */
        const val SCROLL_FEATURE_UNLIMITED: Int = 2
    }

    /**
     * 滚动特征取值范围.
     */
    @IntDef(SCROLL_FEATURE_LIMITED, SCROLL_FEATURE_UNLIMITED)
    @Retention(AnnotationRetention.SOURCE)
    annotation class ScrollFeature

    /**
     * 当前布局是否满足 position 唯一约束并实际启用了循环滚动.
     */
    internal var isLoopingActive: Boolean = false
        private set

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
            isLoopingActive = false
            return
        }

        val centerAdapterPosition: Int = normalizeAdapterPosition(centerViewPosition)
        val shouldTryLooping: Boolean =
            scrollFeature == SCROLL_FEATURE_UNLIMITED && itemCount > 1
        isLoopingActive = shouldTryLooping && layoutFromCenter(
            recycler = recycler,
            centerPosition = centerAdapterPosition,
            centerOffsetY = centerViewOffsetY,
            allowLooping = true,
        )
        if (!isLoopingActive) {
            if (shouldTryLooping) {
                detachAndScrapAttachedViews(recycler)
            }
            layoutFromCenter(
                recycler = recycler,
                centerPosition = centerAdapterPosition,
                centerOffsetY = centerViewOffsetY,
                allowLooping = false,
            )
        }
        checkIfCenterYPositionChanged(findCenterChildPosition())
    }

    private fun layoutFromCenter(
        recycler: RecyclerView.Recycler,
        centerPosition: Int,
        centerOffsetY: Int,
        allowLooping: Boolean,
    ): Boolean {
        val centerY: Int = (height - paddingTop - paddingBottom) / 2
        val laidOutPositions: MutableSet<Int> = mutableSetOf(centerPosition)
        val centerView: View = recycler.getViewForPosition(centerPosition)
        addView(centerView)
        measureChildWithMargins(centerView, 0, 0)
        val firstWidth: Int = getDecoratedMeasuredWidth(centerView)
        val firstHeight: Int = getDecoratedMeasuredHeight(centerView)
        val firstLeft: Int = paddingLeft + getLeftDecorationWidth(centerView)
        val firstTop: Int = centerY - firstHeight / 2 + centerOffsetY
        val firstRight: Int = firstLeft + firstWidth
        val firstBottom: Int = firstTop + firstHeight
        layoutDecoratedWithMargins(centerView, firstLeft, firstTop, firstRight, firstBottom)

        val startCovered: Boolean = layoutChild(
            getDecoratedTop(centerView),
            centerPosition - 1,
            LAYOUT_START,
            recycler,
            allowLooping,
            laidOutPositions,
        )
        val endCovered: Boolean = layoutChild(
            getDecoratedBottom(centerView),
            centerPosition + 1,
            LAYOUT_END,
            recycler,
            allowLooping,
            laidOutPositions,
        )
        return startCovered && endCovered
    }

    override fun isAutoMeasureEnabled(): Boolean = true

    override fun canScrollVertically(): Boolean = true

    override fun scrollVerticallyBy(
        dy: Int,
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State,
    ): Int {
        if (dy == 0 || childCount == 0) {
            return 0
        }
        val maximumLoopingStep: Int = maxOf(1, (height - paddingTop - paddingBottom) / 2)
        var remaining: Int = dy
        var totalConsumed: Int = 0
        while (remaining != 0) {
            val requestedStep: Int = if (isLoopingActive) {
                remaining.coerceIn(-maximumLoopingStep, maximumLoopingStep)
            } else {
                remaining
            }
            val consumedStep: Int = fill(requestedStep, recycler)
            if (consumedStep == 0) {
                break
            }
            offsetChildrenVertical(-consumedStep)
            totalConsumed += consumedStep
            remaining -= consumedStep
            if (consumedStep != requestedStep || !isLoopingActive) {
                break
            }
        }
        checkIfCenterYPositionChanged(findCenterChildPosition())
        return totalConsumed
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
        val direction: Int = when {
            !isLoopingActive -> if (targetPosition > centerChildPosition) 1 else -1
            else -> {
                val up: Int = (centerChildPosition - targetPosition + itemCount) % itemCount
                val down: Int = (targetPosition - centerChildPosition + itemCount) % itemCount
                when {
                    up < down -> -1
                    up > down -> 1
                    else -> if (targetPosition > centerChildPosition) 1 else -1
                }
            }
        }
        return PointF(0f, direction.toFloat())
    }

    /**
     * 返回当前覆盖 RecyclerView 纵向中心的条目, 没有已布局条目时返回 null.
     */
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

    /**
     * 返回当前覆盖 RecyclerView 纵向中心的 Adapter position.
     */
    fun findCenterChildPosition(): Int =
        findCenterChild()?.let(::getPosition) ?: RecyclerView.NO_POSITION

    /**
     * 设置中心条目 position 变化监听器, null 表示移除.
     */
    fun setOnCenterYPositionChangeListener(listener: OnCenterYPositionChangeListener?) {
        onCenterYPositionChangeListener = listener
    }

    private fun fill(dy: Int, recycler: RecyclerView.Recycler): Int = when {
        dy > 0 -> fillEnd(dy, recycler)
        dy < 0 -> fillStart(dy, recycler)
        else -> 0
    }

    private fun fillEnd(dy: Int, recycler: RecyclerView.Recycler): Int {
        if (isLoopingActive) {
            recycleStartBeforeScroll(dy, recycler)
        }
        val anchor: View = getChildAt(childCount - 1) ?: return 0
        val anchorPosition: Int = getPosition(anchor)
        var lastCenterY: Int = anchor.top + anchor.height / 2
        var offset: Int = getDecoratedBottom(anchor)
        var position: Int = anchorPosition + 1
        var duplicatePositionBlockedFill: Boolean = false
        while (
            (isLoopingActive || position < itemCount) &&
            offset < height - paddingBottom + dy
        ) {
            val adapterPosition: Int = resolveAdapterPosition(position, isLoopingActive) ?: break
            if (isLoopingActive && isAdapterPositionAttached(adapterPosition)) {
                duplicatePositionBlockedFill = true
                break
            }
            val view: View = recycler.getViewForPosition(adapterPosition)
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
        if (duplicatePositionBlockedFill) {
            isLoopingActive = false
            requestLayout()
        }
        return if (isLoopingActive) {
            dy
        } else {
            minOf(dy, lastCenterY - (height - paddingTop - paddingBottom) / 2)
        }
    }

    private fun fillStart(dy: Int, recycler: RecyclerView.Recycler): Int {
        if (isLoopingActive) {
            recycleEndBeforeScroll(dy, recycler)
        }
        val anchor: View = getChildAt(0) ?: return 0
        val anchorPosition: Int = getPosition(anchor)
        var firstCenterY: Int = anchor.top + anchor.height / 2
        var offset: Int = getDecoratedTop(anchor)
        var position: Int = anchorPosition - 1
        var duplicatePositionBlockedFill: Boolean = false
        while (
            (isLoopingActive || position >= 0) &&
            offset > paddingTop + dy
        ) {
            val adapterPosition: Int = resolveAdapterPosition(position, isLoopingActive) ?: break
            if (isLoopingActive && isAdapterPositionAttached(adapterPosition)) {
                duplicatePositionBlockedFill = true
                break
            }
            val view: View = recycler.getViewForPosition(adapterPosition)
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
        if (duplicatePositionBlockedFill) {
            isLoopingActive = false
            requestLayout()
        }
        return if (isLoopingActive) {
            dy
        } else {
            maxOf(dy, firstCenterY - (height - paddingTop - paddingBottom) / 2)
        }
    }

    private fun recycleStartBeforeScroll(dy: Int, recycler: RecyclerView.Recycler) {
        while (childCount > 1) {
            val first: View = getChildAt(0) ?: return
            if (getDecoratedBottom(first) - dy <= paddingTop) {
                removeAndRecycleView(first, recycler)
            } else {
                return
            }
        }
    }

    private fun recycleEndBeforeScroll(dy: Int, recycler: RecyclerView.Recycler) {
        while (childCount > 1) {
            val last: View = getChildAt(childCount - 1) ?: return
            if (getDecoratedTop(last) - dy >= height - paddingBottom) {
                removeAndRecycleView(last, recycler)
            } else {
                return
            }
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
        allowLooping: Boolean,
        laidOutPositions: MutableSet<Int>,
    ): Boolean = when (layoutDirection) {
        LAYOUT_START -> layoutChildToStart(
            baseline,
            position,
            recycler,
            allowLooping,
            laidOutPositions,
        )
        LAYOUT_END -> layoutChildToEnd(
            baseline,
            position,
            recycler,
            allowLooping,
            laidOutPositions,
        )
        else -> false
    }

    private fun resolveAdapterPosition(position: Int, allowLooping: Boolean): Int? {
        if (position in 0 until itemCount) {
            return position
        }
        return if (allowLooping) normalizeAdapterPosition(position) else null
    }

    private fun layoutChildToStart(
        baseline: Int,
        position: Int,
        recycler: RecyclerView.Recycler,
        allowLooping: Boolean,
        laidOutPositions: MutableSet<Int>,
    ): Boolean {
        var offset: Int = baseline
        var targetPosition: Int = position
        while (offset >= paddingTop) {
            val adapterPosition: Int = resolveAdapterPosition(targetPosition, allowLooping) ?: return false
            if (!laidOutPositions.add(adapterPosition)) {
                return false
            }
            val view: View = recycler.getViewForPosition(adapterPosition)
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
        return true
    }

    private fun layoutChildToEnd(
        baseline: Int,
        position: Int,
        recycler: RecyclerView.Recycler,
        allowLooping: Boolean,
        laidOutPositions: MutableSet<Int>,
    ): Boolean {
        var offset: Int = baseline
        var targetPosition: Int = position
        while (offset < height - paddingBottom) {
            val adapterPosition: Int = resolveAdapterPosition(targetPosition, allowLooping) ?: return false
            if (!laidOutPositions.add(adapterPosition)) {
                return false
            }
            val view: View = recycler.getViewForPosition(adapterPosition)
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
        return true
    }

    private fun normalizeAdapterPosition(position: Int): Int =
        (position % itemCount + itemCount) % itemCount

    private fun isAdapterPositionAttached(position: Int): Boolean {
        for (index: Int in 0 until childCount) {
            val child: View = getChildAt(index) ?: continue
            if (getPosition(child) == position) {
                return true
            }
        }
        return false
    }

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

    /**
     * 中心位置的 Adapter position 发生变化后的通知.
     */
    fun interface OnCenterYPositionChangeListener {
        fun onCenterYPositionChanged(position: Int)
    }
}

private const val LAYOUT_START: Int = -1
private const val LAYOUT_END: Int = 1
