package com.whisper.kit.view.feed

/**
 * 通用内容瀑布流封面高度比例计算.
 *
 * 有有效宽高时使用真实高宽比; 高宽比超过 4/3 时截断; 宽高缺失或非正数时使用默认比例 170:147.
 *
 * @author 张梁
 * @since 2026/08/17
 */
object KitContentFeedCoverHeight {

    /**
     * 默认封面宽度基准.
     */
    const val DEFAULT_RATIO_WIDTH: Int = 170

    /**
     * 默认封面高度基准.
     */
    const val DEFAULT_RATIO_HEIGHT: Int = 147

    /**
     * 封面高度相对宽度的上限比例.
     */
    const val MAX_HEIGHT_TO_WIDTH_RATIO: Float = 4f / 3f

    /**
     * 解析封面高度相对宽度的比例.
     *
     * @param coverWidth 后端封面宽.
     * @param coverHeight 后端封面高.
     * @return 高度 / 宽度.
     */
    fun resolveHeightToWidthRatio(coverWidth: Int?, coverHeight: Int?): Float {
        if (coverWidth != null && coverHeight != null && coverWidth > 0 && coverHeight > 0) {
            val rawRatio: Float = coverHeight.toFloat() / coverWidth.toFloat()
            return if (rawRatio > MAX_HEIGHT_TO_WIDTH_RATIO) {
                MAX_HEIGHT_TO_WIDTH_RATIO
            } else {
                rawRatio
            }
        }
        return DEFAULT_RATIO_HEIGHT.toFloat() / DEFAULT_RATIO_WIDTH.toFloat()
    }

    /**
     * 按列宽计算封面像素高度.
     *
     * @param coverWidthPx 封面渲染宽度, 单位 px.
     * @param coverWidth 后端封面宽.
     * @param coverHeight 后端封面高.
     * @return 封面高度像素; 宽度无效时返回 0.
     */
    fun resolveCoverHeightPx(
        coverWidthPx: Int,
        coverWidth: Int?,
        coverHeight: Int?,
    ): Int {
        if (coverWidthPx <= 0) {
            return 0
        }
        val ratio: Float = resolveHeightToWidthRatio(coverWidth, coverHeight)
        return (coverWidthPx * ratio).toInt().coerceAtLeast(1)
    }

    /**
     * 按 RecyclerView 宽度与列数推算 Feed 封面渲染宽度.
     *
     * @param recyclerViewWidthPx RecyclerView 像素宽度.
     * @param spanCount 瀑布流列数.
     * @param itemHorizontalMarginPx 单项左右 margin 的单侧像素值.
     * @return 封面渲染宽度; 参数无效时返回 0.
     */
    fun resolveFeedColumnWidthPx(
        recyclerViewWidthPx: Int,
        spanCount: Int,
        itemHorizontalMarginPx: Int,
    ): Int {
        if (recyclerViewWidthPx <= 0 || spanCount <= 0) {
            return 0
        }
        val spanWidthPx: Int = recyclerViewWidthPx / spanCount
        return (spanWidthPx - itemHorizontalMarginPx * 2).coerceAtLeast(1)
    }
}
