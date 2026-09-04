package com.whisper.kit.recyclerview.decoration

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 验证 Divider 分段绘制和极大合法参数下的坐标边界. Robolectric 的 Native Canvas 从 API 26
 * 开始可用, 因而 API 24 只验证与平台绘制无关的完整 bounds 契约.
 *
 * @author whisper
 * @since 2026/09/03
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 35])
class DividerDrawingGeometryTest {

    @Test
    fun nonNegativeDrawingSize_whenMarginsExceedSize_doesNotWrapPositive() {
        val remainingSize: Long = 300L - Int.MAX_VALUE - Int.MAX_VALUE

        assertEquals(0, nonNegativeDrawingSize(remainingSize))
    }

    @Test
    fun saturatedDrawingCoordinate_whenValueExceedsIntRange_clampsToBoundary() {
        assertEquals(Int.MAX_VALUE, saturatedDrawingCoordinate(Int.MAX_VALUE.toLong() + Int.MAX_VALUE))
        assertEquals(Int.MIN_VALUE, saturatedDrawingCoordinate(Int.MIN_VALUE.toLong() - Int.MAX_VALUE))
    }

    @Test
    fun drawDividerOutsideChildren_segmentsKeepFullBounds() {
        val divider: RecordingDrawable = RecordingDrawable()
        val fullBounds: Rect = Rect(DIVIDER_LEFT, 0, DIVIDER_RIGHT, BITMAP_HEIGHT)

        drawDividerOutsideChildren(
            canvas = Canvas(),
            divider = divider,
            left = fullBounds.left,
            top = fullBounds.top,
            right = fullBounds.right,
            bottom = fullBounds.bottom,
            orientation = RecyclerView.VERTICAL,
            childBounds = listOf(Rect(0, COVERED_START, BITMAP_WIDTH, COVERED_END)),
        )

        assertEquals(listOf(fullBounds, fullBounds), divider.drawBounds)
    }

    @Test
    @Config(sdk = [26, 35])
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun drawDividerOutsideChildren_segmentsClipCoveredArea() {
        val bitmap: Bitmap = Bitmap.createBitmap(BITMAP_WIDTH, BITMAP_HEIGHT, Bitmap.Config.ARGB_8888)

        drawDividerOutsideChildren(
            canvas = Canvas(bitmap),
            divider = RecordingDrawable(),
            left = DIVIDER_LEFT,
            top = 0,
            right = DIVIDER_RIGHT,
            bottom = BITMAP_HEIGHT,
            orientation = RecyclerView.VERTICAL,
            childBounds = listOf(Rect(0, COVERED_START, BITMAP_WIDTH, COVERED_END)),
        )

        assertEquals(Color.RED, bitmap.getPixel(DIVIDER_SAMPLE_X, VISIBLE_START_SAMPLE_Y))
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(DIVIDER_SAMPLE_X, COVERED_SAMPLE_Y))
        assertEquals(Color.RED, bitmap.getPixel(DIVIDER_SAMPLE_X, VISIBLE_END_SAMPLE_Y))
    }

    private class RecordingDrawable : Drawable() {

        val drawBounds: ArrayList<Rect> = ArrayList()
        private val paint: Paint = Paint().apply {
            color = Color.RED
        }

        override fun draw(canvas: Canvas) {
            drawBounds += Rect(bounds)
            canvas.drawRect(bounds, paint)
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
        }

        @Suppress("OVERRIDE_DEPRECATION")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private companion object {

        private const val BITMAP_WIDTH: Int = 20
        private const val BITMAP_HEIGHT: Int = 30
        private const val DIVIDER_LEFT: Int = 5
        private const val DIVIDER_RIGHT: Int = 10
        private const val DIVIDER_SAMPLE_X: Int = 7
        private const val COVERED_START: Int = 10
        private const val COVERED_END: Int = 20
        private const val VISIBLE_START_SAMPLE_Y: Int = 5
        private const val COVERED_SAMPLE_Y: Int = 15
        private const val VISIBLE_END_SAMPLE_Y: Int = 25
    }
}
