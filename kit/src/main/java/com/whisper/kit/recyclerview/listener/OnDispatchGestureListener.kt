package com.whisper.kit.recyclerview.listener

import android.graphics.Matrix
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.View
import android.view.ViewGroup

/**
 * 支持 View 变换的 RecyclerView 手势命中基类.
 *
 * 该类按照近似 Android ViewGroup 分发的方式, 将父容器坐标转换到子 View
 * 本地坐标后再判断命中区域, 避免对旋转、缩放、平移后的 View 使用外接矩形误判.
 *
 * @author whisper
 * @since 2026/07/30
 */
internal abstract class OnDispatchGestureListener(
    private val filter: ItemViewFilter?,
) : SimpleOnGestureListener() {

    /**
     * 坐标逆变换复用矩阵.
     */
    private val tempMatrix: Matrix = Matrix()

    /**
     * 坐标逆变换复用点位.
     */
    private val tempPoint: FloatArray = FloatArray(2)

    /**
     * 在 [view] 的本地坐标系中, 查找 [localX], [localY] 命中的最深层目标 View.
     *
     * 子 View 会优先命中视觉上更靠上的节点; 如果子树没有可点击目标, 再回退检查当前
     * [view] 是否满足内部过滤条件, 形成简化版冒泡语义.
     *
     * @param view 当前搜索根 View.
     * @param localX [view] 本地坐标系中的 x 坐标.
     * @param localY [view] 本地坐标系中的 y 坐标.
     * @return 命中的最深层目标 View, 没有命中时返回 `null`.
     */
    protected fun findViewOnPoint(view: View, localX: Float, localY: Float): View? {
        if (view is ViewGroup) {
            val orderedChildren: ArrayList<View> = buildOrderedChildren(view)
            for (i: Int in orderedChildren.lastIndex downTo 0) {
                val child: View = orderedChildren[i]
                if (!child.canReceivePointerEvents()) continue

                transformPointToChildLocal(view, child, localX, localY)
                if (pointInView(child, tempPoint[0], tempPoint[1])) {
                    val childLocalX: Float = tempPoint[0]
                    val childLocalY: Float = tempPoint[1]
                    findViewOnPoint(child, childLocalX, childLocalY)?.let { target: View ->
                        return target
                    }
                }
            }
        }

        return if (filter?.filter(view) != false && pointInView(view, localX, localY)) {
            view
        } else {
            null
        }
    }

    /**
     * 在 [viewGroup] 的本地坐标系中, 查找 [localX], [localY] 命中的最上层直接子 View.
     *
     * 命中顺序跟随常规 child order + z/elevation: 后绘制、z 更高的 View 优先.
     * 这里不读取自定义 ViewGroup 的 getChildDrawingOrder, 特殊容器如果改变了绘制顺序,
     * 命中顺序可能和它的自定义绘制顺序不完全一致.
     *
     * @param viewGroup 当前搜索的 ViewGroup.
     * @param localX [viewGroup] 本地坐标系中的 x 坐标.
     * @param localY [viewGroup] 本地坐标系中的 y 坐标.
     * @return 命中的最上层直接子 View, 没有命中时返回 `null`.
     */
    protected fun findChildViewOnPoint(viewGroup: ViewGroup, localX: Float, localY: Float): View? {
        val orderedChildren: ArrayList<View> = buildOrderedChildren(viewGroup)
        for (i: Int in orderedChildren.lastIndex downTo 0) {
            val child: View = orderedChildren[i]
            if (!child.canReceivePointerEvents()) continue

            transformPointToChildLocal(viewGroup, child, localX, localY)
            if (pointInView(child, tempPoint[0], tempPoint[1])) {
                return child
            }
        }
        return null
    }

    /**
     * 将父容器本地坐标转换到子 View 本地坐标.
     *
     * 等价于 Android ViewGroup.transformPointToViewLocal 的核心逻辑:
     * parent local -> child local, 包含 scroll、left/top 和 child matrix 的逆变换.
     *
     * @param parent 子 View 所属父容器.
     * @param child 待转换坐标的子 View.
     * @param parentLocalX [parent] 本地坐标系中的 x 坐标.
     * @param parentLocalY [parent] 本地坐标系中的 y 坐标.
     * @return [child] 本地坐标系中的复用坐标数组.
     */
    protected fun transformPointToChildLocal(
        parent: ViewGroup,
        child: View,
        parentLocalX: Float,
        parentLocalY: Float,
    ): FloatArray {
        tempPoint[0] = parentLocalX + parent.scrollX - child.left.toFloat()
        tempPoint[1] = parentLocalY + parent.scrollY - child.top.toFloat()

        if (!child.matrix.isIdentity) {
            if (child.matrix.invert(tempMatrix)) {
                tempMatrix.mapPoints(tempPoint)
            } else {
                tempPoint[0] = Float.NaN
                tempPoint[1] = Float.NaN
            }
        }

        return tempPoint
    }

    /**
     * 构建按绘制层级排序的子 View 列表.
     */
    private fun buildOrderedChildren(viewGroup: ViewGroup): ArrayList<View> {
        val children: ArrayList<View> = ArrayList(viewGroup.childCount)
        for (i: Int in 0 until viewGroup.childCount) {
            val child: View = viewGroup.getChildAt(i)
            val childZ: Float = child.z
            var insertIndex: Int = children.size
            while (insertIndex > 0 && children[insertIndex - 1].z > childZ) {
                insertIndex--
            }
            children.add(insertIndex, child)
        }
        return children
    }

    /**
     * 判断 View 是否仍可接收指针事件.
     */
    private fun View.canReceivePointerEvents(): Boolean =
        visibility == View.VISIBLE || animation != null

    /**
     * 判断本地坐标是否落在 View 原始内容矩形内.
     */
    private fun pointInView(view: View, localX: Float, localY: Float): Boolean =
        localX >= 0f
            && localY >= 0f
            && localX < view.width.toFloat()
            && localY < view.height.toFloat()
}
