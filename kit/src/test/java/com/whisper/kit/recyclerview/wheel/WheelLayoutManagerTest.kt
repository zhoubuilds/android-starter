package com.whisper.kit.recyclerview.wheel

import android.app.Activity
import android.graphics.Rect
import android.os.Looper
import android.os.Parcel
import android.os.Parcelable
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.lang.reflect.Modifier
import java.util.concurrent.TimeUnit

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
    fun create_withInvalidScrollFeature_throws() {
        listOf(-1, 1, 3, Int.MAX_VALUE).forEach { scrollFeature: Int ->
            assertThrows(IllegalArgumentException::class.java) {
                WheelLayoutManager(scrollFeature)
            }
        }
    }

    @Test
    fun create_doesNotClaimPredictiveItemAnimationSupport() {
        val layoutManager = WheelLayoutManager()

        assertFalse(layoutManager.supportsPredictiveItemAnimations())
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
    fun centerListener_whenScrollingAcrossItems_notifiesChangedCentersWithoutDuplicates() {
        val fixture: Fixture = createFixture(itemCount = 20)
        val centerChanges: MutableList<Int> = mutableListOf()
        fixture.layoutManager.setOnCenterYPositionChangeListener(centerChanges::add)

        repeat(3) {
            fixture.recyclerView.scrollBy(0, ITEM_HEIGHT)
        }
        fixture.recyclerView.scrollBy(0, ITEM_HEIGHT / 2)

        assertEquals(listOf(1, 2, 3), centerChanges)

        fixture.recyclerView.scrollBy(0, ITEM_HEIGHT / 2)

        assertEquals(listOf(1, 2, 3, 4), centerChanges)
    }

    @Test
    fun accessibilityScrollActions_atBoundedEdges_followScrollableRange() {
        val fixture: Fixture = createFixture(
            itemCount = 20,
            scrollFeature = WheelLayoutManager.SCROLL_FEATURE_LIMITED,
        )
        val activityController = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            activityController.get().setContentView(fixture.recyclerView)
            layout(fixture)

            assertFalse(fixture.recyclerView.canScrollVertically(-1))
            assertFalse(
                fixture.recyclerView.performAccessibilityAction(
                    AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD,
                    null,
                ),
            )
            assertTrue(fixture.recyclerView.canScrollVertically(1))
            assertTrue(
                fixture.recyclerView.performAccessibilityAction(
                    AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,
                    null,
                ),
            )
            shadowOf(Looper.getMainLooper()).idleFor(5L, TimeUnit.SECONDS)
            assertTrue(fixture.layoutManager.findCenterChildPosition() > 0)

            fixture.recyclerView.scrollBy(0, 10_000)
            assertFalse(fixture.recyclerView.canScrollVertically(1))
            assertFalse(
                fixture.recyclerView.performAccessibilityAction(
                    AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,
                    null,
                ),
            )
            assertTrue(fixture.recyclerView.canScrollVertically(-1))
            assertTrue(
                fixture.recyclerView.performAccessibilityAction(
                    AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD,
                    null,
                ),
            )
            shadowOf(Looper.getMainLooper()).idleFor(5L, TimeUnit.SECONDS)
            assertTrue(fixture.layoutManager.findCenterChildPosition() < fixture.adapter.itemCount - 1)
        } finally {
            activityController.close()
        }
    }

    @Test
    fun accessibilityScrollActions_inLoopingMode_keepBothDirectionsAvailable() {
        val fixture: Fixture = createFixture(
            itemCount = 20,
            scrollFeature = WheelLayoutManager.SCROLL_FEATURE_UNLIMITED,
        )
        val activityController = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            activityController.get().setContentView(fixture.recyclerView)
            layout(fixture)
            val initialCenterPosition: Int = fixture.layoutManager.findCenterChildPosition()

            assertTrue(fixture.layoutManager.isLoopingActive)
            assertTrue(fixture.recyclerView.canScrollVertically(-1))
            assertTrue(fixture.recyclerView.canScrollVertically(1))
            assertTrue(
                fixture.recyclerView.performAccessibilityAction(
                    AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,
                    null,
                ),
            )
            shadowOf(Looper.getMainLooper()).idleFor(5L, TimeUnit.SECONDS)

            val forwardCenterPosition: Int = fixture.layoutManager.findCenterChildPosition()
            assertNotEquals(initialCenterPosition, forwardCenterPosition)
            assertTrue(fixture.recyclerView.canScrollVertically(-1))
            assertTrue(fixture.recyclerView.canScrollVertically(1))
            assertUniqueAttachedPositions(fixture)
            assertTrue(
                fixture.recyclerView.performAccessibilityAction(
                    AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD,
                    null,
                ),
            )
            shadowOf(Looper.getMainLooper()).idleFor(5L, TimeUnit.SECONDS)

            assertNotEquals(forwardCenterPosition, fixture.layoutManager.findCenterChildPosition())
            assertTrue(fixture.layoutManager.isLoopingActive)
            assertUniqueAttachedPositions(fixture)
        } finally {
            activityController.close()
        }
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

    @Test
    fun unlimitedLayout_withInsufficientItems_fallsBackWithoutDuplicatePositions() {
        val fixture: Fixture = createFixture(itemCount = 3)

        assertFalse(fixture.layoutManager.isLoopingActive)
        assertEquals(listOf(0, 1, 2), attachedPositions(fixture))

        fixture.recyclerView.scrollBy(0, 10_000)

        assertEquals(2, fixture.layoutManager.findCenterChildPosition())
        assertUniqueAttachedPositions(fixture)
    }

    @Test
    fun unlimitedLayout_withFewLargeItems_usesMeasuredCoverageInsteadOfItemCount() {
        val fixture: Fixture = createFixture(
            itemCount = 3,
            itemHeight = 250,
        )

        assertTrue(fixture.layoutManager.isLoopingActive)
        assertEquals(setOf(0, 1, 2), attachedPositions(fixture).toSet())
        assertUniqueAttachedPositions(fixture)

        fixture.recyclerView.scrollBy(0, -250)

        assertEquals(2, fixture.layoutManager.findCenterChildPosition())
        assertTrue(fixture.layoutManager.isLoopingActive)
        assertUniqueAttachedPositions(fixture)
    }

    @Test
    fun unlimitedLayout_withZeroHeightItems_finishesAsBoundedLayout() {
        val fixture: Fixture = createFixture(
            itemCount = 20,
            itemHeight = 0,
        )

        assertFalse(fixture.layoutManager.isLoopingActive)
        assertEquals(20, fixture.layoutManager.childCount)
        assertUniqueAttachedPositions(fixture)

        fixture.recyclerView.scrollBy(0, 10_000)

        assertFalse(fixture.layoutManager.isLoopingActive)
        assertUniqueAttachedPositions(fixture)
    }

    @Test
    fun unlimitedLayout_withMixedZeroHeightItems_usesActualCoverage() {
        val fixture: Fixture = createFixture(
            itemHeights = listOf(0, 250, 250),
        )

        assertTrue(fixture.layoutManager.isLoopingActive)
        assertUniqueAttachedPositions(fixture)

        fixture.recyclerView.scrollBy(0, -250)

        assertTrue(fixture.layoutManager.isLoopingActive)
        assertUniqueAttachedPositions(fixture)
    }

    @Test
    fun unlimitedLayout_withSingleItem_keepsOneCenteredChild() {
        val fixture: Fixture = createFixture(itemCount = 1)
        val child: View = checkNotNull(fixture.layoutManager.getChildAt(0))

        assertFalse(fixture.layoutManager.isLoopingActive)
        assertEquals(listOf(0), attachedPositions(fixture))
        assertEquals(
            fixture.recyclerView.height / 2,
            fixture.layoutManager.getDecoratedTop(child) +
                fixture.layoutManager.getDecoratedMeasuredHeight(child) / 2,
        )

        fixture.recyclerView.scrollBy(0, 10_000)

        assertEquals(0, fixture.layoutManager.findCenterChildPosition())
        assertEquals(listOf(0), attachedPositions(fixture))
    }

    @Test
    fun initialLayout_withDifferentVerticalPadding_usesPaddedContentCenter() {
        val paddingCases: List<Rect> = listOf(
            Rect(0, 0, 0, 0),
            Rect(0, 20, 0, 20),
            Rect(0, 20, 0, 0),
            Rect(0, 20, 0, 40),
        )

        paddingCases.forEach { padding: Rect ->
            val fixture: Fixture = createFixture(
                itemCount = 8,
                scrollFeature = WheelLayoutManager.SCROLL_FEATURE_LIMITED,
                padding = padding,
                layoutHeight = 401,
            )
            val centerChild: View = checkNotNull(fixture.layoutManager.findCenterChild())
            val expectedCenterY: Int = padding.top +
                (fixture.recyclerView.height - padding.top - padding.bottom) / 2

            assertEquals(expectedCenterY, decoratedCenterY(fixture.layoutManager, centerChild))
            assertEquals(0, fixture.layoutManager.distanceToCenter(centerChild))
            assertArrayEquals(
                intArrayOf(0, 0),
                WheelSnapHelper().calculateDistanceToFinalSnap(fixture.layoutManager, centerChild),
            )
        }
    }

    @Test
    fun scrollToPosition_withAsymmetricPadding_usesSameCenterAsSnapHelper() {
        val fixture: Fixture = createFixture(
            itemCount = 8,
            scrollFeature = WheelLayoutManager.SCROLL_FEATURE_LIMITED,
            padding = Rect(0, 20, 0, 40),
        )

        fixture.layoutManager.scrollToPosition(4)
        layout(fixture)

        val centerChild: View = checkNotNull(fixture.layoutManager.findCenterChild())
        assertEquals(4, fixture.layoutManager.getPosition(centerChild))
        assertEquals(0, fixture.layoutManager.distanceToCenter(centerChild))
        assertArrayEquals(
            intArrayOf(0, 0),
            WheelSnapHelper().calculateDistanceToFinalSnap(fixture.layoutManager, centerChild),
        )
    }

    @Test
    fun scrollToPosition_withOutOfBoundsTarget_preservesCurrentCenter() {
        val fixture: Fixture = createFixture(
            itemCount = 8,
            scrollFeature = WheelLayoutManager.SCROLL_FEATURE_LIMITED,
        )
        fixture.layoutManager.scrollToPosition(3)
        layout(fixture)

        fixture.layoutManager.scrollToPosition(8)
        layout(fixture)

        assertEquals(3, fixture.layoutManager.findCenterChildPosition())
        assertEquals(
            0,
            fixture.layoutManager.distanceToCenter(
                checkNotNull(fixture.layoutManager.findCenterChild()),
            ),
        )
    }

    @Test
    fun smoothScrollToPosition_withAsymmetricPadding_usesSharedCenterDistance() {
        val fixture: Fixture = createFixture(
            itemCount = 12,
            scrollFeature = WheelLayoutManager.SCROLL_FEATURE_LIMITED,
            padding = Rect(0, 20, 0, 40),
        )
        val activityController = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            activityController.get().setContentView(fixture.recyclerView)
            layout(fixture)

            fixture.recyclerView.smoothScrollToPosition(7)
            shadowOf(Looper.getMainLooper()).idleFor(5L, TimeUnit.SECONDS)

            val centerChild: View = checkNotNull(fixture.layoutManager.findCenterChild())
            assertEquals(7, fixture.layoutManager.getPosition(centerChild))
            assertEquals(0, fixture.layoutManager.distanceToCenter(centerChild))
        } finally {
            activityController.close()
        }
    }

    @Test
    fun smoothScrollToPosition_withNegativeTarget_throws() {
        val fixture: Fixture = createFixture(
            itemCount = 20,
            scrollFeature = WheelLayoutManager.SCROLL_FEATURE_LIMITED,
        )

        listOf(RecyclerView.NO_POSITION, -2, Int.MIN_VALUE).forEach { position: Int ->
            assertThrows(IllegalArgumentException::class.java) {
                fixture.recyclerView.smoothScrollToPosition(position)
            }
        }
    }

    @Test
    fun smoothScrollToPosition_withEmptyAdapter_doesNotRetainTarget() {
        val fixture: Fixture = createFixture(itemCount = 0)

        fixture.recyclerView.smoothScrollToPosition(Int.MAX_VALUE)
        fixture.adapter.appendItems(20, ITEM_HEIGHT)
        layout(fixture)

        assertEquals(0, fixture.layoutManager.findCenterChildPosition())
    }

    @Test
    fun smoothScrollToPosition_withTargetAboveAdapterRange_usesLastRealPosition() {
        listOf(
            WheelLayoutManager.SCROLL_FEATURE_LIMITED,
            WheelLayoutManager.SCROLL_FEATURE_UNLIMITED,
        ).forEach { scrollFeature: Int ->
            val fixture: Fixture = createFixture(
                itemCount = 20,
                scrollFeature = scrollFeature,
            )
            val activityController = Robolectric.buildActivity(Activity::class.java).setup()
            try {
                activityController.get().setContentView(fixture.recyclerView)
                layout(fixture)
                if (scrollFeature == WheelLayoutManager.SCROLL_FEATURE_UNLIMITED) {
                    assertTrue(fixture.layoutManager.isLoopingActive)
                    assertEquals(
                        -1f,
                        fixture.layoutManager.computeScrollVectorForPosition(19)?.y,
                    )
                }

                fixture.recyclerView.smoothScrollToPosition(Int.MAX_VALUE)
                shadowOf(Looper.getMainLooper()).idleFor(5L, TimeUnit.SECONDS)

                val centerChild: View = checkNotNull(fixture.layoutManager.findCenterChild())
                assertEquals(19, fixture.layoutManager.getPosition(centerChild))
                assertEquals(0, fixture.layoutManager.distanceToCenter(centerChild))
            } finally {
                activityController.close()
            }
        }
    }

    @Test
    fun computeScrollVectorForPosition_withInvalidTarget_returnsNull() {
        val fixture: Fixture = createFixture(itemCount = 20)

        assertNull(fixture.layoutManager.computeScrollVectorForPosition(RecyclerView.NO_POSITION))
        assertNull(fixture.layoutManager.computeScrollVectorForPosition(20))
        assertNull(fixture.layoutManager.computeScrollVectorForPosition(Int.MAX_VALUE))
    }

    @Test
    fun boundedScrollMetrics_atFirstPosition_reportOnlyForwardScrolling() {
        val fixture: Fixture = createFixture(
            itemCount = 20,
            scrollFeature = WheelLayoutManager.SCROLL_FEATURE_LIMITED,
        )

        assertEquals(0, fixture.recyclerView.computeVerticalScrollOffset())
        assertEquals(400, fixture.recyclerView.computeVerticalScrollExtent())
        assertScrollMetricsValid(fixture)
        assertFalse(fixture.recyclerView.canScrollVertically(-1))
        assertTrue(fixture.recyclerView.canScrollVertically(1))

        fixture.recyclerView.scrollBy(0, 1)

        assertTrue(fixture.recyclerView.canScrollVertically(-1))
        assertTrue(fixture.recyclerView.canScrollVertically(1))
    }

    @Test
    fun boundedScrollMetrics_beforeLastPosition_keepBothDirectionsScrollable() {
        val fixture: Fixture = createFixture(
            itemCount = 20,
            scrollFeature = WheelLayoutManager.SCROLL_FEATURE_LIMITED,
        )

        fixture.layoutManager.scrollToPosition(18)
        layout(fixture)

        assertScrollMetricsValid(fixture)
        assertTrue(fixture.recyclerView.canScrollVertically(-1))
        assertTrue(fixture.recyclerView.canScrollVertically(1))
    }

    @Test
    fun boundedScrollMetrics_atLastPosition_reportOnlyBackwardScrolling() {
        val fixture: Fixture = createFixture(
            itemCount = 20,
            scrollFeature = WheelLayoutManager.SCROLL_FEATURE_LIMITED,
        )

        fixture.layoutManager.scrollToPosition(19)
        layout(fixture)

        assertScrollMetricsValid(fixture)
        assertTrue(fixture.recyclerView.canScrollVertically(-1))
        assertFalse(fixture.recyclerView.canScrollVertically(1))

        fixture.recyclerView.scrollBy(0, -1)

        assertTrue(fixture.recyclerView.canScrollVertically(-1))
        assertTrue(fixture.recyclerView.canScrollVertically(1))
    }

    @Test
    fun loopingScrollMetrics_acrossAdapterSeam_remainStableAndScrollable() {
        val fixture: Fixture = createFixture(itemCount = 20)
        fixture.layoutManager.scrollToPosition(19)
        layout(fixture)
        assertTrue(fixture.layoutManager.isLoopingActive)
        val metricsBeforeSeam: IntArray = verticalScrollMetrics(fixture)

        fixture.recyclerView.scrollBy(0, ITEM_HEIGHT)

        assertEquals(0, fixture.layoutManager.findCenterChildPosition())
        assertArrayEquals(metricsBeforeSeam, verticalScrollMetrics(fixture))
        assertScrollMetricsValid(fixture)
        assertTrue(fixture.recyclerView.canScrollVertically(-1))
        assertTrue(fixture.recyclerView.canScrollVertically(1))
    }

    @Test
    fun requestedLoopingScrollMetrics_afterFallback_useBoundedEdges() {
        val fixture: Fixture = createFixture(itemCount = 3)
        assertFalse(fixture.layoutManager.isLoopingActive)

        assertEquals(0, fixture.recyclerView.computeVerticalScrollOffset())
        assertScrollMetricsValid(fixture)
        assertFalse(fixture.recyclerView.canScrollVertically(-1))
        assertTrue(fixture.recyclerView.canScrollVertically(1))

        fixture.layoutManager.scrollToPosition(2)
        layout(fixture)

        assertScrollMetricsValid(fixture)
        assertTrue(fixture.recyclerView.canScrollVertically(-1))
        assertFalse(fixture.recyclerView.canScrollVertically(1))
    }

    @Test
    fun loopingScrollMetrics_duringRuntimeFallback_remainStableUntilBoundedRelayout() {
        val fixture: Fixture = createFixture(
            itemHeights = listOf(111, 22),
            layoutHeight = 87,
        )
        assertTrue(fixture.layoutManager.isLoopingActive)
        val loopingMetrics: IntArray = verticalScrollMetrics(fixture)

        fixture.recyclerView.scrollBy(0, -34)
        assertTrue(fixture.layoutManager.isLoopingActive)
        fixture.recyclerView.scrollBy(0, -43)

        assertFalse(fixture.layoutManager.isLoopingActive)
        assertEquals(1, fixture.layoutManager.findCenterChildPosition())
        assertArrayEquals(loopingMetrics, verticalScrollMetrics(fixture))
        assertTrue(fixture.recyclerView.canScrollVertically(-1))
        assertTrue(fixture.recyclerView.canScrollVertically(1))
        val pendingLayoutPositions: List<Int> = attachedPositions(fixture)

        fixture.recyclerView.scrollBy(0, -1)

        assertEquals(pendingLayoutPositions, attachedPositions(fixture))
        assertUniqueAttachedPositions(fixture)
        assertEquals(1, fixture.layoutManager.findCenterChildPosition())
        assertArrayEquals(loopingMetrics, verticalScrollMetrics(fixture))

        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(fixture.recyclerView.isLayoutRequested)
        layout(fixture)

        assertFalse(fixture.layoutManager.isLoopingActive)
        assertEquals(1, fixture.layoutManager.findCenterChildPosition())
        assertScrollMetricsValid(fixture)
        assertTrue(fixture.recyclerView.canScrollVertically(-1))
        assertFalse(fixture.recyclerView.canScrollVertically(1))
    }

    @Test
    fun boundedScrollMetrics_withMixedItemGeometry_preserveEdgesAndNonNegativeValues() {
        val fixture: Fixture = createFixture(
            itemHeights = listOf(0, 20, 0, 60, 40, 0, 80, 0),
            scrollFeature = WheelLayoutManager.SCROLL_FEATURE_LIMITED,
            padding = Rect(0, 20, 0, 40),
            itemMargins = Rect(0, 3, 0, 7),
            decorationOffsets = Rect(0, 5, 0, 11),
        )

        assertEquals(340, fixture.recyclerView.computeVerticalScrollExtent())
        assertEquals(0, fixture.recyclerView.computeVerticalScrollOffset())
        assertScrollMetricsValid(fixture)
        assertFalse(fixture.recyclerView.canScrollVertically(-1))
        assertTrue(fixture.recyclerView.canScrollVertically(1))

        fixture.layoutManager.scrollToPosition(7)
        layout(fixture)

        assertScrollMetricsValid(fixture)
        assertTrue(fixture.recyclerView.canScrollVertically(-1))
        assertFalse(fixture.recyclerView.canScrollVertically(1))
    }

    @Test
    fun boundedScrollMetrics_withNoScrollableDistance_reportNoDirection() {
        listOf(
            emptyList(),
            listOf(ITEM_HEIGHT),
            List(8) { 0 },
        ).forEach { itemHeights: List<Int> ->
            val fixture: Fixture = createFixture(
                itemHeights = itemHeights,
                scrollFeature = WheelLayoutManager.SCROLL_FEATURE_LIMITED,
            )

            assertScrollMetricsValid(fixture)
            assertFalse(fixture.recyclerView.canScrollVertically(-1))
            assertFalse(fixture.recyclerView.canScrollVertically(1))
        }
    }

    @Test
    fun stateRestore_preservesCenteredPosition() {
        val original: Fixture = createFixture(
            itemCount = 20,
            scrollFeature = WheelLayoutManager.SCROLL_FEATURE_LIMITED,
        )
        original.layoutManager.scrollToPosition(7)
        layout(original)
        val savedState: Parcelable = original.layoutManager.onSaveInstanceState()
        val restored: Fixture = createFixture(
            itemCount = 20,
            padding = Rect(0, 20, 0, 40),
            layoutHeight = 501,
            scrollFeature = WheelLayoutManager.SCROLL_FEATURE_LIMITED,
            performInitialLayout = false,
        )

        restored.layoutManager.onRestoreInstanceState(savedState)
        layout(restored)

        assertEquals(7, restored.layoutManager.findCenterChildPosition())
        assertEquals(
            0,
            restored.layoutManager.distanceToCenter(
                checkNotNull(restored.layoutManager.findCenterChild()),
            ),
        )
    }

    @Test
    fun stateRestore_afterParcelRoundTrip_preservesPartialCenterOffset() {
        val original: Fixture = createFixture(itemCount = 20)
        original.recyclerView.scrollBy(0, ITEM_HEIGHT / 2)
        val originalCenterView: View = checkNotNull(original.layoutManager.findCenterChild())
        val expectedPosition: Int = original.layoutManager.getPosition(originalCenterView)
        val expectedOffset: Int = original.layoutManager.distanceToCenter(originalCenterView)
        val savedState: Parcelable = parcelRoundTrip(original.layoutManager.onSaveInstanceState())
        val restored: Fixture = createFixture(
            itemCount = 20,
            padding = Rect(0, 20, 0, 40),
            layoutHeight = 501,
            performInitialLayout = false,
        )

        restored.layoutManager.onRestoreInstanceState(savedState)
        layout(restored)

        val restoredCenterView: View = checkNotNull(restored.layoutManager.findCenterChild())
        assertTrue(expectedOffset != 0)
        assertEquals(expectedPosition, restored.layoutManager.getPosition(restoredCenterView))
        assertEquals(expectedOffset, restored.layoutManager.distanceToCenter(restoredCenterView))
    }

    @Test
    fun stateRestore_afterItemHeightShrinks_recentersCompletelyInvisibleAnchor() {
        val original: Fixture = createFixture(
            itemCount = 3,
            itemHeight = 1_000,
            scrollFeature = WheelLayoutManager.SCROLL_FEATURE_LIMITED,
        )
        original.recyclerView.scrollBy(0, 400)
        val originalCenterView: View = checkNotNull(original.layoutManager.findCenterChild())
        assertEquals(0, original.layoutManager.getPosition(originalCenterView))
        assertEquals(-400, original.layoutManager.distanceToCenter(originalCenterView))
        val savedState: Parcelable = parcelRoundTrip(original.layoutManager.onSaveInstanceState())
        val restored: Fixture = createFixture(
            itemCount = 3,
            itemHeight = 10,
            scrollFeature = WheelLayoutManager.SCROLL_FEATURE_LIMITED,
            performInitialLayout = false,
        )

        restored.layoutManager.onRestoreInstanceState(savedState)
        layout(restored)

        val restoredCenterView: View = checkNotNull(restored.layoutManager.findCenterChild())
        assertEquals(0, restored.layoutManager.getPosition(restoredCenterView))
        assertEquals(0, restored.layoutManager.distanceToCenter(restoredCenterView))
    }

    @Test
    fun stateRestore_afterItemHeightChanges_preservesVisibleCenterOffset() {
        val original: Fixture = createFixture(
            itemCount = 20,
            itemHeight = 200,
            scrollFeature = WheelLayoutManager.SCROLL_FEATURE_LIMITED,
        )
        original.recyclerView.scrollBy(0, 40)
        val originalCenterView: View = checkNotNull(original.layoutManager.findCenterChild())
        val expectedPosition: Int = original.layoutManager.getPosition(originalCenterView)
        val expectedOffset: Int = original.layoutManager.distanceToCenter(originalCenterView)
        val savedState: Parcelable = parcelRoundTrip(original.layoutManager.onSaveInstanceState())
        val restored: Fixture = createFixture(
            itemCount = 20,
            itemHeight = 100,
            scrollFeature = WheelLayoutManager.SCROLL_FEATURE_LIMITED,
            performInitialLayout = false,
        )

        restored.layoutManager.onRestoreInstanceState(savedState)
        layout(restored)

        val restoredCenterView: View = checkNotNull(restored.layoutManager.findCenterChild())
        assertTrue(expectedOffset != 0)
        assertEquals(expectedPosition, restored.layoutManager.getPosition(restoredCenterView))
        assertEquals(expectedOffset, restored.layoutManager.distanceToCenter(restoredCenterView))
    }

    @Test
    fun stateRestore_zeroHeightAnchorWithInvisibleCenter_recentersSamePosition() {
        val restored: Fixture = createFixture(
            itemCount = 8,
            itemHeight = 0,
            scrollFeature = WheelLayoutManager.SCROLL_FEATURE_LIMITED,
            performInitialLayout = false,
        )
        val savedState: Parcelable = parcelRoundTrip(
            WheelLayoutManager.SavedState(
                anchorPosition = 4,
                anchorOffsetY = restored.layoutHeight,
            ),
        )

        restored.layoutManager.onRestoreInstanceState(savedState)
        layout(restored)

        val restoredCenterView: View = checkNotNull(restored.layoutManager.findCenterChild())
        assertEquals(4, restored.layoutManager.getPosition(restoredCenterView))
        assertEquals(0, restored.layoutManager.distanceToCenter(restoredCenterView))
    }

    @Test
    fun savedState_isJvmPublicForPlatformParcelReflection() {
        val fixture: Fixture = createFixture(itemCount = 20)
        val savedState: Parcelable = fixture.layoutManager.onSaveInstanceState()

        assertTrue(Modifier.isPublic(savedState.javaClass.modifiers))
    }

    @Test
    fun stateRestore_withEmptyAdapter_waitsForData() {
        val original: Fixture = createFixture(itemCount = 20)
        original.layoutManager.scrollToPosition(7)
        layout(original)
        val savedState: Parcelable = original.layoutManager.onSaveInstanceState()
        val restored: Fixture = createFixture(
            itemCount = 0,
            performInitialLayout = false,
        )

        restored.layoutManager.onRestoreInstanceState(savedState)
        layout(restored)
        restored.adapter.appendItems(count = 20, height = ITEM_HEIGHT)
        layout(restored)

        assertEquals(7, restored.layoutManager.findCenterChildPosition())
        assertEquals(
            0,
            restored.layoutManager.distanceToCenter(
                checkNotNull(restored.layoutManager.findCenterChild()),
            ),
        )
    }

    @Test
    fun centerListener_whenDataBecomesEmpty_reportsNoPositionAndRestoredCenter() {
        listOf(
            WheelLayoutManager.SCROLL_FEATURE_LIMITED,
            WheelLayoutManager.SCROLL_FEATURE_UNLIMITED,
        ).forEach { scrollFeature: Int ->
            val fixture: Fixture = createFixture(
                itemCount = 20,
                scrollFeature = scrollFeature,
            )
            val centerChanges: MutableList<Int> = mutableListOf()
            fixture.layoutManager.setOnCenterYPositionChangeListener(centerChanges::add)

            fixture.adapter.keepFirst(0)
            layout(fixture)

            assertEquals(RecyclerView.NO_POSITION, fixture.layoutManager.findCenterChildPosition())
            assertFalse(fixture.layoutManager.isLoopingActive)
            assertEquals(listOf(RecyclerView.NO_POSITION), centerChanges)

            layout(fixture)

            assertEquals(listOf(RecyclerView.NO_POSITION), centerChanges)

            fixture.adapter.appendItems(count = 20, height = ITEM_HEIGHT)
            layout(fixture)

            assertEquals(0, fixture.layoutManager.findCenterChildPosition())
            assertEquals(listOf(RecyclerView.NO_POSITION, 0), centerChanges)
        }
    }

    @Test
    fun scrollToPosition_withEmptyAdapter_doesNotWrapOutOfBoundsTargetWhenDataArrives() {
        listOf(
            WheelLayoutManager.SCROLL_FEATURE_LIMITED,
            WheelLayoutManager.SCROLL_FEATURE_UNLIMITED,
        ).forEach { scrollFeature: Int ->
            val fixture: Fixture = createFixture(
                itemCount = 0,
                scrollFeature = scrollFeature,
                performInitialLayout = false,
            )

            fixture.layoutManager.scrollToPosition(5)
            layout(fixture)
            fixture.adapter.appendItems(count = 3, height = ITEM_HEIGHT)
            layout(fixture)

            assertEquals(0, fixture.layoutManager.findCenterChildPosition())
            assertUniqueAttachedPositions(fixture)
        }
    }

    @Test
    fun stateRestore_withEmptyAdapter_preservesPendingExplicitTarget() {
        val original: Fixture = createFixture(
            itemCount = 0,
            performInitialLayout = false,
        )
        original.layoutManager.scrollToPosition(5)
        layout(original)
        val savedState: Parcelable = parcelRoundTrip(original.layoutManager.onSaveInstanceState())
        val restored: Fixture = createFixture(
            itemCount = 0,
            performInitialLayout = false,
        )

        restored.layoutManager.onRestoreInstanceState(savedState)
        layout(restored)
        restored.adapter.appendItems(count = 8, height = ITEM_HEIGHT)
        layout(restored)

        assertEquals(5, restored.layoutManager.findCenterChildPosition())
    }

    @Test
    fun stateRestore_explicitScrollPositionTakesPrecedence() {
        val original: Fixture = createFixture(itemCount = 20)
        original.layoutManager.scrollToPosition(7)
        layout(original)
        val savedState: Parcelable = original.layoutManager.onSaveInstanceState()
        listOf(true, false).forEach { restoreBeforeExplicitScroll: Boolean ->
            val restored: Fixture = createFixture(
                itemCount = 20,
                performInitialLayout = false,
            )

            if (restoreBeforeExplicitScroll) {
                restored.layoutManager.onRestoreInstanceState(savedState)
                restored.layoutManager.scrollToPosition(3)
            } else {
                restored.layoutManager.scrollToPosition(3)
                restored.layoutManager.onRestoreInstanceState(savedState)
            }
            layout(restored)

            assertEquals(3, restored.layoutManager.findCenterChildPosition())
            assertEquals(
                0,
                restored.layoutManager.distanceToCenter(
                    checkNotNull(restored.layoutManager.findCenterChild()),
                ),
            )
        }
    }

    @Test
    fun stateRestore_afterOutOfBoundsExplicitTarget_preservesCurrentCenter() {
        val original: Fixture = createFixture(
            itemCount = 8,
            scrollFeature = WheelLayoutManager.SCROLL_FEATURE_LIMITED,
        )
        original.layoutManager.scrollToPosition(3)
        layout(original)
        original.layoutManager.scrollToPosition(8)
        val savedState: Parcelable = original.layoutManager.onSaveInstanceState()
        val restored: Fixture = createFixture(
            itemCount = 8,
            scrollFeature = WheelLayoutManager.SCROLL_FEATURE_LIMITED,
            performInitialLayout = false,
        )

        restored.layoutManager.onRestoreInstanceState(savedState)
        layout(restored)

        assertEquals(3, restored.layoutManager.findCenterChildPosition())
    }

    @Test
    fun stateRestore_withOutOfBoundsPosition_doesNotWrapInUnlimitedMode() {
        val original: Fixture = createFixture(itemCount = 20)
        original.layoutManager.scrollToPosition(19)
        layout(original)
        val savedState: Parcelable = original.layoutManager.onSaveInstanceState()
        val restored: Fixture = createFixture(
            itemCount = 3,
            performInitialLayout = false,
        )

        restored.layoutManager.onRestoreInstanceState(savedState)
        layout(restored)

        assertEquals(0, restored.layoutManager.findCenterChildPosition())
        assertFalse(restored.layoutManager.isLoopingActive)
        assertUniqueAttachedPositions(restored)
    }

    @Test
    fun relayout_afterHeightChange_preservesCenterPositionAndRelativeOffset() {
        val fixture: Fixture = createFixture(
            itemCount = 12,
            scrollFeature = WheelLayoutManager.SCROLL_FEATURE_LIMITED,
        )
        fixture.recyclerView.scrollBy(0, ITEM_HEIGHT / 2)
        val positionBeforeRelayout: Int = fixture.layoutManager.findCenterChildPosition()
        val offsetBeforeRelayout: Int = fixture.layoutManager.distanceToCenter(
            checkNotNull(fixture.layoutManager.findCenterChild()),
        )

        layout(fixture, layoutHeight = 600)

        assertEquals(positionBeforeRelayout, fixture.layoutManager.findCenterChildPosition())
        assertEquals(
            offsetBeforeRelayout,
            fixture.layoutManager.distanceToCenter(checkNotNull(fixture.layoutManager.findCenterChild())),
        )
    }

    @Test
    fun relayout_afterAsymmetricPaddingChange_movesAnchorWithContentCenter() {
        val fixture: Fixture = createFixture(
            itemCount = 12,
            scrollFeature = WheelLayoutManager.SCROLL_FEATURE_LIMITED,
            padding = Rect(0, 20, 0, 40),
        )
        val positionBeforeRelayout: Int = fixture.layoutManager.findCenterChildPosition()

        fixture.recyclerView.setPadding(0, 60, 0, 20)
        layout(fixture)

        assertEquals(positionBeforeRelayout, fixture.layoutManager.findCenterChildPosition())
        assertEquals(
            0,
            fixture.layoutManager.distanceToCenter(checkNotNull(fixture.layoutManager.findCenterChild())),
        )
    }

    @Test
    fun initialLayout_withAtMostHeight_usesMeasuredContentCenter() {
        val fixture: Fixture = createFixture(
            itemCount = 8,
            scrollFeature = WheelLayoutManager.SCROLL_FEATURE_LIMITED,
            layoutHeight = 400,
            layoutHeightMode = View.MeasureSpec.AT_MOST,
        )

        assertTrue(fixture.recyclerView.height < fixture.layoutHeight)
        assertEquals(
            0,
            fixture.layoutManager.distanceToCenter(checkNotNull(fixture.layoutManager.findCenterChild())),
        )
    }

    @Test
    fun relayout_withAtMostHeight_preservesPositionAndOffsetAcrossLayoutPasses() {
        val fixture: Fixture = createFixture(
            itemCount = 12,
            scrollFeature = WheelLayoutManager.SCROLL_FEATURE_LIMITED,
            padding = Rect(0, 20, 0, 40),
        )
        fixture.recyclerView.scrollBy(0, ITEM_HEIGHT / 2)
        val positionBeforeRelayout: Int = fixture.layoutManager.findCenterChildPosition()
        val offsetBeforeRelayout: Int = fixture.layoutManager.distanceToCenter(
            checkNotNull(fixture.layoutManager.findCenterChild()),
        )
        fixture.recyclerView.setPadding(0, 60, 0, 20)

        layout(
            fixture = fixture,
            layoutHeight = 400,
            layoutHeightMode = View.MeasureSpec.AT_MOST,
        )

        assertTrue(fixture.recyclerView.height < fixture.layoutHeight)
        assertEquals(positionBeforeRelayout, fixture.layoutManager.findCenterChildPosition())
        assertEquals(
            offsetBeforeRelayout,
            fixture.layoutManager.distanceToCenter(checkNotNull(fixture.layoutManager.findCenterChild())),
        )
    }

    @Test
    fun smoothScrollToPosition_withZeroHeightItems_confirmsTargetOnce() {
        val fixture: Fixture = createFixture(
            itemCount = 8,
            itemHeight = 0,
            scrollFeature = WheelLayoutManager.SCROLL_FEATURE_LIMITED,
        )
        val centerChanges: MutableList<Int> = mutableListOf()
        val activityController = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            activityController.get().setContentView(fixture.recyclerView)
            layout(fixture)
            fixture.layoutManager.setOnCenterYPositionChangeListener(centerChanges::add)

            fixture.recyclerView.smoothScrollToPosition(5)
            shadowOf(Looper.getMainLooper()).idleFor(5L, TimeUnit.SECONDS)

            assertEquals(5, fixture.layoutManager.findCenterChildPosition())
            assertEquals(listOf(5), centerChanges)

            fixture.recyclerView.smoothScrollToPosition(5)
            shadowOf(Looper.getMainLooper()).idleFor(5L, TimeUnit.SECONDS)

            assertEquals(listOf(5), centerChanges)
        } finally {
            activityController.close()
        }
    }

    @Test
    fun layout_withMarginsAndDecoration_preservesItemSizeAndSharedGeometry() {
        val padding = Rect(13, 20, 17, 40)
        val margins = Rect(5, 10, 9, 6)
        val decorationOffsets = Rect(3, 7, 4, 11)
        val fixture: Fixture = createFixture(
            itemCount = 8,
            scrollFeature = WheelLayoutManager.SCROLL_FEATURE_LIMITED,
            padding = padding,
            itemMargins = margins,
            decorationOffsets = decorationOffsets,
        )
        val centerChild: View = checkNotNull(fixture.layoutManager.findCenterChild())
        val centerLayoutParams: RecyclerView.LayoutParams =
            centerChild.layoutParams as RecyclerView.LayoutParams

        assertEquals(ITEM_HEIGHT, centerChild.height)
        assertEquals(
            ITEM_HEIGHT + margins.top + margins.bottom +
                decorationOffsets.top + decorationOffsets.bottom,
            decoratedEnd(fixture.layoutManager, centerChild) -
                decoratedStart(fixture.layoutManager, centerChild),
        )
        assertEquals(
            padding.top + (fixture.recyclerView.height - padding.top - padding.bottom) / 2,
            decoratedCenterY(fixture.layoutManager, centerChild),
        )
        assertEquals(
            padding.left,
            fixture.layoutManager.getDecoratedLeft(centerChild) - centerLayoutParams.leftMargin,
        )
        assertEquals(
            fixture.recyclerView.width - padding.right,
            fixture.layoutManager.getDecoratedRight(centerChild) + centerLayoutParams.rightMargin,
        )
        for (index: Int in 1 until fixture.layoutManager.childCount) {
            val previous: View = checkNotNull(fixture.layoutManager.getChildAt(index - 1))
            val current: View = checkNotNull(fixture.layoutManager.getChildAt(index))
            assertEquals(
                decoratedEnd(fixture.layoutManager, previous),
                decoratedStart(fixture.layoutManager, current),
            )
        }
        assertArrayEquals(
            intArrayOf(0, 0),
            WheelSnapHelper().calculateDistanceToFinalSnap(fixture.layoutManager, centerChild),
        )

        fixture.recyclerView.scrollBy(0, 10_000)
        assertEquals(7, fixture.layoutManager.findCenterChildPosition())
        assertEquals(
            0,
            fixture.layoutManager.distanceToCenter(checkNotNull(fixture.layoutManager.findCenterChild())),
        )

        fixture.recyclerView.scrollBy(0, -10_000)
        assertEquals(0, fixture.layoutManager.findCenterChildPosition())
        assertEquals(
            0,
            fixture.layoutManager.distanceToCenter(checkNotNull(fixture.layoutManager.findCenterChild())),
        )
    }

    @Test
    fun limitedLayout_withExtremeScrollDistances_reachesBothAdapterEdges() {
        val fixture: Fixture = createFixture(
            itemHeights = listOf(0, 20, 0, 60, 40, 0, 80, 0),
            scrollFeature = WheelLayoutManager.SCROLL_FEATURE_LIMITED,
            padding = Rect(0, 20, 0, 40),
            itemMargins = Rect(0, 3, 0, 7),
            decorationOffsets = Rect(0, 5, 0, 11),
        )

        fixture.recyclerView.scrollBy(0, Int.MAX_VALUE)

        assertEquals(7, fixture.layoutManager.findCenterChildPosition())
        assertEquals(
            0,
            fixture.layoutManager.distanceToCenter(checkNotNull(fixture.layoutManager.findCenterChild())),
        )

        fixture.recyclerView.scrollBy(0, Int.MIN_VALUE)

        assertEquals(0, fixture.layoutManager.findCenterChildPosition())
        assertEquals(
            0,
            fixture.layoutManager.distanceToCenter(checkNotNull(fixture.layoutManager.findCenterChild())),
        )
    }

    @Test
    fun centerSelection_whenTwoChildrenAreEquidistant_keepsCurrentPosition() {
        val fixture: Fixture = createFixture(
            itemCount = 10,
            scrollFeature = WheelLayoutManager.SCROLL_FEATURE_LIMITED,
        )
        val snapHelper = WheelSnapHelper()

        fixture.recyclerView.scrollBy(0, ITEM_HEIGHT / 2)

        assertEquals(0, fixture.layoutManager.findCenterChildPosition())
        assertSame(
            fixture.layoutManager.findCenterChild(),
            snapHelper.findSnapView(fixture.layoutManager),
        )

        fixture.recyclerView.scrollBy(0, 1)

        assertEquals(1, fixture.layoutManager.findCenterChildPosition())
        assertSame(
            fixture.layoutManager.findCenterChild(),
            snapHelper.findSnapView(fixture.layoutManager),
        )
    }

    @Test
    fun centerSelection_whenMiddleChildIsCentered_returnsSameChild() {
        val fixture: Fixture = createFixture(itemCount = 20)
        val middleChild: View = checkNotNull(
            fixture.layoutManager.getChildAt(fixture.layoutManager.childCount / 2),
        )

        assertEquals(0, fixture.layoutManager.distanceToCenter(middleChild))
        assertSame(middleChild, fixture.layoutManager.findCenterChild())
    }

    @Test
    fun unlimitedLayout_withEnoughUniqueItems_scrollsAcrossAdapterBoundary() {
        val fixture: Fixture = createFixture(itemCount = 20)

        assertTrue(fixture.layoutManager.isLoopingActive)
        assertUniqueAttachedPositions(fixture)

        fixture.recyclerView.scrollBy(0, -ITEM_HEIGHT)

        assertEquals(19, fixture.layoutManager.findCenterChildPosition())
        assertTrue(fixture.layoutManager.isLoopingActive)
        assertUniqueAttachedPositions(fixture)
    }

    @Test
    fun unlimitedLayout_afterLargeScroll_keepsAttachedPositionsUnique() {
        val fixture: Fixture = createFixture(itemCount = 20)

        repeat(4) {
            fixture.recyclerView.scrollBy(0, 10_000)
            assertTrue(fixture.layoutManager.isLoopingActive)
            assertUniqueAttachedPositions(fixture)
            fixture.recyclerView.scrollBy(0, -10_000)
            assertTrue(fixture.layoutManager.isLoopingActive)
            assertUniqueAttachedPositions(fixture)
        }
    }

    @Test
    fun unlimitedLayout_largeScroll_skipsRepeatedFullAdapterCycles() {
        val itemHeights: List<Int> = listOf(0, 2, 3, 0, 4)
        val margins = Rect(0, 1, 0, 1)
        val decorationOffsets = Rect(0, 1, 0, 1)
        val itemOuterInsets: Int = margins.top + margins.bottom +
            decorationOffsets.top + decorationOffsets.bottom
        val cycleSpan: Int = itemHeights.sumOf { itemHeight: Int -> itemHeight + itemOuterInsets }

        listOf(10_000, -10_000, Int.MAX_VALUE, Int.MIN_VALUE).forEach { distance: Int ->
            val largeScroll: Fixture = createFixture(
                itemHeights = itemHeights,
                itemMargins = margins,
                decorationOffsets = decorationOffsets,
                layoutHeight = 1,
            )
            val remainderScroll: Fixture = createFixture(
                itemHeights = itemHeights,
                itemMargins = margins,
                decorationOffsets = decorationOffsets,
                layoutHeight = 1,
            )
            largeScroll.adapter.clearBindCounts()

            largeScroll.recyclerView.scrollBy(0, distance)
            remainderScroll.recyclerView.scrollBy(0, distance % cycleSpan)

            assertEquals(attachedPositions(remainderScroll), attachedPositions(largeScroll))
            assertEquals(
                decoratedCenterY(
                    remainderScroll.layoutManager,
                    checkNotNull(remainderScroll.layoutManager.findCenterChild()),
                ),
                decoratedCenterY(
                    largeScroll.layoutManager,
                    checkNotNull(largeScroll.layoutManager.findCenterChild()),
                ),
            )
            assertTrue(largeScroll.adapter.totalBindCount() <= itemHeights.size * 3)
            assertTrue(largeScroll.layoutManager.isLoopingActive)
            assertUniqueAttachedPositions(largeScroll)
        }
    }

    @Test
    fun unlimitedLayout_largeScrollAfterItemGeometryChange_usesCurrentCycleSpan() {
        val initialItemHeights: List<Int> = listOf(2, 3, 4, 5, 6)
        val updatedItemHeights: List<Int> = listOf(7, 3, 5, 4, 7)
        val largeScroll: Fixture = createFixture(
            itemHeights = initialItemHeights,
            layoutHeight = 1,
        )

        largeScroll.recyclerView.scrollBy(0, 10_000)
        largeScroll.adapter.updateHeights(updatedItemHeights)
        largeScroll.layoutManager.scrollToPosition(0)
        layout(largeScroll)

        val remainderScroll: Fixture = createFixture(
            itemHeights = updatedItemHeights,
            layoutHeight = 1,
        )
        val distance: Int = 10_000
        largeScroll.adapter.clearBindCounts()

        largeScroll.recyclerView.scrollBy(0, distance)
        remainderScroll.recyclerView.scrollBy(0, distance % updatedItemHeights.sum())

        assertEquals(attachedPositions(remainderScroll), attachedPositions(largeScroll))
        assertEquals(
            decoratedCenterY(
                remainderScroll.layoutManager,
                checkNotNull(remainderScroll.layoutManager.findCenterChild()),
            ),
            decoratedCenterY(
                largeScroll.layoutManager,
                checkNotNull(largeScroll.layoutManager.findCenterChild()),
            ),
        )
        assertTrue(largeScroll.adapter.totalBindCount() <= updatedItemHeights.size * 3)
        assertTrue(largeScroll.layoutManager.isLoopingActive)
        assertUniqueAttachedPositions(largeScroll)
    }

    @Test
    fun unlimitedLayout_notifyItemChanged_rebindsOneUniqueVisiblePosition() {
        val fixture: Fixture = createFixture(itemCount = 20)
        fixture.adapter.clearBindCounts()

        fixture.adapter.notifyItemChanged(0)
        layout(fixture)

        assertEquals(1, fixture.adapter.bindCount(0))
        assertUniqueAttachedPositions(fixture)
    }

    @Test
    fun unlimitedLayout_withStableIds_updatesWithoutDuplicateIdentity() {
        val fixture: Fixture = createFixture(
            itemCount = 20,
            hasStableIds = true,
        )
        fixture.adapter.clearBindCounts()

        fixture.adapter.notifyItemChanged(0)
        layout(fixture)

        assertEquals(1, fixture.adapter.bindCount(0))
        assertUniqueAttachedPositions(fixture)
    }

    @Test
    fun unlimitedLayout_notifyItemRemoved_withSimpleAnimations_keepsPostLayoutPositionsUnique() {
        val fixture: Fixture = createFixture(itemCount = 20)

        assertNotNull(fixture.recyclerView.itemAnimator)

        fixture.adapter.removeAt(0)
        layout(fixture)

        assertEquals(19, fixture.adapter.itemCount)
        assertTrue(fixture.layoutManager.isLoopingActive)
        assertUniqueAttachedPositions(fixture)
    }

    @Test
    fun unlimitedLayout_itemCountChanges_recalculatesLoopingAvailability() {
        val fixture: Fixture = createFixture(itemCount = 20)

        fixture.adapter.keepFirst(3)
        layout(fixture)

        assertFalse(fixture.layoutManager.isLoopingActive)
        assertEquals(listOf(0, 1, 2), attachedPositions(fixture))

        fixture.adapter.appendItems(count = 17, height = ITEM_HEIGHT)
        layout(fixture)

        assertTrue(fixture.layoutManager.isLoopingActive)
        assertUniqueAttachedPositions(fixture)
    }

    @Test
    fun unlimitedLayout_itemHeightChanges_recalculatesLoopingAvailability() {
        val fixture: Fixture = createFixture(itemCount = 20)

        fixture.adapter.updateHeights(List(20) { 0 })
        layout(fixture)

        assertFalse(fixture.layoutManager.isLoopingActive)
        assertUniqueAttachedPositions(fixture)

        fixture.adapter.updateHeights(List(20) { ITEM_HEIGHT })
        layout(fixture)

        assertTrue(fixture.layoutManager.isLoopingActive)
        assertUniqueAttachedPositions(fixture)
    }

    private fun createFixture(
        itemCount: Int,
        hasStableIds: Boolean = false,
        itemHeight: Int = ITEM_HEIGHT,
        scrollFeature: Int = WheelLayoutManager.SCROLL_FEATURE_UNLIMITED,
        padding: Rect = Rect(),
        itemMargins: Rect = Rect(),
        decorationOffsets: Rect? = null,
        layoutWidth: Int = 200,
        layoutHeight: Int = 400,
        layoutHeightMode: Int = View.MeasureSpec.EXACTLY,
        performInitialLayout: Boolean = true,
    ): Fixture = createFixture(
        itemHeights = List(itemCount) { itemHeight },
        hasStableIds = hasStableIds,
        scrollFeature = scrollFeature,
        padding = padding,
        itemMargins = itemMargins,
        decorationOffsets = decorationOffsets,
        layoutWidth = layoutWidth,
        layoutHeight = layoutHeight,
        layoutHeightMode = layoutHeightMode,
        performInitialLayout = performInitialLayout,
    )

    private fun createFixture(
        itemHeights: List<Int>,
        hasStableIds: Boolean = false,
        scrollFeature: Int = WheelLayoutManager.SCROLL_FEATURE_UNLIMITED,
        padding: Rect = Rect(),
        itemMargins: Rect = Rect(),
        decorationOffsets: Rect? = null,
        layoutWidth: Int = 200,
        layoutHeight: Int = 400,
        layoutHeightMode: Int = View.MeasureSpec.EXACTLY,
        performInitialLayout: Boolean = true,
    ): Fixture {
        val layoutManager = WheelLayoutManager(scrollFeature)
        val adapter = CountingAdapter(itemHeights, hasStableIds, itemMargins)
        val recyclerView = RecyclerView(RuntimeEnvironment.getApplication()).apply {
            this.layoutManager = layoutManager
            this.adapter = adapter
            setPadding(padding.left, padding.top, padding.right, padding.bottom)
            decorationOffsets?.let { offsets: Rect ->
                addItemDecoration(
                    object : RecyclerView.ItemDecoration() {
                        override fun getItemOffsets(
                            outRect: Rect,
                            view: View,
                            parent: RecyclerView,
                            state: RecyclerView.State,
                        ) {
                            outRect.set(offsets)
                        }
                    },
                )
            }
        }
        val fixture = Fixture(
            recyclerView = recyclerView,
            layoutManager = layoutManager,
            adapter = adapter,
            layoutWidth = layoutWidth,
            layoutHeight = layoutHeight,
            layoutHeightMode = layoutHeightMode,
        )
        if (performInitialLayout) {
            layout(fixture)
        }
        return fixture
    }

    private fun parcelRoundTrip(state: Parcelable): Parcelable {
        val parcel: Parcel = Parcel.obtain()
        return try {
            parcel.writeParcelable(state, 0)
            parcel.setDataPosition(0)
            @Suppress("DEPRECATION")
            val restoredState: Parcelable? = parcel.readParcelable(
                WheelLayoutManager::class.java.classLoader,
            )
            checkNotNull(restoredState)
        } finally {
            parcel.recycle()
        }
    }

    private fun layout(
        fixture: Fixture,
        layoutWidth: Int = fixture.layoutWidth,
        layoutHeight: Int = fixture.layoutHeight,
        layoutHeightMode: Int = fixture.layoutHeightMode,
    ) {
        val widthSpec: Int =
            View.MeasureSpec.makeMeasureSpec(layoutWidth, View.MeasureSpec.EXACTLY)
        val heightSpec: Int =
            View.MeasureSpec.makeMeasureSpec(layoutHeight, layoutHeightMode)
        fixture.recyclerView.measure(widthSpec, heightSpec)
        fixture.recyclerView.layout(
            0,
            0,
            fixture.recyclerView.measuredWidth,
            fixture.recyclerView.measuredHeight,
        )
    }

    private fun attachedPositions(fixture: Fixture): List<Int> =
        (0 until fixture.layoutManager.childCount).map { index: Int ->
            fixture.layoutManager.getPosition(checkNotNull(fixture.layoutManager.getChildAt(index)))
        }

    private fun verticalScrollMetrics(fixture: Fixture): IntArray = intArrayOf(
        fixture.recyclerView.computeVerticalScrollOffset(),
        fixture.recyclerView.computeVerticalScrollExtent(),
        fixture.recyclerView.computeVerticalScrollRange(),
    )

    private fun assertScrollMetricsValid(fixture: Fixture) {
        val (offset: Int, extent: Int, range: Int) = verticalScrollMetrics(fixture)

        assertTrue(offset >= 0)
        assertTrue(extent >= 0)
        assertTrue(range >= extent)
        assertTrue(offset <= range - extent)
    }

    private fun assertUniqueAttachedPositions(fixture: Fixture) {
        val positions: List<Int> = attachedPositions(fixture)
        assertEquals(positions.toSet().size, positions.size)
    }

    private fun decoratedStart(layoutManager: WheelLayoutManager, view: View): Int {
        val layoutParams: RecyclerView.LayoutParams = view.layoutParams as RecyclerView.LayoutParams
        return layoutManager.getDecoratedTop(view) - layoutParams.topMargin
    }

    private fun decoratedEnd(layoutManager: WheelLayoutManager, view: View): Int {
        val layoutParams: RecyclerView.LayoutParams = view.layoutParams as RecyclerView.LayoutParams
        return layoutManager.getDecoratedBottom(view) + layoutParams.bottomMargin
    }

    private fun decoratedCenterY(layoutManager: WheelLayoutManager, view: View): Int =
        decoratedStart(layoutManager, view) +
            (decoratedEnd(layoutManager, view) - decoratedStart(layoutManager, view)) / 2

    private data class Fixture(
        val recyclerView: RecyclerView,
        val layoutManager: WheelLayoutManager,
        val adapter: CountingAdapter,
        val layoutWidth: Int,
        val layoutHeight: Int,
        val layoutHeightMode: Int,
    )

    private class CountingAdapter(
        itemHeights: List<Int>,
        hasStableIds: Boolean,
        private val itemMargins: Rect,
    ) : RecyclerView.Adapter<Holder>() {

        private val items: MutableList<Item> = itemHeights.mapIndexed { index: Int, height: Int ->
            Item(id = index.toLong(), height = height)
        }.toMutableList()
        private val bindCounts: MutableMap<Int, Int> = mutableMapOf()
        private var nextItemId: Long = items.size.toLong()

        init {
            setHasStableIds(hasStableIds)
        }

        fun clearBindCounts() {
            bindCounts.clear()
        }

        fun bindCount(position: Int): Int = bindCounts[position] ?: 0

        fun totalBindCount(): Int = bindCounts.values.sum()

        fun removeAt(position: Int) {
            require(position in items.indices)
            items.removeAt(position)
            notifyItemRemoved(position)
        }

        fun keepFirst(count: Int) {
            require(count in 0..items.size)
            val removedCount: Int = items.size - count
            if (removedCount == 0) {
                return
            }
            items.subList(count, items.size).clear()
            notifyItemRangeRemoved(count, removedCount)
        }

        fun appendItems(count: Int, height: Int) {
            require(count >= 0)
            if (count == 0) {
                return
            }
            val insertionPosition: Int = items.size
            repeat(count) {
                items += Item(id = nextItemId, height = height)
                nextItemId += 1
            }
            notifyItemRangeInserted(insertionPosition, count)
        }

        fun updateHeights(heights: List<Int>) {
            require(heights.size == items.size)
            items.forEachIndexed { index: Int, item: Item ->
                item.height = heights[index]
            }
            notifyItemRangeChanged(0, items.size)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
            View(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                ).apply {
                    setMargins(
                        itemMargins.left,
                        itemMargins.top,
                        itemMargins.right,
                        itemMargins.bottom,
                    )
                }
            },
        )

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.itemView.layoutParams = holder.itemView.layoutParams.apply {
                height = items[position].height
            }
            bindCounts[position] = bindCount(position) + 1
        }

        override fun getItemId(position: Int): Long = items[position].id

        override fun getItemCount(): Int = items.size

        private data class Item(
            val id: Long,
            var height: Int,
        )
    }

    private class Holder(itemView: View) : RecyclerView.ViewHolder(itemView)

    private companion object {
        const val ITEM_HEIGHT: Int = 40
    }
}
