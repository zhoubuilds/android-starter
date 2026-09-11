package com.whisper.kit.widget

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.whisper.kit.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/**
 * 验证范围选择控件的轨道绘制, 双端外观优先级, 测量约束和 Drawable 生命周期.
 *
 * @author whisper
 * @since 2026/09/09
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KitRangeBarDrawableTest {

    @Test
    @Config(qualifiers = "xxhdpi")
    fun thumbs_originalInstancePreservesProgrammaticPixelDimensionsAtHighDensity() {
        val source = thumb(20, 20)
        val bar = createBar().apply { setThumb(source) }

        assertEquals(20, source.intrinsicWidth)
        assertSame(source, bar.getStartThumb())
        assertSame(source, bar.getEndThumb())
        assertEquals(20, requireNotNull(bar.getStartThumb()).intrinsicWidth)
        assertEquals(20, requireNotNull(bar.getEndThumb()).intrinsicWidth)
        measure(bar, height = 100, heightMode = View.MeasureSpec.AT_MOST)
        assertEquals(20, bar.measuredHeight)
    }

    @Test
    @Config(qualifiers = "mdpi")
    fun thumbs_explicitBitmapTargetDensityPreservesRenderedSizeAndHitArea() {
        val source = bitmapThumb(160, 320)
        val bar = createBar().apply { setThumb(source) }
        val bitmap = draw(bar)

        assertEquals(40, source.intrinsicWidth)
        assertEquals(40, source.intrinsicHeight)
        assertSame(source, bar.getStartThumb())
        assertSame(source, bar.getEndThumb())
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(35, 20))
        assertEquals(Color.BLUE, bitmap.getPixel(36, 20))
        assertEquals(Color.BLUE, bitmap.getPixel(75, 20))
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(76, 20))
        measure(bar, height = 100, heightMode = View.MeasureSpec.AT_MOST)
        assertEquals(40, bar.measuredHeight)

        touch(bar, MotionEvent.ACTION_DOWN, listOf(7 to 37f))
        touch(bar, MotionEvent.ACTION_MOVE, listOf(7 to 55f))
        assertEquals(30, bar.start)
        touch(bar, MotionEvent.ACTION_UP, listOf(7 to 55f))
    }

    @Test
    @Config(qualifiers = "mdpi")
    fun thumbs_lowBitmapTargetDensityPreservesIntrinsicAndRenderedSize() {
        val source = bitmapThumb(160, 80)
        val bar = createBar().apply { setThumb(source) }
        val bitmap = draw(bar)

        assertEquals(10, source.intrinsicWidth)
        assertEquals(10, source.intrinsicHeight)
        assertSame(source, bar.getStartThumb())
        assertSame(source, bar.getEndThumb())
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(41, 20))
        assertEquals(Color.BLUE, bitmap.getPixel(42, 20))
        assertEquals(Color.BLUE, bitmap.getPixel(51, 20))
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(52, 20))
        measure(bar, height = 100, heightMode = View.MeasureSpec.AT_MOST)
        assertEquals(10, bar.measuredHeight)
    }

    @Test
    @Config(qualifiers = "mdpi")
    fun thumbs_layerBitmapTargetDensityPreservesRenderedSizeAndHitArea() {
        val source = LayerDrawable(arrayOf(bitmapThumb(160, 320)))
        val bar = createBar().apply { setThumb(source) }
        val bitmap = draw(bar)

        assertEquals(40, source.intrinsicWidth)
        assertSame(source, bar.getStartThumb())
        assertSame(source, bar.getEndThumb())
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(35, 20))
        assertEquals(Color.BLUE, bitmap.getPixel(36, 20))
        assertEquals(Color.BLUE, bitmap.getPixel(75, 20))
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(76, 20))
        measure(bar, height = 100, heightMode = View.MeasureSpec.AT_MOST)
        assertEquals(40, bar.measuredHeight)

        touch(bar, MotionEvent.ACTION_DOWN, listOf(7 to 37f))
        touch(bar, MotionEvent.ACTION_MOVE, listOf(7 to 55f))
        assertEquals(30, bar.start)
        touch(bar, MotionEvent.ACTION_UP, listOf(7 to 55f))
    }

    @Test
    @Config(qualifiers = "mdpi")
    fun thumbs_nestedInsetBitmapTargetDensityPreservesPaddingAndHitArea() {
        val source = LayerDrawable(arrayOf(InsetDrawable(bitmapThumb(160, 320), 2)))
        val bar = createBar().apply { setThumb(source) }
        val bitmap = draw(bar)

        assertEquals(44, source.intrinsicWidth)
        assertSame(source, bar.getStartThumb())
        assertSame(source, bar.getEndThumb())
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(36, 20))
        assertEquals(Color.BLUE, bitmap.getPixel(37, 20))
        assertEquals(Color.BLUE, bitmap.getPixel(76, 20))
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(77, 20))
        measure(bar, height = 100, heightMode = View.MeasureSpec.AT_MOST)
        assertEquals(44, bar.measuredHeight)

        touch(bar, MotionEvent.ACTION_DOWN, listOf(7 to 36f))
        touch(bar, MotionEvent.ACTION_MOVE, listOf(7 to 54f))
        assertEquals(30, bar.start)
        touch(bar, MotionEvent.ACTION_UP, listOf(7 to 54f))
    }

    @Test
    @Config(qualifiers = "mdpi")
    fun thumbs_fixedLayerSizePreservesNestedBitmapTargetDensity() {
        val source = LayerDrawable(arrayOf(bitmapThumb(160, 320))).apply { setLayerSize(0, 40, 40) }
        val bar = createBar().apply { setThumb(source) }

        assertSame(source, bar.getStartThumb())
        assertSame(source, bar.getEndThumb())
        assertEquals(40, source.getDrawable(0).intrinsicWidth)
    }

    @Test
    fun thumbs_layerWithEmptyChildCanShareOriginalInstance() {
        val source = LayerDrawable(arrayOf<Drawable?>(null, thumb(20, 20)))
        val bar = createBar().apply { setThumb(source) }

        assertSame(source, bar.getStartThumb())
        assertSame(source, bar.getEndThumb())
        assertEquals(20, requireNotNull(bar.getStartThumb()).intrinsicWidth)
        assertEquals(20, requireNotNull(bar.getEndThumb()).intrinsicWidth)
        draw(bar)
    }

    @Test
    @Config(qualifiers = "mdpi")
    fun thumbs_inactiveSelectorBitmapKeepsSizeWhenPressedAndRestoresSharedState() {
        val source = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_drag_hovered), InsetDrawable(bitmapThumb(160, 320), 2))
            addState(intArrayOf(), thumb(20, 12, Color.RED))
            state = intArrayOf(android.R.attr.state_enabled)
            setBounds(1, 2, 31, 32)
        }
        val initialBounds = Rect(source.bounds)
        val initialState = source.state.toList()
        val bar = createBar().apply { setThumb(source) }
        val initialCurrent = source.current
        draw(bar)

        assertSame(source, bar.getStartThumb())
        assertSame(source, bar.getEndThumb())
        touch(bar, MotionEvent.ACTION_DOWN, listOf(7 to 50f))
        val bitmap = draw(bar)

        assertEquals(Color.TRANSPARENT, bitmap.getPixel(36, 20))
        assertEquals(Color.BLUE, bitmap.getPixel(37, 20))
        assertEquals(Color.BLUE, bitmap.getPixel(76, 20))
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(77, 20))
        assertEquals(Color.RED, bitmap.getPixel(163, 20))
        assertEquals(initialBounds, source.bounds)
        assertEquals(initialState, source.state.toList())
        assertSame(initialCurrent, source.current)
        measure(bar, height = 100, heightMode = View.MeasureSpec.AT_MOST)
        assertEquals(44, bar.measuredHeight)

        touch(bar, MotionEvent.ACTION_UP, listOf(7 to 50f))
        measure(bar, height = 100, heightMode = View.MeasureSpec.AT_MOST)
        assertEquals(12, bar.measuredHeight)
        assertEquals(initialBounds, source.bounds)
        assertEquals(initialState, source.state.toList())
        assertSame(initialCurrent, source.current)
    }

    @Test
    fun thumbs_sharedSelectorPreservesConfiguredStateBoundsAndLevelBetweenDraws() {
        val source = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_drag_hovered), thumb(40, 32, Color.RED))
            addState(intArrayOf(), thumb(20, 12, Color.BLUE))
            state = intArrayOf(android.R.attr.state_enabled, android.R.attr.state_drag_hovered)
            level = 42
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBounds(1, 2, 31, 32)
        }
        val initialBounds = Rect(source.bounds)
        val initialState = source.state.toList()
        val bar = createBar().apply { setThumb(source) }
        val initialCurrent = source.current
        val idleBitmap = draw(bar)

        assertSame(source, bar.getStartThumb())
        assertSame(source, bar.getEndThumb())
        assertEquals(Color.BLUE, idleBitmap.getPixel(50, 20))
        assertEquals(Color.BLUE, idleBitmap.getPixel(170, 20))
        touch(bar, MotionEvent.ACTION_DOWN, listOf(7 to 50f))
        val pressedBitmap = draw(bar)
        assertEquals(Color.RED, pressedBitmap.getPixel(56, 20))
        assertEquals(Color.BLUE, pressedBitmap.getPixel(164, 20))
        assertEquals(Color.RED, pressedBitmap.getPixel(36, 20))
        assertEquals(Color.TRANSPARENT, pressedBitmap.getPixel(35, 20))
        assertEquals(initialBounds, source.bounds)
        assertEquals(initialState, source.state.toList())
        assertSame(initialCurrent, source.current)
        assertSame(bar, source.callback)
        assertEquals(42, source.level)
        assertEquals(bar.layoutDirection, source.layoutDirection)
        touch(bar, MotionEvent.ACTION_UP, listOf(7 to 50f))
    }

    @Test
    @Config(qualifiers = "mdpi")
    fun thumbs_densityIndependentBitmapUsesOriginalInstance() {
        val source = bitmapThumb(Bitmap.DENSITY_NONE, 320)
        val bar = createBar().apply { setThumb(source) }

        assertEquals(20, source.intrinsicWidth)
        assertEquals(20, requireNotNull(bar.getStartThumb()).intrinsicWidth)
        assertEquals(20, requireNotNull(bar.getEndThumb()).intrinsicWidth)
        assertSame(source, bar.getStartThumb())
        assertSame(source, bar.getEndThumb())
        measure(bar, height = 100, heightMode = View.MeasureSpec.AT_MOST)
        assertEquals(20, bar.measuredHeight)
    }

    @Test
    @Config(qualifiers = "mdpi")
    fun thumbs_commonBitmapAppearanceChangesApplyToBothEndpoints() {
        val source = bitmapThumb(160, 160)
        val bar = createBar().apply { setThumb(source) }
        val start = requireNotNull(bar.getStartThumb())
        val end = requireNotNull(bar.getEndThumb())

        assertEquals(20, start.intrinsicWidth)
        assertEquals(20, end.intrinsicWidth)
        assertSame(source, start)
        assertSame(source, end)
        start.alpha = 127
        val bitmap = draw(bar)
        assertEquals(127, end.alpha)
        assertEquals(127, source.alpha)
        assertEquals(127, Color.alpha(bitmap.getPixel(50, 20)))
        assertEquals(127, Color.alpha(bitmap.getPixel(170, 20)))
    }

    @Test
    fun drawables_reusingCommonThumbAsTrackRestoresBoundsWithoutContinuousInvalidation() {
        val bar = createBar().apply { setThumb(thumb(20, 20)) }
        val start = requireNotNull(bar.getStartThumb())
        val initialBounds = Rect(start.bounds)
        bar.setBarForeground(start)
        draw(bar)
        shadowOf(bar).clearWasInvalidated()

        bar.draw(Canvas(Bitmap.createBitmap(bar.width, bar.height, Bitmap.Config.ARGB_8888)))

        assertFalse(shadowOf(bar).wasInvalidated())
        assertEquals(initialBounds, start.bounds)
        assertSame(bar, start.callback)
    }

    @Test
    fun drawables_sharedTrackAndThumbRestoreBoundsWithoutContinuousInvalidation() {
        val source = StatefulRecordingDrawable()
        val bar = createBar().apply {
            setThumb(source)
            setBarBackground(source)
            setBarForeground(source)
        }
        draw(bar)
        val initialBounds = Rect(source.bounds)
        val initialState = source.state.toList()
        source.drawnBounds.clear()
        shadowOf(bar).clearWasInvalidated()

        bar.draw(Canvas(Bitmap.createBitmap(bar.width, bar.height, Bitmap.Config.ARGB_8888)))

        assertEquals(4, source.drawnBounds.size)
        assertEquals(listOf(200, 200, 20, 20), source.drawnBounds.map { it.width() })
        assertEquals(initialBounds, source.bounds)
        assertEquals(initialState, source.state.toList())
        assertFalse(shadowOf(bar).wasInvalidated())
        assertSame(bar, source.callback)

        source.invalidateSelf()
        assertTrue(shadowOf(bar).wasInvalidated())
    }

    @Test
    fun drawables_sharedDrawableCanInvalidateFromInsideDraw() {
        val source = StatefulRecordingDrawable()
        val bar = createBar().apply {
            setThumb(source)
            setBarBackground(source)
        }
        draw(bar)
        shadowOf(bar).clearWasInvalidated()
        source.invalidateOnDraw = true

        bar.draw(Canvas(Bitmap.createBitmap(bar.width, bar.height, Bitmap.Config.ARGB_8888)))

        assertTrue("Invalidation from draw must schedule the next frame", shadowOf(bar).wasInvalidated())
    }

    @Test
    fun draw_foregroundPreservesUnselectedTrackAndViewBackground() {
        val bar = createBar().apply {
            background = ColorDrawable(Color.YELLOW)
            setThumb(thumb(20, 20))
            setBarBackground(ColorDrawable(Color.GRAY))
            setBarForeground(ColorDrawable(Color.RED))
        }

        val bitmap = draw(bar)

        assertEquals(Color.GRAY, bitmap.getPixel(20, 20))
        assertEquals(Color.RED, bitmap.getPixel(110, 20))
        assertEquals(Color.GRAY, bitmap.getPixel(200, 20))
        assertEquals(Color.YELLOW, bitmap.getPixel(110, 5))
    }

    @Test
    fun draw_withoutConfiguredDrawablesRemainsTransparent() {
        val bar = createBar()

        val bitmap = draw(bar)

        assertNull(bar.getStartThumb())
        assertNull(bar.getEndThumb())
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(50, 20))
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(110, 20))
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(170, 20))
    }

    @Test
    fun measure_exactHeightDoesNotUseMinimumWidth() {
        val bar = createBar().apply {
            minimumWidth = 100
            minimumHeight = 80
            setThumb(thumb(20, 40))
        }

        measure(bar, height = 24, heightMode = View.MeasureSpec.EXACTLY)

        assertEquals(220, bar.measuredWidth)
        assertEquals(24, bar.measuredHeight)
    }

    @Test
    fun measure_atMostHeightClampsContentAndHonorsMinimumHeight() {
        val bar = createBar().apply {
            setThumb(thumb(20, 40))
            setPadding(3, 5, 7, 9)
        }

        measure(bar, height = 18, heightMode = View.MeasureSpec.AT_MOST)
        assertEquals(18, bar.measuredHeight)

        measure(bar, height = 100, heightMode = View.MeasureSpec.AT_MOST)
        assertEquals(54, bar.measuredHeight)

        bar.minimumHeight = 72
        measure(bar, height = 100, heightMode = View.MeasureSpec.AT_MOST)
        assertEquals(72, bar.measuredHeight)
    }

    @Test
    fun draw_distinctIntrinsicSizesShareTrackAndFitAtBothDomainEdges() {
        val start = RecordingDrawable(20, 16)
        val end = RecordingDrawable(40, 32)
        val bar = createBar().apply {
            setStartThumb(start)
            setEndThumb(end)
        }

        for (value in listOf(0, 100)) {
            bar.setSelectedRange(value, value)
            draw(bar)
            val startBounds = start.drawnBounds.last()
            val endBounds = end.drawnBounds.last()

            assertEquals(20, startBounds.width())
            assertEquals(16, startBounds.height())
            assertEquals(40, endBounds.width())
            assertEquals(32, endBounds.height())
            assertEquals(startBounds.centerX(), endBounds.centerX())
            assertEquals(startBounds.centerY(), endBounds.centerY())
            assertEquals(if (value == 0) 20 else 200, endBounds.centerX())
            assertTrue(startBounds.left >= 0 && startBounds.right <= bar.width)
            assertTrue(endBounds.left >= 0 && endBounds.right <= bar.width)
        }

        measure(bar, height = 100, heightMode = View.MeasureSpec.AT_MOST)
        assertEquals(32, bar.measuredHeight)
    }

    @Test
    fun draw_explicitXmlThumbDimensionsApplyToBothEndpoints() {
        val start = RecordingDrawable(20, 16)
        val end = RecordingDrawable(40, 32)
        val bar = createBar(R.attr.kitThumbWidth to "30px", R.attr.kitThumbHeight to "24px").apply {
            setStartThumb(start)
            setEndThumb(end)
        }

        draw(bar)

        for (drawable in listOf(start, end)) {
            assertEquals(30, drawable.drawnBounds.last().width())
            assertEquals(24, drawable.drawnBounds.last().height())
        }
    }

    @Test
    fun draw_rtlMirrorsValuesAndKeepsResolvedPhysicalPadding() {
        val applicationInfo = RuntimeEnvironment.getApplication().applicationInfo
        val originalFlags: Int = applicationInfo.flags
        applicationInfo.flags = originalFlags or ApplicationInfo.FLAG_SUPPORTS_RTL
        try {
            val start = RecordingDrawable(20, 20)
            val end = RecordingDrawable(20, 20)
            val bar = createBar().apply {
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                setPaddingRelative(40, 0, 10, 0)
                setStartThumb(start)
                setEndThumb(end)
                setSelectedRange(0, 100)
            }

            draw(bar)

            assertEquals(10, bar.paddingLeft)
            assertEquals(40, bar.paddingRight)
            assertEquals(180, start.drawnBounds.last().right)
            assertEquals(10, end.drawnBounds.last().left)

            bar.isRtlMirroringEnabled = false
            draw(bar)
            assertEquals(View.LAYOUT_DIRECTION_RTL, bar.layoutDirection)
            assertEquals(10, bar.paddingLeft)
            assertEquals(40, bar.paddingRight)
            assertEquals(10, start.drawnBounds.last().left)
            assertEquals(180, end.drawnBounds.last().right)
        } finally {
            applicationInfo.flags = originalFlags
        }
    }

    @Test
    fun layout_actualSizeUpdatesGeometryWithoutAnotherMeasure() {
        val start = RecordingDrawable(20, 20)
        val end = RecordingDrawable(20, 20)
        val bar = createBar().apply {
            setStartThumb(start)
            setEndThumb(end)
            setSelectedRange(0, 100)
        }
        draw(bar)

        bar.layout(0, 0, 320, 40)
        bar.draw(Canvas(Bitmap.createBitmap(320, 40, Bitmap.Config.ARGB_8888)))

        assertEquals(0, start.drawnBounds.last().left)
        assertEquals(320, end.drawnBounds.last().right)
    }

    @Test
    fun thumbs_runtimeEndpointOverridesSurviveCommonChangesAndCanBeCleared() {
        val bar = createBar().apply {
            setThumb(ColorDrawable(Color.BLUE))
            setStartThumb(ColorDrawable(Color.RED))
            setEndThumb(ColorDrawable(Color.GREEN))
        }

        bar.setThumb(ColorDrawable(Color.YELLOW))
        assertEquals(Color.RED, (bar.getStartThumb() as ColorDrawable).color)
        assertEquals(Color.GREEN, (bar.getEndThumb() as ColorDrawable).color)

        bar.setStartThumb(KitRangeBar.INHERIT_THUMB)
        assertEquals(Color.YELLOW, (bar.getStartThumb() as ColorDrawable).color)
        assertEquals(Color.GREEN, (bar.getEndThumb() as ColorDrawable).color)

        bar.setThumb(null)
        assertNull(bar.getStartThumb())
        assertEquals(Color.GREEN, (bar.getEndThumb() as ColorDrawable).color)
        bar.setEndThumb(null)
        assertNull(bar.getEndThumb())
    }

    @Test
    fun thumbs_nullClearsAnEndpointUntilInheritanceIsExplicitlyRestored() {
        val bar = createBar().apply {
            setThumb(ColorDrawable(Color.BLUE))
            setStartThumb(null)
        }

        assertNull(bar.getStartThumb())
        assertEquals(Color.BLUE, (bar.getEndThumb() as ColorDrawable).color)

        bar.setThumb(ColorDrawable(Color.GREEN))
        assertNull(bar.getStartThumb())
        assertEquals(Color.GREEN, (bar.getEndThumb() as ColorDrawable).color)

        bar.setStartThumb(KitRangeBar.INHERIT_THUMB)
        assertEquals(Color.GREEN, (bar.getStartThumb() as ColorDrawable).color)
        bar.setEndThumb(null)
        bar.setThumb(ColorDrawable(Color.RED))
        assertEquals(Color.RED, (bar.getStartThumb() as ColorDrawable).color)
        assertNull(bar.getEndThumb())

        bar.setEndThumb(KitRangeBar.INHERIT_THUMB)
        assertEquals(Color.RED, (bar.getEndThumb() as ColorDrawable).color)
    }

    @Test
    fun thumbs_xmlEndpointOverridesTakePriorityOverCommonDrawable() {
        val bar = createBar(
            R.attr.kitThumb to "@android:color/holo_blue_light",
            R.attr.kitStartThumb to "@android:color/holo_red_light",
            R.attr.kitEndThumb to "@android:color/holo_green_light",
        )
        val resources = bar.resources

        assertEquals(resources.getColor(android.R.color.holo_red_light, null), (bar.getStartThumb() as ColorDrawable).color)
        assertEquals(resources.getColor(android.R.color.holo_green_light, null), (bar.getEndThumb() as ColorDrawable).color)

        bar.setStartThumb(KitRangeBar.INHERIT_THUMB)
        assertEquals(resources.getColor(android.R.color.holo_blue_light, null), (bar.getStartThumb() as ColorDrawable).color)
        assertEquals(resources.getColor(android.R.color.holo_green_light, null), (bar.getEndThumb() as ColorDrawable).color)
    }

    @Test
    fun thumbs_xmlNoneClearsWhileOmittedEndpointInherits() {
        val bar = createBar(
            R.attr.kitThumb to "@android:color/holo_blue_light",
            R.attr.kitStartThumb to "none",
        )

        assertNull(bar.getStartThumb())
        assertEquals(
            bar.resources.getColor(android.R.color.holo_blue_light, null),
            (bar.getEndThumb() as ColorDrawable).color,
        )
        bar.setThumb(ColorDrawable(Color.GREEN))
        assertNull(bar.getStartThumb())
        assertEquals(Color.GREEN, (bar.getEndThumb() as ColorDrawable).color)
    }

    @Test
    fun thumbs_xmlInheritanceSentinelTracksCommonReplacements() {
        val bar = createBar(
            R.attr.kitThumb to "@android:color/holo_blue_light",
            R.attr.kitStartThumb to "inherit",
            R.attr.kitEndThumb to "inherit",
        )

        bar.setThumb(ColorDrawable(Color.GREEN))

        assertEquals(Color.GREEN, (bar.getStartThumb() as ColorDrawable).color)
        assertEquals(Color.GREEN, (bar.getEndThumb() as ColorDrawable).color)
    }

    @Test
    fun thumbs_commonConstantStateUsesOriginalMutableInstanceForBothEndpoints() {
        val source = ColorDrawable(Color.BLUE)
        val bar = createBar().apply { setThumb(source) }
        val start = bar.getStartThumb() as ColorDrawable
        val end = bar.getEndThumb() as ColorDrawable

        assertSame(source, start)
        assertSame(source, end)
        start.color = Color.RED

        assertEquals(Color.RED, start.color)
        assertEquals(Color.RED, end.color)
        assertEquals(Color.RED, source.color)
    }

    @Test
    fun thumbs_colorDrawableKeepsConfiguredTranslucentAlpha() {
        val source = ColorDrawable(Color.BLUE).apply { alpha = 128 }
        val bar = createBar(R.attr.kitThumbWidth to "20px", R.attr.kitThumbHeight to "20px").apply {
            setThumb(source)
        }

        val bitmap = draw(bar)

        assertSame(source, bar.getStartThumb())
        assertSame(source, bar.getEndThumb())
        assertEquals(128, source.alpha)
        assertEquals(Color.argb(128, 0, 0, 255), bitmap.getPixel(50, 20))
        assertEquals(Color.argb(128, 0, 0, 255), bitmap.getPixel(170, 20))
    }

    @Test
    fun thumbs_layerChildrenKeepIndependentAlphaFilterAndTint() {
        val filter = PorterDuffColorFilter(Color.GREEN, PorterDuff.Mode.SRC_IN)
        val source = LayerDrawable(
            arrayOf(
                ColorDrawable(Color.BLUE).apply { setTint(Color.YELLOW) },
                thumb(20, 20, Color.RED).apply {
                    alpha = 64
                    colorFilter = filter
                },
            ),
        ).apply {
            setLayerInset(0, 0, 0, 10, 0)
            setLayerInset(1, 10, 0, 0, 0)
        }
        val reference = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        source.setBounds(0, 0, 20, 20)
        source.draw(Canvas(reference))
        val tintedPixel = reference.getPixel(5, 10)
        val filteredPixel = reference.getPixel(15, 10)
        assertEquals(Color.YELLOW, tintedPixel)
        assertTrue(Color.alpha(filteredPixel) in 1..254)
        assertEquals(0, Color.red(filteredPixel))
        assertEquals(255, Color.green(filteredPixel))
        assertEquals(0, Color.blue(filteredPixel))
        val bar = createBar(R.attr.kitThumbWidth to "20px", R.attr.kitThumbHeight to "20px").apply {
            setThumb(source)
        }

        val bitmap = draw(bar)

        assertSame(source, bar.getStartThumb())
        assertEquals(255, source.getDrawable(0).alpha)
        assertEquals(64, source.getDrawable(1).alpha)
        assertSame(filter, source.getDrawable(1).colorFilter)
        for (left in listOf(40, 160)) {
            assertEquals(tintedPixel, bitmap.getPixel(left + 5, 20))
            assertEquals(filteredPixel, bitmap.getPixel(left + 15, 20))
        }
    }

    @Test
    fun thumbs_sharedStatefulTintResolvesEachEndpointWithoutChangingCommonAppearance() {
        val source = ColorDrawable(Color.WHITE).apply {
            setTintList(
                ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_drag_hovered), intArrayOf()),
                    intArrayOf(Color.RED, Color.BLUE),
                ),
            )
        }
        val bar = createBar(R.attr.kitThumbWidth to "20px", R.attr.kitThumbHeight to "20px").apply {
            setThumb(source)
        }
        draw(bar)

        touch(bar, MotionEvent.ACTION_DOWN, listOf(7 to 50f))
        assertDragStates(bar, startDragging = true, endDragging = false)
        touch(bar, MotionEvent.ACTION_UP, listOf(7 to 50f))
        assertDragStates(bar, startDragging = false, endDragging = false)
        assertEquals(Color.WHITE, source.color)
        assertEquals(255, source.alpha)
    }

    @Test
    @Config(qualifiers = "mdpi")
    fun thumbs_programmaticCompositePreservesChildInstancesAndRuntimeAppearance() {
        val filter = PorterDuffColorFilter(Color.GREEN, PorterDuff.Mode.SRC_IN)
        val bitmap = bitmapThumb(160, 320).apply {
            alpha = 96
            colorFilter = filter
        }
        val inset = InsetDrawable(bitmap, 2)
        val source = LayerDrawable(arrayOf(inset))
        val bar = createBar().apply { setThumb(source) }

        val rendered = draw(bar)

        assertSame(source, bar.getStartThumb())
        assertSame(source, bar.getEndThumb())
        assertSame(inset, source.getDrawable(0))
        assertSame(bitmap, inset.drawable)
        assertSame(filter, bitmap.colorFilter)
        assertEquals(40, bitmap.intrinsicWidth)
        assertEquals(96, bitmap.alpha)
        assertEquals(Color.argb(96, 0, 255, 0), rendered.getPixel(56, 20))
        assertEquals(Color.argb(96, 0, 255, 0), rendered.getPixel(163, 20))
    }

    @Test
    fun thumbs_xmlResourcesIsolateAppearanceBetweenViewsForCommonAndIndependentEntries() {
        for (attribute in listOf(R.attr.kitThumb, R.attr.kitStartThumb, R.attr.kitEndThumb)) {
            val first = createBar(attribute to "@android:color/holo_blue_light")
            val second = createBar(attribute to "@android:color/holo_blue_light")
            val firstDrawable = (if (attribute == R.attr.kitEndThumb) first.getEndThumb() else first.getStartThumb())
                as ColorDrawable
            val secondDrawable = (if (attribute == R.attr.kitEndThumb) second.getEndThumb() else second.getStartThumb())
                as ColorDrawable
            val originalColor = secondDrawable.color

            firstDrawable.color = Color.RED

            assertEquals(Color.RED, firstDrawable.color)
            assertEquals(originalColor, secondDrawable.color)
            if (attribute == R.attr.kitThumb) {
                assertSame(firstDrawable, first.getEndThumb())
                assertSame(secondDrawable, second.getEndThumb())
            }
        }
    }

    @Test
    fun thumbs_sharedFadeSelectorUsesFinalStatesWithoutContinuousInvalidation() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val parent = FrameLayout(controller.get())
            val selector = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_drag_hovered), thumb(20, 20, Color.RED))
                addState(intArrayOf(), thumb(20, 20, Color.BLUE))
                setEnterFadeDuration(1000)
                setExitFadeDuration(1000)
                setBounds(1, 2, 21, 22)
            }
            val initialBounds = Rect(selector.bounds)
            val initialState = selector.state.toList()
            val bar = createBar().apply { setThumb(selector) }
            parent.addView(bar, FrameLayout.LayoutParams(220, 40))
            controller.get().setContentView(parent)
            controller.visible()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            draw(bar)

            touch(bar, MotionEvent.ACTION_DOWN, listOf(7 to 50f))
            assertDragStates(bar, startDragging = true, endDragging = false)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            shadowOf(bar).clearWasInvalidated()
            assertDragStates(bar, startDragging = true, endDragging = false)
            assertFalse("Temporary state switches must not invalidate the view", shadowOf(bar).wasInvalidated())
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1100))
            assertFalse("A shared selector must not schedule fading frames", shadowOf(bar).wasInvalidated())
            assertEquals(initialBounds, selector.bounds)
            assertEquals(initialState, selector.state.toList())

            touch(bar, MotionEvent.ACTION_UP, listOf(7 to 50f))
            assertDragStates(bar, startDragging = false, endDragging = false)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun thumbs_sharedToExclusiveRefreshesTheRemainingEndpointState() {
        val source = StateAnimationDrawable()
        val bar = createBar().apply { setThumb(source) }
        draw(bar)
        touch(bar, MotionEvent.ACTION_DOWN, listOf(7 to 50f))
        assertFalse(source.state.contains(android.R.attr.state_drag_hovered))
        val jumpsWhileShared = source.jumpCount

        bar.setEndThumb(thumb(20, 20, Color.BLUE))
        val bitmap = draw(bar)

        assertSame(source, bar.getStartThumb())
        assertTrue(source.state.contains(android.R.attr.state_drag_hovered))
        assertEquals(Color.RED, bitmap.getPixel(50, 20))
        assertEquals(Color.BLUE, bitmap.getPixel(170, 20))
        assertEquals(jumpsWhileShared, source.jumpCount)
        touch(bar, MotionEvent.ACTION_UP, listOf(7 to 50f))
    }

    @Test
    fun thumbs_exclusiveStateAnimationKeepsScheduledFramesAndIsNotForcedToJump() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val parent = FrameLayout(controller.get())
            val source = StateAnimationDrawable()
            val bar = createBar().apply {
                setStartThumb(source)
                setEndThumb(thumb(20, 20, Color.BLUE))
            }
            parent.addView(bar, FrameLayout.LayoutParams(220, 40))
            controller.get().setContentView(parent)
            controller.visible()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            draw(bar)
            val initialJumpCount = source.jumpCount

            touch(bar, MotionEvent.ACTION_DOWN, listOf(7 to 50f))
            val bitmap = draw(bar)
            assertEquals(Color.RED, bitmap.getPixel(50, 20))
            assertEquals(initialJumpCount, source.jumpCount)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))

            assertEquals(1, source.frameCount)
            assertEquals(initialJumpCount, source.jumpCount)
            touch(bar, MotionEvent.ACTION_UP, listOf(7 to 50f))
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun thumbs_exclusiveAnimationBecomingSharedCancelsPendingFrames() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val parent = FrameLayout(controller.get())
            val source = StateAnimationDrawable()
            val bar = createBar().apply {
                setStartThumb(source)
                setEndThumb(thumb(20, 20, Color.BLUE))
            }
            parent.addView(bar, FrameLayout.LayoutParams(220, 40))
            controller.get().setContentView(parent)
            controller.visible()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            draw(bar)
            touch(bar, MotionEvent.ACTION_DOWN, listOf(7 to 50f))

            bar.setEndThumb(source)
            assertDragStates(bar, startDragging = true, endDragging = false)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))

            assertEquals(0, source.frameCount)
            assertTrue(source.jumpCount > 0)
            touch(bar, MotionEvent.ACTION_UP, listOf(7 to 50f))
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun thumbs_becomingSharedBeforeAttachmentCancelsOnlyItsDeferredAnimation() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val parent = FrameLayout(controller.get())
            val source = RecordingDrawable(20, 20)
            val bar = createBar().apply { setStartThumb(source) }
            assertFalse(bar.isAttachedToWindow)
            var drawableCallbackCount = 0
            var viewCallbackCount = 0
            source.scheduleSelf({ drawableCallbackCount++ }, SystemClock.uptimeMillis() + 30)
            bar.postDelayed({ viewCallbackCount++ }, 30)

            bar.setEndThumb(source)
            parent.addView(bar, FrameLayout.LayoutParams(220, 40))
            controller.get().setContentView(parent)
            controller.visible()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))

            assertTrue(bar.isAttachedToWindow)
            assertEquals(0, drawableCallbackCount)
            assertEquals(1, viewCallbackCount)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun drawables_deferredTasksPreserveOtherOwnersWhileCancellationAndReplacementRemoveTheirTasks() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val parent = FrameLayout(controller.get())
            val start = RecordingDrawable(20, 20)
            val end = RecordingDrawable(20, 20)
            val track = RecordingDrawable()
            val bar = createBar().apply {
                setStartThumb(start)
                setEndThumb(end)
                setBarBackground(track)
            }
            assertFalse(bar.isAttachedToWindow)
            val callbacks = mutableListOf<String>()
            val sharedTask = Runnable { callbacks += "shared" }
            val cancelledTask = Runnable { callbacks += "cancelled" }
            val executeAt = SystemClock.uptimeMillis() + 30
            start.scheduleSelf(sharedTask, executeAt)
            track.scheduleSelf(sharedTask, executeAt)
            end.scheduleSelf(sharedTask, executeAt)
            start.unscheduleSelf(sharedTask)
            start.scheduleSelf({ callbacks += "start" }, executeAt)
            start.scheduleSelf(cancelledTask, executeAt)
            start.unscheduleSelf(cancelledTask)
            end.scheduleSelf({ callbacks += "old-end" }, executeAt)
            val replacement = RecordingDrawable(20, 20)
            bar.setEndThumb(replacement)
            replacement.scheduleSelf({ callbacks += "replacement" }, executeAt)
            bar.postDelayed({ callbacks += "view" }, 30)
            assertTrue(callbacks.isEmpty())

            parent.addView(bar, FrameLayout.LayoutParams(220, 40))
            controller.get().setContentView(parent)
            controller.visible()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))

            assertEquals(listOf("replacement", "shared", "start", "view"), callbacks.sorted())
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun thumbs_commonDrawableSharesInstanceWithIndependentDrawStateAndSize() {
        val source = StatefulRecordingDrawable()
        val bar = createBar().apply { setThumb(source) }
        FrameLayout(bar.context).addView(bar)
        draw(bar)
        assertSame(source, bar.getStartThumb())
        assertSame(source, bar.getEndThumb())

        touch(bar, MotionEvent.ACTION_DOWN, listOf(7 to 50f))
        assertSharedThumbDraw(bar, source, startDragging = true, endDragging = false)
        touch(bar, MotionEvent.ACTION_POINTER_DOWN, listOf(7 to 50f, 19 to 164f), actionIndex = 1)
        assertSharedThumbDraw(bar, source, startDragging = true, endDragging = true)
        touch(bar, MotionEvent.ACTION_POINTER_UP, listOf(7 to 50f, 19 to 164f), actionIndex = 0)
        assertSharedThumbDraw(bar, source, startDragging = false, endDragging = true)
        touch(bar, MotionEvent.ACTION_UP, listOf(19 to 164f))
        assertSharedThumbDraw(bar, source, startDragging = false, endDragging = false)
    }

    @Test
    fun thumbs_independentOverridesCanShareOneInstance() {
        val start = RecordingDrawable(20, 20)
        val end = RecordingDrawable(30, 30)
        val bar = createBar().apply {
            setStartThumb(start)
            setEndThumb(end)
        }

        assertSame(start, bar.getStartThumb())
        assertSame(end, bar.getEndThumb())
        bar.setEndThumb(start)

        assertSame(start, bar.getStartThumb())
        assertSame(start, bar.getEndThumb())
        assertSame(bar, start.callback)
        assertNull(end.callback)
        draw(bar)
        assertEquals(2, start.drawnBounds.size)
    }

    @Test
    fun thumbs_commonReplacementRequestsLayoutAndResolvesNewIntrinsicDimensions() {
        val bar = createBar().apply { setThumb(thumb(20, 16)) }
        draw(bar)
        assertFalse(bar.isLayoutRequested)
        shadowOf(bar).clearWasInvalidated()

        bar.setThumb(thumb(40, 36, Color.BLUE))

        assertTrue(bar.isLayoutRequested)
        assertTrue(shadowOf(bar).wasInvalidated())
        measure(bar, height = 100, heightMode = View.MeasureSpec.AT_MOST)
        assertEquals(36, bar.measuredHeight)
        val bitmap = draw(bar)
        assertEquals(Color.BLUE, bitmap.getPixel(36, 20))
        assertEquals(Color.BLUE, bitmap.getPixel(75, 20))
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(35, 20))
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(76, 20))
        assertEquals(Color.BLUE, bitmap.getPixel(144, 20))
        assertEquals(Color.BLUE, bitmap.getPixel(183, 20))
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(143, 20))
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(184, 20))
    }

    @Test
    fun thumbs_stateDependentIntrinsicSizeRequestsLayout() {
        val selector = StateListDrawable().apply {
            addState(intArrayOf(-android.R.attr.state_enabled), thumb(32, 36))
            addState(intArrayOf(), thumb(20, 12))
        }
        val bar = createBar().apply { setThumb(selector) }
        measure(bar, height = 100, heightMode = View.MeasureSpec.AT_MOST)
        bar.layout(0, 0, bar.measuredWidth, bar.measuredHeight)
        assertEquals(12, bar.measuredHeight)
        assertFalse(bar.isLayoutRequested)

        bar.isEnabled = false

        assertTrue(bar.isLayoutRequested)
        measure(bar, height = 100, heightMode = View.MeasureSpec.AT_MOST)
        assertEquals(36, bar.measuredHeight)
    }

    @Test
    fun thumbs_bothDragStatesRemainIndependentAcrossPointerRelease() {
        val selector = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_drag_hovered), thumb(20, 20, Color.RED))
            addState(intArrayOf(), thumb(20, 20, Color.BLUE))
        }
        val bar = createBar().apply { setThumb(selector) }
        FrameLayout(bar.context).addView(bar)
        draw(bar)

        touch(bar, MotionEvent.ACTION_DOWN, listOf(7 to 50f))
        assertDragStates(bar, startDragging = true, endDragging = false)
        touch(bar, MotionEvent.ACTION_POINTER_DOWN, listOf(7 to 50f, 19 to 170f), actionIndex = 1)
        assertDragStates(bar, startDragging = true, endDragging = true)
        touch(bar, MotionEvent.ACTION_POINTER_UP, listOf(7 to 50f, 19 to 170f), actionIndex = 0)
        assertDragStates(bar, startDragging = false, endDragging = true)
        touch(bar, MotionEvent.ACTION_UP, listOf(19 to 170f))
        assertDragStates(bar, startDragging = false, endDragging = false)
    }

    @Test
    fun drawables_invalidateSelfRefreshesViewAndRuntimeTrackSettersInvalidate() {
        val drawables = List(4) { RecordingDrawable(20, 20) }
        val bar = createBar().apply {
            setStartThumb(drawables[0])
            setEndThumb(drawables[1])
            setBarBackground(drawables[2])
            setBarForeground(drawables[3])
        }
        draw(bar)

        for (drawable in drawables) {
            assertSame(bar, drawable.callback)
            shadowOf(bar).clearWasInvalidated()
            drawable.invalidateSelf()
            assertTrue("Expected Drawable callback to invalidate its owner", shadowOf(bar).wasInvalidated())
        }
        shadowOf(bar).clearWasInvalidated()
        bar.setBarBackground(ColorDrawable(Color.GREEN))
        assertTrue(shadowOf(bar).wasInvalidated())
        assertNull(drawables[2].callback)
        shadowOf(bar).clearWasInvalidated()
        bar.setBarForeground(ColorDrawable(Color.RED))
        assertTrue(shadowOf(bar).wasInvalidated())
        assertNull(drawables[3].callback)
    }

    @Test
    fun drawables_visibilityReplacementAndDetachmentFollowViewLifecycle() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val parent = FrameLayout(controller.get())
            val bar = KitRangeBar(controller.get())
            val start = RecordingDrawable(20, 20)
            val end = RecordingDrawable(20, 20)
            val track = RecordingDrawable()
            bar.setStartThumb(start)
            bar.setEndThumb(end)
            bar.setBarBackground(track)
            parent.addView(bar, FrameLayout.LayoutParams(220, 40))
            controller.get().setContentView(parent)
            controller.visible()
            assertTrue(start.isVisible && end.isVisible && track.isVisible)

            parent.visibility = View.INVISIBLE
            assertFalse(start.isVisible || end.isVisible || track.isVisible)
            val replacement = RecordingDrawable(30, 30)
            bar.setStartThumb(replacement)
            assertNull(start.callback)
            assertFalse(start.isVisible)
            assertFalse(replacement.isVisible)

            parent.visibility = View.VISIBLE
            assertTrue(replacement.isVisible && end.isVisible && track.isVisible)
            parent.removeView(bar)
            assertFalse(replacement.isVisible || end.isVisible || track.isVisible)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun drawables_activeSchedulesRunButReplacementAndDetachCancelPendingWork() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val parent = FrameLayout(controller.get())
            val bar = KitRangeBar(controller.get())
            val start = RecordingDrawable(20, 20)
            bar.setStartThumb(start)
            bar.setEndThumb(RecordingDrawable(20, 20))
            parent.addView(bar, FrameLayout.LayoutParams(220, 40))
            controller.get().setContentView(parent)
            controller.visible()
            var callbackCount = 0
            start.scheduleSelf({ callbackCount++ }, SystemClock.uptimeMillis() + 30)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            assertEquals(1, callbackCount)

            start.scheduleSelf({ callbackCount++ }, SystemClock.uptimeMillis() + 30)
            val replacement = RecordingDrawable(20, 20)
            bar.setStartThumb(replacement)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            assertEquals(1, callbackCount)

            replacement.scheduleSelf({ callbackCount++ }, SystemClock.uptimeMillis() + 30)
            parent.removeView(bar)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            assertEquals(1, callbackCount)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun createBar(vararg attributes: Pair<Int, String>): KitRangeBar {
        val builder = Robolectric.buildAttributeSet()
            .addAttribute(android.R.attr.gravity, "center_vertical")
            .addAttribute(R.attr.kitStart, "20")
            .addAttribute(R.attr.kitEnd, "80")
            .addAttribute(R.attr.kitBarSize, "8px")
        attributes.forEach { (attribute: Int, value: String) -> builder.addAttribute(attribute, value) }
        return KitRangeBar(RuntimeEnvironment.getApplication(), builder.build()).apply {
            background = null
            minimumWidth = 0
            minimumHeight = 0
            setPadding(0, 0, 0, 0)
        }
    }

    private fun thumb(width: Int, height: Int, color: Int = Color.TRANSPARENT): GradientDrawable =
        GradientDrawable().apply {
            setSize(width, height)
            setColor(color)
        }

    private fun bitmapThumb(sourceDensity: Int, targetDensity: Int): BitmapDrawable {
        val bitmap = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888).apply {
            density = sourceDensity
            eraseColor(Color.BLUE)
        }
        return BitmapDrawable(RuntimeEnvironment.getApplication().resources, bitmap).apply {
            setTargetDensity(targetDensity)
        }
    }

    private fun measure(bar: KitRangeBar, height: Int, heightMode: Int) {
        bar.measure(
            View.MeasureSpec.makeMeasureSpec(220, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, heightMode),
        )
    }

    private fun draw(bar: KitRangeBar): Bitmap {
        measure(bar, height = 40, heightMode = View.MeasureSpec.EXACTLY)
        bar.layout(0, 0, bar.measuredWidth, bar.measuredHeight)
        return Bitmap.createBitmap(bar.width, bar.height, Bitmap.Config.ARGB_8888).also {
            bar.draw(Canvas(it))
        }
    }

    private fun assertDragStates(bar: KitRangeBar, startDragging: Boolean, endDragging: Boolean) {
        val bitmap = draw(bar)
        assertEquals(if (startDragging) Color.RED else Color.BLUE, bitmap.getPixel(50, 20))
        assertEquals(if (endDragging) Color.RED else Color.BLUE, bitmap.getPixel(170, 20))
    }

    private fun assertSharedThumbDraw(
        bar: KitRangeBar,
        drawable: StatefulRecordingDrawable,
        startDragging: Boolean,
        endDragging: Boolean,
    ) {
        val initialBounds = Rect(drawable.bounds)
        val initialState = drawable.state.toList()
        drawable.drawnBounds.clear()
        drawable.drawnDragging.clear()
        val bitmap = draw(bar)
        val indices = drawable.drawnBounds.indices.sortedBy { drawable.drawnBounds[it].centerX() }
        assertEquals(2, indices.size)

        for ((index, dragging) in indices.zip(listOf(startDragging, endDragging))) {
            val bounds = drawable.drawnBounds[index]
            assertEquals(dragging, drawable.drawnDragging[index])
            assertEquals(if (dragging) 40 else 20, bounds.width())
            assertEquals(if (dragging) 32 else 12, bounds.height())
            assertEquals(if (dragging) Color.RED else Color.BLUE, bitmap.getPixel(bounds.centerX(), bounds.centerY()))
        }
        assertEquals(initialBounds, drawable.bounds)
        assertEquals(initialState, drawable.state.toList())
        measure(bar, height = 100, heightMode = View.MeasureSpec.AT_MOST)
        assertEquals(if (startDragging || endDragging) 32 else 12, bar.measuredHeight)
    }

    private fun touch(bar: KitRangeBar, action: Int, pointers: List<Pair<Int, Float>>, actionIndex: Int = 0) {
        val event = MotionEvent.obtain(
            0L,
            SystemClock.uptimeMillis(),
            action or (actionIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            pointers.size,
            pointers.map { (pointerId: Int, _: Float) ->
                MotionEvent.PointerProperties().apply {
                    id = pointerId
                    toolType = MotionEvent.TOOL_TYPE_FINGER
                }
            }.toTypedArray(),
            pointers.map { (_, pointerX: Float) ->
                MotionEvent.PointerCoords().apply {
                    x = pointerX
                    y = 20f
                    pressure = 1f
                    size = 1f
                }
            }.toTypedArray(),
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0,
        )
        try {
            assertTrue(bar.onTouchEvent(event))
        } finally {
            event.recycle()
        }
    }

    /**
     * 记录经过 Canvas 平移后的真实绘制边界.
     */
    private class RecordingDrawable(
        private val desiredWidth: Int = -1,
        private val desiredHeight: Int = -1,
    ) : Drawable() {

        val drawnBounds: MutableList<Rect> = mutableListOf()

        override fun draw(canvas: Canvas) {
            val visualBounds = RectF(bounds)
            @Suppress("DEPRECATION")
            canvas.matrix.mapRect(visualBounds)
            drawnBounds += Rect().apply { visualBounds.round(this) }
            canvas.drawRect(bounds, Paint().apply { color = Color.TRANSPARENT })
        }

        override fun getIntrinsicWidth(): Int = desiredWidth

        override fun getIntrinsicHeight(): Int = desiredHeight

        override fun setAlpha(alpha: Int) = Unit

        override fun setColorFilter(colorFilter: ColorFilter?) = Unit

        @Deprecated("Deprecated in Android SDK")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    /**
     * 以一次延迟帧模拟状态动画, 记录跳转是否错误取消独占实例的动画调度.
     */
    private class StateAnimationDrawable : Drawable() {

        var jumpCount: Int = 0
            private set
        var frameCount: Int = 0
            private set
        private val frame: Runnable = Runnable {
            frameCount++
            invalidateSelf()
        }
        private val paint: Paint = Paint()

        override fun draw(canvas: Canvas) {
            paint.color = if (state.contains(android.R.attr.state_drag_hovered)) Color.RED else Color.BLUE
            canvas.drawRect(bounds, paint)
        }

        override fun isStateful(): Boolean = true

        override fun onStateChange(state: IntArray): Boolean {
            unscheduleSelf(frame)
            if (state.contains(android.R.attr.state_drag_hovered)) {
                scheduleSelf(frame, SystemClock.uptimeMillis() + 30)
            }
            invalidateSelf()
            return true
        }

        override fun jumpToCurrentState() {
            jumpCount++
            unscheduleSelf(frame)
        }

        override fun getIntrinsicWidth(): Int = 20

        override fun getIntrinsicHeight(): Int = 20

        override fun setAlpha(alpha: Int) = Unit

        override fun setColorFilter(colorFilter: ColorFilter?) = Unit

        @Deprecated("Deprecated in Android SDK")
        override fun getOpacity(): Int = PixelFormat.OPAQUE
    }

    /**
     * 无 ConstantState 的状态图形, 同步失效状态和边界以验证共享时不会产生重绘循环.
     */
    private class StatefulRecordingDrawable : Drawable() {

        val drawnBounds: MutableList<Rect> = mutableListOf()
        val drawnDragging: MutableList<Boolean> = mutableListOf()
        var invalidateOnDraw: Boolean = false
        private val dragging: Boolean
            get() = state.contains(android.R.attr.state_drag_hovered)
        private val paint: Paint = Paint()

        override fun draw(canvas: Canvas) {
            val visualBounds = RectF(bounds)
            @Suppress("DEPRECATION")
            canvas.matrix.mapRect(visualBounds)
            drawnBounds += Rect().apply { visualBounds.round(this) }
            drawnDragging += dragging
            paint.color = if (dragging) Color.RED else Color.BLUE
            canvas.drawRect(bounds, paint)
            if (invalidateOnDraw) {
                invalidateSelf()
            }
        }

        override fun isStateful(): Boolean = true

        override fun onStateChange(state: IntArray): Boolean {
            invalidateSelf()
            return true
        }

        override fun onBoundsChange(bounds: Rect) {
            invalidateSelf()
        }

        override fun getIntrinsicWidth(): Int = if (dragging) 40 else 20

        override fun getIntrinsicHeight(): Int = if (dragging) 32 else 12

        override fun setAlpha(alpha: Int) = Unit

        override fun setColorFilter(colorFilter: ColorFilter?) = Unit

        @Deprecated("Deprecated in Android SDK")
        override fun getOpacity(): Int = PixelFormat.OPAQUE
    }
}
