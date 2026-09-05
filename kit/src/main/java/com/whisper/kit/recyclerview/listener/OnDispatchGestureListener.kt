package com.whisper.kit.recyclerview.listener

import android.graphics.Matrix
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible

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
    private val filter: GestureTargetFilter?,
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
     * [view] 是否满足内部过滤条件, 形成简化版冒泡语义. 被过滤但仍会处理标准指针事件的
     * 前景 View 会阻断后方兄弟节点, 避免点击穿透. 该判断只依据公开的标准 View 标志,
     * 不探测 OnTouchListener 或 TouchDelegate 的自定义分发结果.
     *
     * @param view 当前搜索根 View.
     * @param localX [view] 本地坐标系中的 x 坐标.
     * @param localY [view] 本地坐标系中的 y 坐标.
     * @return 命中的最深层目标 View, 没有命中时返回 `null`.
     */
    protected fun findViewOnPoint(view: View, localX: Float, localY: Float): View? {
        return when (val result: ViewHitResult = findViewHitResult(view, localX, localY)) {
            is ViewHitResult.Target -> result.view
            ViewHitResult.Blocked,
            ViewHitResult.Miss,
            -> null
        }
    }

    /**
     * 判断 [view] 当前是否仍满足目标过滤条件.
     */
    protected fun isViewTarget(view: View): Boolean =
        filter?.filter(view) != false

    /**
     * 判断 [ancestor] 坐标系中的点是否仍位于 [descendant] 的本地边界内.
     *
     * 坐标只沿既有父链逐层逆变换, 不重新扫描或选择其它目标. [descendant] 已不属于
     * [ancestor] 或任一矩阵不可逆时返回 `false`.
     */
    protected fun isPointInsideDescendant(
        ancestor: ViewGroup,
        descendant: View,
        ancestorLocalX: Float,
        ancestorLocalY: Float,
        touchSlop: Float,
    ): Boolean {
        if (!transformPointToDescendantLocal(
                ancestor,
                descendant,
                ancestorLocalX,
                ancestorLocalY,
            )
        ) {
            return false
        }
        return pointInView(descendant, tempPoint[0], tempPoint[1], touchSlop)
    }

    /**
     * 查找命中目标, 同时保留前景 View 阻断后方目标的语义.
     */
    private fun findViewHitResult(view: View, localX: Float, localY: Float): ViewHitResult {
        if (view is ViewGroup) {
            val orderedChildren: ArrayList<View> = buildOrderedChildren(view)
            for (i: Int in orderedChildren.lastIndex downTo 0) {
                val child: View = orderedChildren[i]
                if (!child.canReceivePointerEvents()) continue

                transformPointToChildLocal(view, child, localX, localY)
                if (pointInView(child, tempPoint[0], tempPoint[1])) {
                    val childLocalX: Float = tempPoint[0]
                    val childLocalY: Float = tempPoint[1]
                    when (val result: ViewHitResult = findViewHitResult(child, childLocalX, childLocalY)) {
                        is ViewHitResult.Target,
                        ViewHitResult.Blocked,
                        -> return result

                        ViewHitResult.Miss -> Unit
                    }
                }
            }
        }

        if (!pointInView(view, localX, localY)) return ViewHitResult.Miss
        if (isViewTarget(view)) return ViewHitResult.Target(view)

        return if (view.blocksLowerSiblingTarget()) {
            ViewHitResult.Blocked
        } else {
            ViewHitResult.Miss
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
     * 将祖先坐标沿当前父链转换到后代 View 的本地坐标.
     */
    private fun transformPointToDescendantLocal(
        ancestor: ViewGroup,
        descendant: View,
        ancestorLocalX: Float,
        ancestorLocalY: Float,
    ): Boolean {
        val parent: ViewGroup = descendant.parent as? ViewGroup ?: return false
        if (parent === ancestor) {
            transformPointToChildLocal(parent, descendant, ancestorLocalX, ancestorLocalY)
        } else {
            if (!transformPointToDescendantLocal(
                    ancestor,
                    parent,
                    ancestorLocalX,
                    ancestorLocalY,
                )
            ) {
                return false
            }
            val parentLocalX: Float = tempPoint[0]
            val parentLocalY: Float = tempPoint[1]
            transformPointToChildLocal(parent, descendant, parentLocalX, parentLocalY)
        }
        return tempPoint[0].isFinite() && tempPoint[1].isFinite()
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
        isVisible || animation != null

    /**
     * 判断被过滤的 View 是否仍会按标准 View 触摸语义阻断后方兄弟节点.
     */
    private fun View.blocksLowerSiblingTarget(): Boolean =
        isClickable || isLongClickable || isContextClickable

    /**
     * 判断本地坐标是否落在 View 原始内容矩形内.
     */
    private fun pointInView(
        view: View,
        localX: Float,
        localY: Float,
        touchSlop: Float = 0f,
    ): Boolean =
        localX >= -touchSlop
            && localY >= -touchSlop
            && localX < view.width.toFloat() + touchSlop
            && localY < view.height.toFloat() + touchSlop
}

/**
 * View 命中递归的内部结果.
 */
private sealed interface ViewHitResult {

    /**
     * 找到可回调目标.
     */
    data class Target(val view: View) : ViewHitResult

    /**
     * 前景 View 会处理指针, 但不满足当前目标过滤条件.
     */
    data object Blocked : ViewHitResult

    /**
     * 当前子树未命中目标且不阻断后方兄弟节点.
     */
    data object Miss : ViewHitResult
}
