package com.whisper.kit.view.input

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.text.InputFilter
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * 验证分格文本输入控件的输入和绘制边界.
 *
 * @author whisper
 * @since 2026/08/14
 */
@RunWith(RobolectricTestRunner::class)
class KitCodeInputEditTextTest {

    /**
     * 验证 codeLength 同时限制输入长度, 文本仍由原生 Editable 持有.
     */
    @Test
    fun codeLength_limitsNativeText() {
        val input: KitCodeInputEditText = createInput()

        input.codeLength = 4
        input.setText("123456")

        assertEquals("1234", input.text.toString())
    }

    /**
     * 验证原生 LengthFilter 是分格数量的 XML 和运行时数据源.
     */
    @Test
    fun nativeLengthFilter_controlsCodeLength() {
        val input: KitCodeInputEditText = createInput()

        input.filters = arrayOf(InputFilter.LengthFilter(4))
        input.setText("123456")

        assertEquals(4, input.codeLength)
        assertEquals("1234", input.text.toString())
    }

    /**
     * 验证控件隐藏光标, 并保留供绘制和输入法读取的原生 hint.
     */
    @Test
    fun init_hidesCursorAndRetainsHint() {
        val input: KitCodeInputEditText = createInput()

        input.hint = "-"

        assertFalse(input.isCursorVisible)
        assertEquals("-", input.hint.toString())
    }

    /**
     * 验证点击或调用方改变选区时, 输入位置固定回到文本末尾.
     */
    @Test
    fun selectionChanged_movesSelectionToTextEnd() {
        val input: KitCodeInputEditText = createInput()
        input.setText("123")

        input.setSelection(0)

        assertEquals(3, input.selectionStart)
        assertEquals(3, input.selectionEnd)
    }

    /**
     * 验证配置输入格并存在部分文本时可以完成绘制.
     */
    @Test
    fun draw_withConfiguredItemsDoesNotCrash() {
        val input: KitCodeInputEditText = createInput().apply {
            background = null
            setPadding(0, 0, 0, 0)
            codeLength = 2
            itemBackground = ColorDrawable(Color.RED)
            itemWidth = 60f
            itemHeight = 40f
            itemSpacing = 10f
            hint = "-"
            setText("1")
        }

        draw(input, width = 130, height = 40)
    }

    /**
     * 验证 wrap_content 按格子尺寸、数量、间距和 padding 计算期望尺寸.
     */
    @Test
    fun measure_wrapContentUsesItemDesiredSize() {
        val input: KitCodeInputEditText = createInput().apply {
            background = null
            minWidth = 0
            minHeight = 0
            setPadding(2, 3, 4, 5)
            codeLength = 3
            itemWidth = 20f
            itemHeight = 30f
            itemSpacing = 5f
        }

        val widthSpec: Int = View.MeasureSpec.makeMeasureSpec(1_000, View.MeasureSpec.AT_MOST)
        val heightSpec: Int = View.MeasureSpec.makeMeasureSpec(1_000, View.MeasureSpec.AT_MOST)
        input.measure(widthSpec, heightSpec)

        assertEquals(76, input.measuredWidth)
        assertEquals(38, input.measuredHeight)
    }

    /**
     * 验证父布局控制宽度时忽略期望间距, 根据格宽和数量均分剩余空间.
     */
    @Test
    fun draw_parentControlledWidthDistributesSpacingByLength() {
        val background = RecordingDrawable()
        val input: KitCodeInputEditText = createInput().apply {
            this.background = null
            setPadding(0, 0, 0, 0)
            codeLength = 3
            itemBackground = background
            itemWidth = 20f
            itemHeight = 20f
            itemSpacing = 100f
        }

        draw(input, width = 100, height = 20)

        assertEquals(
            listOf(Rect(0, 0, 20, 20), Rect(40, 0, 60, 20), Rect(80, 0, 100, 20)),
            background.drawnBounds,
        )
    }

    /**
     * 验证父布局宽度不足时按负间距继续绘制, 通过格子重叠暴露尺寸问题.
     */
    @Test
    fun draw_insufficientParentWidthStillDrawsEveryItem() {
        val background = RecordingDrawable()
        val input: KitCodeInputEditText = createInput().apply {
            this.background = null
            setPadding(0, 0, 0, 0)
            codeLength = 3
            itemBackground = background
            itemWidth = 20f
            itemHeight = 20f
            itemSpacing = 100f
        }

        draw(input, width = 30, height = 20)

        assertEquals(
            listOf(Rect(0, 0, 20, 20), Rect(5, 0, 25, 20), Rect(10, 0, 30, 20)),
            background.drawnBounds,
        )
    }

    private fun createInput(): KitCodeInputEditText =
        KitCodeInputEditText(RuntimeEnvironment.getApplication())

    private fun draw(input: KitCodeInputEditText, width: Int, height: Int) {
        val widthSpec: Int = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
        val heightSpec: Int = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        input.measure(widthSpec, heightSpec)
        input.layout(0, 0, width, height)
        val bitmap: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas: Canvas = Canvas(bitmap)
        val onDraw = KitCodeInputEditText::class.java.getDeclaredMethod(
            "onDraw",
            Canvas::class.java,
        ).apply {
            isAccessible = true
        }
        onDraw.invoke(input, canvas)
    }

    /**
     * 记录每次绘制边界的测试 Drawable.
     */
    private class RecordingDrawable : Drawable() {

        val drawnBounds: MutableList<Rect> = mutableListOf()

        override fun draw(canvas: Canvas) {
            drawnBounds += Rect(bounds)
        }

        override fun setAlpha(alpha: Int) = Unit

        override fun setColorFilter(colorFilter: ColorFilter?) = Unit

        @Deprecated("Deprecated in Android SDK")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
