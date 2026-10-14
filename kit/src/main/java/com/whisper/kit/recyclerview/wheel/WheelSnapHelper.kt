package com.whisper.kit.recyclerview.wheel

import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView

/**
 * 将滚轮停止后的目标条目调整到 RecyclerView 中心.
 *
 * 循环模式不使用 [LinearSnapHelper] 基于 Adapter 末端向量计算的 fling 目标, 滚动空闲后仍会完成中心吸附.
 *
 * @author whisper
 * @since 2026/10/14
 */
class WheelSnapHelper : LinearSnapHelper() {

    override fun findTargetSnapPosition(
        layoutManager: RecyclerView.LayoutManager,
        velocityX: Int,
        velocityY: Int,
    ): Int = if (
        layoutManager is WheelLayoutManager &&
        layoutManager.isLoopingActive
    ) {
        RecyclerView.NO_POSITION
    } else {
        super.findTargetSnapPosition(layoutManager, velocityX, velocityY)
    }
}
