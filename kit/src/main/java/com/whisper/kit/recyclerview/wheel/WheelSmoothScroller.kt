package com.whisper.kit.recyclerview.wheel

import android.content.Context
import android.graphics.PointF
import android.util.DisplayMetrics
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs
import kotlin.math.ceil

/**
 * [WheelLayoutManager] 使用的纵向中心平滑滚动实现.
 */
internal class WheelSmoothScroller(context: Context) : RecyclerView.SmoothScroller() {

    private val displayMetrics: DisplayMetrics = context.resources.displayMetrics
    private val linearInterpolator: LinearInterpolator = LinearInterpolator()
    private val decelerateInterpolator: DecelerateInterpolator = DecelerateInterpolator()
    private var hasCalculatedMillisPerPixel: Boolean = false
    private var millisPerPixel: Float = 0f
    private var interimTargetDy: Int = 0

    override fun onStart() = Unit

    override fun onStop() = Unit

    override fun onSeekTargetStep(
        dx: Int,
        dy: Int,
        state: RecyclerView.State,
        action: Action,
    ) {
        if (childCount == 0) {
            stop()
            return
        }
        interimTargetDy = clampApplyScroll(interimTargetDy, dy)
        if (interimTargetDy == 0) {
            updateActionForInterimTarget(action)
        }
    }

    override fun onTargetFound(
        targetView: View,
        state: RecyclerView.State,
        action: Action,
    ) {
        val targetLayoutManager: RecyclerView.LayoutManager = layoutManager ?: return
        if (!targetLayoutManager.canScrollVertically()) {
            return
        }
        val targetViewY: Int = targetLayoutManager.getDecoratedTop(targetView) +
            targetLayoutManager.getDecoratedMeasuredHeight(targetView) / 2
        val parentY: Int = targetLayoutManager.paddingTop + targetLayoutManager.height / 2
        val dy: Int = parentY - targetViewY
        val time: Int = calculateTimeForDeceleration(dy)
        if (time > 0) {
            action.update(0, -dy, time, decelerateInterpolator)
        }
    }

    private fun clampApplyScroll(temporaryDistance: Int, distance: Int): Int {
        val before: Int = temporaryDistance
        val after: Int = temporaryDistance - distance
        return if (before * after <= 0) 0 else after
    }

    private fun updateActionForInterimTarget(action: Action) {
        val scrollVector: PointF = computeScrollVectorForPosition(targetPosition) ?: run {
            action.jumpTo(targetPosition)
            stop()
            return
        }
        if (scrollVector.x == 0f && scrollVector.y == 0f) {
            action.jumpTo(targetPosition)
            stop()
            return
        }
        normalize(scrollVector)
        interimTargetDy = (TARGET_SEEK_SCROLL_DISTANCE_PX * scrollVector.y).toInt()
        val time: Int = calculateTimeForScrolling(TARGET_SEEK_SCROLL_DISTANCE_PX)
        action.update(
            0,
            (interimTargetDy * TARGET_SEEK_EXTRA_SCROLL_RATIO).toInt(),
            (time * TARGET_SEEK_EXTRA_SCROLL_RATIO).toInt(),
            linearInterpolator,
        )
    }

    private fun calculateTimeForDeceleration(distance: Int): Int =
        ceil(calculateTimeForScrolling(distance) / DECELERATION_FACTOR).toInt()

    private fun calculateTimeForScrolling(distance: Int): Int =
        ceil(abs(distance) * speedPerPixel).toInt()

    private val speedPerPixel: Float
        get() {
            if (!hasCalculatedMillisPerPixel) {
                millisPerPixel = MILLISECONDS_PER_INCH / displayMetrics.densityDpi
                hasCalculatedMillisPerPixel = true
            }
            return millisPerPixel
        }

    companion object {
        private const val MILLISECONDS_PER_INCH: Float = 25f
        private const val TARGET_SEEK_SCROLL_DISTANCE_PX: Int = 10_000
        private const val TARGET_SEEK_EXTRA_SCROLL_RATIO: Float = 1.2f
        private const val DECELERATION_FACTOR: Double = 0.3356
    }
}
