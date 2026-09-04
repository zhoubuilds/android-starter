package com.whisper.kit.recyclerview.decoration

/**
 * 判断 Staggered item 是否位于 Adapter 逻辑起始边界.
 *
 * 起始处没有 full-span item 时, 前 spanCount 个 item 分别开启各个 span. 若起始前缀中存在
 * full-span item, 它会封闭此前尚未开启的 span, 因而只有它之前的普通 item 接触起始边界.
 */
internal fun isAtStaggeredMainAxisStart(
    position: Int,
    itemCount: Int,
    spanCount: Int,
    isFullSpan: Boolean,
    fullSpanProvider: StaggeredFullSpanProvider,
): Boolean {
    if (position !in 0 until itemCount || spanCount <= 0) return false
    if (position == 0) return true

    val prefixEndExclusive: Int = minOf(itemCount, spanCount)
    if (position >= prefixEndExclusive || isFullSpan) return false

    for (prefixPosition: Int in 0 until position) {
        if (fullSpanProvider.isFullSpan(prefixPosition)) return false
    }
    return true
}
