package com.whisper.kit.recyclerview.decoration

import android.graphics.Rect
import android.view.View
import androidx.annotation.Px
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * RecyclerView item 间距装饰器.
 *
 * 该装饰器支持 [LinearLayoutManager] 和 [GridLayoutManager], 可分别设置主轴 item 间距、
 * 交叉轴 item 间距以及主轴首尾边界间距. 交叉轴不额外提供容器边界间距, 以避免 item 在交叉轴方向尺寸不一致.
 *
 * @property mainAxisSpace 主轴 item 间距, 单位 px.
 * @property crossAxisSpace 交叉轴 item 间距, 单位 px.
 * @property startSpace 主轴起始边界间距, 单位 px.
 * @property endSpace 主轴结束边界间距, 单位 px.
 *
 * @author whisper
 * @since 2026/07/30
 */
open class ItemSpaceDecoration(
    @param:Px
    protected val mainAxisSpace: Int,
    @param:Px
    protected val crossAxisSpace: Int,
    @param:Px
    private val startSpace: Int = 0,
    @param:Px
    private val endSpace: Int = 0,
) : RecyclerView.ItemDecoration() {

    /**
     * 创建主轴和交叉轴间距相同的 item 间距装饰器.
     *
     * @param space item 间距, 单位 px.
     */
    constructor(@Px space: Int) : this(
        mainAxisSpace = space,
        crossAxisSpace = space,
    )

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
        val layoutManager: RecyclerView.LayoutManager = parent.layoutManager ?: return
        val adapter: RecyclerView.Adapter<*> = parent.adapter ?: return
        when (layoutManager) {
            is GridLayoutManager -> getItemOffsetForGrid(layoutManager, adapter, outRect, view, parent)
            is LinearLayoutManager -> getItemOffsetForLinear(layoutManager, adapter, outRect, view, parent)
        }
    }

    /**
     * 计算 GridLayoutManager item 间距.
     *
     * @param layoutManager GridLayoutManager.
     * @param adapter RecyclerView Adapter.
     * @param outRect 输出间距矩形.
     * @param view 当前 itemView.
     * @param parent RecyclerView.
     */
    open fun getItemOffsetForGrid(
        layoutManager: GridLayoutManager,
        adapter: RecyclerView.Adapter<*>,
        outRect: Rect,
        view: View,
        parent: RecyclerView,
    ) {
        val position: Int = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION || adapter.itemCount <= 0) return

        val spanCount: Int = layoutManager.spanCount
        val lookup: GridLayoutManager.SpanSizeLookup = layoutManager.spanSizeLookup
        val spanGroupIndex: Int = lookup.getSpanGroupIndex(position, spanCount)
        val lastSpanGroupIndex: Int = lookup.getSpanGroupIndex(adapter.itemCount - 1, spanCount)
        val mainStart: Int = if (spanGroupIndex == 0) startSpace else mainAxisSpace / 2
        val mainEnd: Int = if (spanGroupIndex == lastSpanGroupIndex) {
            endSpace
        } else {
            mainAxisSpace - mainAxisSpace / 2
        }

        val spanIndex: Int = lookup.getSpanIndex(position, spanCount)
        val spanEndIndex: Int = spanIndex + lookup.getSpanSize(position) - 1
        val crossStart: Int = if (spanIndex == 0) {
            0
        } else {
            crossAxisSpace - crossAxisEndForSpan(spanCount, spanIndex - 1)
        }
        val crossEnd: Int = crossAxisEndForSpan(spanCount, spanEndIndex)
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
     * @param adapter RecyclerView Adapter.
     * @param outRect 输出间距矩形.
     * @param view 当前 itemView.
     * @param parent RecyclerView.
     */
    open fun getItemOffsetForLinear(
        layoutManager: LinearLayoutManager,
        adapter: RecyclerView.Adapter<*>,
        outRect: Rect,
        view: View,
        parent: RecyclerView,
    ) {
        val position: Int = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION || adapter.itemCount <= 0) return

        val mainStart: Int = if (position == 0) startSpace else mainAxisSpace / 2
        val mainEnd: Int = if (position == adapter.itemCount - 1) {
            endSpace
        } else {
            mainAxisSpace - mainAxisSpace / 2
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
     * 计算指定 span 槽位结束侧分到的交叉轴间距.
     */
    private fun crossAxisEndForSpan(spanCount: Int, spanIndex: Int): Int {
        if (spanCount <= 1) return 0
        return ((spanCount - 1 - spanIndex) * crossAxisSpace) / spanCount
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
}
