package com.whisper.kit.recyclerview.decoration

import androidx.recyclerview.widget.GridLayoutManager

/**
 * 判断当前 item 是否位于第一个 span group.
 *
 * 当前 item 的 span index 直接使用 LayoutManager 已分配的结果. 只有可能属于首组时才读取前置 span size,
 * 且读取次数不会超过 spanCount.
 */
internal fun isInFirstSpanGroup(
    position: Int,
    spanCount: Int,
    spanIndex: Int,
    lookup: GridLayoutManager.SpanSizeLookup,
): Boolean {
    if (position < 0 || spanCount <= 0 || spanIndex !in 0 until spanCount) return false
    if (position == 0) return true
    if (position >= spanCount) return false

    var occupiedSpanCount: Int = 0
    for (previousPosition: Int in 0 until position) {
        val previousSpanSize: Int = lookup.getSpanSize(previousPosition)
        if (previousSpanSize <= 0 || occupiedSpanCount + previousSpanSize >= spanCount) {
            return false
        }
        occupiedSpanCount += previousSpanSize
    }
    return spanIndex == occupiedSpanCount
}

/**
 * 判断当前 item 是否位于最后一个 span group.
 *
 * 只有 Adapter 最后 spanCount 个 position 可能属于最后一组. 在该范围内从当前 item 已分配的 span
 * 向后探测当前组剩余位置. 合法的 SpanSizeLookup 每次至少占用一个 span, 因而读取次数不会超过 spanCount,
 * 不随 adapter position 或 itemCount 增长.
 */
internal fun isInLastSpanGroup(
    position: Int,
    itemCount: Int,
    spanCount: Int,
    spanIndex: Int,
    spanSize: Int,
    lookup: GridLayoutManager.SpanSizeLookup,
): Boolean {
    if (
        position !in 0 until itemCount ||
        spanCount <= 0 ||
        spanIndex !in 0 until spanCount ||
        spanSize <= 0 ||
        spanIndex + spanSize > spanCount
    ) {
        return false
    }
    if (position == itemCount - 1) return true
    if (position < itemCount - spanCount) return false

    var occupiedSpanCount: Int = spanIndex + spanSize
    var inspectedItemCount: Int = 0
    var nextPosition: Int = position + 1
    while (nextPosition < itemCount && inspectedItemCount < spanCount) {
        if (occupiedSpanCount == spanCount) return false

        val nextSpanSize: Int = lookup.getSpanSize(nextPosition)
        if (nextSpanSize <= 0 || occupiedSpanCount + nextSpanSize > spanCount) {
            return false
        }
        occupiedSpanCount += nextSpanSize
        nextPosition++
        inspectedItemCount++
    }
    return nextPosition == itemCount
}
