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
 *
 * 搜索阶段沿 LayoutManager 提供的方向分段前进, 发现目标后使用同一中心距离减速对齐.
 */
internal class WheelSmoothScroller(context: Context) : RecyclerView.SmoothScroller() {

    companion object {
        private const val MILLISECONDS_PER_INCH: Float = 25f
        private const val TARGET_SEEK_SCROLL_DISTANCE_PX: Int = 10_000
        private const val TARGET_SEEK_EXTRA_SCROLL_RATIO: Float = 1.2f
        private const val DECELERATION_FACTOR: Double = 0.3356
    }

    private val displayMetrics: DisplayMetrics = context.resources.displayMetrics
    private val linearInterpolator: LinearInterpolator = LinearInterpolator()
    private val decelerateInterpolator: DecelerateInterpolator = DecelerateInterpolator()
    private var hasCalculatedMillisPerPixel: Boolean = false
    private var millisPerPixel: Float = 0f
    private var interimTargetDy: Int = 0

    /**
     * 按设备密度延迟计算并缓存每像素滚动时间.
     */
    private val speedPerPixel: Float
        get() {
            if (!hasCalculatedMillisPerPixel) {
                millisPerPixel = MILLISECONDS_PER_INCH / displayMetrics.densityDpi
                hasCalculatedMillisPerPixel = true
            }
            return millisPerPixel
        }

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
        val targetLayoutManager: WheelLayoutManager = layoutManager as? WheelLayoutManager ?: return
        val dy: Int = targetLayoutManager.distanceToCenter(targetView)
        if (dy == 0) {
            targetLayoutManager.confirmCenteredTarget(targetView)
            return
        }
        val time: Int = calculateTimeForDeceleration(dy)
        if (time > 0) {
            action.update(0, dy, time, decelerateInterpolator)
        }
    }

    /**
     * 从临时目标距离中扣除本步消费量, 穿过目标方向时收敛为 0.
     */
    private fun clampApplyScroll(remainingDistance: Int, consumedDistance: Int): Int {
        val before: Int = remainingDistance
        val after: Int = remainingDistance - consumedDistance
        return if (before * after <= 0) 0 else after
    }

    /**
     * 为尚未附着的目标生成下一段线性搜索动作.
     */
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
}
