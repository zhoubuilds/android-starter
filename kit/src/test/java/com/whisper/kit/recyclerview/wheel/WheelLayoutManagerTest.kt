package com.whisper.kit.recyclerview.wheel

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 验证滚轮布局迁移后的基础公开调用形态.
 *
 * @author whisper
 * @since 2026/10/14
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WheelLayoutManagerTest {

    @Test
    fun create_withoutScrollFeature_usesLimitedVerticalLayout() {
        val layoutManager = WheelLayoutManager()

        assertEquals(WheelLayoutManager.SCROLL_FEATURE_LIMITED, layoutManager.scrollFeature)
        assertTrue(layoutManager.canScrollVertically())
        assertFalse(layoutManager.canScrollHorizontally())
        assertNull(layoutManager.findCenterChild())
        assertEquals(RecyclerView.NO_POSITION, layoutManager.findCenterChildPosition())
    }

    @Test
    fun create_withUnlimitedFeature_preservesExplicitConfiguration() {
        val layoutManager = WheelLayoutManager(WheelLayoutManager.SCROLL_FEATURE_UNLIMITED)

        assertEquals(WheelLayoutManager.SCROLL_FEATURE_UNLIMITED, layoutManager.scrollFeature)
        assertNull(layoutManager.computeScrollVectorForPosition(0))
    }

    @Test
    fun generateDefaultLayoutParams_usesWrapContentOnBothAxes() {
        val layoutParams: RecyclerView.LayoutParams = WheelLayoutManager().generateDefaultLayoutParams()

        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, layoutParams.width)
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, layoutParams.height)
    }

    @Test
    fun centerListener_supportsLambdaAndNullableRemoval() {
        val listener = WheelLayoutManager.OnCenterYPositionChangeListener { }
        val layoutManager = WheelLayoutManager()

        layoutManager.setOnCenterYPositionChangeListener(listener)
        layoutManager.setOnCenterYPositionChangeListener(null)

        assertNotNull(listener)
    }

    @Test
    fun snapHelper_withUnlimitedLayoutAndNoChildren_hasNoFlingTarget() {
        val target: Int = WheelSnapHelper().findTargetSnapPosition(
            WheelLayoutManager(WheelLayoutManager.SCROLL_FEATURE_UNLIMITED),
            0,
            1_000,
        )

        assertEquals(RecyclerView.NO_POSITION, target)
    }
}
