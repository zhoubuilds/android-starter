package com.whisper.kit.recyclerview.wheel

import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.os.Parcel
import android.os.Parcelable
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IntDef
import androidx.recyclerview.widget.OrientationHelper
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

/**
 * 从 RecyclerView 纵向中心开始布局的滚轮 LayoutManager, 支持有界和条件循环滚动.
 *
 * 应同时安装 [WheelSnapHelper], 使滚动停止后的中心条目与 RecyclerView 中心对齐.
 * [scrollToPosition] 和 [smoothScrollToPosition] 均表示将目标条目移动到中心位置.
 * 循环模式只在每个 Adapter position 最多布局一次即可覆盖布局窗口时生效, 否则自动退化为有界模式.
 * item 的纵向占用尺寸可以为 0; 布局终止依赖 Adapter position 推进, 不依赖每个 item 产生像素位移.
 * 不支持 RecyclerView predictive item animations; 默认 ItemAnimator 仍可执行普通的增删、移动和内容变化动画.
 * RecyclerView 重建时保存并恢复中心 Adapter position 及其相对内容中心的像素偏移.
 * [scrollFeature] 只接受 [SCROLL_FEATURE_LIMITED] 和 [SCROLL_FEATURE_UNLIMITED].
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

    /**
     * 循环填充退化后, 当前循环 child 顺序是否仍在等待有界重布局替换.
     */
    private var isLoopingFallbackLayoutPending: Boolean = false

    /**
     * 将滚动期间被 RecyclerView 拦截的循环退化重布局投递到当前滚动之后.
     */
    private val loopingFallbackLayoutHandler: Handler = Handler(Looper.getMainLooper())

    /**
     * 仅在循环退化仍等待有界重布局时重新发起布局请求.
     */
    private val loopingFallbackLayoutAction: Runnable = Runnable {
        if (isLoopingFallbackLayoutPending) {
            requestLayout()
        }
    }

    /**
     * RecyclerView 扣除纵向 padding 后的内容中心坐标.
     */
    private val contentCenterY: Int
        get() = verticalOrientationHelper.startAfterPadding +
            verticalOrientationHelper.totalSpace / 2

    private val verticalOrientationHelper: OrientationHelper =
        OrientationHelper.createVerticalHelper(this)
    private var pendingScrollPosition: Int = RecyclerView.NO_POSITION
    private var pendingSavedState: SavedState? = null
    private var centerYPosition: Int = RecyclerView.NO_POSITION

    /**
     * 上一次非 pre-layout 布局的内容中心, null 表示当前没有可复用的重布局锚点.
     */
    private var lastLayoutPassContentCenterY: Int? = null
    private var onCenterYPositionChangeListener: OnCenterYPositionChangeListener? = null

    init {
        require(
            scrollFeature == SCROLL_FEATURE_LIMITED ||
                scrollFeature == SCROLL_FEATURE_UNLIMITED,
        ) {
            "scrollFeature must be SCROLL_FEATURE_LIMITED or SCROLL_FEATURE_UNLIMITED"
        }
    }

    override fun generateDefaultLayoutParams(): RecyclerView.LayoutParams = RecyclerView.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )

    override fun onLayoutChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
        if (state.isPreLayout) {
            return
        }
        if (itemCount <= 0) {
            detachAndScrapAttachedViews(recycler)
            isLoopingActive = false
            isLoopingFallbackLayoutPending = false
            loopingFallbackLayoutHandler.removeCallbacks(loopingFallbackLayoutAction)
            lastLayoutPassContentCenterY = null
            checkIfCenterYPositionChanged(RecyclerView.NO_POSITION)
            verticalOrientationHelper.onLayoutComplete()
            return
        }

        val centerViewPosition: Int
        val centerViewOffsetY: Int
        val isRestoringSavedState: Boolean
        val requestedPosition: Int? = pendingScrollPosition.takeIf(::isValidAdapterPosition)
        val restoredState: SavedState? = pendingSavedState?.takeIf { savedState: SavedState ->
            isValidAdapterPosition(savedState.anchorPosition)
        }
        if (requestedPosition != null) {
            centerViewPosition = requestedPosition
            centerViewOffsetY = 0
            isRestoringSavedState = false
        } else if (restoredState != null) {
            centerViewPosition = restoredState.anchorPosition
            centerViewOffsetY = restoredState.anchorOffsetY
            isRestoringSavedState = true
        } else {
            val anchorCenterY: Int = lastLayoutPassContentCenterY ?: contentCenterY
            val centerView: View? = findCenterChild(anchorCenterY, centerYPosition)
            val position: Int = centerView?.let(::getPosition) ?: RecyclerView.NO_POSITION
            if (position == RecyclerView.NO_POSITION) {
                centerViewPosition = 0
                centerViewOffsetY = 0
            } else {
                val attachedCenterView: View = checkNotNull(centerView)
                centerViewPosition = minOf(position, itemCount - 1)
                centerViewOffsetY = decoratedCenterY(attachedCenterView) - anchorCenterY
            }
            isRestoringSavedState = false
        }
        detachAndScrapAttachedViews(recycler)

        val centerAdapterPosition: Int = centerViewPosition
        val shouldTryLooping: Boolean =
            scrollFeature == SCROLL_FEATURE_UNLIMITED && itemCount > 1
        isLoopingActive = shouldTryLooping && layoutFromCenter(
            recycler = recycler,
            centerPosition = centerAdapterPosition,
            centerOffsetY = centerViewOffsetY,
            allowLooping = true,
            isRestoringSavedState = isRestoringSavedState,
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
                isRestoringSavedState = isRestoringSavedState,
            )
        }
        isLoopingFallbackLayoutPending = false
        loopingFallbackLayoutHandler.removeCallbacks(loopingFallbackLayoutAction)
        checkIfCenterYPositionChanged(
            findCenterChild(contentCenterY, centerAdapterPosition)?.let(::getPosition)
                ?: RecyclerView.NO_POSITION,
        )
        lastLayoutPassContentCenterY = contentCenterY
        verticalOrientationHelper.onLayoutComplete()
    }

    private fun layoutFromCenter(
        recycler: RecyclerView.Recycler,
        centerPosition: Int,
        centerOffsetY: Int,
        allowLooping: Boolean,
        isRestoringSavedState: Boolean,
    ): Boolean {
        val laidOutPositions: MutableSet<Int> = mutableSetOf(centerPosition)
        val centerView: View = recycler.getViewForPosition(centerPosition)
        addView(centerView)
        measureChildWithMargins(centerView, 0, 0)
        val firstWidth: Int = verticalOrientationHelper.getDecoratedMeasurementInOther(centerView)
        val firstHeight: Int = verticalOrientationHelper.getDecoratedMeasurement(centerView)
        val firstLeft: Int = paddingLeft
        val resolvedCenterOffsetY: Int = if (
            isRestoringSavedState &&
            !isAnchorVisibleAtOffset(centerOffsetY, firstHeight)
        ) {
            0
        } else {
            centerOffsetY
        }
        val firstTop: Int = contentCenterY - firstHeight / 2 + resolvedCenterOffsetY
        val firstRight: Int = firstLeft + firstWidth
        val firstBottom: Int = firstTop + firstHeight
        layoutDecoratedWithMargins(centerView, firstLeft, firstTop, firstRight, firstBottom)

        val startCovered: Boolean = layoutChild(
            verticalOrientationHelper.getDecoratedStart(centerView),
            centerPosition - 1,
            LAYOUT_START,
            recycler,
            allowLooping,
            laidOutPositions,
        )
        val endCovered: Boolean = layoutChild(
            verticalOrientationHelper.getDecoratedEnd(centerView),
            centerPosition + 1,
            LAYOUT_END,
            recycler,
            allowLooping,
            laidOutPositions,
        )
        return startCovered && endCovered
    }

    private fun isAnchorVisibleAtOffset(centerOffsetY: Int, decoratedMeasurement: Int): Boolean {
        val anchorStart: Long = contentCenterY.toLong() -
            decoratedMeasurement.toLong() / 2L +
            centerOffsetY.toLong()
        val anchorEnd: Long = anchorStart + decoratedMeasurement.toLong()
        val contentStart: Long = verticalOrientationHelper.startAfterPadding.toLong()
        val contentEnd: Long = verticalOrientationHelper.endAfterPadding.toLong()
        return if (decoratedMeasurement == 0) {
            anchorStart in contentStart..contentEnd
        } else {
            anchorStart < contentEnd && anchorEnd > contentStart
        }
    }

    override fun isAutoMeasureEnabled(): Boolean = true

    override fun canScrollVertically(): Boolean = true

    override fun scrollVerticallyBy(
        dy: Int,
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State,
    ): Int {
        if (dy == 0 || childCount == 0 || isLoopingFallbackLayoutPending) {
            return 0
        }
        val maximumLoopingStep: Int = maxOf(1, verticalOrientationHelper.totalSpace / 2)
        val cycleTracker: LoopCycleTracker? = if (
            isLoopingActive &&
            !state.hasTargetScrollPosition() &&
            abs(dy.toLong()) > maximumLoopingStep.toLong()
        ) {
            getChildAt(childCount / 2)?.let { referenceView: View ->
                val referencePosition: Int = getPosition(referenceView)
                if (isValidAdapterPosition(referencePosition)) {
                    LoopCycleTracker(
                        referencePosition = referencePosition,
                        referenceWorldCenter = decoratedCenterY(referenceView).toLong(),
                    )
                } else {
                    null
                }
            }
        } else {
            null
        }
        var remaining: Long = dy.toLong()
        var totalConsumed: Long = 0L
        while (remaining != 0L) {
            val requestedStep: Int = if (isLoopingActive) {
                remaining.coerceIn(
                    -maximumLoopingStep.toLong(),
                    maximumLoopingStep.toLong(),
                ).toInt()
            } else {
                // Int.MIN_VALUE 无法安全取反传给 offsetChildren, 至多保留 1px 未消费.
                remaining.coerceAtLeast(-Int.MAX_VALUE.toLong()).toInt()
            }
            val consumedStep: Int = fill(
                dy = requestedStep,
                recycler = recycler,
                cycleTracker = cycleTracker,
                totalConsumedBeforeStep = totalConsumed,
            )
            if (consumedStep == 0) {
                break
            }
            verticalOrientationHelper.offsetChildren(-consumedStep)
            totalConsumed += consumedStep.toLong()
            remaining -= consumedStep.toLong()
            if (consumedStep != requestedStep || !isLoopingActive) {
                break
            }
            val cycleSpan: Long = cycleTracker?.cycleSpan ?: 0L
            if (cycleSpan > 0L) {
                val completeCycleCount: Long = abs(remaining) / cycleSpan
                if (completeCycleCount > 0L) {
                    val absoluteSkippedDistance: Long = completeCycleCount * cycleSpan
                    val skippedDistance: Long = if (remaining > 0L) {
                        absoluteSkippedDistance
                    } else {
                        -absoluteSkippedDistance
                    }
                    totalConsumed += skippedDistance
                    remaining -= skippedDistance
                }
            }
        }
        checkIfCenterYPositionChanged(findCenterChildPosition())
        return totalConsumed.toInt()
    }

    override fun scrollToPosition(position: Int) {
        pendingScrollPosition = position
        pendingSavedState = null
        requestLayout()
    }

    override fun onSaveInstanceState(): Parcelable {
        val canSavePendingPosition: Boolean = pendingScrollPosition != RecyclerView.NO_POSITION &&
            (itemCount <= 0 || isValidAdapterPosition(pendingScrollPosition))
        if (canSavePendingPosition) {
            return SavedState(pendingScrollPosition, 0)
        }
        val savedState: SavedState? = pendingSavedState?.takeIf { state: SavedState ->
            itemCount <= 0 || isValidAdapterPosition(state.anchorPosition)
        }
        if (savedState != null) {
            return SavedState(savedState)
        }
        val anchorCenterY: Int = lastLayoutPassContentCenterY ?: contentCenterY
        val centerView: View = findCenterChild(anchorCenterY, centerYPosition)
            ?: return SavedState(RecyclerView.NO_POSITION, 0)
        return SavedState(
            anchorPosition = getPosition(centerView),
            anchorOffsetY = decoratedCenterY(centerView) - anchorCenterY,
        )
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is SavedState) {
            return
        }
        pendingSavedState = if (pendingScrollPosition == RecyclerView.NO_POSITION) {
            SavedState(state)
        } else {
            null
        }
        requestLayout()
    }

    override fun onLayoutCompleted(state: RecyclerView.State) {
        super.onLayoutCompleted(state)
        if (state.isPreLayout || state.itemCount <= 0) {
            return
        }
        pendingScrollPosition = RecyclerView.NO_POSITION
        pendingSavedState = null
    }

    /**
     * 当前布局在 pre-layout 阶段不会布局旧结构, 也不维护 appearing 和 disappearing child 的预测位置,
     * 因此不能声明支持 predictive item animations. 返回 false 仅关闭预测布局, 不影响 ItemAnimator
     * 根据普通布局前后快照执行增删、移动和内容变化动画.
     */
    override fun supportsPredictiveItemAnimations(): Boolean = false

    override fun computeVerticalScrollExtent(state: RecyclerView.State): Int =
        computeVerticalScrollMetric(state, VerticalScrollMetric.EXTENT)

    override fun computeVerticalScrollRange(state: RecyclerView.State): Int =
        computeVerticalScrollMetric(state, VerticalScrollMetric.RANGE)

    override fun computeVerticalScrollOffset(state: RecyclerView.State): Int =
        computeVerticalScrollMetric(state, VerticalScrollMetric.OFFSET)

    /**
     * 将目标 Adapter position 平滑滚动到内容中心.
     *
     * 空 Adapter 不启动滚动, 超过当前 Adapter 上界的目标收敛到最后一个真实 position.
     *
     * @throws IllegalArgumentException position 小于 0.
     */
    override fun smoothScrollToPosition(
        recyclerView: RecyclerView,
        state: RecyclerView.State,
        position: Int,
    ) {
        require(position >= 0) { "position must be non-negative" }
        val adapterItemCount: Int = recyclerView.adapter?.itemCount ?: return
        if (adapterItemCount <= 0) {
            return
        }
        val targetAdapterPosition: Int = minOf(position, adapterItemCount - 1)
        startSmoothScroll(
            WheelSmoothScroller(recyclerView.context).apply {
                targetPosition = targetAdapterPosition
            },
        )
    }

    override fun computeScrollVectorForPosition(targetPosition: Int): PointF? {
        if (childCount == 0 || !isValidAdapterPosition(targetPosition)) {
            return null
        }
        val centerView: View = findCenterChild() ?: return null
        val centerChildPosition: Int = getPosition(centerView)
        if (!isValidAdapterPosition(centerChildPosition) || centerChildPosition == targetPosition) {
            return null
        }
        val direction: Int = when {
            !isLoopingActive -> if (targetPosition > centerChildPosition) 1 else -1
            else -> {
                val itemCountLong: Long = itemCount.toLong()
                val up: Int = (
                    (centerChildPosition.toLong() - targetPosition.toLong() + itemCountLong) %
                        itemCountLong
                    ).toInt()
                val down: Int = (
                    (targetPosition.toLong() - centerChildPosition.toLong() + itemCountLong) %
                        itemCountLong
                    ).toInt()
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
     * 返回中心最接近 RecyclerView 内容中心的条目, 没有已布局条目时返回 null.
     *
     * 多个条目同距时优先保留当前中心 position, 否则返回布局顺序中的第一个条目.
     */
    fun findCenterChild(): View? = findCenterChild(contentCenterY, centerYPosition)

    private fun findCenterChild(centerY: Int, preferredPosition: Int): View? {
        val attachedChildCount: Int = childCount
        if (attachedChildCount == 0) {
            return null
        }

        val middleIndex: Int = attachedChildCount / 2
        var closestView: View? = null
        var closestDistance: Int = Int.MAX_VALUE
        var closestIndex: Int = Int.MAX_VALUE
        var distanceFromMiddle: Int = 0
        var inspectAfterMiddle: Boolean = false
        while (
            distanceFromMiddle == 0 ||
            middleIndex - distanceFromMiddle >= 0 ||
            middleIndex + distanceFromMiddle < attachedChildCount
        ) {
            val index: Int = if (inspectAfterMiddle) {
                middleIndex + distanceFromMiddle
            } else {
                middleIndex - distanceFromMiddle
            }
            when {
                distanceFromMiddle == 0 -> distanceFromMiddle = 1
                inspectAfterMiddle -> {
                    distanceFromMiddle += 1
                    inspectAfterMiddle = false
                }
                else -> inspectAfterMiddle = true
            }
            if (index !in 0 until attachedChildCount) {
                continue
            }
            val child: View = getChildAt(index) ?: continue
            val distance: Int = abs(decoratedCenterY(child) - centerY)
            val candidateIsPreferred: Boolean = preferredPosition != RecyclerView.NO_POSITION &&
                getPosition(child) == preferredPosition
            val currentClosestView: View? = closestView
            val closestIsPreferred: Boolean = preferredPosition != RecyclerView.NO_POSITION &&
                currentClosestView != null &&
                getPosition(currentClosestView) == preferredPosition
            val shouldReplaceClosest: Boolean = distance < closestDistance ||
                distance == closestDistance && when {
                    candidateIsPreferred != closestIsPreferred -> candidateIsPreferred
                    else -> index < closestIndex
                }
            if (shouldReplaceClosest) {
                closestView = child
                closestDistance = distance
                closestIndex = index
            }
            if (distance == 0 && candidateIsPreferred) {
                return closestView
            }
        }
        return closestView
    }

    /**
     * 返回中心最接近 RecyclerView 内容中心的 Adapter position.
     */
    fun findCenterChildPosition(): Int =
        findCenterChild()?.let(::getPosition) ?: RecyclerView.NO_POSITION

    /**
     * 设置实时中心条目 position 变化监听器, null 表示移除.
     *
     * 滚动、fling、布局或吸附期间, 最近中心 position 变化后立即通知, 不等待 RecyclerView idle 或吸附完成;
     * position 不变时不重复通知, 没有中心条目时通知 [RecyclerView.NO_POSITION].
     */
    fun setOnCenterYPositionChangeListener(listener: OnCenterYPositionChangeListener?) {
        onCenterYPositionChangeListener = listener
    }

    /**
     * 返回条目中心相对 RecyclerView 内容中心的有符号距离.
     */
    internal fun distanceToCenter(view: View): Int = decoratedCenterY(view) - contentCenterY

    /**
     * 确认已经到达内容中心的平滑滚动目标, 重复确认不会再次通知监听器.
     */
    internal fun confirmCenteredTarget(view: View) {
        val position: Int = getPosition(view)
        if (position == RecyclerView.NO_POSITION || distanceToCenter(view) != 0) {
            return
        }
        checkIfCenterYPositionChanged(position)
    }

    private fun decoratedCenterY(view: View): Int =
        verticalOrientationHelper.getDecoratedStart(view) +
            verticalOrientationHelper.getDecoratedMeasurement(view) / 2

    private fun computeVerticalScrollMetric(
        state: RecyclerView.State,
        metric: VerticalScrollMetric,
    ): Int {
        val viewportExtent: Int = verticalOrientationHelper.totalSpace.coerceAtLeast(0)
        if (childCount == 0 || state.itemCount <= 0 || viewportExtent == 0) {
            return 0
        }
        if (isLoopingActive || isLoopingFallbackLayoutPending) {
            return computeLoopingVerticalScrollMetric(viewportExtent, metric)
        }
        var startView: View? = null
        var startPosition: Int = Int.MAX_VALUE
        var endView: View? = null
        var endPosition: Int = RecyclerView.NO_POSITION
        for (index: Int in 0 until childCount) {
            val child: View = getChildAt(index) ?: continue
            val position: Int = getPosition(child)
            if (position !in 0 until state.itemCount) {
                continue
            }
            if (position < startPosition) {
                startView = child
                startPosition = position
            }
            if (position > endPosition) {
                endView = child
                endPosition = position
            }
        }
        val validStartView: View = startView ?: return metric.resolve(
            offset = 0,
            extent = viewportExtent,
            range = viewportExtent,
        )
        val validEndView: View = endView ?: return metric.resolve(
            offset = 0,
            extent = viewportExtent,
            range = viewportExtent,
        )
        val startCenterY: Int = decoratedCenterY(validStartView)
        val endCenterY: Int = decoratedCenterY(validEndView)
        val canScrollBackward: Boolean = startPosition > 0 ||
            startCenterY < contentCenterY
        val canScrollForward: Boolean = endPosition < state.itemCount - 1 ||
            endCenterY > contentCenterY
        if (!canScrollBackward && !canScrollForward) {
            return metric.resolve(
                offset = 0,
                extent = viewportExtent,
                range = viewportExtent,
            )
        }

        val averageCenterDistance: Double = estimateAverageCenterDistance(
            startView = validStartView,
            startPosition = startPosition,
            endPosition = endPosition,
            startCenterY = startCenterY,
            endCenterY = endCenterY,
        )
        val extent: Int = minOf(
            viewportExtent,
            Int.MAX_VALUE - MIN_BIDIRECTIONAL_METRIC_SPAN,
        )
        val maximumTerminalOffset: Int = Int.MAX_VALUE - extent
        val estimatedMaximumOffset: Long = (
            averageCenterDistance * (state.itemCount - 1).toDouble()
            ).toLongInRange(0L, maximumTerminalOffset.toLong() - 1L)
        val minimumTerminalOffset: Int = if (canScrollBackward && canScrollForward) {
            MIN_BIDIRECTIONAL_METRIC_SPAN
        } else {
            MIN_SCROLLABLE_METRIC_SPAN
        }
        val terminalOffset: Int = maxOf(
            estimatedMaximumOffset + 1L,
            minimumTerminalOffset.toLong(),
        ).coerceAtMost(maximumTerminalOffset.toLong()).toInt()
        val estimatedOffset: Long = (
            startPosition.toDouble() * averageCenterDistance +
                contentCenterY.toDouble() - startCenterY.toDouble()
            ).toLongInRange(0L, terminalOffset.toLong())
        val offset: Int = when {
            !canScrollBackward -> 0
            !canScrollForward -> terminalOffset
            else -> estimatedOffset.coerceIn(
                1L,
                terminalOffset.toLong() - 2L,
            ).toInt()
        }
        return metric.resolve(
            offset = offset,
            extent = extent,
            range = extent + terminalOffset,
        )
    }

    private fun computeLoopingVerticalScrollMetric(
        viewportExtent: Int,
        metric: VerticalScrollMetric,
    ): Int {
        val extent: Int = minOf(
            viewportExtent,
            Int.MAX_VALUE - MIN_BIDIRECTIONAL_METRIC_SPAN,
        )
        val maximumMetricSpan: Long = Int.MAX_VALUE.toLong() - extent.toLong()
        val metricSpan: Int = maxOf(
            MIN_BIDIRECTIONAL_METRIC_SPAN.toLong(),
            extent.toLong() * LOOPING_METRIC_EXTENT_MULTIPLIER,
        ).coerceAtMost(maximumMetricSpan).toInt()
        return metric.resolve(
            offset = metricSpan / 2,
            extent = extent,
            range = extent + metricSpan,
        )
    }

    private fun estimateAverageCenterDistance(
        startView: View,
        startPosition: Int,
        endPosition: Int,
        startCenterY: Int,
        endCenterY: Int,
    ): Double {
        val positionSpan: Int = endPosition - startPosition
        if (positionSpan <= 0) {
            return verticalOrientationHelper.getDecoratedMeasurement(
                startView,
            ).coerceAtLeast(0).toDouble()
        }
        val laidOutCenterDistance: Long = abs(
            endCenterY.toLong() - startCenterY.toLong(),
        )
        return laidOutCenterDistance.toDouble() / positionSpan.toDouble()
    }

    private fun fill(
        dy: Int,
        recycler: RecyclerView.Recycler,
        cycleTracker: LoopCycleTracker?,
        totalConsumedBeforeStep: Long,
    ): Int = when {
        dy > 0 -> fillEnd(dy, recycler, cycleTracker, totalConsumedBeforeStep)
        dy < 0 -> fillStart(dy, recycler, cycleTracker, totalConsumedBeforeStep)
        else -> 0
    }

    private fun fillEnd(
        dy: Int,
        recycler: RecyclerView.Recycler,
        cycleTracker: LoopCycleTracker?,
        totalConsumedBeforeStep: Long,
    ): Int {
        if (isLoopingActive) {
            recycleStartBeforeScroll(dy, recycler)
        }
        val anchor: View = getChildAt(childCount - 1) ?: return 0
        val anchorPosition: Int = getPosition(anchor)
        var lastCenterY: Int = decoratedCenterY(anchor)
        var offset: Int = verticalOrientationHelper.getDecoratedEnd(anchor)
        var position: Int = anchorPosition + 1
        var attemptedPositionCount: Int = 0
        var loopingFillBlocked: Boolean = false
        val fillLimit: Long = verticalOrientationHelper.endAfterPadding.toLong() + dy.toLong()
        while (
            (isLoopingActive || position < itemCount) &&
            offset.toLong() < fillLimit
        ) {
            if (isLoopingActive && attemptedPositionCount >= itemCount) {
                loopingFillBlocked = true
                break
            }
            val adapterPosition: Int = resolveAdapterPosition(position, isLoopingActive) ?: break
            attemptedPositionCount += 1
            if (isLoopingActive && isAdapterPositionAttached(adapterPosition)) {
                loopingFillBlocked = true
                break
            }
            val view: View = recycler.getViewForPosition(adapterPosition)
            addView(view)
            measureChildWithMargins(view, 0, 0)
            val width: Int = verticalOrientationHelper.getDecoratedMeasurementInOther(view)
            val viewHeight: Int = verticalOrientationHelper.getDecoratedMeasurement(view)
            val left: Int = paddingLeft
            val top: Int = offset
            val right: Int = left + width
            val bottom: Int = top + viewHeight
            layoutDecoratedWithMargins(view, left, top, right, bottom)
            offset = verticalOrientationHelper.getDecoratedEnd(view)
            lastCenterY = decoratedCenterY(view)
            cycleTracker?.recordLaidOutChild(
                position = adapterPosition,
                centerY = lastCenterY,
                totalConsumedBeforeStep = totalConsumedBeforeStep,
            )
            recycleStart(offset, anchor, dy, recycler)
            position += 1
        }
        if (loopingFillBlocked) {
            deactivateLooping()
        }
        return if (isLoopingActive) {
            dy
        } else {
            minOf(
                dy.toLong(),
                lastCenterY.toLong() - contentCenterY.toLong(),
            ).coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
        }
    }

    private fun fillStart(
        dy: Int,
        recycler: RecyclerView.Recycler,
        cycleTracker: LoopCycleTracker?,
        totalConsumedBeforeStep: Long,
    ): Int {
        if (isLoopingActive) {
            recycleEndBeforeScroll(dy, recycler)
        }
        val anchor: View = getChildAt(0) ?: return 0
        val anchorPosition: Int = getPosition(anchor)
        var firstCenterY: Int = decoratedCenterY(anchor)
        var offset: Int = verticalOrientationHelper.getDecoratedStart(anchor)
        var position: Int = anchorPosition - 1
        var attemptedPositionCount: Int = 0
        var loopingFillBlocked: Boolean = false
        val fillLimit: Long = verticalOrientationHelper.startAfterPadding.toLong() + dy.toLong()
        while (
            (isLoopingActive || position >= 0) &&
            offset.toLong() > fillLimit
        ) {
            if (isLoopingActive && attemptedPositionCount >= itemCount) {
                loopingFillBlocked = true
                break
            }
            val adapterPosition: Int = resolveAdapterPosition(position, isLoopingActive) ?: break
            attemptedPositionCount += 1
            if (isLoopingActive && isAdapterPositionAttached(adapterPosition)) {
                loopingFillBlocked = true
                break
            }
            val view: View = recycler.getViewForPosition(adapterPosition)
            addView(view, 0)
            measureChildWithMargins(view, 0, 0)
            val width: Int = verticalOrientationHelper.getDecoratedMeasurementInOther(view)
            val viewHeight: Int = verticalOrientationHelper.getDecoratedMeasurement(view)
            val left: Int = paddingLeft
            val top: Int = offset - viewHeight
            val right: Int = left + width
            val bottom: Int = top + viewHeight
            layoutDecoratedWithMargins(view, left, top, right, bottom)
            offset = verticalOrientationHelper.getDecoratedStart(view)
            firstCenterY = decoratedCenterY(view)
            cycleTracker?.recordLaidOutChild(
                position = adapterPosition,
                centerY = firstCenterY,
                totalConsumedBeforeStep = totalConsumedBeforeStep,
            )
            recycleEnd(offset, anchor, dy, recycler)
            position -= 1
        }
        if (loopingFillBlocked) {
            deactivateLooping()
        }
        return if (isLoopingActive) {
            dy
        } else {
            maxOf(
                dy.toLong(),
                firstCenterY.toLong() - contentCenterY.toLong(),
            ).coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
        }
    }

    private fun deactivateLooping() {
        if (!isLoopingActive) {
            return
        }
        isLoopingActive = false
        isLoopingFallbackLayoutPending = true
        loopingFallbackLayoutHandler.post(loopingFallbackLayoutAction)
    }

    private fun recycleStartBeforeScroll(dy: Int, recycler: RecyclerView.Recycler) {
        while (childCount > 1) {
            val first: View = getChildAt(0) ?: return
            if (
                verticalOrientationHelper.getDecoratedEnd(first).toLong() - dy.toLong() <=
                verticalOrientationHelper.startAfterPadding.toLong()
            ) {
                removeAndRecycleView(first, recycler)
            } else {
                return
            }
        }
    }

    private fun recycleEndBeforeScroll(dy: Int, recycler: RecyclerView.Recycler) {
        while (childCount > 1) {
            val last: View = getChildAt(childCount - 1) ?: return
            if (
                verticalOrientationHelper.getDecoratedStart(last).toLong() - dy.toLong() >=
                verticalOrientationHelper.endAfterPadding.toLong()
            ) {
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
        val anchorEnd: Long = verticalOrientationHelper.getDecoratedEnd(anchor).toLong()
        val recycleDistance: Long = minOf(offset.toLong() - anchorEnd, dy.toLong())
        val recycleLimit: Long = verticalOrientationHelper.startAfterPadding.toLong() + recycleDistance
        while (childCount > 0) {
            val first: View = getChildAt(0) ?: break
            if (
                verticalOrientationHelper.getDecoratedEnd(first).toLong() < recycleLimit
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
        val anchorStart: Long = verticalOrientationHelper.getDecoratedStart(anchor).toLong()
        val recycleDistance: Long = maxOf(offset.toLong() - anchorStart, dy.toLong())
        val recycleLimit: Long = verticalOrientationHelper.endAfterPadding.toLong() + recycleDistance
        while (childCount > 0) {
            val last: View = getChildAt(childCount - 1) ?: break
            if (
                verticalOrientationHelper.getDecoratedStart(last).toLong() > recycleLimit
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
        if (isValidAdapterPosition(position)) {
            return position
        }
        return if (allowLooping) normalizeAdapterPosition(position) else null
    }

    private fun isValidAdapterPosition(position: Int): Boolean = position in 0 until itemCount

    private fun layoutChildToStart(
        baseline: Int,
        position: Int,
        recycler: RecyclerView.Recycler,
        allowLooping: Boolean,
        laidOutPositions: MutableSet<Int>,
    ): Boolean {
        var offset: Int = baseline
        var targetPosition: Int = position
        while (offset >= verticalOrientationHelper.startAfterPadding) {
            val adapterPosition: Int = resolveAdapterPosition(targetPosition, allowLooping) ?: return false
            if (!laidOutPositions.add(adapterPosition)) {
                return false
            }
            val view: View = recycler.getViewForPosition(adapterPosition)
            addView(view, 0)
            measureChildWithMargins(view, 0, 0)
            val width: Int = verticalOrientationHelper.getDecoratedMeasurementInOther(view)
            val viewHeight: Int = verticalOrientationHelper.getDecoratedMeasurement(view)
            val left: Int = paddingLeft
            val top: Int = offset - viewHeight
            val right: Int = left + width
            val bottom: Int = top + viewHeight
            layoutDecoratedWithMargins(view, left, top, right, bottom)
            offset = verticalOrientationHelper.getDecoratedStart(view)
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
        while (offset < verticalOrientationHelper.endAfterPadding) {
            val adapterPosition: Int = resolveAdapterPosition(targetPosition, allowLooping) ?: return false
            if (!laidOutPositions.add(adapterPosition)) {
                return false
            }
            val view: View = recycler.getViewForPosition(adapterPosition)
            addView(view)
            measureChildWithMargins(view, 0, 0)
            val width: Int = verticalOrientationHelper.getDecoratedMeasurementInOther(view)
            val viewHeight: Int = verticalOrientationHelper.getDecoratedMeasurement(view)
            val left: Int = paddingLeft
            val top: Int = offset
            val right: Int = left + width
            val bottom: Int = top + viewHeight
            layoutDecoratedWithMargins(view, left, top, right, bottom)
            offset = verticalOrientationHelper.getDecoratedEnd(view)
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

    private fun checkIfCenterYPositionChanged(position: Int) {
        if (centerYPosition == position) {
            return
        }
        centerYPosition = position
        onCenterYPositionChangeListener?.onCenterYPositionChanged(position)
    }

    /**
     * 最近中心位置的 Adapter position 实时发生变化后的通知.
     *
     * 通知不等待 RecyclerView idle 或吸附完成; 没有中心条目时 position 为 [RecyclerView.NO_POSITION].
     */
    fun interface OnCenterYPositionChangeListener {
        fun onCenterYPositionChanged(position: Int)
    }

    /**
     * 保存滚轮中心锚点, offset 使用相对 RecyclerView 内容中心的 decorated-with-margins 坐标.
     *
     * internal 保证 JVM 类为 public, 供 Android Parcel 在冷启动恢复时反射读取 [CREATOR].
     */
    internal class SavedState : Parcelable {
        val anchorPosition: Int
        val anchorOffsetY: Int

        constructor(anchorPosition: Int, anchorOffsetY: Int) {
            this.anchorPosition = anchorPosition
            this.anchorOffsetY = anchorOffsetY
        }

        constructor(other: SavedState) : this(other.anchorPosition, other.anchorOffsetY)

        private constructor(parcel: Parcel) : this(
            anchorPosition = parcel.readInt(),
            anchorOffsetY = parcel.readInt(),
        )

        override fun writeToParcel(destination: Parcel, flags: Int) {
            destination.writeInt(anchorPosition)
            destination.writeInt(anchorOffsetY)
        }

        override fun describeContents(): Int = 0

        companion object {
            @JvmField
            val CREATOR: Parcelable.Creator<SavedState> = object : Parcelable.Creator<SavedState> {
                override fun createFromParcel(source: Parcel): SavedState = SavedState(source)
                override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
            }
        }
    }
}

private const val LAYOUT_START: Int = -1
private const val LAYOUT_END: Int = 1
private const val MIN_SCROLLABLE_METRIC_SPAN: Int = 2
private const val MIN_BIDIRECTIONAL_METRIC_SPAN: Int = 3
private const val LOOPING_METRIC_EXTENT_MULTIPLIER: Long = 2L

/**
 * 在单次实际循环滚动中识别同一 Adapter position 相邻两次出现之间的像素跨度.
 */
private class LoopCycleTracker(
    private val referencePosition: Int,
    private val referenceWorldCenter: Long,
) {
    var cycleSpan: Long = 0L
        private set

    fun recordLaidOutChild(position: Int, centerY: Int, totalConsumedBeforeStep: Long) {
        if (cycleSpan > 0L || position != referencePosition) {
            return
        }
        val currentWorldCenter: Long = centerY.toLong() + totalConsumedBeforeStep
        cycleSpan = abs(currentWorldCenter - referenceWorldCenter)
    }
}

/**
 * RecyclerView 请求的纵向滚动指标类型.
 */
private enum class VerticalScrollMetric {
    OFFSET,
    EXTENT,
    RANGE,
}

/**
 * 从一次同源计算中返回指定的 RecyclerView 纵向滚动指标.
 */
private fun VerticalScrollMetric.resolve(offset: Int, extent: Int, range: Int): Int = when (this) {
    VerticalScrollMetric.OFFSET -> offset
    VerticalScrollMetric.EXTENT -> extent
    VerticalScrollMetric.RANGE -> range
}

/**
 * 将浮点估算结果四舍五入并限制在可表示的指标范围内.
 */
private fun Double.toLongInRange(minimum: Long, maximum: Long): Long = when {
    isNaN() || this <= minimum.toDouble() -> minimum
    this >= maximum.toDouble() -> maximum
    else -> kotlin.math.round(this).toLong()
}
