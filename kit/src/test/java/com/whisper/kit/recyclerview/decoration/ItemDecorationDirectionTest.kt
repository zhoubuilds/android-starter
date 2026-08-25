package com.whisper.kit.recyclerview.decoration

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 验证 RecyclerView decoration 在布局方向上的边界行为.
 *
 * @author whisper
 * @since 2026/07/30
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ItemDecorationDirectionTest {

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
        val decoration: ItemSpaceDecoration = ItemSpaceDecoration(
            mainAxisSpace = 10,
            crossAxisSpace = 0,
            startSpace = 30,
            endSpace = 40,
        )
        val outRect: Rect = Rect()

        decoration.getItemOffsets(outRect, childAtAdapterPosition(recyclerView, 0), recyclerView, RecyclerView.State())

        assertEquals(5, outRect.left)
        assertEquals(30, outRect.right)
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
        val decoration: ItemSpaceDecoration = ItemSpaceDecoration(
            mainAxisSpace = 10,
            crossAxisSpace = 0,
            startSpace = 30,
            endSpace = 40,
        )
        val outRect: Rect = Rect()

        decoration.getItemOffsets(outRect, childAtAdapterPosition(recyclerView, 0), recyclerView, RecyclerView.State())

        assertEquals(30, outRect.left)
        assertEquals(5, outRect.right)
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
        val decoration: ItemSpaceDecoration = ItemSpaceDecoration(
            mainAxisSpace = 10,
            crossAxisSpace = 12,
            startSpace = 30,
            endSpace = 40,
        )
        val outRect: Rect = Rect()

        decoration.getItemOffsets(outRect, childAtAdapterPosition(recyclerView, 0), recyclerView, RecyclerView.State())

        assertEquals(8, outRect.left)
        assertEquals(0, outRect.right)
    }

    /**
     * 验证垂直 RTL 主轴分割线使用逻辑 start/end margin 映射物理 left.
     */
    @Test
    fun itemDividerDecoration_verticalRtlLinear_mapsLogicalMarginsToPhysicalLeft() {
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
        val decoration: ItemDividerDecoration = ItemDividerDecoration(
            mainAxisDividerSize = 4,
            crossAxisDividerSize = 0,
            mainAxisDividerMarginStart = 3,
            mainAxisDividerMarginEnd = 7,
            crossAxisDividerMarginStart = 0,
            crossAxisDividerMarginEnd = 0,
            mainAxisDivider = divider,
            crossAxisDivider = null,
        )
        val canvas: Canvas = Canvas(Bitmap.createBitmap(RECYCLER_SIZE, RECYCLER_SIZE, Bitmap.Config.ARGB_8888))

        decoration.onDrawOver(canvas, recyclerView, RecyclerView.State())

        assertTrue(divider.drawCalls.isNotEmpty())
        assertEquals(260, divider.drawCalls.first().bounds.width())
    }

    /**
     * 创建完成布局的 RecyclerView.
     */
    private fun createRecyclerView(
        layoutManager: RecyclerView.LayoutManager,
        layoutDirection: Int,
    ): RecyclerView {
        val recyclerView: RecyclerView = DirectionRecyclerView(RuntimeEnvironment.getApplication(), layoutDirection)
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = TestAdapter()
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
     * 测试用 Adapter.
     */
    private class TestAdapter : RecyclerView.Adapter<TestViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TestViewHolder {
            val itemView: View = View(parent.context)
            itemView.layoutParams = RecyclerView.LayoutParams(ITEM_WIDTH, ITEM_HEIGHT)
            return TestViewHolder(itemView)
        }

        override fun onBindViewHolder(holder: TestViewHolder, position: Int) {
        }

        override fun getItemCount(): Int = ITEM_COUNT
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

    /**
     * 记录绘制平移和 bounds 的 Drawable.
     */
    private class RecordingDrawable : Drawable() {

        /**
         * 已发生的绘制调用.
         */
        val drawCalls: ArrayList<DrawCall> = ArrayList()

        override fun draw(canvas: Canvas) {
            drawCalls.add(
                DrawCall(
                    bounds = Rect(bounds),
                ),
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
