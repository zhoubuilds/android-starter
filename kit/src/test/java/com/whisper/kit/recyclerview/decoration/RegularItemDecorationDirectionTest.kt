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
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowCanvas
import kotlin.math.roundToInt

/**
 * 验证 RecyclerView decoration 在布局方向上的边界行为.
 *
 * @author whisper
 * @since 2026/07/30
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RegularItemDecorationDirectionTest {

    @Test
    fun itemDividerDecoration_whenAnySizeOrMarginIsNegative_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException::class.java) {
            createRegularItemDividerDecoration(mainAxisDividerSize = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            createRegularItemDividerDecoration(crossAxisDividerSize = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            createRegularItemDividerDecoration(mainAxisDividerCrossAxisStartMargin = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            createRegularItemDividerDecoration(mainAxisDividerCrossAxisEndMargin = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            createRegularItemDividerDecoration(crossAxisDividerMainAxisStartMargin = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            createRegularItemDividerDecoration(crossAxisDividerMainAxisEndMargin = -1)
        }
    }

    @Test
    fun itemDividerDecoration_nullDrawables_preserveTransparentGridSpacing() {
        val recyclerView: RecyclerView = createRecyclerView(
            layoutManager = GridLayoutManager(RuntimeEnvironment.getApplication(), 2),
            layoutDirection = View.LAYOUT_DIRECTION_LTR,
            adapter = TestAdapter(itemCountValue = 4),
        )
        val decoration: RegularItemDividerDecoration = RegularItemDividerDecoration(
            mainAxisDividerSize = 5,
            crossAxisDividerSize = 3,
            mainAxisDivider = null,
            crossAxisDivider = null,
        )
        recyclerView.addItemDecoration(decoration)

        layoutRecyclerView(recyclerView)

        assertEquals(Rect(0, 0, 1, 0), decorationOffsets(recyclerView, position = 0))
        assertEquals(Rect(2, 0, 0, 0), decorationOffsets(recyclerView, position = 1))
        assertEquals(Rect(0, 5, 1, 0), decorationOffsets(recyclerView, position = 2))
        assertEquals(Rect(2, 5, 0, 0), decorationOffsets(recyclerView, position = 3))
    }

    /**
     * 验证横向 RTL 线性布局会将 adapter 起始侧映射到物理右侧.
     */
    @Test
    fun itemSpaceDecoration_horizontalRtlLinear_placesStartSpaceOnRight() {
        val recyclerView: RecyclerView = createRecyclerView(
            layoutManager = LinearLayoutManager(
                RuntimeEnvironment.getApplication(),
                RecyclerView.HORIZONTAL,
                false,
            ),
            layoutDirection = View.LAYOUT_DIRECTION_RTL,
        )
        val decoration: RegularItemSpaceDecoration = RegularItemSpaceDecoration(
            mainAxisSpace = 10,
            crossAxisSpace = 0,
            startSpace = 30,
            endSpace = 40,
        )
        recyclerView.addItemDecoration(decoration)

        layoutRecyclerView(recyclerView)
        val outRect: Rect = decorationOffsets(recyclerView, position = 0)
        val secondOutRect: Rect = decorationOffsets(recyclerView, position = 1)

        assertEquals(0, outRect.left)
        assertEquals(30, outRect.right)
        assertEquals(0, secondOutRect.left)
        assertEquals(10, secondOutRect.right)
    }

    /**
     * 验证横向 RTL + reverseLayout 会再次反转为物理左侧起始.
     */
    @Test
    fun itemSpaceDecoration_horizontalRtlReverseLinear_placesStartSpaceOnLeft() {
        val recyclerView: RecyclerView = createRecyclerView(
            layoutManager = LinearLayoutManager(
                RuntimeEnvironment.getApplication(),
                RecyclerView.HORIZONTAL,
                true,
            ),
            layoutDirection = View.LAYOUT_DIRECTION_RTL,
        )
        val decoration: RegularItemSpaceDecoration = RegularItemSpaceDecoration(
            mainAxisSpace = 10,
            crossAxisSpace = 0,
            startSpace = 30,
            endSpace = 40,
        )
        recyclerView.addItemDecoration(decoration)

        layoutRecyclerView(recyclerView)
        val outRect: Rect = decorationOffsets(recyclerView, position = 0)
        val secondOutRect: Rect = decorationOffsets(recyclerView, position = 1)

        assertEquals(30, outRect.left)
        assertEquals(0, outRect.right)
        assertEquals(10, secondOutRect.left)
        assertEquals(0, secondOutRect.right)
    }

    /**
     * 验证垂直 RTL 网格会镜像交叉轴物理 left/right 间距.
     */
    @Test
    fun itemSpaceDecoration_verticalRtlGrid_mirrorsCrossAxisOffsets() {
        val recyclerView: RecyclerView = createRecyclerView(
            layoutManager = GridLayoutManager(
                RuntimeEnvironment.getApplication(),
                3,
                RecyclerView.VERTICAL,
                false,
            ),
            layoutDirection = View.LAYOUT_DIRECTION_RTL,
        )
        val decoration: RegularItemSpaceDecoration = RegularItemSpaceDecoration(
            mainAxisSpace = 10,
            crossAxisSpace = 12,
            startSpace = 30,
            endSpace = 40,
        )
        recyclerView.addItemDecoration(decoration)

        layoutRecyclerView(recyclerView)
        val outRect: Rect = decorationOffsets(recyclerView, position = 0)

        assertEquals(8, outRect.left)
        assertEquals(0, outRect.right)
    }

    /**
     * 验证垂直 RTL 主轴分割线的交叉轴 start/end margin 映射到正确物理边.
     */
    @Test
    fun itemDividerDecoration_verticalRtlLinear_mapsCrossAxisMarginsToPhysicalLeft() {
        val recyclerView: RecyclerView = createRecyclerView(
            layoutManager = LinearLayoutManager(
                RuntimeEnvironment.getApplication(),
                RecyclerView.VERTICAL,
                false,
            ),
            layoutDirection = View.LAYOUT_DIRECTION_RTL,
        )
        recyclerView.setPaddingRelative(10, 0, 20, 0)
        layoutRecyclerView(recyclerView)
        val divider: RecordingDrawable = RecordingDrawable()
        val decoration: RegularItemDividerDecoration = RegularItemDividerDecoration(
            mainAxisDividerSize = 4,
            crossAxisDividerSize = 0,
            mainAxisDividerCrossAxisStartMargin = 3,
            mainAxisDividerCrossAxisEndMargin = 7,
            crossAxisDividerMainAxisStartMargin = 0,
            crossAxisDividerMainAxisEndMargin = 0,
            mainAxisDivider = divider,
            crossAxisDivider = null,
        )
        recyclerView.addItemDecoration(decoration)
        layoutRecyclerView(recyclerView)
        val canvas: Canvas = Canvas(Bitmap.createBitmap(RECYCLER_SIZE, RECYCLER_SIZE, Bitmap.Config.ARGB_8888))

        recyclerView.draw(canvas)

        assertTrue(divider.drawCalls.isNotEmpty())
        assertEquals(260, divider.drawCalls.first().bounds.width())
    }

    @Test
    fun itemDividerDecoration_verticalLinearWithoutClipToPadding_usesFullRecyclerViewWidth() {
        val recyclerView: RecyclerView = createRecyclerView(
            layoutManager = LinearLayoutManager(RuntimeEnvironment.getApplication()),
            layoutDirection = View.LAYOUT_DIRECTION_LTR,
        ).apply {
            setPadding(11, 13, 17, 19)
            clipToPadding = false
        }
        val divider: RecordingDrawable = RecordingDrawable()
        recyclerView.addItemDecoration(
            createRegularItemDividerDecoration(
                mainAxisDividerSize = 4,
                mainAxisDividerCrossAxisStartMargin = 3,
                mainAxisDividerCrossAxisEndMargin = 5,
                mainAxisDivider = divider,
            ),
        )
        layoutRecyclerView(recyclerView)

        recyclerView.draw(
            Canvas(Bitmap.createBitmap(RECYCLER_SIZE, RECYCLER_SIZE, Bitmap.Config.ARGB_8888)),
        )

        assertTrue(divider.drawCalls.isNotEmpty())
        assertTrue(divider.drawCalls.all { drawCall: DrawCall ->
            drawCall.bounds.width() == RECYCLER_SIZE - 3 - 5
        })
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun itemDividerDecoration_verticalLinearWithClipToPadding_clipsTranslatedDividerFromPadding() {
        val recyclerView: RecyclerView = createRecyclerView(
            layoutManager = LinearLayoutManager(RuntimeEnvironment.getApplication()),
            layoutDirection = View.LAYOUT_DIRECTION_LTR,
        ).apply {
            setPadding(0, 20, 0, 0)
            clipToPadding = true
        }
        recyclerView.addItemDecoration(
            createRegularItemDividerDecoration(
                mainAxisDividerSize = 4,
                mainAxisDivider = ColorDrawable(Color.RED),
            ),
        )
        layoutRecyclerView(recyclerView)
        val secondChild: View = childAtAdapterPosition(recyclerView, position = 1)
        secondChild.translationY = -40f
        val dividerTop: Int = secondChild.top - 4 + secondChild.translationY.toInt()
        val bitmap: Bitmap = Bitmap.createBitmap(RECYCLER_SIZE, RECYCLER_SIZE, Bitmap.Config.ARGB_8888)

        recyclerView.draw(Canvas(bitmap))

        assertTrue(dividerTop in 0 until recyclerView.paddingTop)
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(RECYCLER_SIZE / 2, dividerTop + 1))
    }

    @Test
    fun itemDividerDecoration_whenMarginsExceedCrossAxis_doesNotOverflowDrawableSize() {
        val recyclerView: RecyclerView = createRecyclerView(
            layoutManager = LinearLayoutManager(RuntimeEnvironment.getApplication()),
            layoutDirection = View.LAYOUT_DIRECTION_LTR,
        )
        val divider: RecordingDrawable = RecordingDrawable()
        recyclerView.addItemDecoration(
            createRegularItemDividerDecoration(
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
        assertTrue(divider.drawCalls.all { drawCall: DrawCall -> drawCall.bounds.width() == 0 })
    }

    @Test
    fun itemDividerDecoration_verticalLinear_followsLogicalStartItemTranslation() {
        val recyclerView: RecyclerView = createRecyclerView(
            layoutManager = LinearLayoutManager(
                RuntimeEnvironment.getApplication(),
                RecyclerView.VERTICAL,
                false,
            ),
            layoutDirection = View.LAYOUT_DIRECTION_LTR,
        )
        val dividerSize: Int = 4
        val divider: RecordingDrawable = RecordingDrawable()
        val decoration: RegularItemDividerDecoration = createRegularItemDividerDecoration(
            mainAxisDividerSize = dividerSize,
            mainAxisDivider = divider,
        )
        recyclerView.addItemDecoration(decoration)
        layoutRecyclerView(recyclerView)
        val firstChild: View = childAtAdapterPosition(recyclerView, position = 0)
        val secondChild: View = childAtAdapterPosition(recyclerView, position = 1)
        secondChild.translationY = 11f
        val staticDividerTop: Int = secondChild.top - dividerSize
        val translatedDividerTop: Int = staticDividerTop + secondChild.translationY.toInt()
        val canvas: Canvas = Canvas(Bitmap.createBitmap(RECYCLER_SIZE, RECYCLER_SIZE, Bitmap.Config.ARGB_8888))

        recyclerView.draw(canvas)

        assertEquals(firstChild.bottom, staticDividerTop)
        val canvasDescription: String = ShadowCanvas.visualize(canvas)
        assertTrue(canvasDescription, canvasDescription.contains(" at (0,$translatedDividerTop)"))
    }

    @Test
    fun itemDividerDecoration_gridDrawAtLargePosition_doesNotScanFromAdapterStart() {
        val lookup: CountingSpanSizeLookup = CountingSpanSizeLookup()
        val layoutManager: GridLayoutManager = GridLayoutManager(
            RuntimeEnvironment.getApplication(),
            LARGE_GRID_SPAN_COUNT,
        ).apply {
            spanSizeLookup = lookup
            scrollToPosition(LARGE_GRID_POSITION)
        }
        val recyclerView: RecyclerView = createRecyclerView(
            layoutManager = layoutManager,
            layoutDirection = View.LAYOUT_DIRECTION_LTR,
            adapter = TestAdapter(itemCountValue = LARGE_GRID_ITEM_COUNT),
        )
        val decoration: RegularItemDividerDecoration = createRegularItemDividerDecoration(
            mainAxisDividerSize = 4,
            mainAxisDivider = RecordingDrawable(),
        )
        recyclerView.addItemDecoration(decoration)
        layoutRecyclerView(recyclerView)
        lookup.reset()
        val canvas: Canvas = Canvas(Bitmap.createBitmap(RECYCLER_SIZE, RECYCLER_SIZE, Bitmap.Config.ARGB_8888))

        recyclerView.draw(canvas)

        assertTrue(recyclerView.childCount > 0)
        assertEquals(0, lookup.callCount)
    }

    @Test
    fun itemDividerDecoration_afterGridMoveAndManualInvalidation_recalculatesFinalOwnership() {
        val adapter: TestAdapter = TestAdapter(itemCountValue = GRID_ANIMATION_ITEM_COUNT)
        val recyclerView: RecyclerView = createRecyclerView(
            layoutManager = GridLayoutManager(
                RuntimeEnvironment.getApplication(),
                GRID_ANIMATION_SPAN_COUNT,
            ),
            layoutDirection = View.LAYOUT_DIRECTION_LTR,
            adapter = adapter,
        )
        val divider: RecordingDrawable = RecordingDrawable()
        val decoration: RegularItemDividerDecoration = createRegularItemDividerDecoration(
            mainAxisDividerSize = 4,
            mainAxisDivider = divider,
        )
        recyclerView.addItemDecoration(decoration)
        layoutRecyclerView(recyclerView)
        val canvas: Canvas = Canvas(Bitmap.createBitmap(RECYCLER_SIZE, RECYCLER_SIZE, Bitmap.Config.ARGB_8888))
        recyclerView.draw(canvas)
        assertEquals(2, divider.drawCalls.size)
        divider.drawCalls.clear()

        adapter.notifyItemMoved(2, 0)
        recyclerView.draw(canvas)
        divider.drawCalls.clear()

        layoutRecyclerView(recyclerView)
        assertTrue(!recyclerView.hasPendingAdapterUpdates())
        recyclerView.invalidateItemDecorations()
        assertTrue(recyclerView.isLayoutRequested)

        recyclerView.itemAnimator?.endAnimations()
        layoutRecyclerView(recyclerView)
        assertEquals(0, decorationOffsets(recyclerView, position = 0).top)
        assertEquals(0, decorationOffsets(recyclerView, position = 1).top)
        assertEquals(4, decorationOffsets(recyclerView, position = 2).top)
        assertEquals(4, decorationOffsets(recyclerView, position = 3).top)
        val completedLayoutCanvas: Canvas =
            Canvas(Bitmap.createBitmap(RECYCLER_SIZE, RECYCLER_SIZE, Bitmap.Config.ARGB_8888))
        recyclerView.draw(completedLayoutCanvas)

        val expectedTranslations: List<Pair<Int, Int>> = listOf(2, 3).map { position: Int ->
            val child: View = childAtAdapterPosition(recyclerView, position)
            Pair(
                child.left + child.translationX.roundToInt(),
                child.top - 4 + child.translationY.roundToInt(),
            )
        }
        val canvasDescription: String = ShadowCanvas.visualize(completedLayoutCanvas)
        assertEquals(2, divider.drawCalls.size)
        assertTrue(canvasDescription, expectedTranslations.all { translation: Pair<Int, Int> ->
            canvasDescription.contains(" at (${translation.first},${translation.second})")
        })
    }

    @Test
    fun itemDividerDecoration_verticalGrid_drawsContinuousCrossAxisDividerBeforeItemSegments() {
        val recyclerView: RecyclerView = createRecyclerView(
            layoutManager = GridLayoutManager(
                RuntimeEnvironment.getApplication(),
                2,
                RecyclerView.VERTICAL,
                false,
            ),
            layoutDirection = View.LAYOUT_DIRECTION_LTR,
        )
        val drawOrder: ArrayList<String> = ArrayList()
        val mainAxisDivider: RecordingDrawable = RecordingDrawable("main", drawOrder)
        val crossAxisDivider: RecordingDrawable = RecordingDrawable("cross", drawOrder)
        val decoration: RegularItemDividerDecoration = createRegularItemDividerDecoration(
            mainAxisDividerSize = 4,
            crossAxisDividerSize = 6,
            mainAxisDividerCrossAxisStartMargin = 3,
            mainAxisDividerCrossAxisEndMargin = 5,
            crossAxisDividerMainAxisStartMargin = 7,
            crossAxisDividerMainAxisEndMargin = 9,
            mainAxisDivider = mainAxisDivider,
            crossAxisDivider = crossAxisDivider,
        )
        recyclerView.addItemDecoration(decoration)
        layoutRecyclerView(recyclerView)
        val firstChild: View = childAtAdapterPosition(recyclerView, position = 0)
        val lastChild: View = childAtAdapterPosition(recyclerView, position = 2)
        val canvas: Canvas = Canvas(Bitmap.createBitmap(RECYCLER_SIZE, RECYCLER_SIZE, Bitmap.Config.ARGB_8888))

        recyclerView.draw(canvas)

        assertEquals(listOf("cross", "main"), drawOrder)
        assertEquals(1, crossAxisDivider.drawCalls.size)
        assertEquals(6, crossAxisDivider.drawCalls.single().bounds.width())
        assertEquals(
            lastChild.bottom - firstChild.top - 7 - 9,
            crossAxisDivider.drawCalls.single().bounds.height(),
        )
        assertEquals(1, mainAxisDivider.drawCalls.size)
        assertEquals(4, mainAxisDivider.drawCalls.single().bounds.height())
        assertEquals(
            lastChild.width - 3 - 5,
            mainAxisDivider.drawCalls.single().bounds.width(),
        )
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun itemDividerDecoration_verticalGridAfterScroll_keepsRecycledRowIntersectionFilled() {
        val layoutManager: GridLayoutManager = GridLayoutManager(
            RuntimeEnvironment.getApplication(),
            2,
            RecyclerView.VERTICAL,
            false,
        )
        val recyclerView: RecyclerView = createRecyclerView(
            layoutManager = layoutManager,
            layoutDirection = View.LAYOUT_DIRECTION_LTR,
            adapter = TestAdapter(itemCountValue = SCROLLING_GRID_ITEM_COUNT),
        )
        recyclerView.addItemDecoration(
            createRegularItemDividerDecoration(
                mainAxisDividerSize = 4,
                crossAxisDividerSize = 6,
                crossAxisDivider = ColorDrawable(Color.RED),
            ),
        )
        layoutRecyclerView(recyclerView)

        recyclerView.scrollBy(0, ITEM_HEIGHT + 1)

        assertEquals(null, layoutManager.findViewByPosition(0))
        assertEquals(null, layoutManager.findViewByPosition(1))
        val firstVisibleGroupStart: View = childAtAdapterPosition(recyclerView, position = 2)
        val boundaryAnchor: View = childAtAdapterPosition(recyclerView, position = 3)
        val bitmap: Bitmap = Bitmap.createBitmap(RECYCLER_SIZE, RECYCLER_SIZE, Bitmap.Config.ARGB_8888)

        recyclerView.draw(Canvas(bitmap))

        assertTrue(firstVisibleGroupStart.top in 1..4)
        assertEquals(
            Color.RED,
            bitmap.getPixel(
                boundaryAnchor.left - 3,
                firstVisibleGroupStart.top - 1,
            ),
        )
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun itemDividerDecoration_verticalReverseGridAfterScroll_keepsRecycledRowIntersectionFilled() {
        val layoutManager: GridLayoutManager = GridLayoutManager(
            RuntimeEnvironment.getApplication(),
            2,
            RecyclerView.VERTICAL,
            true,
        )
        val recyclerView: RecyclerView = createRecyclerView(
            layoutManager = layoutManager,
            layoutDirection = View.LAYOUT_DIRECTION_LTR,
            adapter = TestAdapter(itemCountValue = SCROLLING_GRID_ITEM_COUNT),
        )
        recyclerView.addItemDecoration(
            createRegularItemDividerDecoration(
                mainAxisDividerSize = 4,
                crossAxisDividerSize = 6,
                mainAxisDivider = ColorDrawable(Color.BLUE),
                crossAxisDivider = ColorDrawable(Color.RED),
            ),
        )
        layoutRecyclerView(recyclerView)

        recyclerView.scrollBy(0, -(ITEM_HEIGHT + 1))

        assertEquals(null, layoutManager.findViewByPosition(0))
        assertEquals(null, layoutManager.findViewByPosition(1))
        val firstVisibleGroupStart: View = childAtAdapterPosition(recyclerView, position = 2)
        val boundaryAnchor: View = childAtAdapterPosition(recyclerView, position = 3)
        val bitmap: Bitmap = Bitmap.createBitmap(RECYCLER_SIZE, RECYCLER_SIZE, Bitmap.Config.ARGB_8888)

        recyclerView.draw(Canvas(bitmap))

        assertTrue(firstVisibleGroupStart.bottom in RECYCLER_SIZE - 4 until RECYCLER_SIZE)
        assertEquals(
            Color.RED,
            bitmap.getPixel(
                boundaryAnchor.left - 3,
                firstVisibleGroupStart.bottom,
            ),
        )
    }

    @Test
    fun itemDividerDecoration_verticalGridWithoutClipToPadding_drawsContinuousLineIntoPadding() {
        val recyclerView: RecyclerView = createRecyclerView(
            layoutManager = GridLayoutManager(
                RuntimeEnvironment.getApplication(),
                2,
                RecyclerView.VERTICAL,
                false,
            ),
            layoutDirection = View.LAYOUT_DIRECTION_LTR,
        ).apply {
            setPadding(0, 20, 0, 20)
            clipToPadding = false
        }
        val divider: RecordingDrawable = RecordingDrawable()
        recyclerView.addItemDecoration(
            createRegularItemDividerDecoration(
                crossAxisDividerSize = 6,
                crossAxisDivider = divider,
            ),
        )
        layoutRecyclerView(recyclerView)
        val firstChild: View = childAtAdapterPosition(recyclerView, position = 0)
        firstChild.translationY = -firstChild.top.toFloat()

        recyclerView.draw(
            Canvas(Bitmap.createBitmap(RECYCLER_SIZE, RECYCLER_SIZE, Bitmap.Config.ARGB_8888)),
        )

        assertEquals(1, divider.drawCalls.size)
        assertEquals(0, divider.drawCalls.single().bounds.top)
    }

    @Test
    fun itemDividerDecoration_horizontalGrid_drawsCrossAxisDividerAlongMainAxis() {
        val recyclerView: RecyclerView = createRecyclerView(
            layoutManager = GridLayoutManager(
                RuntimeEnvironment.getApplication(),
                2,
                RecyclerView.HORIZONTAL,
                false,
            ),
            layoutDirection = View.LAYOUT_DIRECTION_LTR,
        )
        val divider: RecordingDrawable = RecordingDrawable()
        val decoration: RegularItemDividerDecoration = createRegularItemDividerDecoration(
            crossAxisDividerSize = 6,
            crossAxisDividerMainAxisStartMargin = 7,
            crossAxisDividerMainAxisEndMargin = 9,
            crossAxisDivider = divider,
        )
        recyclerView.addItemDecoration(decoration)
        layoutRecyclerView(recyclerView)
        val firstChild: View = childAtAdapterPosition(recyclerView, position = 0)
        val lastChild: View = childAtAdapterPosition(recyclerView, position = 2)
        val canvas: Canvas = Canvas(Bitmap.createBitmap(RECYCLER_SIZE, RECYCLER_SIZE, Bitmap.Config.ARGB_8888))

        recyclerView.draw(canvas)

        assertEquals(1, divider.drawCalls.size)
        assertEquals(6, divider.drawCalls.single().bounds.height())
        assertEquals(
            lastChild.right - firstChild.left - 7 - 9,
            divider.drawCalls.single().bounds.width(),
        )
    }

    @Test
    fun itemDividerDecoration_verticalRtlGrid_mirrorsContinuousSpanBoundaries() {
        val recyclerView: RecyclerView = createRecyclerView(
            layoutManager = GridLayoutManager(
                RuntimeEnvironment.getApplication(),
                3,
                RecyclerView.VERTICAL,
                false,
            ),
            layoutDirection = View.LAYOUT_DIRECTION_RTL,
        )
        val divider: RecordingDrawable = RecordingDrawable()
        val decoration: RegularItemDividerDecoration = createRegularItemDividerDecoration(
            crossAxisDividerSize = 12,
            crossAxisDivider = divider,
        )
        recyclerView.addItemDecoration(decoration)
        layoutRecyclerView(recyclerView)
        val canvas: Canvas = Canvas(Bitmap.createBitmap(RECYCLER_SIZE, RECYCLER_SIZE, Bitmap.Config.ARGB_8888))

        recyclerView.draw(canvas)

        assertEquals(2, divider.drawCalls.size)
        assertEquals(196, divider.drawCalls[0].bounds.left)
        assertEquals(92, divider.drawCalls[1].bounds.left)
    }

    @Test
    @Config(sdk = [26, 35])
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun itemDividerDecoration_verticalGrid_keepsIntersectionFilledWithoutCrossingSpanningItem() {
        val layoutManager: GridLayoutManager = GridLayoutManager(
            RuntimeEnvironment.getApplication(),
            2,
            RecyclerView.VERTICAL,
            false,
        ).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {

                override fun getSpanSize(position: Int): Int = if (position == 0) 2 else 1
            }
        }
        val recyclerView: RecyclerView = createRecyclerView(
            layoutManager = layoutManager,
            layoutDirection = View.LAYOUT_DIRECTION_LTR,
            adapter = TestAdapter(itemWidth = ViewGroup.LayoutParams.MATCH_PARENT),
        )
        val decoration: RegularItemDividerDecoration = createRegularItemDividerDecoration(
            mainAxisDividerSize = 4,
            crossAxisDividerSize = 6,
            mainAxisDivider = ColorDrawable(Color.BLUE),
            crossAxisDivider = ColorDrawable(Color.RED),
        )
        recyclerView.addItemDecoration(decoration)
        layoutRecyclerView(recyclerView)
        val spanningChild: View = childAtAdapterPosition(recyclerView, position = 0)
        val secondGroupChild: View = childAtAdapterPosition(recyclerView, position = 1)
        val bitmap: Bitmap = Bitmap.createBitmap(RECYCLER_SIZE, RECYCLER_SIZE, Bitmap.Config.ARGB_8888)

        recyclerView.draw(Canvas(bitmap))

        assertEquals(
            "spanningChild=${spanningChild.left},${spanningChild.top}," +
                "${spanningChild.right},${spanningChild.bottom}",
            Color.TRANSPARENT,
            bitmap.getPixel(RECYCLER_SIZE / 2, spanningChild.top + spanningChild.height / 2),
        )
        assertEquals(
            Color.RED,
            bitmap.getPixel(RECYCLER_SIZE / 2, secondGroupChild.top - 2),
        )
    }

    private fun createRegularItemDividerDecoration(
        mainAxisDividerSize: Int = 0,
        crossAxisDividerSize: Int = 0,
        mainAxisDividerCrossAxisStartMargin: Int = 0,
        mainAxisDividerCrossAxisEndMargin: Int = 0,
        crossAxisDividerMainAxisStartMargin: Int = 0,
        crossAxisDividerMainAxisEndMargin: Int = 0,
        mainAxisDivider: Drawable? = null,
        crossAxisDivider: Drawable? = null,
    ): RegularItemDividerDecoration = RegularItemDividerDecoration(
        mainAxisDividerSize = mainAxisDividerSize,
        crossAxisDividerSize = crossAxisDividerSize,
        mainAxisDividerCrossAxisStartMargin = mainAxisDividerCrossAxisStartMargin,
        mainAxisDividerCrossAxisEndMargin = mainAxisDividerCrossAxisEndMargin,
        crossAxisDividerMainAxisStartMargin = crossAxisDividerMainAxisStartMargin,
        crossAxisDividerMainAxisEndMargin = crossAxisDividerMainAxisEndMargin,
        mainAxisDivider = mainAxisDivider,
        crossAxisDivider = crossAxisDivider,
    )

    /**
     * 创建完成布局的 RecyclerView.
     */
    private fun createRecyclerView(
        layoutManager: RecyclerView.LayoutManager,
        layoutDirection: Int,
        adapter: RecyclerView.Adapter<*> = TestAdapter(),
    ): RecyclerView {
        val recyclerView: RecyclerView = DirectionRecyclerView(RuntimeEnvironment.getApplication(), layoutDirection)
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter
        layoutRecyclerView(recyclerView)
        return recyclerView
    }

    /**
     * 触发布局, 让测试可以按 adapter position 获取 child.
     */
    private fun layoutRecyclerView(recyclerView: RecyclerView) {
        val sizeSpec: Int = View.MeasureSpec.makeMeasureSpec(RECYCLER_SIZE, View.MeasureSpec.EXACTLY)
        recyclerView.measure(sizeSpec, sizeSpec)
        recyclerView.layout(0, 0, RECYCLER_SIZE, RECYCLER_SIZE)
    }

    /**
     * 查找指定 adapter position 对应的 child.
     */
    private fun childAtAdapterPosition(recyclerView: RecyclerView, position: Int): View {
        for (i: Int in 0 until recyclerView.childCount) {
            val child: View = recyclerView.getChildAt(i)
            if (recyclerView.getChildAdapterPosition(child) == position) {
                return child
            }
        }
        error("Missing child at adapter position $position.")
    }

    /**
     * 读取指定 item 由 ItemDecoration 提供的四边 offset.
     */
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

    /**
     * 测试用 Adapter.
     */
    private class TestAdapter(
        private val itemWidth: Int = ITEM_WIDTH,
        private val itemCountValue: Int = ITEM_COUNT,
    ) : RecyclerView.Adapter<TestViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TestViewHolder {
            val itemView: View = View(parent.context)
            itemView.layoutParams = RecyclerView.LayoutParams(itemWidth, ITEM_HEIGHT)
            return TestViewHolder(itemView)
        }

        override fun onBindViewHolder(holder: TestViewHolder, position: Int) {
        }

        override fun getItemCount(): Int = itemCountValue
    }

    /**
     * 测试用 ViewHolder.
     */
    private class TestViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    /**
     * 固定布局方向的测试 RecyclerView.
     */
    private class DirectionRecyclerView(
        context: Context,
        private val layoutDirectionValue: Int,
    ) : RecyclerView(context) {

        override fun getLayoutDirection(): Int = layoutDirectionValue
    }

    private class CountingSpanSizeLookup : GridLayoutManager.SpanSizeLookup() {

        var callCount: Int = 0
            private set

        override fun getSpanSize(position: Int): Int {
            callCount++
            return 1
        }

        fun reset() {
            callCount = 0
        }
    }

    /**
     * 记录绘制平移和 bounds 的 Drawable.
     */
    private class RecordingDrawable(
        private val name: String? = null,
        private val drawOrder: MutableList<String>? = null,
    ) : Drawable() {

        /**
         * 已发生的绘制调用.
         */
        val drawCalls: ArrayList<DrawCall> = ArrayList()

        override fun draw(canvas: Canvas) {
            name?.let { drawOrder?.add(it) }
            drawCalls.add(
                DrawCall(
                    bounds = Rect(bounds),
                ),
            )
            canvas.drawBitmap(
                Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888),
                0f,
                0f,
                null,
            )
        }

        override fun setAlpha(alpha: Int) {
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
        }

        @Suppress("OVERRIDE_DEPRECATION")
        override fun getOpacity(): Int = PixelFormat.TRANSPARENT
    }

    /**
     * 单次 Drawable 绘制信息.
     */
    private data class DrawCall(
        val bounds: Rect,
    )

    private companion object {

        /**
         * RecyclerView 测试尺寸.
         */
        private const val RECYCLER_SIZE: Int = 300

        /**
         * 测试 item 数量.
         */
        private const val ITEM_COUNT: Int = 3
        private const val LARGE_GRID_ITEM_COUNT: Int = 100_000
        private const val LARGE_GRID_POSITION: Int = 50_000
        private const val LARGE_GRID_SPAN_COUNT: Int = 4
        private const val GRID_ANIMATION_ITEM_COUNT: Int = 4
        private const val GRID_ANIMATION_SPAN_COUNT: Int = 2
        private const val SCROLLING_GRID_ITEM_COUNT: Int = 40

        /**
         * 测试 item 宽度.
         */
        private const val ITEM_WIDTH: Int = 40

        /**
         * 测试 item 高度.
         */
        private const val ITEM_HEIGHT: Int = 30
    }
}
