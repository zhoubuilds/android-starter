package com.whisper.kit.recyclerview.decoration

import android.graphics.Rect
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.lang.reflect.Modifier

/**
 * 验证 RecyclerView item 间距装饰器的输入和首尾边界.
 *
 * @author whisper
 * @since 2026/09/02
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RegularItemSpaceDecorationTest {

    @Test
    fun itemDividerDecoration_whenInspectingInheritance_usesSpaceDecorationWithFinalOffsets() {
        assertEquals(
            RegularItemSpaceDecoration::class.java,
            RegularItemDividerDecoration::class.java.superclass,
        )
        val getItemOffsets = RegularItemSpaceDecoration::class.java.getDeclaredMethod(
            "getItemOffsets",
            Rect::class.java,
            View::class.java,
            RecyclerView::class.java,
            RecyclerView.State::class.java,
        )
        assertTrue(Modifier.isFinal(getItemOffsets.modifiers))
    }

    @Test
    fun constructor_whenAnySpaceIsNegative_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException::class.java) {
            RegularItemSpaceDecoration(mainAxisSpace = -1, crossAxisSpace = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RegularItemSpaceDecoration(mainAxisSpace = 0, crossAxisSpace = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RegularItemSpaceDecoration(mainAxisSpace = 0, crossAxisSpace = 0, startSpace = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RegularItemSpaceDecoration(mainAxisSpace = 0, crossAxisSpace = 0, endSpace = -1)
        }
    }

    @Test
    fun getItemOffsets_whenLayoutStateIsEmpty_clearsOutputRect() {
        val recyclerView: RecyclerView = RecyclerView(RuntimeEnvironment.getApplication()).apply {
            layoutManager = LinearLayoutManager(context)
        }
        val outRect: Rect = Rect(1, 2, 3, 4)

        RegularItemSpaceDecoration(space = 8).getItemOffsets(
            outRect,
            View(recyclerView.context),
            recyclerView,
            RecyclerView.State(),
        )

        assertEquals(Rect(), outRect)
    }

    @Test
    fun getItemOffsets_whenLayoutManagerIsUnsupported_logsOnlyOnceAndClearsOutputRect() {
        ShadowLog.clear()
        val recyclerView: RecyclerView = RecyclerView(RuntimeEnvironment.getApplication()).apply {
            layoutManager = StaggeredGridLayoutManager(2, RecyclerView.VERTICAL)
        }
        val decoration: RegularItemSpaceDecoration = RegularItemSpaceDecoration(space = 8)
        val outRect: Rect = Rect(1, 2, 3, 4)

        repeat(2) {
            decoration.getItemOffsets(
                outRect,
                View(recyclerView.context),
                recyclerView,
                RecyclerView.State(),
            )
        }

        assertEquals(Rect(), outRect)
        assertEquals(
            1,
            ShadowLog.getLogsForTag(DECORATION_LOG_TAG).count { item: ShadowLog.LogItem ->
                item.type == Log.WARN && item.msg.contains("supports only LinearLayoutManager")
            },
        )
    }

    @Test
    fun itemOffsets_assignMainAxisSpaceToLogicalStart_preserveExactGapsAndBoundaries() {
        val recyclerView: RecyclerView = RecyclerView(RuntimeEnvironment.getApplication()).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = TestAdapter()
            addItemDecoration(
                RegularItemSpaceDecoration(
                    mainAxisSpace = 5,
                    crossAxisSpace = 0,
                    startSpace = 30,
                    endSpace = 40,
                ),
            )
        }

        layoutRecyclerView(recyclerView)

        assertEquals(Rect(0, 30, 0, 0), decorationOffsets(recyclerView, position = 0))
        assertEquals(Rect(0, 5, 0, 0), decorationOffsets(recyclerView, position = 1))
        assertEquals(Rect(0, 5, 0, 40), decorationOffsets(recyclerView, position = 2))
    }

    @Test
    fun gridOffsets_assignMainAxisSpaceToLogicalStartOfNonFirstSpanGroup() {
        val recyclerView: RecyclerView = RecyclerView(RuntimeEnvironment.getApplication()).apply {
            layoutManager = GridLayoutManager(context, GRID_SPAN_COUNT)
            adapter = TestAdapter(itemCountValue = GRID_ITEM_COUNT)
            addItemDecoration(
                RegularItemSpaceDecoration(
                    mainAxisSpace = 5,
                    crossAxisSpace = 0,
                    startSpace = 30,
                    endSpace = 40,
                ),
            )
        }

        layoutRecyclerView(recyclerView)

        assertEquals(Rect(0, 30, 0, 0), decorationOffsets(recyclerView, position = 0))
        assertEquals(Rect(0, 30, 0, 0), decorationOffsets(recyclerView, position = 2))
        assertEquals(Rect(0, 5, 0, 40), decorationOffsets(recyclerView, position = 3))
        assertEquals(Rect(0, 5, 0, 40), decorationOffsets(recyclerView, position = 5))
    }

    @Test
    fun gridOffsets_whenOnlyOneSpanGroup_applyBothMainAxisBoundaries() {
        val recyclerView: RecyclerView = RecyclerView(RuntimeEnvironment.getApplication()).apply {
            layoutManager = GridLayoutManager(context, GRID_SPAN_COUNT)
            adapter = TestAdapter(itemCountValue = GRID_SPAN_COUNT)
            addItemDecoration(
                RegularItemSpaceDecoration(
                    mainAxisSpace = 5,
                    crossAxisSpace = 2,
                    startSpace = 30,
                    endSpace = 40,
                ),
            )
        }

        layoutRecyclerView(recyclerView)

        assertEquals(Rect(0, 30, 1, 40), decorationOffsets(recyclerView, position = 0))
        assertEquals(Rect(1, 30, 0, 40), decorationOffsets(recyclerView, position = 1))
        assertEquals(Rect(2, 30, 0, 40), decorationOffsets(recyclerView, position = 2))
    }

    @Test
    fun gridOffsets_whenSpanSizesVary_applyBoundariesToEveryItemInFirstAndLastGroups() {
        val layoutManager: GridLayoutManager = GridLayoutManager(
            RuntimeEnvironment.getApplication(),
            GRID_SPAN_COUNT,
        ).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {

                override fun getSpanSize(position: Int): Int = if (position < 2) 2 else 1
            }
        }
        val recyclerView: RecyclerView = RecyclerView(RuntimeEnvironment.getApplication()).apply {
            this.layoutManager = layoutManager
            adapter = TestAdapter(itemCountValue = ITEM_COUNT)
            addItemDecoration(
                RegularItemSpaceDecoration(
                    mainAxisSpace = 5,
                    crossAxisSpace = 3,
                    startSpace = 30,
                    endSpace = 40,
                ),
            )
        }

        layoutRecyclerView(recyclerView)

        assertEquals(Rect(0, 30, 1, 0), decorationOffsets(recyclerView, position = 0))
        assertEquals(Rect(0, 5, 1, 40), decorationOffsets(recyclerView, position = 1))
        assertEquals(Rect(2, 5, 0, 40), decorationOffsets(recyclerView, position = 2))
    }

    @Test
    fun getItemOffsetForGrid_whenPositionCannotBeInLastGroup_skipsSpanSizeQueries() {
        val lookup: CountingSpanSizeLookup = CountingSpanSizeLookup()
        val layoutManager: GridLayoutManager = GridLayoutManager(
            RuntimeEnvironment.getApplication(),
            LARGE_GRID_SPAN_COUNT,
        ).apply {
            spanSizeLookup = lookup
            scrollToPosition(LARGE_GRID_POSITION)
        }
        val adapter: TestAdapter = TestAdapter(itemCountValue = LARGE_GRID_ITEM_COUNT)
        val decoration: RegularItemSpaceDecoration = RegularItemSpaceDecoration(
            mainAxisSpace = 5,
            crossAxisSpace = 3,
            startSpace = 30,
            endSpace = 40,
        )
        val recyclerView: RecyclerView = RecyclerView(RuntimeEnvironment.getApplication()).apply {
            this.layoutManager = layoutManager
            this.adapter = adapter
            addItemDecoration(decoration)
        }
        layoutRecyclerView(recyclerView)
        val child: View = checkNotNull(layoutManager.findViewByPosition(LARGE_GRID_POSITION))
        lookup.reset()

        recyclerView.invalidateItemDecorations()
        layoutManager.calculateItemDecorationsForChild(child, Rect())

        assertEquals(0, lookup.callCount)
    }

    @Test
    fun getItemOffsetForGrid_whenEndSpaceIsZero_skipsLastGroupQueries() {
        val lookup: CountingSpanSizeLookup = CountingSpanSizeLookup()
        val layoutManager: GridLayoutManager = GridLayoutManager(
            RuntimeEnvironment.getApplication(),
            2,
        ).apply {
            spanSizeLookup = lookup
        }
        val adapter: TestAdapter = TestAdapter(itemCountValue = 4)
        val decoration: RegularItemSpaceDecoration = RegularItemSpaceDecoration(
            mainAxisSpace = 5,
            crossAxisSpace = 0,
            startSpace = 0,
            endSpace = 0,
        )
        val recyclerView: RecyclerView = RecyclerView(RuntimeEnvironment.getApplication()).apply {
            this.layoutManager = layoutManager
            this.adapter = adapter
            addItemDecoration(decoration)
        }
        layoutRecyclerView(recyclerView)
        val child: View = checkNotNull(layoutManager.findViewByPosition(2))
        lookup.reset()

        recyclerView.invalidateItemDecorations()
        layoutManager.calculateItemDecorationsForChild(child, Rect())

        assertEquals(0, lookup.callCount)
    }

    @Test
    fun getItemOffsetForGrid_whenLayoutCountExceedsAdapterCount_doesNotQueryMissingSpanPosition() {
        val adapterItemCount: Int = 3
        val lookup: CountingSpanSizeLookup = CountingSpanSizeLookup(validItemCount = adapterItemCount + 1)
        val layoutManager: GridLayoutManager = GridLayoutManager(
            RuntimeEnvironment.getApplication(),
            2,
        ).apply {
            spanSizeLookup = lookup
        }
        val adapter: TestAdapter = TestAdapter(itemCountValue = adapterItemCount + 1)
        val decoration: RegularItemSpaceDecoration = RegularItemSpaceDecoration(
            mainAxisSpace = 5,
            crossAxisSpace = 0,
            startSpace = 30,
            endSpace = 40,
        )
        val recyclerView: RecyclerView = RecyclerView(RuntimeEnvironment.getApplication()).apply {
            this.layoutManager = layoutManager
            this.adapter = adapter
            addItemDecoration(decoration)
        }
        layoutRecyclerView(recyclerView)
        val child: View = checkNotNull(layoutManager.findViewByPosition(adapterItemCount - 1))
        val outRect: Rect = Rect()

        adapter.removeLast()
        lookup.validItemCount = adapterItemCount
        lookup.reset()

        recyclerView.invalidateItemDecorations()
        layoutManager.calculateItemDecorationsForChild(child, outRect)

        assertEquals(0, lookup.callCount)
        assertEquals(Rect(0, 5, 0, 0), outRect)
    }

    @Test
    fun gridOffsets_whenCrossAxisSpaceIsOnePixel_preserveEveryGapAndColumnRounding() {
        val recyclerView: RecyclerView = createGridRecyclerView(crossAxisSpace = 1)

        layoutRecyclerView(recyclerView)

        val firstColumn: Rect = decorationOffsets(recyclerView, position = 0)
        val secondColumn: Rect = decorationOffsets(recyclerView, position = 1)
        val thirdColumn: Rect = decorationOffsets(recyclerView, position = 2)
        val totalOffsets: List<Int> = listOf(firstColumn, secondColumn, thirdColumn).map { offsets: Rect ->
            offsets.left + offsets.right
        }
        val minTotalOffset: Int = checkNotNull(totalOffsets.minOrNull())
        val maxTotalOffset: Int = checkNotNull(totalOffsets.maxOrNull())
        assertEquals(1, firstColumn.right + secondColumn.left)
        assertEquals(1, secondColumn.right + thirdColumn.left)
        assertTrue(maxTotalOffset - minTotalOffset <= 1)
        assertEquals(firstColumn, decorationOffsets(recyclerView, position = 3))
        assertEquals(secondColumn, decorationOffsets(recyclerView, position = 4))
        assertEquals(thirdColumn, decorationOffsets(recyclerView, position = 5))
    }

    @Test
    fun gridOffsets_whenCrossAxisSpaceCannotBeDivided_ceilStartAndFloorEnd() {
        val recyclerView: RecyclerView = createGridRecyclerView(crossAxisSpace = 2)

        layoutRecyclerView(recyclerView)

        assertEquals(Rect(0, 0, 1, 0), decorationOffsets(recyclerView, position = 0))
        assertEquals(Rect(1, 0, 0, 0), decorationOffsets(recyclerView, position = 1))
        assertEquals(Rect(2, 0, 0, 0), decorationOffsets(recyclerView, position = 2))
    }

    @Test
    fun adapterMove_whenCallerInvalidatesAfterUpdateLayout_recalculatesFinalOffsets() {
        val adapter: TestAdapter = TestAdapter(itemCountValue = 4)
        val recyclerView: RecyclerView = RecyclerView(RuntimeEnvironment.getApplication()).apply {
            layoutManager = GridLayoutManager(context, 2)
            this.adapter = adapter
            addItemDecoration(RegularItemSpaceDecoration(mainAxisSpace = 5, crossAxisSpace = 0))
        }
        layoutRecyclerView(recyclerView)

        adapter.notifyItemMoved(2, 0)
        layoutRecyclerView(recyclerView)
        recyclerView.invalidateItemDecorations()
        assertTrue(recyclerView.isLayoutRequested)
        layoutRecyclerView(recyclerView)
        assertEquals(Rect(0, 0, 0, 0), decorationOffsets(recyclerView, position = 0))
        assertEquals(Rect(0, 0, 0, 0), decorationOffsets(recyclerView, position = 1))
        assertEquals(Rect(0, 5, 0, 0), decorationOffsets(recyclerView, position = 2))
        assertEquals(Rect(0, 5, 0, 0), decorationOffsets(recyclerView, position = 3))
    }

    private fun createGridRecyclerView(crossAxisSpace: Int): RecyclerView =
        RecyclerView(RuntimeEnvironment.getApplication()).apply {
            layoutManager = GridLayoutManager(context, GRID_SPAN_COUNT)
            adapter = TestAdapter(itemCountValue = GRID_ITEM_COUNT)
            addItemDecoration(
                RegularItemSpaceDecoration(
                    mainAxisSpace = 0,
                    crossAxisSpace = crossAxisSpace,
                ),
            )
        }

    private fun layoutRecyclerView(recyclerView: RecyclerView) {
        val sizeSpec: Int = View.MeasureSpec.makeMeasureSpec(RECYCLER_SIZE, View.MeasureSpec.EXACTLY)
        recyclerView.measure(sizeSpec, sizeSpec)
        recyclerView.layout(0, 0, RECYCLER_SIZE, RECYCLER_SIZE)
    }

    private fun decorationOffsets(recyclerView: RecyclerView, position: Int): Rect {
        val layoutManager: RecyclerView.LayoutManager = checkNotNull(recyclerView.layoutManager)
        val child: View = checkNotNull(layoutManager.findViewByPosition(position))
        return Rect(
            layoutManager.getLeftDecorationWidth(child),
            layoutManager.getTopDecorationHeight(child),
            layoutManager.getRightDecorationWidth(child),
            layoutManager.getBottomDecorationHeight(child),
        )
    }

    private class TestAdapter(
        private var itemCountValue: Int = ITEM_COUNT,
    ) : RecyclerView.Adapter<TestViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TestViewHolder {
            val itemView: View = View(parent.context)
            itemView.layoutParams = RecyclerView.LayoutParams(ITEM_WIDTH, ITEM_HEIGHT)
            return TestViewHolder(itemView)
        }

        override fun onBindViewHolder(holder: TestViewHolder, position: Int) {
        }

        override fun getItemCount(): Int = itemCountValue

        fun removeLast() {
            val removedPosition: Int = itemCountValue - 1
            itemCountValue--
            notifyItemRemoved(removedPosition)
        }
    }

    private class TestViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    private class CountingSpanSizeLookup(
        var validItemCount: Int = Int.MAX_VALUE,
    ) : GridLayoutManager.SpanSizeLookup() {

        var callCount: Int = 0
            private set

        override fun getSpanSize(position: Int): Int {
            require(position in 0 until validItemCount) {
                "Position $position is outside the current Adapter data."
            }
            callCount++
            return 1
        }

        fun reset() {
            callCount = 0
        }
    }

    private companion object {

        private const val DECORATION_LOG_TAG: String = "RecyclerViewDecoration"
        private const val RECYCLER_SIZE: Int = 300
        private const val ITEM_COUNT: Int = 3
        private const val GRID_ITEM_COUNT: Int = 6
        private const val GRID_SPAN_COUNT: Int = 3
        private const val LARGE_GRID_ITEM_COUNT: Int = 100_000
        private const val LARGE_GRID_POSITION: Int = 50_000
        private const val LARGE_GRID_SPAN_COUNT: Int = 4
        private const val ITEM_WIDTH: Int = 40
        private const val ITEM_HEIGHT: Int = 30
    }
}
