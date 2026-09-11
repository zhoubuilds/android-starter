package com.whisper.kit.widget

/**
 * 范围选择控件归一化后的有序整数端点.
 *
 * @author whisper
 * @since 2026/09/09
 */
internal data class KitRangeSelection(val start: Int, val end: Int)

/**
 * 在全部合法刻度范围中最小化两端相对输入值的平方位移之和, 等距时依次选择较小的起点和终点.
 *
 * 输入端点不预先排序或吸附, 避免丢失寻找最近范围所需的距离信息. 约束无可行解时拒绝配置.
 */
internal fun nearestKitRangeSelection(
    start: Int,
    end: Int,
    total: Int,
    step: Int,
    startMax: Int,
    endMin: Int,
    minSpan: Int,
    startMin: Int = -1,
): KitRangeSelection {
    require(total > 0) { "total must be positive" }
    require(step > 0) { "step must be positive" }
    require(startMax >= -1) { "startMax must be nonnegative or -1" }
    require(endMin >= -1) { "endMin must be nonnegative or -1" }
    require(minSpan >= 0) { "minSpan must be nonnegative" }
    require(startMin >= -1) { "startMin must be nonnegative or -1" }

    val stride = step.toLong()
    val lastTick = total.toLong() / stride
    val minimumStartTick = if (startMin < 0) 0L else (startMin.toLong() + stride - 1L) / stride
    val minimumEndTick = if (endMin < 0) 0L else (endMin.toLong() + stride - 1L) / stride
    val minimumSpanTicks = (minSpan.toLong() + stride - 1L) / stride
    require(minimumEndTick <= lastTick && minimumSpanTicks <= lastTick) {
        "Range constraints have no valid step-aligned selection"
    }
    val maximumStartTick = minOf(
        if (startMax < 0) lastTick else startMax.toLong() / stride,
        lastTick - minimumSpanTicks,
    )
    require(minimumStartTick <= maximumStartTick) {
        "Range constraints have no valid step-aligned selection"
    }
    val nonnegativeEnd = end.toLong().coerceAtLeast(0L)
    val roundedEndTick = nonnegativeEnd / stride +
        if (nonnegativeEnd % stride * 2L > stride) 1L else 0L
    val preferredEndTick = roundedEndTick.coerceIn(minimumEndTick, lastTick)

    fun endTickFor(startTick: Long): Long = maxOf(preferredEndTick, startTick + minimumSpanTicks)

    // 固定起点后, 最近终点唯一确定. 凸目标的一阶差分除以正步长后只需线性整数运算,
    // 避免平方位移或步长乘积溢出, 同时无需遍历整个 Int 数值域.
    var low = minimumStartTick
    var high = maximumStartTick
    while (low < high) {
        val middle = low + (high - low) / 2L
        val currentEndTick = endTickFor(middle)
        val nextEndTick = endTickFor(middle + 1L)
        val startDifference = 2L * (middle * stride - start.toLong()) + stride
        val endDifference = if (nextEndTick == currentEndTick) {
            0L
        } else {
            2L * (currentEndTick * stride - end.toLong()) + stride
        }
        if (startDifference + endDifference >= 0L) {
            high = middle
        } else {
            low = middle + 1L
        }
    }
    return KitRangeSelection((low * stride).toInt(), (endTickFor(low) * stride).toInt())
}
