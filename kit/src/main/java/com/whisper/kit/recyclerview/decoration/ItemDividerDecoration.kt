package com.whisper.kit.recyclerview.decoration

import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.Px
import androidx.core.graphics.drawable.toDrawable
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.roundToInt

/**
 * RecyclerView item 分割线装饰器.
 *
 * 该装饰器在 item 间预留分割线空间, 并在 item 绘制之后绘制分割线. 主轴分割线按行或列贯穿 RecyclerView
 * 可用区域绘制; 交叉轴分割线按 item 绘制, 用于网格布局中列或行之间的分隔. 分割线绘制锚点会跟随 item
 * 对应方向的 translation, 以适配 RecyclerView item 动画中的视觉位置.
 *
 * @property mainAxisDividerMarginStart 主轴分割线开始边距, 单位 px.
 * @property mainAxisDividerMarginEnd 主轴分割线结束边距, 单位 px.
 * @property crossAxisDividerMarginStart 交叉轴分割线开始边距, 单位 px.
 * @property crossAxisDividerMarginEnd 交叉轴分割线结束边距, 单位 px.
 * @property mainAxisDivider 主轴分割线 Drawable.
 * @property crossAxisDivider 交叉轴分割线 Drawable.
 *
 * @author whisper
 * @since 2026/07/30
 */
class ItemDividerDecoration(
    @Px
    mainAxisDividerSize: Int,
    @Px
    crossAxisDividerSize: Int,
    @param:Px
    private val mainAxisDividerMarginStart: Int,
    @param:Px
    private val mainAxisDividerMarginEnd: Int,
    @param:Px
    private val crossAxisDividerMarginStart: Int,
    @param:Px
    private val crossAxisDividerMarginEnd: Int,
    private val mainAxisDivider: Drawable?,
    private val crossAxisDivider: Drawable?,
) : ItemSpaceDecoration(
    mainAxisSpace = mainAxisDividerSize,
    crossAxisSpace = crossAxisDividerSize,
) {

    /**
     * 创建主轴和交叉轴分割线尺寸、边距、Drawable 都相同的分割线装饰器.
     *
     * @param dividerSize 分割线尺寸, 单位 px.
     * @param dividerMargin 分割线边距, 单位 px.
     * @param divider 分割线 Drawable.
     */
    constructor(
        @Px
        dividerSize: Int,
        @Px
        dividerMargin: Int,
        divider: Drawable?,
    ) : this(
        mainAxisDividerSize = dividerSize,
        crossAxisDividerSize = dividerSize,
        mainAxisDividerMarginStart = dividerMargin,
        mainAxisDividerMarginEnd = dividerMargin,
        crossAxisDividerMarginStart = dividerMargin,
        crossAxisDividerMarginEnd = dividerMargin,
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
        dividerSize: Int,
        @Px
        dividerMargin: Int,
        @ColorInt
        dividerColor: Int,
    ) : this(
        dividerSize = dividerSize,
        dividerMargin = dividerMargin,
        divider = dividerColor.toDrawable(),
    )

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val layoutManager: RecyclerView.LayoutManager = parent.layoutManager ?: return
        val adapter: RecyclerView.Adapter<*> = parent.adapter ?: return
        when (layoutManager) {
            is GridLayoutManager -> onDrawOverForGrid(c, parent, layoutManager)
            is LinearLayoutManager -> onDrawOverForLinear(c, parent, layoutManager, adapter)
        }
    }

    /**
     * 绘制网格布局分割线.
     */
    private fun onDrawOverForGrid(
        canvas: Canvas,
        parent: RecyclerView,
        layoutManager: GridLayoutManager,
    ) {
        if (mainAxisDivider == null && crossAxisDivider == null) return

        val spanCount: Int = layoutManager.spanCount
        val lookup: GridLayoutManager.SpanSizeLookup = layoutManager.spanSizeLookup
        var lastMainAxisDividerGroup: Int = -1
        for (i: Int in 0 until parent.childCount) {
            val child: View = parent.getChildAt(i)
            val position: Int = parent.getChildAdapterPosition(child)
            if (position == RecyclerView.NO_POSITION) continue

            val spanGroupIndex: Int = lookup.getSpanGroupIndex(position, spanCount)
            if (mainAxisDivider != null && spanGroupIndex > 0 && spanGroupIndex != lastMainAxisDividerGroup) {
                lastMainAxisDividerGroup = spanGroupIndex
                drawGridMainAxisDivider(canvas, parent, layoutManager, child)
            }

            val spanIndex: Int = lookup.getSpanIndex(position, spanCount)
            if (crossAxisDivider != null && spanIndex > 0) {
                drawGridCrossAxisDivider(canvas, parent, layoutManager, child)
            }
        }
    }

    /**
     * 绘制网格主轴分割线.
     */
    private fun drawGridMainAxisDivider(
        canvas: Canvas,
        parent: RecyclerView,
        layoutManager: GridLayoutManager,
        child: View,
    ) {
        val divider: Drawable = mainAxisDivider ?: return
        val mainAxisReversed: Boolean = isMainAxisReversed(layoutManager, parent)
        if (layoutManager.orientation == RecyclerView.HORIZONTAL) {
            val height: Int = parent.height - parent.paddingTop - parent.paddingBottom -
                mainAxisDividerMarginStart - mainAxisDividerMarginEnd
            divider.setBounds(0, 0, mainAxisSpace, height.coerceAtLeast(0))
            drawTranslated(canvas, divider) {
                val x: Int = if (mainAxisReversed) child.right else child.left - mainAxisSpace
                translate(
                    translatedX(child, x).toFloat(),
                    (parent.paddingTop + mainAxisDividerMarginStart).toFloat(),
                )
            }
        } else {
            val width: Int = parent.width - parent.paddingLeft - parent.paddingRight -
                mainAxisDividerMarginStart - mainAxisDividerMarginEnd
            divider.setBounds(0, 0, width.coerceAtLeast(0), mainAxisSpace)
            drawTranslated(canvas, divider) {
                val y: Int = if (mainAxisReversed) child.bottom else child.top - mainAxisSpace
                translate(
                    mainAxisDividerLeft(parent).toFloat(),
                    translatedY(child, y).toFloat(),
                )
            }
        }
    }

    /**
     * 绘制网格交叉轴分割线.
     */
    private fun drawGridCrossAxisDivider(
        canvas: Canvas,
        parent: RecyclerView,
        layoutManager: GridLayoutManager,
        child: View,
    ) {
        val divider: Drawable = crossAxisDivider ?: return
        val mainAxisReversed: Boolean = isMainAxisReversed(layoutManager, parent)
        if (layoutManager.orientation == RecyclerView.HORIZONTAL) {
            val width: Int = child.width - crossAxisDividerMarginStart - crossAxisDividerMarginEnd
            divider.setBounds(0, 0, width.coerceAtLeast(0), crossAxisSpace)
            drawTranslated(canvas, divider) {
                val x: Int = if (mainAxisReversed) {
                    child.right - width - crossAxisDividerMarginStart
                } else {
                    child.left + crossAxisDividerMarginStart
                }
                val y: Int = child.top - crossAxisSpace
                translate(translatedX(child, x).toFloat(), translatedY(child, y).toFloat())
            }
        } else {
            val height: Int = child.height - crossAxisDividerMarginStart - crossAxisDividerMarginEnd
            divider.setBounds(0, 0, crossAxisSpace, height.coerceAtLeast(0))
            drawTranslated(canvas, divider) {
                val y: Int = if (mainAxisReversed) {
                    child.bottom - height - crossAxisDividerMarginStart
                } else {
                    child.top + crossAxisDividerMarginStart
                }
                val x: Int = if (isRtl(parent)) child.right else child.left - crossAxisSpace
                translate(translatedX(child, x).toFloat(), translatedY(child, y).toFloat())
            }
        }
    }

    /**
     * 绘制线性布局分割线.
     */
    private fun onDrawOverForLinear(
        canvas: Canvas,
        parent: RecyclerView,
        layoutManager: LinearLayoutManager,
        adapter: RecyclerView.Adapter<*>,
    ) {
        val divider: Drawable = mainAxisDivider ?: return
        val mainAxisReversed: Boolean = isMainAxisReversed(layoutManager, parent)
        for (i: Int in 0 until parent.childCount) {
            val child: View = parent.getChildAt(i)
            val position: Int = parent.getChildAdapterPosition(child)
            if (position == RecyclerView.NO_POSITION || position == adapter.itemCount - 1) continue

            if (layoutManager.orientation == RecyclerView.HORIZONTAL) {
                val height: Int = parent.height - parent.paddingTop - parent.paddingBottom -
                    mainAxisDividerMarginStart - mainAxisDividerMarginEnd
                divider.setBounds(0, 0, mainAxisSpace, height.coerceAtLeast(0))
                drawTranslated(canvas, divider) {
                    val x: Int = if (mainAxisReversed) child.left - mainAxisSpace else child.right
                    translate(
                        translatedX(child, x).toFloat(),
                        (parent.paddingTop + mainAxisDividerMarginStart).toFloat(),
                    )
                }
            } else {
                val width: Int = parent.width - parent.paddingLeft - parent.paddingRight -
                    mainAxisDividerMarginStart - mainAxisDividerMarginEnd
                divider.setBounds(0, 0, width.coerceAtLeast(0), mainAxisSpace)
                drawTranslated(canvas, divider) {
                    val y: Int = if (mainAxisReversed) child.top - mainAxisSpace else child.bottom
                    translate(
                        mainAxisDividerLeft(parent).toFloat(),
                        translatedY(child, y).toFloat(),
                    )
                }
            }
        }
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

    /**
     * 计算垂直布局主轴分割线在物理 x 轴上的起点.
     */
    private fun mainAxisDividerLeft(parent: RecyclerView): Int =
        if (isRtl(parent)) {
            parent.paddingLeft + mainAxisDividerMarginEnd
        } else {
            parent.paddingLeft + mainAxisDividerMarginStart
        }

    /**
     * 计算跟随 item 横向动画位移后的绘制坐标.
     */
    private fun translatedX(child: View, x: Int): Int =
        x + child.translationX.roundToInt()

    /**
     * 计算跟随 item 纵向动画位移后的绘制坐标.
     */
    private fun translatedY(child: View, y: Int): Int =
        y + child.translationY.roundToInt()
}
