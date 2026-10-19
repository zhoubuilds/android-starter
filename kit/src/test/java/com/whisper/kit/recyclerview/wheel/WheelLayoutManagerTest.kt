package com.whisper.kit.recyclerview.wheel

import android.app.Activity
import android.graphics.Rect
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
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
        layout(fixture)
        return fixture
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
