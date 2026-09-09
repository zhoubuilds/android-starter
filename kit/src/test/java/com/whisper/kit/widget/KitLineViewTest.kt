package com.whisper.kit.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.GradientDrawable
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.LinearLayout
import com.whisper.kit.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 验证分割线的方向映射, 测量约束, 点划绘制和运行时状态更新.
 *
 * @author whisper
 * @since 2026/09/09
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KitLineViewTest {

    @Test
    fun orientationConstants_matchAndroidSdk() {
        assertEquals(LinearLayout.HORIZONTAL, KitLineView.HORIZONTAL)
        assertEquals(LinearLayout.VERTICAL, KitLineView.VERTICAL)
    }

    @Test
    fun draw_defaultLineIsSolidAndUsesThemeSecondaryTextColors() {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), android.R.style.Theme_Material_Light)
        val attributes = context.obtainStyledAttributes(intArrayOf(android.R.attr.textColorSecondary))
        val colors = requireNotNull(attributes.getColorStateList(0))
        attributes.recycle()
        val line = KitLineView(context)

        assertEquals(KitLineView.HORIZONTAL, line.orientation)
        assertEquals(0f, line.dotWidth, 0f)
        assertEquals(0f, line.dashWidth, 0f)
        assertEquals(0f, line.gapWidth, 0f)
        for (enabled in listOf(true, false)) {
            line.isEnabled = enabled
            val expected = colors.getColorForState(line.drawableState, colors.defaultColor)
            val bitmap = draw(line)

            assertTrue("Expected visible theme color", Color.alpha(expected) > 0)
            assertEquals(expected, bitmap.getPixel(2, 2))
            assertEquals(expected, bitmap.getPixel(18, 2))
        }
    }

    @Test
    fun draw_missingThemeColorFallsBackToGray() {
        val application: Context = RuntimeEnvironment.getApplication()
        val context = ContextThemeWrapper(application, application.resources.newTheme())

        val bitmap = draw(KitLineView(context))

        assertEquals(Color.GRAY, bitmap.getPixel(2, 2))
    }

    @Test
    fun draw_xmlColorOverridesThemeEvenWithoutLineLengths() {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), android.R.style.Theme_Material_Light)
        val bitmap = draw(createLine(dotWidth = null, dashWidth = null, gapWidth = null, context = context))

        assertEquals(Color.RED, bitmap.getPixel(2, 2))
        assertEquals(Color.RED, bitmap.getPixel(18, 2))
    }

    @Test
    fun draw_xmlSelectorHonorsEnabledStateBeforeAndAfterFirstDraw() {
        val context: Context = RuntimeEnvironment.getApplication()
        val colors = context.getColorStateList(android.R.color.primary_text_light)
        assertTrue(colors.isStateful)

        for (initiallyEnabled in listOf(true, false)) {
            val line = createLine(color = "@android:color/primary_text_light").apply {
                isEnabled = initiallyEnabled
            }
            assertEquals(initiallyEnabled, line.isEnabled)
            val initialColor = colors.getColorForState(line.drawableState, colors.defaultColor)
            assertEquals(initialColor, draw(line).getPixel(2, 2))

            line.isEnabled = !initiallyEnabled
            val changedColor = colors.getColorForState(line.drawableState, colors.defaultColor)
            assertTrue("Expected distinct enabled and disabled colors", initialColor != changedColor)
            assertEquals(changedColor, draw(line).getPixel(2, 2))
        }
    }

    @Test
    fun draw_runtimeColorsFollowEnabledSelectedAndFocusedStates() {
        val line = createLine().apply {
            lineColors = ColorStateList(
                arrayOf(
                    intArrayOf(-android.R.attr.state_enabled),
                    intArrayOf(android.R.attr.state_focused),
                    intArrayOf(android.R.attr.state_selected),
                    intArrayOf()
                ),
                intArrayOf(Color.BLUE, Color.YELLOW, Color.GREEN, Color.RED)
            )
            isFocusableInTouchMode = true
        }
        assertEquals(Color.RED, draw(line).getPixel(2, 2))

        line.isSelected = true
        assertEquals(Color.GREEN, draw(line).getPixel(2, 2))
        line.isSelected = false
        assertEquals(Color.RED, draw(line).getPixel(2, 2))

        assertTrue(line.requestFocus())
        assertEquals(Color.YELLOW, draw(line).getPixel(2, 2))
        line.isFocusable = false
        assertFalse(line.isFocused)
        assertEquals(Color.RED, draw(line).getPixel(2, 2))

        line.isEnabled = false
        assertEquals(Color.BLUE, draw(line).getPixel(2, 2))
        line.isEnabled = true
        assertEquals(Color.RED, draw(line).getPixel(2, 2))
    }

    @Test
    fun draw_replacingColorsImmediatelyResolvesCurrentStateAndCanRestoreSolidColor() {
        val line = createLine().apply { isEnabled = false }
        draw(line)

        line.lineColors = ColorStateList(
            arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(Color.GREEN, Color.BLUE)
        )

        assertFalse(line.isLayoutRequested)
        assertEquals(Color.GREEN, draw(line).getPixel(2, 2))
        line.setLineColor(Color.MAGENTA)
        assertFalse(line.lineColors.isStateful)
        assertEquals(Color.MAGENTA, draw(line).getPixel(2, 2))
        line.isEnabled = true
        assertEquals(Color.MAGENTA, draw(line).getPixel(2, 2))
    }

    @Test
    fun orientation_runtimeChangeRequestsMeasurementAndRotatesPattern() {
        val line = createLine(gapWidth = "4px")
        val horizontalBitmap = draw(line)
        assertEquals(Color.RED, horizontalBitmap.getPixel(2, 6))
        assertEquals(Color.TRANSPARENT, horizontalBitmap.getPixel(6, 2))
        line.measure(atMost(20), atMost(20))
        line.layout(0, 0, line.measuredWidth, line.measuredHeight)
        assertEquals(20, line.measuredWidth)
        assertEquals(1, line.measuredHeight)
        assertFalse(line.isLayoutRequested)

        line.orientation = KitLineView.VERTICAL

        assertTrue(line.isLayoutRequested)
        line.measure(atMost(20), atMost(20))
        assertEquals(1, line.measuredWidth)
        assertEquals(20, line.measuredHeight)
        val bitmap = draw(line)
        assertEquals(Color.RED, bitmap.getPixel(6, 2))
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(2, 6))
    }

    @Test
    fun orientation_invalidValueLeavesCurrentDirectionUnchanged() {
        val line = createLine("vertical", gapWidth = "4px")
        draw(line)

        for (invalid in listOf(-1, 2, Int.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) { line.orientation = invalid }
            assertEquals(KitLineView.VERTICAL, line.orientation)
            assertFalse(line.isLayoutRequested)
            assertEquals(Color.TRANSPARENT, draw(line).getPixel(2, 6))
        }
    }

    @Test
    fun draw_runtimeLengthsRebuildPatternAndCanReturnToSolidWithoutRequestingLayout() {
        val line = createLine()
        assertEquals(Color.RED, draw(line).getPixel(6, 2))

        line.gapWidth = 4f
        assertFalse(line.isLayoutRequested)
        assertEquals(Color.TRANSPARENT, draw(line).getPixel(6, 2))

        line.dashWidth = 8f
        assertFalse(line.isLayoutRequested)
        val mixed = draw(line)
        assertEquals(Color.RED, mixed.getPixel(2, 2))
        assertEquals(Color.TRANSPARENT, mixed.getPixel(6, 2))
        assertEquals(Color.RED, mixed.getPixel(14, 2))

        line.dotWidth = 0f
        assertFalse(line.isLayoutRequested)
        val dashed = draw(line)
        assertEquals(Color.RED, dashed.getPixel(6, 2))
        assertEquals(Color.TRANSPARENT, dashed.getPixel(10, 2))

        line.dashWidth = 0f
        val empty = draw(line)
        assertEquals(Color.TRANSPARENT, empty.getPixel(2, 2))
        assertEquals(Color.TRANSPARENT, empty.getPixel(14, 2))

        line.gapWidth = 0f
        val solid = draw(line)
        assertEquals(Color.RED, solid.getPixel(2, 2))
        assertEquals(Color.RED, solid.getPixel(14, 2))
    }

    @Test
    fun draw_patternChangesAdvancePathGenerationWithoutGeometryChanges() {
        val line = createLine(gapWidth = "4px")
        line.layout(0, 0, 20, 20)
        var previousGeneration: Int = drawnPathGeneration(line)

        val patternChanges: List<() -> Unit> = listOf(
            { line.dotWidth = 6f },
            { line.dashWidth = 8f },
            { line.gapWidth = 2f },
            {
                line.gapWidth = 0f
                line.draw(Canvas())
                line.gapWidth = 2f
            },
            {
                line.dotWidth = 0f
                line.dashWidth = 0f
                line.draw(Canvas())
                line.dotWidth = 6f
                line.dashWidth = 8f
            },
        )
        for ((index, change) in patternChanges.withIndex()) {
            change()
            val generation: Int = drawnPathGeneration(line)
            assertNotEquals(
                "Pattern change $index must invalidate the native path cache key.",
                previousGeneration,
                generation,
            )
            previousGeneration = generation
        }
    }

    @Test
    fun draw_colorStateAndUnchangedLengthsPreservePathGeneration() {
        val line = createLine(gapWidth = "4px")
        line.layout(0, 0, 20, 20)
        val generation: Int = drawnPathGeneration(line)

        line.setLineColor(Color.BLUE)
        assertEquals(generation, drawnPathGeneration(line))
        line.lineColors = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_selected), intArrayOf()),
            intArrayOf(Color.GREEN, Color.RED),
        )
        assertEquals(generation, drawnPathGeneration(line))
        line.isSelected = true
        assertEquals(generation, drawnPathGeneration(line))
        line.dotWidth = line.dotWidth
        line.dashWidth = line.dashWidth
        line.gapWidth = line.gapWidth
        assertEquals(generation, drawnPathGeneration(line))
    }

    @Test
    fun draw_xmlDashWithoutDotProducesOnlyDashPattern() {
        val line = createLine(dotWidth = null, dashWidth = "8px", gapWidth = "4px")
        val bitmap = draw(line)

        assertEquals(0f, line.dotWidth, 0f)
        assertEquals(Color.RED, bitmap.getPixel(6, 2))
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(10, 2))
        assertEquals(Color.RED, bitmap.getPixel(14, 2))
    }

    @Test
    fun lengths_negativeValuesNormalizeToZeroInXmlAndAtRuntime() {
        val xmlLine = createLine(dotWidth = "-1px", dashWidth = "-2px", gapWidth = "-3px")
        val runtimeLine = createLine(dotWidth = "4px", dashWidth = "8px", gapWidth = "4px").apply {
            dotWidth = -1f
            dashWidth = -2f
            gapWidth = -3f
        }

        for (line in listOf(xmlLine, runtimeLine)) {
            assertEquals(0f, line.dotWidth, 0f)
            assertEquals(0f, line.dashWidth, 0f)
            assertEquals(0f, line.gapWidth, 0f)
            assertEquals(Color.RED, draw(line).getPixel(6, 2))
        }
    }

    @Test
    fun lengths_nonFiniteValuesAreRejectedWithoutChangingPattern() {
        val line = createLine(dotWidth = "4px", dashWidth = "8px", gapWidth = "4px")
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { line.dotWidth = invalid }
            assertThrows(IllegalArgumentException::class.java) { line.dashWidth = invalid }
            assertThrows(IllegalArgumentException::class.java) { line.gapWidth = invalid }

            assertEquals(4f, line.dotWidth, 0f)
            assertEquals(8f, line.dashWidth, 0f)
            assertEquals(4f, line.gapWidth, 0f)
            val bitmap = draw(line)
            assertEquals(Color.RED, bitmap.getPixel(2, 2))
            assertEquals(Color.TRANSPARENT, bitmap.getPixel(6, 2))
            assertEquals(Color.RED, bitmap.getPixel(14, 2))
        }
    }

    @Test
    fun draw_xmlHorizontalPlacesGapsAlongWidth() {
        val bitmap = draw(createLine(orientation = "horizontal", gapWidth = "4px"))

        assertEquals(Color.RED, bitmap.getPixel(2, 2))
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(6, 2))
        assertEquals(Color.RED, bitmap.getPixel(2, 6))
    }

    @Test
    fun draw_xmlVerticalPlacesGapsAlongHeight() {
        val bitmap = draw(createLine(orientation = "vertical", gapWidth = "4px"))

        assertEquals(Color.RED, bitmap.getPixel(2, 2))
        assertEquals(Color.RED, bitmap.getPixel(6, 2))
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(2, 6))
    }

    @Test
    fun measure_wrapContentIncludesPaddingAndOnePixelThickness() {
        val horizontal = createLine("horizontal").apply { setPadding(3, 4, 5, 6) }
        val vertical = createLine("vertical").apply { setPadding(3, 4, 5, 6) }

        horizontal.measure(atMost(100), atMost(100))
        vertical.measure(atMost(100), atMost(100))

        assertEquals(100, horizontal.measuredWidth)
        assertEquals(11, horizontal.measuredHeight)
        assertEquals(9, vertical.measuredWidth)
        assertEquals(100, vertical.measuredHeight)
    }

    @Test
    fun measure_wrapContentHonorsExplicitMinimumThickness() {
        val horizontal = createLine("horizontal").apply { minimumHeight = 18 }
        val vertical = createLine("vertical").apply { minimumWidth = 17 }

        horizontal.measure(atMost(100), atMost(100))
        vertical.measure(atMost(100), atMost(100))

        assertEquals(18, horizontal.measuredHeight)
        assertEquals(17, vertical.measuredWidth)
    }

    @Test
    fun measure_wrapContentHonorsBackgroundMinimumThickness() {
        val horizontal = createLine("horizontal").apply {
            background = GradientDrawable().apply { setSize(23, 29) }
        }
        val vertical = createLine("vertical").apply {
            background = GradientDrawable().apply { setSize(23, 29) }
        }

        horizontal.measure(atMost(100), atMost(100))
        vertical.measure(atMost(100), atMost(100))

        assertEquals(29, horizontal.measuredHeight)
        assertEquals(23, vertical.measuredWidth)
    }

    @Test
    fun measure_parentLimitsOverrideDesiredThicknessIncludingZero() {
        for (limit in listOf(0, 7)) {
            val horizontal = createLine("horizontal").apply {
                minimumHeight = 18
                setPadding(3, 4, 5, 6)
            }
            val vertical = createLine("vertical").apply {
                minimumWidth = 17
                setPadding(3, 4, 5, 6)
            }

            horizontal.measure(atMost(100), atMost(limit))
            vertical.measure(atMost(limit), atMost(100))

            assertEquals(limit, horizontal.measuredHeight)
            assertEquals(limit, vertical.measuredWidth)
        }
    }

    @Test
    fun measure_exactDimensionsOverrideMinimumAndPadding() {
        for (orientation in listOf("horizontal", "vertical")) {
            val line = createLine(orientation).apply {
                minimumWidth = 100
                minimumHeight = 100
                setPadding(3, 4, 5, 6)
            }

            line.measure(exactly(6), exactly(7))

            assertEquals(6, line.measuredWidth)
            assertEquals(7, line.measuredHeight)
        }
    }

    @Test
    fun measure_unspecifiedThicknessIncludesPadding() {
        val horizontal = createLine("horizontal").apply { setPadding(3, 4, 5, 6) }
        val vertical = createLine("vertical").apply { setPadding(3, 4, 5, 6) }
        val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)

        horizontal.measure(exactly(100), unspecified)
        vertical.measure(unspecified, exactly(100))

        assertEquals(11, horizontal.measuredHeight)
        assertEquals(9, vertical.measuredWidth)
    }

    @Test
    fun draw_solidLineFillsOnlyPositiveContentBounds() {
        for (orientation in listOf("horizontal", "vertical")) {
            val line = createLine(orientation).apply { setPadding(3, 4, 5, 6) }
            val bitmap = draw(line)

            for (y in 0 until bitmap.height) {
                for (x in 0 until bitmap.width) {
                    val expected = if (x in 3 until 15 && y in 4 until 14) Color.RED else Color.TRANSPARENT
                    assertEquals("Unexpected pixel at ($x, $y) for $orientation", expected, bitmap.getPixel(x, y))
                }
            }
        }
    }

    @Test
    fun draw_patternAfterPaddingAndSizeChangesUsesOnlyCurrentContentBounds() {
        for (orientation in listOf("horizontal", "vertical")) {
            val line = createLine(orientation, gapWidth = "4px")
            draw(line)

            line.setPadding(3, 4, 5, 6)
            val padded = draw(line)
            assertEquals(Color.RED, padded.getPixel(5, 6))
            assertEquals(Color.TRANSPARENT, padded.getPixel(9, 10))
            assertEquals(Color.TRANSPARENT, padded.getPixel(1, 6))
            assertEquals(Color.TRANSPARENT, padded.getPixel(5, 2))

            // 实际布局尺寸可以小于上一次测量尺寸, 端点和粗细必须随当前内容区更新.
            line.layout(0, 0, 16, 17)
            val resized = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
            line.draw(Canvas(resized))
            assertEquals(Color.RED, resized.getPixel(5, 6))
            assertEquals(Color.TRANSPARENT, resized.getPixel(9, 10))
            assertEquals(Color.TRANSPARENT, resized.getPixel(13, 6))
            assertEquals(Color.TRANSPARENT, resized.getPixel(5, 13))
        }
    }

    @Test
    fun draw_zeroOrNegativeContentBoundsLeaveCanvasEmpty() {
        val paddings = listOf(
            intArrayOf(10, 0, 10, 0),
            intArrayOf(15, 0, 15, 0),
            intArrayOf(0, 10, 0, 10),
            intArrayOf(0, 15, 0, 15)
        )
        for (orientation in listOf("horizontal", "vertical")) {
            for (padding in paddings) {
                val line = createLine(orientation).apply {
                    setPadding(padding[0], padding[1], padding[2], padding[3])
                }
                val bitmap = draw(line)
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

                assertTrue(
                    "Expected empty canvas for $orientation with padding ${padding.contentToString()}",
                    pixels.all { it == Color.TRANSPARENT }
                )
            }
        }
    }

    @Test
    fun draw_fractionalDotRemainsVisible() {
        val bitmap = draw(createLine(dotWidth = "0.5px", gapWidth = "1px"), scale = 8)

        assertEquals(Color.RED, bitmap.getPixel(2, 16))
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(8, 16))
    }

    @Test
    fun draw_fractionalDashRemainsVisible() {
        val bitmap = draw(createLine(dotWidth = "0px", dashWidth = "0.5px", gapWidth = "1px"), scale = 8)

        assertEquals(Color.RED, bitmap.getPixel(2, 16))
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(8, 16))
    }

    @Test
    fun draw_fractionalGapDoesNotBecomeSolidLine() {
        val bitmap = draw(createLine(gapWidth = "0.5px"), scale = 8)

        assertEquals(Color.RED, bitmap.getPixel(16, 16))
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(34, 16))
        assertEquals(Color.RED, bitmap.getPixel(40, 16))
    }

    @Test
    fun draw_mixedFractionalDotDashRetainsAlternatingLengths() {
        val bitmap = draw(createLine(dotWidth = "0.5px", dashWidth = "2.5px", gapWidth = "0.5px"), scale = 8)

        assertEquals(Color.RED, bitmap.getPixel(2, 16))
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(6, 16))
        assertEquals(Color.RED, bitmap.getPixel(16, 16))
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(30, 16))
        assertEquals(Color.RED, bitmap.getPixel(34, 16))
    }

    private fun createLine(
        orientation: String = "horizontal",
        dotWidth: String? = "4px",
        dashWidth: String? = "0px",
        gapWidth: String? = "0px",
        color: String = "#FFFF0000",
        context: Context = RuntimeEnvironment.getApplication()
    ): KitLineView {
        val attributes = Robolectric.buildAttributeSet()
            .addAttribute(android.R.attr.orientation, orientation)
            .addAttribute(android.R.attr.color, color)
            .apply {
                dotWidth?.let { addAttribute(R.attr.kitDotWidth, it) }
                dashWidth?.let { addAttribute(R.attr.kitDashWidth, it) }
                gapWidth?.let { addAttribute(R.attr.kitGapWidth, it) }
            }
            .build()
        return KitLineView(context, attributes)
    }

    private fun draw(line: KitLineView, scale: Int = 1): Bitmap {
        line.measure(exactly(20), exactly(20))
        line.layout(0, 0, 20, 20)
        return Bitmap.createBitmap(20 * scale, 20 * scale, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            // 放大逻辑坐标, 让亚像素线段和间隔成为可稳定断言的完整像素.
            canvas.scale(scale.toFloat(), scale.toFloat())
            line.draw(canvas)
        }
    }

    private fun drawnPathGeneration(line: KitLineView): Int {
        var generation: Int = 0
        // 观察提交给 Canvas 的公开路径版本, 确定性验证旧 HWUI 缓存键失效, 不依赖 GC 或原生地址复用时机.
        line.draw(object : Canvas() {
            override fun drawPath(path: Path, paint: Paint) {
                generation = path.generationId
            }
        })
        assertTrue("The pattern must submit a nonempty path to Canvas.", generation != 0)
        return generation
    }

    private fun atMost(size: Int): Int = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.AT_MOST)

    private fun exactly(size: Int): Int = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
}
