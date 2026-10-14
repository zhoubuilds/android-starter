package com.whisper.kit.recyclerview.wheel

import android.view.View
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
import org.robolectric.RuntimeEnvironment
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
        layout(fixture.recyclerView)

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
        layout(fixture.recyclerView)

        assertEquals(1, fixture.adapter.bindCount(0))
        assertUniqueAttachedPositions(fixture)
    }

    @Test
    fun unlimitedLayout_notifyItemRemoved_keepsPostLayoutPositionsUnique() {
        val fixture: Fixture = createFixture(itemCount = 20)

        fixture.adapter.removeAt(0)
        layout(fixture.recyclerView)

        assertEquals(19, fixture.adapter.itemCount)
        assertTrue(fixture.layoutManager.isLoopingActive)
        assertUniqueAttachedPositions(fixture)
    }

    private fun createFixture(
        itemCount: Int,
        hasStableIds: Boolean = false,
        itemHeight: Int = ITEM_HEIGHT,
    ): Fixture {
        val layoutManager = WheelLayoutManager(WheelLayoutManager.SCROLL_FEATURE_UNLIMITED)
        val adapter = CountingAdapter(itemCount, hasStableIds, itemHeight)
        val recyclerView = RecyclerView(RuntimeEnvironment.getApplication()).apply {
            this.layoutManager = layoutManager
            this.adapter = adapter
        }
        layout(recyclerView)
        return Fixture(recyclerView, layoutManager, adapter)
    }

    private fun layout(recyclerView: RecyclerView) {
        val widthSpec: Int = View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY)
        val heightSpec: Int = View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY)
        recyclerView.measure(widthSpec, heightSpec)
        recyclerView.layout(0, 0, 200, 400)
    }

    private fun attachedPositions(fixture: Fixture): List<Int> =
        (0 until fixture.layoutManager.childCount).map { index: Int ->
            fixture.layoutManager.getPosition(checkNotNull(fixture.layoutManager.getChildAt(index)))
        }

    private fun assertUniqueAttachedPositions(fixture: Fixture) {
        val positions: List<Int> = attachedPositions(fixture)
        assertEquals(positions.toSet().size, positions.size)
    }

    private data class Fixture(
        val recyclerView: RecyclerView,
        val layoutManager: WheelLayoutManager,
        val adapter: CountingAdapter,
    )

    private class CountingAdapter(
        private var count: Int,
        hasStableIds: Boolean,
        private val itemHeight: Int,
    ) : RecyclerView.Adapter<Holder>() {

        private val bindCounts: MutableMap<Int, Int> = mutableMapOf()

        init {
            setHasStableIds(hasStableIds)
        }

        fun clearBindCounts() {
            bindCounts.clear()
        }

        fun bindCount(position: Int): Int = bindCounts[position] ?: 0

        fun removeAt(position: Int) {
            require(position in 0 until count)
            count -= 1
            notifyItemRemoved(position)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
            View(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    itemHeight,
                )
            },
        )

        override fun onBindViewHolder(holder: Holder, position: Int) {
            bindCounts[position] = bindCount(position) + 1
        }

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getItemCount(): Int = count
    }

    private class Holder(itemView: View) : RecyclerView.ViewHolder(itemView)

    private companion object {
        const val ITEM_HEIGHT: Int = 40
    }
}
