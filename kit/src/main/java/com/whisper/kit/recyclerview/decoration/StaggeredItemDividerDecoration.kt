package com.whisper.kit.recyclerview.decoration

import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.IntRange
import androidx.annotation.Px
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.isEmpty
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import kotlin.math.roundToInt

/**
 * StaggeredGridLayoutManager item 分割线装饰器.
 *
 * 该装饰器继承 [StaggeredItemSpaceDecoration], 完整复用其不可覆写的 offset 和
 * [StaggeredFullSpanProvider] 契约, 自身只增加分割线绘制. 主轴拓扑需要
 * Provider 但 Adapter 未实现时, 会输出一次警告并禁用主轴 offset 和分割线, 交叉轴仍正常工作. 绘制时先在
 * span 间隙中沿主轴连续绘制 [crossAxisDivider], 并跳过与 item 实际区域相交的主轴切片; 再在所有非起始 item
 * 的 logical start 间距中沿交叉轴分段绘制 [mainAxisDivider]. full-span item 不会被交叉轴分割线穿过.
 * 遇到其它非空 LayoutManager 时会沿用 [StaggeredItemSpaceDecoration] 的规则清空 offset、按实例输出一次
 * 警告日志, 并跳过分割线绘制.
 *
 * 瀑布流末端各 span 可能停在不同位置, 因而该装饰器不提供结束边界间距. [crossAxisDividerMainAxisEndMargin]
 * 只缩进当前已布局内容包络中的连续分割线端点, 不为 item 增加 `endSpace`.
 *
 * 与 [StaggeredItemSpaceDecoration] 一样, 不支持 decorated main-axis measurement 为 0 的 item;
 * 每个 item 包含 decoration inset 和 LayoutParams margin 后的主轴占用尺寸必须大于 0.
 * 零尺寸 item 不会推进 span 端点, 无法稳定推导起始分割线归属.
 * [RecyclerView.getClipToPadding] 为 `true` 时, 绘制限制在 padding 内容区; 为 `false` 时,
 * 连续线范围使用完整 RecyclerView 边界.
 * [mainAxisDividerSize] 和 [crossAxisDividerSize] 始终参与 item offset 计算; 对应 Drawable 为 `null` 时
 * 只保留该轴的透明间距, 不绘制分割线. 为保持间距取整和线条归属稳定, 同一个 RecyclerView 的同一轴
 * 最多只能安装一个内部间距非零的 Decoration; 内部间距均为 0 的边界 Decoration 可以共存.
 *
 * 数据更新动画期间按当前布局状态绘制, 不保证分割线严格跟随旧布局归属; item 会覆盖暂时不准确的分割线.
 * 调用方通过同步 Adapter `notifyItem*` 改变 position、itemCount、span 或起始归属时, 应在更新前通过
 * AndroidX Core KTX 官方 [androidx.core.view.doOnNextLayout] 注册下一次布局回调, 并在更新布局完成后失效
 * Decoration:
 *
 * ```
 * recyclerView.doOnNextLayout {
 *     recyclerView.invalidateItemDecorations()
 * }
 * adapter.notifyItemMoved(fromPosition, toPosition)
 * ```
 *
 * `ListAdapter` 或 `AsyncListDiffer` 的 `submitList` 会异步计算差异, 应改在 commit callback 中注册:
 *
 * ```
 * adapter.submitList(newList) {
 *     recyclerView.doOnNextLayout {
 *         recyclerView.invalidateItemDecorations()
 *     }
 * }
 * ```
 *
 * 连续调用 `submitList` 时, 较早但未实际提交的列表可能不会执行 callback; 最终状态恢复必须挂在实际提交的
 * 最新列表 callback 中. 在 `submitList` 前预注册的一次性回调可能被差异提交前的其它布局提前消费.
 * 紧跟 Adapter `notify` 同步调用 [RecyclerView.invalidateItemDecorations] 可能被 predictive pre-layout 消费.
 * 运行时修改 LayoutManager 的 `orientation`、`reverseLayout`、`spanCount`, 或修改 RecyclerView
 * layout direction 后, 应立即调用 [RecyclerView.invalidateItemDecorations],
 * 使下一次布局重算已缓存的 decoration inset. 所有尺寸和边距必须为非负 px 值.
 *
 * @property mainAxisDividerSize 主轴分割线尺寸, 单位 px.
 * @property crossAxisDividerSize 交叉轴分割线尺寸, 单位 px.
 * @property mainAxisDividerCrossAxisStartMargin 主轴分割线沿交叉轴 logical start 的边距, 单位 px.
 * @property mainAxisDividerCrossAxisEndMargin 主轴分割线沿交叉轴 logical end 的边距, 单位 px.
 * @property crossAxisDividerMainAxisStartMargin 交叉轴分割线沿主轴 logical start 的边距, 单位 px.
 * @property crossAxisDividerMainAxisEndMargin 交叉轴分割线沿主轴 logical end 的边距, 单位 px.
 * @property mainAxisDivider 主轴分割线 Drawable; `null` 表示只保留透明主轴间距.
 * @property crossAxisDivider 交叉轴分割线 Drawable; `null` 表示只保留透明交叉轴间距.
 *
 * @author whisper
 * @since 2026/09/03
 */
class StaggeredItemDividerDecoration(
    @param:Px
    @param:IntRange(from = 0)
    private val mainAxisDividerSize: Int,
    @param:Px
    @param:IntRange(from = 0)
    private val crossAxisDividerSize: Int,
    @param:Px
    @param:IntRange(from = 0)
    private val mainAxisDividerCrossAxisStartMargin: Int,
    @param:Px
    @param:IntRange(from = 0)
    private val mainAxisDividerCrossAxisEndMargin: Int,
    @param:Px
    @param:IntRange(from = 0)
    private val crossAxisDividerMainAxisStartMargin: Int,
    @param:Px
    @param:IntRange(from = 0)
    private val crossAxisDividerMainAxisEndMargin: Int,
    private val mainAxisDivider: Drawable?,
    private val crossAxisDivider: Drawable?,
) : StaggeredItemSpaceDecoration(
    mainAxisSpace = mainAxisDividerSize,
    crossAxisSpace = crossAxisDividerSize,
) {

    init {
        require(mainAxisDividerCrossAxisStartMargin >= 0) {
            "mainAxisDividerCrossAxisStartMargin must be non-negative."
        }
        require(mainAxisDividerCrossAxisEndMargin >= 0) {
            "mainAxisDividerCrossAxisEndMargin must be non-negative."
        }
        require(crossAxisDividerMainAxisStartMargin >= 0) {
            "crossAxisDividerMainAxisStartMargin must be non-negative."
        }
        require(crossAxisDividerMainAxisEndMargin >= 0) {
            "crossAxisDividerMainAxisEndMargin must be non-negative."
        }
    }

    /**
     * 创建主轴和交叉轴分别配置尺寸及 Drawable、且所有分割线边距为 0 的装饰器.
     *
     * @param mainAxisDividerSize 主轴分割线尺寸, 单位 px.
     * @param crossAxisDividerSize 交叉轴分割线尺寸, 单位 px.
     * @param mainAxisDivider 主轴分割线 Drawable; `null` 表示只保留透明主轴间距.
     * @param crossAxisDivider 交叉轴分割线 Drawable; `null` 表示只保留透明交叉轴间距.
     */
    constructor(
        @Px
        @IntRange(from = 0)
        mainAxisDividerSize: Int,
        @Px
        @IntRange(from = 0)
        crossAxisDividerSize: Int,
        mainAxisDivider: Drawable?,
        crossAxisDivider: Drawable?,
    ) : this(
        mainAxisDividerSize = mainAxisDividerSize,
        crossAxisDividerSize = crossAxisDividerSize,
        mainAxisDividerCrossAxisStartMargin = 0,
        mainAxisDividerCrossAxisEndMargin = 0,
        crossAxisDividerMainAxisStartMargin = 0,
        crossAxisDividerMainAxisEndMargin = 0,
        mainAxisDivider = mainAxisDivider,
        crossAxisDivider = crossAxisDivider,
    )

    /**
     * 创建主轴和交叉轴分割线尺寸、边距、Drawable 都相同的分割线装饰器.
     *
     * @param dividerSize 分割线尺寸, 单位 px.
     * @param dividerMargin 分割线边距, 单位 px.
     * @param divider 分割线 Drawable; `null` 表示主轴和交叉轴都只保留透明间距.
     */
    constructor(
        @Px
        @IntRange(from = 0)
        dividerSize: Int,
        @Px
        @IntRange(from = 0)
        dividerMargin: Int,
        divider: Drawable?,
    ) : this(
        mainAxisDividerSize = dividerSize,
        crossAxisDividerSize = dividerSize,
        mainAxisDividerCrossAxisStartMargin = dividerMargin,
        mainAxisDividerCrossAxisEndMargin = dividerMargin,
        crossAxisDividerMainAxisStartMargin = dividerMargin,
        crossAxisDividerMainAxisEndMargin = dividerMargin,
        mainAxisDivider = divider,
        crossAxisDivider = divider,
    )

    /**
     * 创建指定颜色的纯色分割线装饰器.
     *
     * @param dividerSize 分割线尺寸, 单位 px.
     * @param dividerMargin 分割线边距, 单位 px.
     * @param dividerColor 分割线颜色.
     */
    constructor(
        @Px
        @IntRange(from = 0)
        dividerSize: Int,
        @Px
        @IntRange(from = 0)
        dividerMargin: Int,
        @ColorInt
        dividerColor: Int,
    ) : this(
        dividerSize = dividerSize,
        dividerMargin = dividerMargin,
        divider = dividerColor.toDrawable(),
    )

    override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val layoutManager: StaggeredGridLayoutManager =
            parent.layoutManager as? StaggeredGridLayoutManager ?: return
        val itemCount: Int = state.itemCount
        if (itemCount <= 0) return

        val saveCount: Int = canvas.save()
        try {
            clipToRecyclerViewContent(canvas, parent)
            val drawsMainAxisDivider: Boolean =
                mainAxisDivider != null && mainAxisDividerSize > 0 && !parent.isEmpty()
            val drawsCrossAxisDivider: Boolean = crossAxisDivider != null &&
                crossAxisDividerSize > 0 && layoutManager.spanCount > 1 && !parent.isEmpty()
            val mainAxisTopology: StaggeredDividerMainAxisTopology? = if (
                drawsMainAxisDivider || (drawsCrossAxisDivider && mainAxisDividerSize > 0)
            ) {
                resolveMainAxisTopology(parent, layoutManager)
            } else {
                null
            }
            drawCrossAxisDividers(canvas, parent, layoutManager, itemCount, mainAxisTopology)
            drawMainAxisDividers(canvas, parent, layoutManager, state, mainAxisTopology)
        } finally {
            canvas.restoreToCount(saveCount)
        }
    }

    /**
     * 沿主轴连续绘制当前已布局内容中的 span 分割线.
     */
    private fun drawCrossAxisDividers(
        canvas: Canvas,
        parent: RecyclerView,
        layoutManager: StaggeredGridLayoutManager,
        itemCount: Int,
        mainAxisTopology: StaggeredDividerMainAxisTopology?,
    ) {
        val divider: Drawable = crossAxisDivider ?: return
        val spanCount: Int = layoutManager.spanCount
        if (crossAxisDividerSize == 0 || spanCount <= 1 || parent.isEmpty()) return

        val boundaryAnchors: Array<View?> = spanBoundaryAnchors(parent, layoutManager)
        val mainAxisBounds: StaggeredDividerAxisBounds = mainAxisBounds(
            parent = parent,
            layoutManager = layoutManager,
            itemCount = itemCount,
            mainAxisTopology = mainAxisTopology,
        ) ?: return
        val childBounds: List<Rect> = translatedRecyclerViewChildBounds(parent, layoutManager.orientation)

        for (spanBoundary: Int in 1 until spanCount) {
            val anchor: View = boundaryAnchors[spanBoundary] ?: continue
            if (layoutManager.orientation == RecyclerView.HORIZONTAL) {
                val top: Int = saturatedDrawingCoordinate(
                    anchor.top.toLong() - crossAxisDividerSize,
                )
                drawDividerOutsideChildren(
                    canvas = canvas,
                    divider = divider,
                    left = mainAxisBounds.start,
                    top = top,
                    right = mainAxisBounds.end,
                    bottom = anchor.top,
                    orientation = layoutManager.orientation,
                    childBounds = childBounds,
                )
            } else {
                val left: Int = if (isRtl(parent)) {
                    anchor.right
                } else {
                    saturatedDrawingCoordinate(anchor.left.toLong() - crossAxisDividerSize)
                }
                val right: Int = if (isRtl(parent)) {
                    saturatedDrawingCoordinate(anchor.right.toLong() + crossAxisDividerSize)
                } else {
                    anchor.left
                }
                drawDividerOutsideChildren(
                    canvas = canvas,
                    divider = divider,
                    left = left,
                    top = mainAxisBounds.start,
                    right = right,
                    bottom = mainAxisBounds.end,
                    orientation = layoutManager.orientation,
                    childBounds = childBounds,
                )
            }
        }
    }

    /**
     * 返回当前已布局普通 item 实际使用的非边界 span 分隔位置.
     */
    private fun spanBoundaryAnchors(
        parent: RecyclerView,
        layoutManager: StaggeredGridLayoutManager,
    ): Array<View?> {
        val spanCount: Int = layoutManager.spanCount
        val result: Array<View?> = arrayOfNulls(spanCount)
        for (i: Int in 0 until parent.childCount) {
            val child: View = parent.getChildAt(i)
            val layoutParams: StaggeredGridLayoutManager.LayoutParams =
                child.layoutParams as? StaggeredGridLayoutManager.LayoutParams ?: continue
            if (layoutParams.isFullSpan) continue

            val logicalSpanIndex: Int = logicalSpanIndex(layoutManager, parent, layoutParams.spanIndex)
            if (logicalSpanIndex in 1 until spanCount && result[logicalSpanIndex] == null) {
                result[logicalSpanIndex] = child
            }
        }
        return result
    }

    /**
     * 计算连续 span 分割线在当前可见内容中的主轴范围, 并包含主轴行为启用时由本装饰器为非起始 item
     * 提供的 logical-start inset.
     */
    private fun mainAxisBounds(
        parent: RecyclerView,
        layoutManager: StaggeredGridLayoutManager,
        itemCount: Int,
        mainAxisTopology: StaggeredDividerMainAxisTopology?,
    ): StaggeredDividerAxisBounds? {
        var childStart: Int = Int.MAX_VALUE
        var childEnd: Int = Int.MIN_VALUE
        var containsAdapterStart: Boolean = false
        var containsAdapterEnd: Boolean = false
        val mainAxisReversed: Boolean = isMainAxisReversed(layoutManager, parent)

        for (i: Int in 0 until parent.childCount) {
            val child: View = parent.getChildAt(i)
            var currentStart: Int
            var currentEnd: Int
            if (layoutManager.orientation == RecyclerView.HORIZONTAL) {
                currentStart = translatedX(child, child.left)
                currentEnd = translatedX(child, child.right)
            } else {
                currentStart = translatedY(child, child.top)
                currentEnd = translatedY(child, child.bottom)
            }
            childStart = minOf(childStart, currentStart)
            childEnd = maxOf(childEnd, currentEnd)

            val position: Int = parent.getChildAdapterPosition(child)
            if (position !in 0 until itemCount) continue
            if (
                mainAxisTopology != null &&
                position in 0 until mainAxisTopology.itemCount &&
                !mainAxisTopology.isAtMainAxisStart(position)
            ) {
                if (mainAxisReversed) {
                    currentEnd = saturatedDrawingCoordinate(
                        currentEnd.toLong() + mainAxisDividerSize,
                    )
                    childEnd = maxOf(childEnd, currentEnd)
                } else {
                    currentStart = saturatedDrawingCoordinate(
                        currentStart.toLong() - mainAxisDividerSize,
                    )
                    childStart = minOf(childStart, currentStart)
                }
            }
            containsAdapterStart = containsAdapterStart || position == 0
            containsAdapterEnd = containsAdapterEnd || position == itemCount - 1
        }
        if (childStart == Int.MAX_VALUE || childEnd == Int.MIN_VALUE) return null

        val marginAtPhysicalStart: Int = if (mainAxisReversed) {
            if (containsAdapterEnd) crossAxisDividerMainAxisEndMargin else 0
        } else {
            if (containsAdapterStart) crossAxisDividerMainAxisStartMargin else 0
        }
        val marginAtPhysicalEnd: Int = if (mainAxisReversed) {
            if (containsAdapterStart) crossAxisDividerMainAxisStartMargin else 0
        } else {
            if (containsAdapterEnd) crossAxisDividerMainAxisEndMargin else 0
        }
        val parentBounds: StaggeredDividerAxisBounds = recyclerViewAxisBounds(
            parent,
            layoutManager.orientation,
        )
        val start: Int = maxOf(
            parentBounds.start,
            saturatedDrawingCoordinate(childStart.toLong() + marginAtPhysicalStart),
        )
        val end: Int = minOf(
            parentBounds.end,
            saturatedDrawingCoordinate(childEnd.toLong() - marginAtPhysicalEnd),
        )
        return if (start < end) StaggeredDividerAxisBounds(start, end) else null
    }

    /**
     * 按 item 分段绘制非起始 item 的主轴分割线.
     */
    private fun drawMainAxisDividers(
        canvas: Canvas,
        parent: RecyclerView,
        layoutManager: StaggeredGridLayoutManager,
        state: RecyclerView.State,
        mainAxisTopology: StaggeredDividerMainAxisTopology?,
    ) {
        if (mainAxisDivider == null || mainAxisDividerSize == 0) return
        val topology: StaggeredDividerMainAxisTopology = mainAxisTopology ?: return
        for (i: Int in 0 until parent.childCount) {
            val child: View = parent.getChildAt(i)
            val position: Int = parent.getChildAdapterPosition(child)
            if (position !in 0 until state.itemCount || position !in 0 until topology.itemCount) continue
            if (topology.isAtMainAxisStart(position)) continue

            drawMainAxisDivider(canvas, parent, layoutManager, child)
        }
    }

    /**
     * 解析一次当前 Adapter 的主轴起始拓扑, 供同一轮连续线与分段线绘制共享.
     */
    private fun resolveMainAxisTopology(
        parent: RecyclerView,
        layoutManager: StaggeredGridLayoutManager,
    ): StaggeredDividerMainAxisTopology? {
        if (mainAxisDividerSize == 0) return null

        val adapter: RecyclerView.Adapter<*> = parent.adapter ?: return null
        val itemCount: Int = adapter.itemCount
        val spanCount: Int = layoutManager.spanCount
        val requiresFullSpanProvider: Boolean = spanCount > 1 && itemCount > 1
        val fullSpanProvider: StaggeredFullSpanProvider? = adapter as? StaggeredFullSpanProvider
        if (requiresFullSpanProvider && fullSpanProvider == null) return null

        val prefixEndExclusive: Int = minOf(itemCount, spanCount)
        val hasVisibleStartPrefixItem: Boolean = requiresFullSpanProvider &&
            (0 until parent.childCount).any { childIndex: Int ->
                val position: Int = parent.getChildAdapterPosition(parent.getChildAt(childIndex))
                position in 1 until prefixEndExclusive
            }
        val firstFullSpanPosition: Int? = if (hasVisibleStartPrefixItem) {
            (0 until minOf(itemCount, spanCount)).firstOrNull(
                checkNotNull(fullSpanProvider)::isFullSpan,
            )
        } else {
            null
        }
        return StaggeredDividerMainAxisTopology(
            itemCount = itemCount,
            spanCount = spanCount,
            hasResolvedStartPrefix = hasVisibleStartPrefixItem,
            firstFullSpanPosition = firstFullSpanPosition,
        )
    }

    /**
     * 绘制 item logical start 间距中的主轴分割线.
     */
    private fun drawMainAxisDivider(
        canvas: Canvas,
        parent: RecyclerView,
        layoutManager: StaggeredGridLayoutManager,
        child: View,
    ) {
        val divider: Drawable = mainAxisDivider ?: return
        val mainAxisReversed: Boolean = isMainAxisReversed(layoutManager, parent)
        if (layoutManager.orientation == RecyclerView.HORIZONTAL) {
            val height: Int = nonNegativeDrawingSize(
                child.height.toLong() - mainAxisDividerCrossAxisStartMargin -
                    mainAxisDividerCrossAxisEndMargin,
            )
            divider.setBounds(0, 0, mainAxisDividerSize, height)
            drawTranslated(canvas, divider) {
                val x: Int = if (mainAxisReversed) {
                    child.right
                } else {
                    saturatedDrawingCoordinate(child.left.toLong() - mainAxisDividerSize)
                }
                translate(
                    translatedX(child, x).toFloat(),
                    translatedY(
                        child,
                        saturatedDrawingCoordinate(
                            child.top.toLong() + mainAxisDividerCrossAxisStartMargin,
                        ),
                    ).toFloat(),
                )
            }
        } else {
            val width: Int = nonNegativeDrawingSize(
                child.width.toLong() - mainAxisDividerCrossAxisStartMargin -
                    mainAxisDividerCrossAxisEndMargin,
            )
            divider.setBounds(0, 0, width, mainAxisDividerSize)
            drawTranslated(canvas, divider) {
                val y: Int = if (mainAxisReversed) {
                    child.bottom
                } else {
                    saturatedDrawingCoordinate(child.top.toLong() - mainAxisDividerSize)
                }
                val margin: Int = if (isRtl(parent)) {
                    mainAxisDividerCrossAxisEndMargin
                } else {
                    mainAxisDividerCrossAxisStartMargin
                }
                val x: Int = saturatedDrawingCoordinate(child.left.toLong() + margin)
                translate(
                    translatedX(child, x).toFloat(),
                    translatedY(child, y).toFloat(),
                )
            }
        }
    }

    /**
     * 返回 LayoutManager span index 对应的交叉轴逻辑索引.
     */
    private fun logicalSpanIndex(
        layoutManager: StaggeredGridLayoutManager,
        parent: RecyclerView,
        spanIndex: Int,
    ): Int = if (layoutManager.orientation == RecyclerView.VERTICAL && isRtl(parent)) {
        layoutManager.spanCount - 1 - spanIndex
    } else {
        spanIndex
    }

    /**
     * 判断 RecyclerView 是否使用 RTL 布局方向.
     */
    private fun isRtl(parent: RecyclerView): Boolean =
        parent.layoutDirection == View.LAYOUT_DIRECTION_RTL

    /**
     * 判断主轴布局方向是否相对 Adapter 顺序反向.
     */
    private fun isMainAxisReversed(
        layoutManager: StaggeredGridLayoutManager,
        parent: RecyclerView,
    ): Boolean =
        layoutManager.reverseLayout.xor(
            layoutManager.orientation == RecyclerView.HORIZONTAL && isRtl(parent),
        )

    /**
     * 按 RecyclerView 的 clipToPadding 设置返回指定物理轴的可绘制范围.
     */
    private fun recyclerViewAxisBounds(
        parent: RecyclerView,
        orientation: Int,
    ): StaggeredDividerAxisBounds {
        val usePadding: Boolean = parent.clipToPadding
        return if (orientation == RecyclerView.HORIZONTAL) {
            StaggeredDividerAxisBounds(
                start = if (usePadding) parent.paddingLeft else 0,
                end = if (usePadding) {
                    saturatedDrawingCoordinate(parent.width.toLong() - parent.paddingRight)
                } else {
                    parent.width
                },
            )
        } else {
            StaggeredDividerAxisBounds(
                start = if (usePadding) parent.paddingTop else 0,
                end = if (usePadding) {
                    saturatedDrawingCoordinate(parent.height.toLong() - parent.paddingBottom)
                } else {
                    parent.height
                },
            )
        }
    }

    /**
     * clipToPadding 启用时把所有分割线限制在 RecyclerView 内容区.
     */
    private fun clipToRecyclerViewContent(canvas: Canvas, parent: RecyclerView) {
        if (!parent.clipToPadding) return

        canvas.clipRect(
            parent.paddingLeft,
            parent.paddingTop,
            saturatedDrawingCoordinate(parent.width.toLong() - parent.paddingRight),
            saturatedDrawingCoordinate(parent.height.toLong() - parent.paddingBottom),
        )
    }

    /**
     * 在平移后的画布上绘制 Drawable.
     */
    private inline fun drawTranslated(canvas: Canvas, divider: Drawable, translate: Canvas.() -> Unit) {
        val saveCount: Int = canvas.save()
        try {
            canvas.translate()
            divider.draw(canvas)
        } finally {
            canvas.restoreToCount(saveCount)
        }
    }

    /**
     * 计算跟随 item 横向动画位移后的绘制坐标.
     */
    private fun translatedX(child: View, x: Int): Int =
        saturatedDrawingCoordinate(x.toLong() + child.translationX.roundToInt())

    /**
     * 计算跟随 item 纵向动画位移后的绘制坐标.
     */
    private fun translatedY(child: View, y: Int): Int =
        saturatedDrawingCoordinate(y.toLong() + child.translationY.roundToInt())
}

/**
 * Staggered 连续分割线在物理主轴上的起止边界.
 */
private data class StaggeredDividerAxisBounds(
    val start: Int,
    val end: Int,
)

/**
 * Staggered Adapter 起始处的主轴归属快照.
 */
private data class StaggeredDividerMainAxisTopology(
    val itemCount: Int,
    val spanCount: Int,
    val hasResolvedStartPrefix: Boolean,
    val firstFullSpanPosition: Int?,
) {

    fun isAtMainAxisStart(position: Int): Boolean = when {
        position == 0 -> true
        spanCount <= 1 || itemCount <= 1 -> false
        position >= minOf(itemCount, spanCount) -> false
        else -> {
            check(hasResolvedStartPrefix)
            firstFullSpanPosition == null || position < firstFullSpanPosition
        }
    }
}
