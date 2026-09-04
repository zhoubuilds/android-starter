package com.whisper.kit.recyclerview.decoration

import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.roundToInt

/**
 * 将绘制坐标饱和到 Int 可表示范围, 避免极大合法尺寸或边距使坐标回绕.
 */
internal fun saturatedDrawingCoordinate(value: Long): Int =
    value.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()

/**
 * 将绘制长度限定在非负 Int 范围, 避免多个合法边距相减时回绕为正数.
 */
internal fun nonNegativeDrawingSize(value: Long): Int =
    value.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

/**
 * 返回 RecyclerView 当前已布局 item 经过 translation 后的实际绘制区域.
 */
internal fun translatedRecyclerViewChildBounds(
    parent: RecyclerView,
    orientation: Int,
): List<Rect> {
    val result: ArrayList<Rect> = ArrayList(parent.childCount)
    for (i: Int in 0 until parent.childCount) {
        val child: View = parent.getChildAt(i)
        val translationX: Int = child.translationX.roundToInt()
        val translationY: Int = child.translationY.roundToInt()
        result += Rect(
            saturatedDrawingCoordinate(child.left.toLong() + translationX),
            saturatedDrawingCoordinate(child.top.toLong() + translationY),
            saturatedDrawingCoordinate(child.right.toLong() + translationX),
            saturatedDrawingCoordinate(child.bottom.toLong() + translationY),
        )
    }
    result.sortBy { bounds: Rect ->
        if (orientation == RecyclerView.HORIZONTAL) bounds.left else bounds.top
    }
    return result
}

/**
 * 绘制排除 item 实际区域后的分割线.
 *
 * 分割线保持完整 bounds, 按主轴裁剪可见分段, 并跳过与任一 item 实际区域相交的区间,
 * 避免依赖不同 Android 版本的 Canvas 差集裁剪实现. [childBounds] 必须按主轴起点升序排列.
 */
internal fun drawDividerOutsideChildren(
    canvas: Canvas,
    divider: Drawable,
    left: Int,
    top: Int,
    right: Int,
    bottom: Int,
    orientation: Int,
    childBounds: List<Rect>,
) {
    if (left >= right || top >= bottom) return

    val mainAxisEnd: Int = if (orientation == RecyclerView.HORIZONTAL) right else bottom
    var visibleStart: Int = if (orientation == RecyclerView.HORIZONTAL) left else top
    for (childBound: Rect in childBounds) {
        val intersectsDivider: Boolean = childBound.left < right && childBound.right > left &&
            childBound.top < bottom && childBound.bottom > top
        if (!intersectsDivider) continue

        val childStart: Int = if (orientation == RecyclerView.HORIZONTAL) {
            childBound.left
        } else {
            childBound.top
        }
        val childEnd: Int = if (orientation == RecyclerView.HORIZONTAL) {
            childBound.right
        } else {
            childBound.bottom
        }
        if (childEnd <= visibleStart) continue
        if (childStart > visibleStart) {
            drawDividerSegment(
                canvas = canvas,
                divider = divider,
                left = left,
                top = top,
                right = right,
                bottom = bottom,
                orientation = orientation,
                start = visibleStart,
                end = minOf(childStart, mainAxisEnd),
            )
        }
        visibleStart = maxOf(visibleStart, childEnd)
        if (visibleStart >= mainAxisEnd) return
    }

    drawDividerSegment(
        canvas = canvas,
        divider = divider,
        left = left,
        top = top,
        right = right,
        bottom = bottom,
        orientation = orientation,
        start = visibleStart,
        end = mainAxisEnd,
    )
}

/**
 * 绘制分割线在主轴上的一个非空分段.
 */
private fun drawDividerSegment(
    canvas: Canvas,
    divider: Drawable,
    left: Int,
    top: Int,
    right: Int,
    bottom: Int,
    orientation: Int,
    start: Int,
    end: Int,
) {
    if (start >= end) return
    divider.setBounds(left, top, right, bottom)
    val saveCount: Int = canvas.save()
    try {
        if (orientation == RecyclerView.HORIZONTAL) {
            canvas.clipRect(start, top, end, bottom)
        } else {
            canvas.clipRect(left, start, right, end)
        }
        divider.draw(canvas)
    } finally {
        canvas.restoreToCount(saveCount)
    }
}
