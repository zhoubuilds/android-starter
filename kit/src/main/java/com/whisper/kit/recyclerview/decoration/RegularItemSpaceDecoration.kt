package com.whisper.kit.recyclerview.decoration

import android.graphics.Rect
import android.util.Log
import android.view.View
import androidx.annotation.IntRange
import androidx.annotation.Px
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * 规整 RecyclerView 布局的 item 间距装饰器.
 *
 * 该装饰器用于 [LinearLayoutManager] 和 [GridLayoutManager] 的行列规整布局, 可分别设置主轴 item 间距、
 * 交叉轴 item 间距以及主轴首尾边界间距. 交叉轴不额外提供容器边界间距, 以避免 item 在交叉轴方向尺寸不一致.
 * [androidx.recyclerview.widget.StaggeredGridLayoutManager] 应使用 [StaggeredItemSpaceDecoration].
 * 所有间距必须为非负 px 值. item 根 View 不应再使用 margin 表达 item 间距或容器边界间距.
 * 遇到不支持的非空 LayoutManager 时会清空 offset、忽略该装饰器, 并按实例输出一次警告日志.
 *
 * 主轴内部间距统一由后一个 item 或 span group 的逻辑 start offset 承担. 首项或首个 span group 的逻辑 start
 * 使用 [startSpace], 逻辑 end 只为末项或最后一个 span group 使用 [endSpace].
 *
 * 网格交叉轴 offset 使用稳定的互补取整: start 向上取整, end 向下取整. 相邻 span 的 end 与 start
 * 之和始终等于 [crossAxisSpace], 各列总 offset 的差异最多 1px, 且取整结果只由 span 索引决定.
 * 为保持该取整和绘制归属稳定, 同一个 RecyclerView 的同一轴最多只能安装一个内部间距非零的 Decoration.
 * 仅设置 [startSpace] / [endSpace]、且主轴和交叉轴内部间距均为 0 的边界 Decoration 可以共存.
 * 子类可以在该间距语义上增加绘制行为, 但不能覆写 [getItemOffsets] 改变取整或布局拓扑规则.
 *
 * 该装饰器不观察 Adapter 数据更新. 同步 `notifyItem*` 会改变 position、itemCount、span 或首尾归属时,
 * 调用方应在更新前通过 AndroidX Core KTX 官方 [androidx.core.view.doOnNextLayout] 注册下一次布局回调,
 * 并在更新布局完成后失效 Decoration:
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
 *
 * 紧跟 Adapter `notify` 同步调用 [RecyclerView.invalidateItemDecorations] 可能被 predictive pre-layout 消费,
 * 不能替代上述布局完成后的调用. 在 pre-layout 中布局状态与 Adapter 数量不一致时,
 * 会保守处理首尾 span group, 不使用旧数量查询 Adapter 的新 span 拓扑.
 *
 * 运行时修改 LayoutManager 的 `orientation`、`reverseLayout`、`spanCount`、替换 `SpanSizeLookup` 实例,
 * 或修改 RecyclerView layout direction 后, 应立即调用 [RecyclerView.invalidateItemDecorations],
 * 使下一次布局重算已缓存的 decoration inset. 如果修改同一个 `SpanSizeLookup` 实例的内部规则,
 * 必须先调用 `SpanSizeLookup.invalidateSpanIndexCache()` 和 `SpanSizeLookup.invalidateSpanGroupIndexCache()`,
 * 再调用 [RecyclerView.invalidateItemDecorations]. 后者不会清除 LayoutManager 的 span 查询缓存.
 *
 * @property mainAxisSpace 主轴 item 间距, 单位 px.
 * @property crossAxisSpace 交叉轴 item 间距, 单位 px.
 * @property startSpace 主轴起始边界间距, 单位 px.
 * @property endSpace 主轴结束边界间距, 单位 px.
 *
 * @author whisper
 * @since 2026/07/30
 */
open class RegularItemSpaceDecoration(
    @param:Px
    @param:IntRange(from = 0)
    private val mainAxisSpace: Int,
    @param:Px
    @param:IntRange(from = 0)
    private val crossAxisSpace: Int,
    @param:Px
    @param:IntRange(from = 0)
    private val startSpace: Int = 0,
    @param:Px
    @param:IntRange(from = 0)
    private val endSpace: Int = 0,
) : RecyclerView.ItemDecoration() {

    private var hasLoggedUnsupportedLayoutManagerWarning: Boolean = false

    init {
        require(mainAxisSpace >= 0) { "mainAxisSpace must be non-negative." }
        require(crossAxisSpace >= 0) { "crossAxisSpace must be non-negative." }
        require(startSpace >= 0) { "startSpace must be non-negative." }
        require(endSpace >= 0) { "endSpace must be non-negative." }
    }

    /**
     * 创建主轴和交叉轴间距相同的 item 间距装饰器.
     *
     * @param space item 间距, 单位 px.
     */
    constructor(@Px @IntRange(from = 0) space: Int) : this(
        mainAxisSpace = space,
        crossAxisSpace = space,
    )

    final override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
        outRect.setEmpty()
        val layoutManager: RecyclerView.LayoutManager = parent.layoutManager ?: return
        if (layoutManager !is LinearLayoutManager) {
            logUnsupportedLayoutManagerOnce(layoutManager)
            return
        }
        val itemCount: Int = state.itemCount
        if (itemCount <= 0) return

        if (layoutManager is GridLayoutManager) {
            getItemOffsetForGrid(layoutManager, itemCount, outRect, view, parent)
        } else {
            getItemOffsetForLinear(layoutManager, itemCount, outRect, view, parent)
        }
    }

    /**
     * 按装饰器实例记录一次不支持的 LayoutManager 警告.
     */
    private fun logUnsupportedLayoutManagerOnce(layoutManager: RecyclerView.LayoutManager) {
        if (hasLoggedUnsupportedLayoutManagerWarning) return
        hasLoggedUnsupportedLayoutManagerWarning = true
        Log.w(
            LOG_TAG,
            "Regular RecyclerView decoration supports only LinearLayoutManager and " +
                "GridLayoutManager; ${layoutManager.javaClass.name} is ignored.",
        )
    }

    /**
     * 计算 GridLayoutManager item 间距.
     *
     * @param layoutManager GridLayoutManager.
     * @param itemCount 当前 RecyclerView 布局状态中的 item 数量.
     * @param outRect 输出间距矩形.
     * @param view 当前 itemView.
     * @param parent RecyclerView.
     */
    private fun getItemOffsetForGrid(
        layoutManager: GridLayoutManager,
        itemCount: Int,
        outRect: Rect,
        view: View,
        parent: RecyclerView,
    ) {
        val adapterItemCount: Int = parent.adapter?.itemCount ?: return
        val position: Int = parent.getChildAdapterPosition(view)
        if (position !in 0 until itemCount || position !in 0 until adapterItemCount) return

        val layoutParams: GridLayoutManager.LayoutParams =
            view.layoutParams as? GridLayoutManager.LayoutParams ?: return
        val spanCount: Int = layoutManager.spanCount
        val lookup: GridLayoutManager.SpanSizeLookup = layoutManager.spanSizeLookup
        val spanIndex: Int = layoutParams.spanIndex
        val spanSize: Int = layoutParams.spanSize
        val topologyMatchesAdapter: Boolean = itemCount == adapterItemCount
        val mainStart: Int = when {
            startSpace == mainAxisSpace -> mainAxisSpace
            position == 0 -> startSpace
            topologyMatchesAdapter && isInFirstSpanGroup(position, spanCount, spanIndex, lookup) -> startSpace
            else -> mainAxisSpace
        }
        val mainEnd: Int = if (
            endSpace > 0 &&
            topologyMatchesAdapter &&
            isInLastSpanGroup(position, itemCount, spanCount, spanIndex, spanSize, lookup)
        ) {
            endSpace
        } else {
            0
        }

        val spanEndIndex: Int = spanIndex + spanSize - 1
        val crossStart: Int = crossAxisStartOffset(crossAxisSpace, spanCount, spanIndex)
        val crossEnd: Int = crossAxisEndOffset(crossAxisSpace, spanCount, spanEndIndex)
        val mainAxisReversed: Boolean = isMainAxisReversed(layoutManager, parent)

        if (layoutManager.orientation == RecyclerView.HORIZONTAL) {
            outRect.left = if (mainAxisReversed) mainEnd else mainStart
            outRect.top = crossStart
            outRect.right = if (mainAxisReversed) mainStart else mainEnd
            outRect.bottom = crossEnd
        } else if (isRtl(parent)) {
            outRect.left = crossEnd
            outRect.top = if (mainAxisReversed) mainEnd else mainStart
            outRect.right = crossStart
            outRect.bottom = if (mainAxisReversed) mainStart else mainEnd
        } else {
            outRect.left = crossStart
            outRect.top = if (mainAxisReversed) mainEnd else mainStart
            outRect.right = crossEnd
            outRect.bottom = if (mainAxisReversed) mainStart else mainEnd
        }
    }

    /**
     * 计算 LinearLayoutManager item 间距.
     *
     * @param layoutManager LinearLayoutManager.
     * @param itemCount 当前 RecyclerView 布局状态中的 item 数量.
     * @param outRect 输出间距矩形.
     * @param view 当前 itemView.
     * @param parent RecyclerView.
     */
    private fun getItemOffsetForLinear(
        layoutManager: LinearLayoutManager,
        itemCount: Int,
        outRect: Rect,
        view: View,
        parent: RecyclerView,
    ) {
        val position: Int = parent.getChildAdapterPosition(view)
        val adapterItemCount: Int = parent.adapter?.itemCount ?: return
        if (position !in 0 until itemCount || position !in 0 until adapterItemCount) return

        val mainStart: Int = if (position == 0) startSpace else mainAxisSpace
        val mainEnd: Int = if (position == itemCount - 1) {
            endSpace
        } else {
            0
        }
        val mainAxisReversed: Boolean = isMainAxisReversed(layoutManager, parent)

        if (layoutManager.orientation == RecyclerView.HORIZONTAL) {
            outRect.left = if (mainAxisReversed) mainEnd else mainStart
            outRect.top = 0
            outRect.right = if (mainAxisReversed) mainStart else mainEnd
            outRect.bottom = 0
        } else {
            outRect.left = 0
            outRect.top = if (mainAxisReversed) mainEnd else mainStart
            outRect.right = 0
            outRect.bottom = if (mainAxisReversed) mainStart else mainEnd
        }
    }

    /**
     * 判断 RecyclerView 是否使用 RTL 布局方向.
     */
    private fun isRtl(parent: RecyclerView): Boolean =
        parent.layoutDirection == View.LAYOUT_DIRECTION_RTL

    /**
     * 判断主轴布局方向是否相对 adapter 顺序反向.
     */
    private fun isMainAxisReversed(
        layoutManager: LinearLayoutManager,
        parent: RecyclerView,
    ): Boolean =
        layoutManager.reverseLayout.xor(layoutManager.orientation == RecyclerView.HORIZONTAL && isRtl(parent))

    private companion object {

        private const val LOG_TAG: String = "RecyclerViewDecoration"
    }
}
