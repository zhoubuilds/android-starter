package com.whisper.kit.view.feed

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 通用内容瀑布流封面高度比例测试.
 *
 * @author 张梁
 * @since 2026/08/17
 */
class KitContentFeedCoverHeightTest {

    /**
     * 有效宽高且未超上限时使用真实比例.
     */
    @Test
    fun resolveRatioUsesRawWhenBelowCap() {
        val ratio: Float = KitContentFeedCoverHeight.resolveHeightToWidthRatio(
            coverWidth = 300,
            coverHeight = 300,
        )
        assertEquals(1f, ratio, 0.0001f)
    }

    /**
     * 高宽比超过 4/3 时截断.
     */
    @Test
    fun resolveRatioCapsAtFourThirds() {
        val ratio: Float = KitContentFeedCoverHeight.resolveHeightToWidthRatio(
            coverWidth = 100,
            coverHeight = 200,
        )
        assertEquals(KitContentFeedCoverHeight.MAX_HEIGHT_TO_WIDTH_RATIO, ratio, 0.0001f)
    }

    /**
     * 宽高缺失时使用默认 170:147.
     */
    @Test
    fun resolveRatioFallsBackToDefault() {
        val ratio: Float = KitContentFeedCoverHeight.resolveHeightToWidthRatio(
            coverWidth = null,
            coverHeight = null,
        )
        val expected: Float =
            KitContentFeedCoverHeight.DEFAULT_RATIO_HEIGHT.toFloat() /
                KitContentFeedCoverHeight.DEFAULT_RATIO_WIDTH.toFloat()
        assertEquals(expected, ratio, 0.0001f)
    }

    /**
     * 非正数宽高时使用默认比例.
     */
    @Test
    fun resolveRatioFallsBackWhenNonPositive() {
        val ratio: Float = KitContentFeedCoverHeight.resolveHeightToWidthRatio(
            coverWidth = 0,
            coverHeight = 120,
        )
        val expected: Float =
            KitContentFeedCoverHeight.DEFAULT_RATIO_HEIGHT.toFloat() /
                KitContentFeedCoverHeight.DEFAULT_RATIO_WIDTH.toFloat()
        assertEquals(expected, ratio, 0.0001f)
    }

    /**
     * 按列宽计算封面像素高度.
     */
    @Test
    fun resolveCoverHeightPxUsesClampedRatio() {
        val height: Int = KitContentFeedCoverHeight.resolveCoverHeightPx(
            coverWidthPx = 150,
            coverWidth = 100,
            coverHeight = 200,
        )
        assertEquals(200, height)
    }

    /**
     * 按 RecyclerView 宽度推算单列封面渲染宽度.
     */
    @Test
    fun resolveFeedColumnWidthPxSubtractsItemMargin() {
        val columnWidth: Int = KitContentFeedCoverHeight.resolveFeedColumnWidthPx(
            recyclerViewWidthPx = 360,
            spanCount = 2,
            itemHorizontalMarginPx = 6,
        )
        assertEquals(168, columnWidth)
    }
}
