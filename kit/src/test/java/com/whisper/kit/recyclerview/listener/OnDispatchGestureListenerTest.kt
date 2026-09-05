package com.whisper.kit.recyclerview.listener

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 验证 RecyclerView 手势分发使用的 View 命中算法.
 *
 * @author whisper
 * @since 2026/09/04
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OnDispatchGestureListenerTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val listener: TestGestureListener = TestGestureListener()

    @Test
    fun findChildViewOnPoint_whenItemHasCombinedTransform_returnsTransformedItem() {
        val root: FrameLayout = createRoot()
        val item: View = View(context).apply {
            pivotX = 18f
            pivotY = 24f
            rotation = 31f
            scaleX = 1.3f
            scaleY = 0.7f
            translationX = 17f
            translationY = -9f
        }
        addChild(root, item, left = 80, top = 90, right = 200, bottom = 170)
        val point: FloatArray = mapPointToParent(root, item, childLocalX = 36f, childLocalY = 42f)

        val target: View? = listener.findDirectChild(root, point[0], point[1])

        assertSame(item, target)
    }

    @Test
    fun findViewOnPoint_whenTransformsAreNested_returnsDeepestClickableTarget() {
        val root: FrameLayout = createRoot().apply {
            scrollTo(11, 7)
        }
        val group: FrameLayout = FrameLayout(context).apply {
            pivotX = 70f
            pivotY = 60f
            rotation = -16f
            scaleX = 1.1f
            translationX = 13f
        }
        val target: View = View(context).apply {
            isClickable = true
            pivotX = 28f
            pivotY = 22f
            rotation = 12f
            rotationY = -38f
            scaleY = 0.8f
            cameraDistance = 8_000f
        }
        addChild(root, group, left = 60, top = 75, right = 260, bottom = 255)
        addChild(group, target, left = 45, top = 50, right = 145, bottom = 120)
        val groupPoint: FloatArray = mapPointToParent(group, target, childLocalX = 30f, childLocalY = 25f)
        val rootPoint: FloatArray = mapPointToParent(root, group, groupPoint[0], groupPoint[1])

        val actual: View? = listener.findTarget(root, rootPoint[0], rootPoint[1])

        assertSame(target, actual)
    }

    @Test
    fun findViewOnPoint_whenPointIsOutsideScaledVisualBounds_doesNotUseLayoutBounds() {
        val root: FrameLayout = createRoot()
        val scaledTarget: View = View(context).apply {
            isClickable = true
            pivotX = 50f
            pivotY = 50f
            scaleX = 0.5f
            scaleY = 0.5f
        }
        addChild(root, scaledTarget, left = 50, top = 50, right = 150, bottom = 150)

        val target: View? = listener.findTarget(root, localX = 55f, localY = 100f)

        assertNull(target)
    }

    @Test
    fun findViewOnPoint_whenForegroundMatrixIsNotInvertible_skipsCollapsedView() {
        val root: FrameLayout = createRoot()
        val backgroundTarget: View = View(context).apply {
            isClickable = true
        }
        val collapsedForeground: View = View(context).apply {
            isClickable = true
            pivotX = 60f
            scaleX = 0f
        }
        addChild(root, backgroundTarget, left = 40, top = 40, right = 160, bottom = 140)
        addChild(root, collapsedForeground, left = 40, top = 40, right = 160, bottom = 140)

        val target: View? = listener.findTarget(root, localX = 80f, localY = 80f)

        assertSame(backgroundTarget, target)
    }

    @Test
    fun findViewOnPoint_whenChildrenOverlap_prefersHigherZ() {
        val root: FrameLayout = createRoot()
        val higherZ: View = View(context).apply {
            isClickable = true
            z = 8f
        }
        val laterChildWithLowerZ: View = View(context).apply {
            isClickable = true
            z = 1f
        }
        addChild(root, higherZ, left = 40, top = 40, right = 160, bottom = 140)
        addChild(root, laterChildWithLowerZ, left = 40, top = 40, right = 160, bottom = 140)

        val target: View? = listener.findTarget(root, localX = 80f, localY = 80f)

        assertSame(higherZ, target)
    }

    @Test
    fun findViewOnPoint_whenDisabledClickableViewCoversTarget_doesNotClickThrough() {
        val root: FrameLayout = createRoot()
        val backgroundTarget: View = View(context).apply {
            isClickable = true
        }
        val disabledForeground: View = View(context).apply {
            isClickable = true
            isEnabled = false
        }
        addChild(root, backgroundTarget, left = 40, top = 40, right = 160, bottom = 140)
        addChild(root, disabledForeground, left = 40, top = 40, right = 160, bottom = 140)

        val target: View? = listener.findTarget(root, localX = 80f, localY = 80f)

        assertNull(target)
    }

    @Test
    fun findViewOnPoint_whenLongClickableViewCoversClickTarget_doesNotClickThrough() {
        val root: FrameLayout = createRoot()
        val backgroundTarget: View = View(context).apply {
            isClickable = true
        }
        val longClickableForeground: View = View(context).apply {
            isLongClickable = true
        }
        addChild(root, backgroundTarget, left = 40, top = 40, right = 160, bottom = 140)
        addChild(root, longClickableForeground, left = 40, top = 40, right = 160, bottom = 140)

        val target: View? = listener.findTarget(root, localX = 80f, localY = 80f)

        assertNull(target)
    }

    @Test
    fun findViewOnPoint_whenNonClickableViewCoversTarget_allowsTargetBehindIt() {
        val root: FrameLayout = createRoot()
        val backgroundTarget: View = View(context).apply {
            isClickable = true
        }
        val passiveForeground: View = View(context)
        addChild(root, backgroundTarget, left = 40, top = 40, right = 160, bottom = 140)
        addChild(root, passiveForeground, left = 40, top = 40, right = 160, bottom = 140)

        val target: View? = listener.findTarget(root, localX = 80f, localY = 80f)

        assertSame(backgroundTarget, target)
    }

    @Test
    fun findViewOnPoint_whenNonClickableTouchConsumerCoversTarget_usesStandardFlagsOnly() {
        val root: FrameLayout = createRoot()
        val backgroundTarget: View = View(context).apply {
            isClickable = true
        }
        val customTouchForeground: View = View(context).apply {
            setOnTouchListener { _, _ -> true }
        }
        addChild(root, backgroundTarget, left = 40, top = 40, right = 160, bottom = 140)
        addChild(root, customTouchForeground, left = 40, top = 40, right = 160, bottom = 140)

        val target: View? = listener.findTarget(root, localX = 80f, localY = 80f)

        assertSame(backgroundTarget, target)
    }

    @Test
    fun isPointInsideDescendant_whenTransformsAreNested_followsCurrentParentChain() {
        val root: FrameLayout = createRoot().apply {
            scrollTo(9, 13)
        }
        val group: FrameLayout = FrameLayout(context).apply {
            pivotX = 60f
            pivotY = 50f
            rotation = -19f
            scaleX = 1.2f
        }
        val target: View = View(context).apply {
            pivotX = 35f
            pivotY = 25f
            rotationY = 32f
            scaleY = 0.75f
            cameraDistance = 8_000f
        }
        addChild(root, group, left = 55, top = 70, right = 255, bottom = 250)
        addChild(group, target, left = 40, top = 45, right = 140, bottom = 115)
        val groupPoint: FloatArray = mapPointToParent(group, target, childLocalX = 30f, childLocalY = 25f)
        val rootPoint: FloatArray = mapPointToParent(root, group, groupPoint[0], groupPoint[1])

        assertTrue(listener.isInsideDescendant(root, target, rootPoint[0], rootPoint[1]))

        target.translationX = 180f

        assertFalse(listener.isInsideDescendant(root, target, rootPoint[0], rootPoint[1]))
    }

    private fun createRoot(): FrameLayout = FrameLayout(context).apply {
        layout(0, 0, ROOT_SIZE, ROOT_SIZE)
    }

    private fun addChild(
        parent: ViewGroup,
        child: View,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) {
        parent.addView(child)
        child.layout(left, top, right, bottom)
    }

    private fun mapPointToParent(
        parent: ViewGroup,
        child: View,
        childLocalX: Float,
        childLocalY: Float,
    ): FloatArray {
        val point: FloatArray = floatArrayOf(childLocalX, childLocalY)
        if (!child.matrix.isIdentity) {
            child.matrix.mapPoints(point)
        }
        point[0] += child.left - parent.scrollX
        point[1] += child.top - parent.scrollY
        return point
    }

    private class TestGestureListener : OnDispatchGestureListener(
        filter = { view: View -> view.isClickable && view.isEnabled },
    ) {

        fun findTarget(view: View, localX: Float, localY: Float): View? =
            findViewOnPoint(view, localX, localY)

        fun findDirectChild(viewGroup: ViewGroup, localX: Float, localY: Float): View? =
            findChildViewOnPoint(viewGroup, localX, localY)

        fun isInsideDescendant(
            ancestor: ViewGroup,
            descendant: View,
            localX: Float,
            localY: Float,
        ): Boolean = isPointInsideDescendant(
            ancestor = ancestor,
            descendant = descendant,
            ancestorLocalX = localX,
            ancestorLocalY = localY,
            touchSlop = 0f,
        )
    }

    private companion object {

        private const val ROOT_SIZE: Int = 400
    }
}
