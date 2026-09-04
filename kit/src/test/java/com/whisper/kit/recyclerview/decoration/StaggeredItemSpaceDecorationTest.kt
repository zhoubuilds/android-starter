package com.whisper.kit.recyclerview.decoration

import android.content.Context
import android.graphics.Rect
import android.util.Log
import android.view.View
import android.view.ViewGroup
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
 * 验证 StaggeredGridLayoutManager item 间距装饰器的拓扑和方向行为.
 *
 * @author whisper
 * @since 2026/09/02
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StaggeredItemSpaceDecorationTest {

    @Test
    fun itemDividerDecoration_whenInspectingInheritance_usesSpaceDecorationWithFinalOffsets() {
        assertEquals(
            StaggeredItemSpaceDecoration::class.java,
            StaggeredItemDividerDecoration::class.java.superclass,
        )
        val getItemOffsets = StaggeredItemSpaceDecoration::class.java.getDeclaredMethod(
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
            StaggeredItemSpaceDecoration(mainAxisSpace = -1, crossAxisSpace = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            StaggeredItemSpaceDecoration(mainAxisSpace = 0, crossAxisSpace = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            StaggeredItemSpaceDecoration(mainAxisSpace = 0, crossAxisSpace = 0, startSpace = -1)
        }
    }

    @Test
    fun getItemOffsets_whenLayoutManagerIsUnsupported_logsOnlyOnceAndClearsOutputRect() {
        ShadowLog.clear()
        val recyclerView: RecyclerView = RecyclerView(RuntimeEnvironment.getApplication()).apply {
            layoutManager = LinearLayoutManager(context)
        }
        val outRect: Rect = Rect(1, 2, 3, 4)
        val decoration: StaggeredItemSpaceDecoration =
            StaggeredItemSpaceDecoration(mainAxisSpace = 8, crossAxisSpace = 8)

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
                item.type == Log.WARN && item.msg.contains("supports only StaggeredGridLayoutManager")
            },
        )
    }

    @Test
    fun itemOffsets_withoutFullSpan_applyStartSpaceToFirstItemOfEverySpan() {
        val recyclerView: RecyclerView = createRecyclerView()

        layoutRecyclerView(recyclerView)

        assertEquals(Rect(0, START_SPACE, 0, 0), decorationOffsets(recyclerView, position = 0))
        assertEquals(Rect(0, START_SPACE, 0, 0), decorationOffsets(recyclerView, position = 1))
        assertEquals(Rect(0, START_SPACE, 0, 0), decorationOffsets(recyclerView, position = 2))
        assertEquals(Rect(0, MAIN_AXIS_SPACE, 0, 0), decorationOffsets(recyclerView, position = 3))
        assertEquals(Rect(0, MAIN_AXIS_SPACE, 0, 0), decorationOffsets(recyclerView, position = 5))
    }

    @Test
    fun itemOffsets_whenFirstItemIsFullSpan_applyStartSpaceOnlyToThatItem() {
        val recyclerView: RecyclerView = createRecyclerView(fullSpanPositions = setOf(0))

        layoutRecyclerView(recyclerView)

        assertEquals(Rect(0, START_SPACE, 0, 0), decorationOffsets(recyclerView, position = 0))
        assertEquals(Rect(0, MAIN_AXIS_SPACE, 0, 0), decorationOffsets(recyclerView, position = 1))
        assertEquals(Rect(0, MAIN_AXIS_SPACE, 0, 0), decorationOffsets(recyclerView, position = 3))
    }

    @Test
    fun itemOffsets_whenInitialPrefixContainsFullSpan_applyStartSpaceOnlyBeforeIt() {
        val recyclerView: RecyclerView = createRecyclerView(fullSpanPositions = setOf(2))

        layoutRecyclerView(recyclerView)

        assertEquals(Rect(0, START_SPACE, 0, 0), decorationOffsets(recyclerView, position = 0))
        assertEquals(Rect(0, START_SPACE, 0, 0), decorationOffsets(recyclerView, position = 1))
        assertEquals(Rect(0, MAIN_AXIS_SPACE, 0, 0), decorationOffsets(recyclerView, position = 2))
        assertEquals(Rect(0, MAIN_AXIS_SPACE, 0, 0), decorationOffsets(recyclerView, position = 3))
    }

    @Test
    fun crossAxisOffsets_whenSpaceCannotBeDivided_preserveGapsAndStableSpanRounding() {
        val recyclerView: RecyclerView = createRecyclerView(
            mainAxisSpace = 0,
            crossAxisSpace = 2,
            startSpace = 0,
        )

        layoutRecyclerView(recyclerView)

        assertEquals(Rect(0, 0, 1, 0), decorationOffsets(recyclerView, position = 0))
        assertEquals(Rect(1, 0, 0, 0), decorationOffsets(recyclerView, position = 1))
        assertEquals(Rect(2, 0, 0, 0), decorationOffsets(recyclerView, position = 2))
        assertEquals(decorationOffsets(recyclerView, position = 0), decorationOffsets(recyclerView, position = 3))
        assertEquals(decorationOffsets(recyclerView, position = 1), decorationOffsets(recyclerView, position = 4))
        assertEquals(decorationOffsets(recyclerView, position = 2), decorationOffsets(recyclerView, position = 5))
    }

    @Test
    fun crossAxisOffsets_whenSpaceIsOnePixel_preserveEveryAdjacentGap() {
        val recyclerView: RecyclerView = createRecyclerView(
            mainAxisSpace = 0,
            crossAxisSpace = 1,
            startSpace = 0,
        )

        layoutRecyclerView(recyclerView)

        val firstSpan: Rect = decorationOffsets(recyclerView, position = 0)
        val secondSpan: Rect = decorationOffsets(recyclerView, position = 1)
        val thirdSpan: Rect = decorationOffsets(recyclerView, position = 2)
        assertEquals(1, firstSpan.right + secondSpan.left)
        assertEquals(1, secondSpan.right + thirdSpan.left)
    }

    @Test
    fun crossAxisOffsets_whenItemIsFullSpan_doNotInsetCrossAxis() {
        val recyclerView: RecyclerView = createRecyclerView(
            crossAxisSpace = 2,
            fullSpanPositions = setOf(0),
        )

        layoutRecyclerView(recyclerView)

        assertEquals(Rect(0, START_SPACE, 0, 0), decorationOffsets(recyclerView, position = 0))
    }

    @Test
    fun crossAxisOffsets_whenVerticalRtl_mirrorLogicalSpanRounding() {
        val recyclerView: RecyclerView = createRecyclerView(
            mainAxisSpace = 0,
            crossAxisSpace = 2,
            startSpace = 0,
            layoutDirection = View.LAYOUT_DIRECTION_RTL,
        )

        layoutRecyclerView(recyclerView)

        assertEquals(Rect(1, 0, 0, 0), decorationOffsets(recyclerView, position = 0))
        assertEquals(Rect(0, 0, 1, 0), decorationOffsets(recyclerView, position = 1))
        assertEquals(Rect(0, 0, 2, 0), decorationOffsets(recyclerView, position = 2))
    }

    @Test
    fun crossAxisOffsets_whenHorizontal_useTopToBottomSpanOrder() {
        val recyclerView: RecyclerView = createRecyclerView(
            mainAxisSpace = 0,
            crossAxisSpace = 2,
            startSpace = 0,
            orientation = RecyclerView.HORIZONTAL,
        )

        layoutRecyclerView(recyclerView)

        assertEquals(Rect(0, 0, 0, 1), decorationOffsets(recyclerView, position = 0))
        assertEquals(Rect(0, 1, 0, 0), decorationOffsets(recyclerView, position = 1))
        assertEquals(Rect(0, 2, 0, 0), decorationOffsets(recyclerView, position = 2))
    }

    @Test
    fun mainAxisOffsets_whenVerticalReverseLayout_mapLogicalStartToBottom() {
        val recyclerView: RecyclerView = createRecyclerView(reverseLayout = true)

        layoutRecyclerView(recyclerView)

        assertEquals(Rect(0, 0, 0, START_SPACE), decorationOffsets(recyclerView, position = 0))
        assertEquals(Rect(0, 0, 0, MAIN_AXIS_SPACE), decorationOffsets(recyclerView, position = 3))
    }

    @Test
    fun mainAxisOffsets_whenHorizontalRtl_mapStartUsingEffectiveReverseDirection() {
        val normalRecyclerView: RecyclerView = createRecyclerView(
            orientation = RecyclerView.HORIZONTAL,
            crossAxisSpace = 0,
            layoutDirection = View.LAYOUT_DIRECTION_RTL,
        )
        val reversedRecyclerView: RecyclerView = createRecyclerView(
            orientation = RecyclerView.HORIZONTAL,
            crossAxisSpace = 0,
            reverseLayout = true,
            layoutDirection = View.LAYOUT_DIRECTION_RTL,
        )

        layoutRecyclerView(normalRecyclerView)
        layoutRecyclerView(reversedRecyclerView)

        assertEquals(Rect(0, 0, START_SPACE, 0), decorationOffsets(normalRecyclerView, position = 0))
        assertEquals(Rect(0, 0, MAIN_AXIS_SPACE, 0), decorationOffsets(normalRecyclerView, position = 3))
        assertEquals(Rect(START_SPACE, 0, 0, 0), decorationOffsets(reversedRecyclerView, position = 0))
        assertEquals(Rect(MAIN_AXIS_SPACE, 0, 0, 0), decorationOffsets(reversedRecyclerView, position = 3))
    }

    @Test
    fun layout_whenAdapterDoesNotProvideFullSpanTopology_disablesMainAxisAndPreservesCrossAxis() {
        ShadowLog.clear()
        val recyclerView: RecyclerView = createRecyclerView(
            crossAxisSpace = 2,
            adapter = PlainTestAdapter(),
        )

        layoutRecyclerView(recyclerView)

        assertEquals(Rect(0, 0, 1, 0), decorationOffsets(recyclerView, position = 0))
        assertEquals(Rect(1, 0, 0, 0), decorationOffsets(recyclerView, position = 1))
        assertEquals(Rect(2, 0, 0, 0), decorationOffsets(recyclerView, position = 2))
        assertEquals(Rect(0, 0, 1, 0), decorationOffsets(recyclerView, position = 3))
        assertEquals(
            1,
            ShadowLog.getLogsForTag(DECORATION_LOG_TAG).count { item: ShadowLog.LogItem ->
                item.type == Log.WARN && item.msg.contains("disabled its main-axis behavior")
            },
        )
    }

    @Test
    fun layout_whenMainAxisDoesNotNeedTopology_allowsAdapterWithoutProvider() {
        ShadowLog.clear()
        val recyclerView: RecyclerView = createRecyclerView(
            mainAxisSpace = MAIN_AXIS_SPACE,
            crossAxisSpace = 2,
            startSpace = MAIN_AXIS_SPACE,
            adapter = PlainTestAdapter(),
        )

        layoutRecyclerView(recyclerView)

        assertEquals(Rect(0, MAIN_AXIS_SPACE, 1, 0), decorationOffsets(recyclerView, position = 0))
        assertEquals(Rect(1, MAIN_AXIS_SPACE, 0, 0), decorationOffsets(recyclerView, position = 1))
        assertEquals(
            0,
            ShadowLog.getLogsForTag(DECORATION_LOG_TAG).count { item: ShadowLog.LogItem ->
                item.type == Log.WARN && item.msg.contains("StaggeredFullSpanProvider")
            },
        )
    }

    @Test
    fun layout_whenProviderAndLayoutParamsDisagree_throwsIllegalStateException() {
        val recyclerView: RecyclerView = createRecyclerView(
            adapter = TestAdapter(
                fullSpanPositions = setOf(0),
                layoutFullSpanPositions = emptySet(),
            ),
        )

        assertThrows(IllegalStateException::class.java) {
            layoutRecyclerView(recyclerView)
        }
    }

    @Test
    fun itemOffsets_whenRecalculated_queriesStartTopologyOnlyForInitialPrefix() {
        val adapter: TestAdapter = TestAdapter()
        val recyclerView: RecyclerView = createRecyclerView(adapter = adapter)
        layoutRecyclerView(recyclerView)
        adapter.resetFullSpanQueryCount()

        recyclerView.invalidateItemDecorations()
        layoutRecyclerView(recyclerView)

        assertEquals(9, adapter.fullSpanQueryCount)
    }

    private fun createRecyclerView(
        mainAxisSpace: Int = MAIN_AXIS_SPACE,
        crossAxisSpace: Int = 0,
        startSpace: Int = START_SPACE,
        orientation: Int = RecyclerView.VERTICAL,
        reverseLayout: Boolean = false,
        layoutDirection: Int = View.LAYOUT_DIRECTION_LTR,
        fullSpanPositions: Set<Int> = emptySet(),
        adapter: RecyclerView.Adapter<*> = TestAdapter(fullSpanPositions = fullSpanPositions),
    ): RecyclerView = DirectionRecyclerView(
        RuntimeEnvironment.getApplication(),
        layoutDirection,
    ).apply {
        layoutManager = StaggeredGridLayoutManager(SPAN_COUNT, orientation).apply {
            this.reverseLayout = reverseLayout
            gapStrategy = StaggeredGridLayoutManager.GAP_HANDLING_NONE
        }
        this.adapter = adapter
        addItemDecoration(
            StaggeredItemSpaceDecoration(
                mainAxisSpace = mainAxisSpace,
                crossAxisSpace = crossAxisSpace,
                startSpace = startSpace,
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

    private open class PlainTestAdapter : RecyclerView.Adapter<TestViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TestViewHolder {
            val itemView: View = View(parent.context)
            itemView.layoutParams = StaggeredGridLayoutManager.LayoutParams(ITEM_WIDTH, ITEM_HEIGHT)
            return TestViewHolder(itemView)
        }

        override fun onBindViewHolder(holder: TestViewHolder, position: Int) {
        }

        override fun getItemCount(): Int = ITEM_COUNT
    }

    private class TestAdapter(
        private val fullSpanPositions: Set<Int> = emptySet(),
        private val layoutFullSpanPositions: Set<Int> = fullSpanPositions,
    ) : PlainTestAdapter(), StaggeredFullSpanProvider {

        var fullSpanQueryCount: Int = 0
            private set

        override fun onBindViewHolder(holder: TestViewHolder, position: Int) {
            val layoutParams: StaggeredGridLayoutManager.LayoutParams =
                holder.itemView.layoutParams as StaggeredGridLayoutManager.LayoutParams
            layoutParams.isFullSpan = position in layoutFullSpanPositions
        }

        override fun isFullSpan(position: Int): Boolean {
            fullSpanQueryCount++
            return position in fullSpanPositions
        }

        fun resetFullSpanQueryCount() {
            fullSpanQueryCount = 0
        }
    }

    private class TestViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    private class DirectionRecyclerView(
        context: Context,
        private val layoutDirectionValue: Int,
    ) : RecyclerView(context) {

        override fun getLayoutDirection(): Int = layoutDirectionValue
    }

    private companion object {

        private const val DECORATION_LOG_TAG: String = "RecyclerViewDecoration"
        private const val RECYCLER_SIZE: Int = 300
        private const val ITEM_COUNT: Int = 6
        private const val SPAN_COUNT: Int = 3
        private const val ITEM_WIDTH: Int = 40
        private const val ITEM_HEIGHT: Int = 30
        private const val MAIN_AXIS_SPACE: Int = 7
        private const val START_SPACE: Int = 11
    }
}
