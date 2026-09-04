package com.whisper.kit.recyclerview.decoration

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.View
import android.view.ViewGroup
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
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLog

/**
 * 验证 StaggeredGridLayoutManager 分割线装饰器的 offset、方向和绘制边界.
 *
 * @author whisper
 * @since 2026/09/03
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StaggeredItemDividerDecorationTest {

    @Test
    fun constructor_whenAnySizeOrMarginIsNegative_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException::class.java) {
            createDecoration(mainAxisDividerSize = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            createDecoration(crossAxisDividerSize = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            createDecoration(mainAxisDividerCrossAxisStartMargin = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            createDecoration(mainAxisDividerCrossAxisEndMargin = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            createDecoration(crossAxisDividerMainAxisStartMargin = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            createDecoration(crossAxisDividerMainAxisEndMargin = -1)
        }
    }

    @Test
    fun nullDrawables_preserveTransparentSpacingUsingStaggeredTopology() {
        val recyclerView: RecyclerView = createRecyclerView(
            spanCount = 2,
            itemCount = 4,
            decoration = createDecoration(
                mainAxisDividerSize = 5,
                crossAxisDividerSize = 3,
            ),
        )

        layoutRecyclerView(recyclerView)

        assertEquals(Rect(0, 0, 1, 0), decorationOffsets(recyclerView, position = 0))
        assertEquals(Rect(2, 0, 0, 0), decorationOffsets(recyclerView, position = 1))
        assertEquals(Rect(0, 5, 1, 0), decorationOffsets(recyclerView, position = 2))
        assertEquals(Rect(2, 5, 0, 0), decorationOffsets(recyclerView, position = 3))
    }

    @Test
    fun adapterWithoutProvider_disablesMainAxisAndPreservesCrossAxis() {
        ShadowLog.clear()
        val mainAxisDivider: RecordingDrawable = RecordingDrawable()
        val crossAxisDivider: RecordingDrawable = RecordingDrawable()
        val recyclerView: RecyclerView = createRecyclerView(
            spanCount = 2,
            itemCount = 4,
            adapter = PlainTestAdapter(itemCountValue = 4),
            decoration = StaggeredItemDividerDecoration(
                mainAxisDividerSize = 5,
                crossAxisDividerSize = 3,
                mainAxisDivider = mainAxisDivider,
                crossAxisDivider = crossAxisDivider,
            ),
        )

        layoutRecyclerView(recyclerView)
        recyclerView.draw(
            Canvas(Bitmap.createBitmap(RECYCLER_SIZE, RECYCLER_SIZE, Bitmap.Config.ARGB_8888)),
        )

        assertEquals(Rect(0, 0, 1, 0), decorationOffsets(recyclerView, position = 0))
        assertEquals(Rect(2, 0, 0, 0), decorationOffsets(recyclerView, position = 1))
        assertEquals(Rect(0, 0, 1, 0), decorationOffsets(recyclerView, position = 2))
        assertEquals(Rect(2, 0, 0, 0), decorationOffsets(recyclerView, position = 3))
        assertTrue(mainAxisDivider.drawCalls.isEmpty())
        assertEquals(1, crossAxisDivider.drawCalls.size)
        assertEquals(
            1,
            ShadowLog.getLogsForTag(DECORATION_LOG_TAG).count { item: ShadowLog.LogItem ->
                item.type == Log.WARN && item.msg.contains("disabled its main-axis behavior")
            },
        )
    }

    @Test
    fun providerAndLayoutParamsDisagree_throwsIllegalStateException() {
        val recyclerView: RecyclerView = createRecyclerView(
            spanCount = 2,
            itemCount = 3,
            adapter = TestAdapter(
                itemCountValue = 3,
                fullSpanPositions = setOf(0),
                layoutFullSpanPositions = emptySet(),
            ),
            decoration = StaggeredItemDividerDecoration(
                mainAxisDividerSize = 5,
                crossAxisDividerSize = 3,
                mainAxisDivider = RecordingDrawable(),
                crossAxisDivider = RecordingDrawable(),
            ),
        )

        assertThrows(IllegalStateException::class.java) {
            layoutRecyclerView(recyclerView)
        }
    }

    @Test
    fun mainAxisDivider_whenMarginsExceedCrossAxis_doesNotOverflowDrawableSize() {
        val divider: RecordingDrawable = RecordingDrawable()
        val recyclerView: RecyclerView = createRecyclerView(
            spanCount = 2,
            itemCount = 4,
            decoration = createDecoration(
                mainAxisDividerSize = 4,
                mainAxisDividerCrossAxisStartMargin = Int.MAX_VALUE,
                mainAxisDividerCrossAxisEndMargin = Int.MAX_VALUE,
                mainAxisDivider = divider,
            ),
        )
        layoutRecyclerView(recyclerView)

        recyclerView.draw(
            Canvas(Bitmap.createBitmap(RECYCLER_SIZE, RECYCLER_SIZE, Bitmap.Config.ARGB_8888)),
        )

        assertTrue(divider.drawCalls.isNotEmpty())
        assertTrue(divider.drawCalls.all { bounds: Rect -> bounds.width() == 0 })
    }

    @Test
    fun verticalLayout_drawsContinuousCrossAxisDividerBeforeMainAxisSegments() {
        val drawOrder: ArrayList<String> = ArrayList()
        val mainAxisDivider: RecordingDrawable = RecordingDrawable("main", drawOrder)
        val crossAxisDivider: RecordingDrawable = RecordingDrawable("cross", drawOrder)
        val recyclerView: RecyclerView = createRecyclerView(
            spanCount = 2,
            itemCount = 4,
            decoration = createDecoration(
                mainAxisDividerSize = 4,
                crossAxisDividerSize = 6,
                mainAxisDividerCrossAxisStartMargin = 3,
                mainAxisDividerCrossAxisEndMargin = 5,
                crossAxisDividerMainAxisStartMargin = 7,
                crossAxisDividerMainAxisEndMargin = 9,
                mainAxisDivider = mainAxisDivider,
                crossAxisDivider = crossAxisDivider,
            ),
        )
        layoutRecyclerView(recyclerView)
        val firstChild: View = childAtAdapterPosition(recyclerView, position = 0)
        val lastChild: View = childAtAdapterPosition(recyclerView, position = 3)
        val canvas: Canvas = Canvas(
            Bitmap.createBitmap(RECYCLER_SIZE, RECYCLER_SIZE, Bitmap.Config.ARGB_8888),
        )

        recyclerView.draw(canvas)

        assertEquals(listOf("cross", "main", "main"), drawOrder)
        assertEquals(1, crossAxisDivider.drawCalls.size)
        assertEquals(6, crossAxisDivider.drawCalls.single().width())
        assertEquals(lastChild.bottom - firstChild.top - 7 - 9, crossAxisDivider.drawCalls.single().height())
        assertEquals(2, mainAxisDivider.drawCalls.size)
        assertTrue(mainAxisDivider.drawCalls.all { bounds: Rect -> bounds.height() == 4 })
        assertTrue(mainAxisDivider.drawCalls.all { bounds: Rect -> bounds.width() == lastChild.width - 3 - 5 })
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun verticalLayoutAfterScroll_keepsRecycledRowIntersectionFilled() {
        val recyclerView: RecyclerView = createRecyclerView(
            spanCount = 2,
            itemCount = SCROLLING_ITEM_COUNT,
            decoration = createDecoration(
                mainAxisDividerSize = 4,
                crossAxisDividerSize = 6,
                crossAxisDivider = ColorDrawable(Color.RED),
            ),
        )
        layoutRecyclerView(recyclerView)

        recyclerView.scrollBy(0, ITEM_HEIGHT + 1)

        val layoutManager: StaggeredGridLayoutManager =
            recyclerView.layoutManager as StaggeredGridLayoutManager
        assertEquals(null, layoutManager.findViewByPosition(0))
        assertEquals(null, layoutManager.findViewByPosition(1))
        val firstVisibleRowStart: View = childAtAdapterPosition(recyclerView, position = 2)
        val boundaryAnchor: View = childAtAdapterPosition(recyclerView, position = 3)
        val bitmap: Bitmap = Bitmap.createBitmap(RECYCLER_SIZE, RECYCLER_SIZE, Bitmap.Config.ARGB_8888)

        recyclerView.draw(Canvas(bitmap))

        assertTrue(firstVisibleRowStart.top in 1..4)
        assertEquals(
            Color.RED,
            bitmap.getPixel(
                boundaryAnchor.left - 3,
                firstVisibleRowStart.top - 1,
            ),
        )
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun verticalReverseLayoutAfterScroll_keepsRecycledRowIntersectionFilled() {
        val recyclerView: RecyclerView = createRecyclerView(
            spanCount = 2,
            itemCount = SCROLLING_ITEM_COUNT,
            reverseLayout = true,
            decoration = createDecoration(
                mainAxisDividerSize = 4,
                crossAxisDividerSize = 6,
                mainAxisDivider = ColorDrawable(Color.BLUE),
                crossAxisDivider = ColorDrawable(Color.RED),
            ),
        )
        layoutRecyclerView(recyclerView)

        recyclerView.scrollBy(0, -(ITEM_HEIGHT + 1))

        val layoutManager: StaggeredGridLayoutManager =
            recyclerView.layoutManager as StaggeredGridLayoutManager
        assertEquals(null, layoutManager.findViewByPosition(0))
        assertEquals(null, layoutManager.findViewByPosition(1))
        val firstVisibleRowStart: View = childAtAdapterPosition(recyclerView, position = 2)
        val boundaryAnchor: View = childAtAdapterPosition(recyclerView, position = 3)
        val bitmap: Bitmap = Bitmap.createBitmap(RECYCLER_SIZE, RECYCLER_SIZE, Bitmap.Config.ARGB_8888)

        recyclerView.draw(Canvas(bitmap))

        assertTrue(firstVisibleRowStart.bottom in RECYCLER_SIZE - 4 until RECYCLER_SIZE)
        assertEquals(
            Color.RED,
            bitmap.getPixel(
                boundaryAnchor.left - 3,
                firstVisibleRowStart.bottom,
            ),
        )
    }

    @Test
    fun verticalLayoutWithoutClipToPadding_drawsContinuousLineIntoPadding() {
        val divider: RecordingDrawable = RecordingDrawable()
        val recyclerView: RecyclerView = createRecyclerView(
            spanCount = 2,
            itemCount = 4,
            decoration = createDecoration(
                crossAxisDividerSize = 6,
                crossAxisDivider = divider,
            ),
        ).apply {
            setPadding(0, 20, 0, 20)
            clipToPadding = false
        }
        layoutRecyclerView(recyclerView)
        val firstChild: View = childAtAdapterPosition(recyclerView, position = 0)
        firstChild.translationY = -firstChild.top.toFloat()

        recyclerView.draw(
            Canvas(Bitmap.createBitmap(RECYCLER_SIZE, RECYCLER_SIZE, Bitmap.Config.ARGB_8888)),
        )

        assertEquals(1, divider.drawCalls.size)
        assertEquals(0, divider.drawCalls.single().top)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun verticalLayoutWithClipToPadding_clipsTranslatedMainAxisDividerFromPadding() {
        val recyclerView: RecyclerView = createRecyclerView(
            spanCount = 2,
            itemCount = 4,
            decoration = createDecoration(
                mainAxisDividerSize = 4,
                mainAxisDivider = ColorDrawable(Color.RED),
            ),
        ).apply {
            setPadding(0, 20, 0, 0)
            clipToPadding = true
        }
        layoutRecyclerView(recyclerView)
        val thirdChild: View = childAtAdapterPosition(recyclerView, position = 2)
        thirdChild.translationY = -40f
        val dividerTop: Int = thirdChild.top - 4 + thirdChild.translationY.toInt()
        val bitmap: Bitmap = Bitmap.createBitmap(RECYCLER_SIZE, RECYCLER_SIZE, Bitmap.Config.ARGB_8888)

        recyclerView.draw(Canvas(bitmap))

        assertTrue(dividerTop in 0 until recyclerView.paddingTop)
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(thirdChild.left + 1, dividerTop + 1))
    }

    @Test
    @Config(sdk = [26, 35])
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun fullSpanItem_isExcludedFromContinuousCrossAxisDivider() {
        val recyclerView: RecyclerView = createRecyclerView(
            spanCount = 2,
            itemCount = 3,
            fullSpanPositions = setOf(0),
            decoration = createDecoration(
                mainAxisDividerSize = 0,
                crossAxisDividerSize = 6,
                crossAxisDivider = ColorDrawable(Color.RED),
            ),
        )
        layoutRecyclerView(recyclerView)
        val fullSpanChild: View = childAtAdapterPosition(recyclerView, position = 0)
        val ordinaryChild: View = childAtAdapterPosition(recyclerView, position = 1)
        val bitmap: Bitmap = Bitmap.createBitmap(RECYCLER_SIZE, RECYCLER_SIZE, Bitmap.Config.ARGB_8888)

        recyclerView.draw(Canvas(bitmap))

        assertEquals(
            Color.TRANSPARENT,
            bitmap.getPixel(RECYCLER_SIZE / 2, fullSpanChild.top + fullSpanChild.height / 2),
        )
        assertEquals(
            Color.RED,
            bitmap.getPixel(RECYCLER_SIZE / 2, ordinaryChild.top + ordinaryChild.height / 2),
        )
    }

    @Test
    fun verticalRtlLayout_mirrorsContinuousSpanBoundaries() {
        val divider: RecordingDrawable = RecordingDrawable()
        val recyclerView: RecyclerView = createRecyclerView(
            spanCount = 3,
            itemCount = 6,
            layoutDirection = View.LAYOUT_DIRECTION_RTL,
            decoration = createDecoration(
                mainAxisDividerSize = 0,
                crossAxisDividerSize = 12,
                crossAxisDivider = divider,
            ),
        )
        layoutRecyclerView(recyclerView)
        val canvas: Canvas = Canvas(
            Bitmap.createBitmap(RECYCLER_SIZE, RECYCLER_SIZE, Bitmap.Config.ARGB_8888),
        )

        recyclerView.draw(canvas)

        assertEquals(2, divider.drawCalls.size)
        assertEquals(196, divider.drawCalls[0].left)
        assertEquals(92, divider.drawCalls[1].left)
    }

    @Test
    fun horizontalLayout_drawsCrossAxisDividerAlongMainAxis() {
        val divider: RecordingDrawable = RecordingDrawable()
        val recyclerView: RecyclerView = createRecyclerView(
            spanCount = 2,
            itemCount = 4,
            orientation = RecyclerView.HORIZONTAL,
            decoration = createDecoration(
                mainAxisDividerSize = 0,
                crossAxisDividerSize = 6,
                crossAxisDivider = divider,
            ),
        )
        layoutRecyclerView(recyclerView)
        val firstChild: View = childAtAdapterPosition(recyclerView, position = 0)
        val lastChild: View = childAtAdapterPosition(recyclerView, position = 3)
        val canvas: Canvas = Canvas(
            Bitmap.createBitmap(RECYCLER_SIZE, RECYCLER_SIZE, Bitmap.Config.ARGB_8888),
        )

        recyclerView.draw(canvas)

        assertEquals(1, divider.drawCalls.size)
        assertEquals(6, divider.drawCalls.single().height())
        assertEquals(lastChild.right - firstChild.left, divider.drawCalls.single().width())
    }

    @Test
    fun reverseLayout_mapsContinuousDividerMarginsToPhysicalEnds() {
        val divider: RecordingDrawable = RecordingDrawable()
        val recyclerView: RecyclerView = createRecyclerView(
            spanCount = 2,
            itemCount = 4,
            reverseLayout = true,
            decoration = createDecoration(
                mainAxisDividerSize = 0,
                crossAxisDividerSize = 6,
                crossAxisDividerMainAxisStartMargin = 7,
                crossAxisDividerMainAxisEndMargin = 9,
                crossAxisDivider = divider,
            ),
        )
        layoutRecyclerView(recyclerView)
        val adapterStartChild: View = childAtAdapterPosition(recyclerView, position = 0)
        val adapterEndChild: View = childAtAdapterPosition(recyclerView, position = 3)
        val canvas: Canvas = Canvas(
            Bitmap.createBitmap(RECYCLER_SIZE, RECYCLER_SIZE, Bitmap.Config.ARGB_8888),
        )

        recyclerView.draw(canvas)

        assertEquals(1, divider.drawCalls.size)
        assertEquals(adapterEndChild.top + 9, divider.drawCalls.single().top)
        assertEquals(adapterStartChild.bottom - 7, divider.drawCalls.single().bottom)
    }

    @Test
    fun drawAtAdapterStart_queriesFullSpanPrefixOnce() {
        val adapter: TestAdapter = TestAdapter(itemCountValue = 6)
        val recyclerView: RecyclerView = DirectionRecyclerView(
            RuntimeEnvironment.getApplication(),
            View.LAYOUT_DIRECTION_LTR,
        ).apply {
            layoutManager = StaggeredGridLayoutManager(3, RecyclerView.VERTICAL).apply {
                gapStrategy = StaggeredGridLayoutManager.GAP_HANDLING_NONE
            }
            this.adapter = adapter
            addItemDecoration(
                createDecoration(
                    mainAxisDividerSize = 4,
                    crossAxisDividerSize = 0,
                    mainAxisDivider = RecordingDrawable(),
                ),
            )
        }
        layoutRecyclerView(recyclerView)
        adapter.resetFullSpanQueryCount()
        val canvas: Canvas = Canvas(
            Bitmap.createBitmap(RECYCLER_SIZE, RECYCLER_SIZE, Bitmap.Config.ARGB_8888),
        )

        recyclerView.draw(canvas)

        assertEquals(3, adapter.fullSpanQueryCount)
    }

    @Test
    fun nullDrawables_doNotQueryFullSpanTopologyDuringDraw() {
        val adapter: TestAdapter = TestAdapter(itemCountValue = 6)
        val recyclerView: RecyclerView = createRecyclerView(
            spanCount = 3,
            itemCount = 6,
            adapter = adapter,
            decoration = createDecoration(
                mainAxisDividerSize = 4,
                crossAxisDividerSize = 6,
            ),
        )
        layoutRecyclerView(recyclerView)
        adapter.resetFullSpanQueryCount()

        recyclerView.draw(
            Canvas(Bitmap.createBitmap(RECYCLER_SIZE, RECYCLER_SIZE, Bitmap.Config.ARGB_8888)),
        )

        assertEquals(0, adapter.fullSpanQueryCount)
    }

    @Test
    fun drawAtLargePosition_doesNotQueryFullSpanTopologyFromAdapterStart() {
        val adapter: TestAdapter = TestAdapter(itemCountValue = LARGE_ITEM_COUNT)
        val divider: RecordingDrawable = RecordingDrawable()
        val layoutManager: StaggeredGridLayoutManager = StaggeredGridLayoutManager(
            2,
            RecyclerView.VERTICAL,
        ).apply {
            gapStrategy = StaggeredGridLayoutManager.GAP_HANDLING_NONE
            scrollToPosition(LARGE_POSITION)
        }
        val recyclerView: RecyclerView = DirectionRecyclerView(
            RuntimeEnvironment.getApplication(),
            View.LAYOUT_DIRECTION_LTR,
        ).apply {
            this.layoutManager = layoutManager
            this.adapter = adapter
            addItemDecoration(
                createDecoration(
                    mainAxisDividerSize = 4,
                    crossAxisDividerSize = 0,
                    mainAxisDivider = divider,
                ),
            )
        }
        layoutRecyclerView(recyclerView)
        adapter.resetFullSpanQueryCount()
        val canvas: Canvas = Canvas(
            Bitmap.createBitmap(RECYCLER_SIZE, RECYCLER_SIZE, Bitmap.Config.ARGB_8888),
        )

        recyclerView.draw(canvas)

        assertTrue(recyclerView.childCount > 0)
        assertTrue(divider.drawCalls.isNotEmpty())
        assertEquals(0, adapter.fullSpanQueryCount)
    }

    private fun createDecoration(
        mainAxisDividerSize: Int = 0,
        crossAxisDividerSize: Int = 0,
        mainAxisDividerCrossAxisStartMargin: Int = 0,
        mainAxisDividerCrossAxisEndMargin: Int = 0,
        crossAxisDividerMainAxisStartMargin: Int = 0,
        crossAxisDividerMainAxisEndMargin: Int = 0,
        mainAxisDivider: Drawable? = null,
        crossAxisDivider: Drawable? = null,
    ): StaggeredItemDividerDecoration = StaggeredItemDividerDecoration(
        mainAxisDividerSize = mainAxisDividerSize,
        crossAxisDividerSize = crossAxisDividerSize,
        mainAxisDividerCrossAxisStartMargin = mainAxisDividerCrossAxisStartMargin,
        mainAxisDividerCrossAxisEndMargin = mainAxisDividerCrossAxisEndMargin,
        crossAxisDividerMainAxisStartMargin = crossAxisDividerMainAxisStartMargin,
        crossAxisDividerMainAxisEndMargin = crossAxisDividerMainAxisEndMargin,
        mainAxisDivider = mainAxisDivider,
        crossAxisDivider = crossAxisDivider,
    )

    private fun createRecyclerView(
        spanCount: Int,
        itemCount: Int,
        decoration: StaggeredItemDividerDecoration,
        orientation: Int = RecyclerView.VERTICAL,
        reverseLayout: Boolean = false,
        layoutDirection: Int = View.LAYOUT_DIRECTION_LTR,
        fullSpanPositions: Set<Int> = emptySet(),
        adapter: RecyclerView.Adapter<*> = TestAdapter(
            itemCountValue = itemCount,
            fullSpanPositions = fullSpanPositions,
        ),
    ): RecyclerView = DirectionRecyclerView(
        RuntimeEnvironment.getApplication(),
        layoutDirection,
    ).apply {
        layoutManager = StaggeredGridLayoutManager(spanCount, orientation).apply {
            this.reverseLayout = reverseLayout
            gapStrategy = StaggeredGridLayoutManager.GAP_HANDLING_NONE
        }
        this.adapter = adapter
        addItemDecoration(decoration)
    }

    private fun layoutRecyclerView(recyclerView: RecyclerView) {
        val sizeSpec: Int = View.MeasureSpec.makeMeasureSpec(RECYCLER_SIZE, View.MeasureSpec.EXACTLY)
        recyclerView.measure(sizeSpec, sizeSpec)
        recyclerView.layout(0, 0, RECYCLER_SIZE, RECYCLER_SIZE)
    }

    private fun childAtAdapterPosition(recyclerView: RecyclerView, position: Int): View {
        for (i: Int in 0 until recyclerView.childCount) {
            val child: View = recyclerView.getChildAt(i)
            if (recyclerView.getChildAdapterPosition(child) == position) return child
        }
        error("Missing child at adapter position $position.")
    }

    private fun decorationOffsets(recyclerView: RecyclerView, position: Int): Rect {
        val layoutManager: RecyclerView.LayoutManager = checkNotNull(recyclerView.layoutManager)
        val child: View = childAtAdapterPosition(recyclerView, position)
        return Rect(
            layoutManager.getLeftDecorationWidth(child),
            layoutManager.getTopDecorationHeight(child),
            layoutManager.getRightDecorationWidth(child),
            layoutManager.getBottomDecorationHeight(child),
        )
    }

    private open class PlainTestAdapter(
        private val itemCountValue: Int,
    ) : RecyclerView.Adapter<TestViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TestViewHolder {
            val itemView: View = View(parent.context)
            itemView.layoutParams = StaggeredGridLayoutManager.LayoutParams(ITEM_WIDTH, ITEM_HEIGHT)
            return TestViewHolder(itemView)
        }

        override fun onBindViewHolder(holder: TestViewHolder, position: Int) {
        }

        override fun getItemCount(): Int = itemCountValue
    }

    private class TestAdapter(
        private val itemCountValue: Int,
        private val fullSpanPositions: Set<Int> = emptySet(),
        private val layoutFullSpanPositions: Set<Int> = fullSpanPositions,
    ) : PlainTestAdapter(itemCountValue), StaggeredFullSpanProvider {

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

    private class RecordingDrawable(
        private val name: String? = null,
        private val drawOrder: MutableList<String>? = null,
    ) : Drawable() {

        val drawCalls: ArrayList<Rect> = ArrayList()

        override fun draw(canvas: Canvas) {
            name?.let { drawOrder?.add(it) }
            drawCalls.add(Rect(bounds))
        }

        override fun setAlpha(alpha: Int) {
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
        }

        @Deprecated("Deprecated in Android")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private companion object {

        private const val DECORATION_LOG_TAG: String = "RecyclerViewDecoration"
        private const val RECYCLER_SIZE: Int = 300
        private const val ITEM_WIDTH: Int = 40
        private const val ITEM_HEIGHT: Int = 30
        private const val SCROLLING_ITEM_COUNT: Int = 40
        private const val LARGE_ITEM_COUNT: Int = 100_000
        private const val LARGE_POSITION: Int = 50_000
    }
}
