package com.whisper.kit.recyclerview.listener

import android.graphics.Matrix
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.View
import android.view.ViewGroup


/**
 *
 *
 * Created by whisper on 2024/11/19
 */
abstract class OnDispatchGestureListener(
    private val _filter: ItemViewFilter?
) : SimpleOnGestureListener() {

    private val _tempMatrix = Matrix()
    private val _tempPoint = FloatArray(2)

    /**
     * 在 [view] 的本地坐标系中, 查找 [localX], [localY] 命中的最深层子 View
     */
    protected fun findViewOnPoint(view: View, localX: Float, localY: Float): View? {
        if (view is ViewGroup) {
            val orderedChildren = buildOrderedChildren(view)
            for (i in orderedChildren.lastIndex downTo 0) {
                val child = orderedChildren[i]
                if (!child.canReceivePointerEvents()) continue

                transformPointToChildLocal(view, child, localX, localY)
                if (pointInView(child, _tempPoint[0], _tempPoint[1])) {
                    val childLocalX = _tempPoint[0]
                    val childLocalY = _tempPoint[1]
                    findViewOnPoint(child, childLocalX, childLocalY)?.let { return it }
                }
            }
        }

        return if (_filter?.filter(view) != false && pointInView(view, localX, localY)) {
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
     */
    protected fun findChildViewOnPoint(viewGroup: ViewGroup, localX: Float, localY: Float): View? {
        val orderedChildren = buildOrderedChildren(viewGroup)
        for (i in orderedChildren.lastIndex downTo 0) {
            val child = orderedChildren[i]
            if (!child.canReceivePointerEvents()) continue

            transformPointToChildLocal(viewGroup, child, localX, localY)
            if (pointInView(child, _tempPoint[0], _tempPoint[1])) {
                return child
            }
        }
        return null
    }

    /**
     * 等价于 Android ViewGroup.transformPointToViewLocal:
     * parent local -> child local, 包含 scroll、left/top 和 child matrix 的逆变换.
     */
    protected fun transformPointToChildLocal(
        parent: ViewGroup,
        child: View,
        parentLocalX: Float,
        parentLocalY: Float
    ): FloatArray {
        _tempPoint[0] = parentLocalX + parent.scrollX - child.left.toFloat()
        _tempPoint[1] = parentLocalY + parent.scrollY - child.top.toFloat()

        if (!child.matrix.isIdentity) {
            if (child.matrix.invert(_tempMatrix)) {
                _tempMatrix.mapPoints(_tempPoint)
            } else {
                _tempPoint[0] = Float.NaN
                _tempPoint[1] = Float.NaN
            }
        }

        return _tempPoint
    }

    private fun buildOrderedChildren(viewGroup: ViewGroup): ArrayList<View> {
        val children = ArrayList<View>(viewGroup.childCount)
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            val childZ = child.z
            var insertIndex = children.size
            while (insertIndex > 0 && children[insertIndex - 1].z > childZ) {
                insertIndex--
            }
            children.add(insertIndex, child)
        }
        return children
    }

    private fun View.canReceivePointerEvents(): Boolean =
        visibility == View.VISIBLE || animation != null

    private fun pointInView(view: View, localX: Float, localY: Float): Boolean =
        localX >= 0f
            && localY >= 0f
            && localX < view.width.toFloat()
            && localY < view.height.toFloat()

}
