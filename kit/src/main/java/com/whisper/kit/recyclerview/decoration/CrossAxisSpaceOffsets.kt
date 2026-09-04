package com.whisper.kit.recyclerview.decoration

/**
 * 计算指定逻辑 span 开始侧分到的交叉轴 offset.
 *
 * 结果等效于 `ceil(spanIndex * space / spanCount)`.
 */
internal fun crossAxisStartOffset(
    space: Int,
    spanCount: Int,
    spanIndex: Int,
): Int {
    if (spanIndex <= 0 || spanCount <= 1) return 0
    val numerator: Long = spanIndex.toLong() * space
    return ((numerator + spanCount - 1L) / spanCount).toInt()
}

/**
 * 计算指定逻辑 span 结束侧分到的交叉轴 offset.
 *
 * 结果等效于 `floor((spanCount - 1 - spanIndex) * space / spanCount)`.
 */
internal fun crossAxisEndOffset(
    space: Int,
    spanCount: Int,
    spanIndex: Int,
): Int {
    if (spanCount <= 1 || spanIndex >= spanCount - 1) return 0
    return (
        (spanCount - 1 - spanIndex).toLong() * space / spanCount
    ).toInt()
}
