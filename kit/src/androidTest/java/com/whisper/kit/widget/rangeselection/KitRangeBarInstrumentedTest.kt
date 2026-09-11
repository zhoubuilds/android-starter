package com.whisper.kit.widget.rangeselection

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Handler
import android.os.Looper
import android.os.Parcel
import android.os.Parcelable
import android.os.SystemClock
import android.util.SparseArray
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.whisper.kit.test.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import com.whisper.kit.R as KitR

/**
 * 验证范围选择控件在硬件窗口中的轨道绘制, 外观主动刷新和双指重合交互.
 *
 * @author whisper
 * @since 2026/09/09
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 26)
class KitRangeBarInstrumentedTest {

    private lateinit var activity: KitRangeBarTestActivity
    private var gestureDownTime: Long = 0L

    @Before
    fun launchActivity() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, KitRangeBarTestActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as KitRangeBarTestActivity
        var ready = false
        val deadline: Long = SystemClock.uptimeMillis() + 5_000L
        while (!ready && SystemClock.uptimeMillis() < deadline) {
            onMainThread {
                ready = activity.rangeBar.hasWindowFocus() && activity.rangeBar.isLaidOut
            }
            if (!ready) {
                SystemClock.sleep(50L)
            }
        }
        assertTrue("The test window must be focused and laid out.", ready)
        onMainThread {
            assertTrue("The range bar must use hardware accelerated rendering.", activity.rangeBar.isHardwareAccelerated)
            assertEquals(220, activity.rangeBar.width)
            assertEquals(40, activity.rangeBar.height)
        }
    }

    @After
    fun finishActivity() {
        if (::activity.isInitialized) {
            onMainThread { activity.finish() }
        }
    }

    @Test
    fun track_selectedForegroundPreservesUnselectedBackgroundInHardwareWindow() {
        awaitWindowPixels("The red selection must preserve the gray track outside its range.") { bitmap ->
            bitmap.getPixel(20, 30) == Color.GRAY && bitmap.getPixel(110, 30) == Color.RED &&
                bitmap.getPixel(200, 30) == Color.GRAY && bitmap.getPixel(110, 10) == Color.WHITE &&
                bitmap.getPixel(50, 30) == Color.BLUE && bitmap.getPixel(170, 30) == Color.BLUE
        }
    }

    @Test
    fun thumbs_runtimeConfigurationAndDrawableCallbacksRepaintWithoutManualInvalidation() {
        awaitThumbColors(50, Color.BLUE, 170, Color.BLUE)

        onMainThread { activity.rangeBar.setThumb(thumb(40, Color.GREEN)) }
        awaitThumbColors(56, Color.GREEN, 164, Color.GREEN)

        onMainThread { activity.rangeBar.setStartThumb(thumb(20, Color.YELLOW)) }
        awaitThumbColors(56, Color.YELLOW, 164, Color.GREEN)

        onMainThread { activity.rangeBar.setThumb(thumb(40, Color.MAGENTA)) }
        awaitThumbColors(56, Color.YELLOW, 164, Color.MAGENTA)

        onMainThread {
            (activity.rangeBar.getStartThumb() as GradientDrawable).setColor(Color.CYAN)
        }
        awaitThumbColors(56, Color.CYAN, 164, Color.MAGENTA)

        onMainThread { activity.rangeBar.setStartThumb(KitRangeBar.INHERIT_THUMB) }
        awaitThumbColors(56, Color.MAGENTA, 164, Color.MAGENTA)
    }

    @Test
    fun thumbs_bitmapTargetDensityPreservesSizeAndTouchBoundsInHardwareWindow() {
        onMainThread {
            val bitmap = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888).apply {
                density = 160
                eraseColor(Color.MAGENTA)
            }
            val source = BitmapDrawable(activity.resources, bitmap).apply { setTargetDensity(320) }
            activity.rangeBar.setThumb(source)
            assertEquals(40, requireNotNull(activity.rangeBar.getStartThumb()).intrinsicWidth)
            assertEquals(40, requireNotNull(activity.rangeBar.getEndThumb()).intrinsicHeight)
        }
        awaitWindowPixels("The configured bitmap density must preserve its 40px thumb bounds.") { bitmap ->
            bitmap.getPixel(35, 5) == Color.WHITE && bitmap.getPixel(36, 5) == Color.MAGENTA &&
                bitmap.getPixel(75, 35) == Color.MAGENTA && bitmap.getPixel(76, 35) == Color.WHITE
        }
        dispatchTouch(MotionEvent.ACTION_DOWN, listOf(7 to 37f))
        dispatchTouch(MotionEvent.ACTION_UP, listOf(7 to 55f))
        assertRange(30, 80)
    }

    @Test
    fun thumbs_nestedBitmapWrappersPreserveDensityAndTouchBoundsInHardwareWindow() {
        onMainThread {
            val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply {
                density = 160
                eraseColor(Color.GREEN)
            }
            val child = BitmapDrawable(activity.resources, bitmap).apply { setTargetDensity(320) }
            val source = LayerDrawable(arrayOf(InsetDrawable(child, 4)))
            assertEquals(40, source.intrinsicWidth)
            activity.rangeBar.setThumb(source)
            assertEquals(40, requireNotNull(activity.rangeBar.getStartThumb()).intrinsicWidth)
            assertEquals(40, requireNotNull(activity.rangeBar.getEndThumb()).intrinsicHeight)
        }
        awaitWindowPixels("Nested drawable wrappers must preserve the configured bitmap bounds.") { bitmap ->
            bitmap.getPixel(39, 6) == Color.WHITE && bitmap.getPixel(40, 6) == Color.GREEN &&
                bitmap.getPixel(71, 34) == Color.GREEN && bitmap.getPixel(72, 34) == Color.WHITE
        }
        dispatchTouch(MotionEvent.ACTION_DOWN, listOf(7 to 37f))
        dispatchTouch(MotionEvent.ACTION_UP, listOf(7 to 55f))
        assertRange(30, 80)
    }

    @Test
    fun thumbs_sharedLayersPreserveConfiguredAlphaFilterAndPixelsInHardwareWindow() {
        var expectedColor = Color.TRANSPARENT
        onMainThread {
            val filter = PorterDuffColorFilter(Color.GREEN, PorterDuff.Mode.SRC_IN)
            val overlay = thumb(20, Color.RED).apply {
                alpha = 64
                colorFilter = filter
            }
            val source = LayerDrawable(arrayOf(thumb(20, Color.BLUE), overlay))
            val expected = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
            try {
                source.setBounds(0, 0, 20, 20)
                source.draw(Canvas(expected))
                expectedColor = expected.getPixel(10, 2)
            } finally {
                expected.recycle()
            }
            activity.rangeBar.setThumb(source)
            assertSame(source, activity.rangeBar.getStartThumb())
            assertSame(source, activity.rangeBar.getEndThumb())
            assertSame(overlay, source.getDrawable(1))
            assertEquals(64, overlay.alpha)
            assertSame(filter, overlay.colorFilter)
        }
        awaitWindowPixels("Both shared thumbs must preserve the configured child alpha and filter.") { bitmap ->
            bitmap.getPixel(50, 22) == expectedColor && bitmap.getPixel(170, 22) == expectedColor
        }
    }

    @Test
    fun thumbs_nullRemovesEndpointUntilInheritanceSentinelRestoresIt() {
        awaitThumbColors(50, Color.BLUE, 170, Color.BLUE)

        onMainThread { activity.rangeBar.setStartThumb(null) }
        awaitThumbColors(50, Color.RED, 170, Color.BLUE)

        onMainThread { activity.rangeBar.setThumb(thumb(20, Color.GREEN)) }
        awaitThumbColors(50, Color.RED, 170, Color.GREEN)

        onMainThread { activity.rangeBar.setStartThumb(KitRangeBar.INHERIT_THUMB) }
        awaitThumbColors(50, Color.GREEN, 170, Color.GREEN)
    }

    @Test
    fun thumbs_stylesDistinguishOmittedClearedAndInheritedEndpoints() {
        onMainThread {
            val context = InstrumentationRegistry.getInstrumentation().context
            val common = KitRangeBar(context, null, 0, R.style.kit_test_range_common)
            assertEquals(Color.BLUE, (common.getStartThumb() as ColorDrawable).color)
            assertEquals(Color.BLUE, (common.getEndThumb() as ColorDrawable).color)

            val cleared = KitRangeBar(context, null, 0, R.style.kit_test_range_cleared)
            assertNull(cleared.getStartThumb())
            assertNull(cleared.getEndThumb())
            cleared.setThumb(ColorDrawable(Color.YELLOW))
            assertNull(cleared.getStartThumb())
            assertNull(cleared.getEndThumb())

            val overridden = KitRangeBar(context, null, 0, R.style.kit_test_range_overridden)
            assertEquals(Color.RED, (overridden.getStartThumb() as ColorDrawable).color)
            assertEquals(Color.GREEN, (overridden.getEndThumb() as ColorDrawable).color)

            val inherited = KitRangeBar(context, null, 0, R.style.kit_test_range_inherited)
            assertEquals(Color.BLUE, (inherited.getStartThumb() as ColorDrawable).color)
            assertEquals(Color.BLUE, (inherited.getEndThumb() as ColorDrawable).color)
        }
    }

    @Test
    fun thumbs_twoPointersSeparateOverlapAndReleaseTheirOwnDrawableState() {
        onMainThread {
            activity.rangeBar.setThumb(StateListDrawable().apply {
                setEnterFadeDuration(400)
                setExitFadeDuration(400)
                addState(intArrayOf(android.R.attr.state_drag_hovered), thumb(20, Color.GREEN))
                addState(intArrayOf(), thumb(20, Color.BLUE))
            })
            activity.rangeBar.setSelectedRange(50, 50)
        }
        awaitThumbColors(110, Color.BLUE, 110, Color.BLUE)

        dispatchTouch(MotionEvent.ACTION_DOWN, listOf(7 to 110f))
        dispatchTouch(MotionEvent.ACTION_POINTER_DOWN, listOf(7 to 110f, 19 to 110f), actionIndex = 1)
        dispatchTouch(MotionEvent.ACTION_MOVE, listOf(7 to 50f, 19 to 170f))
        assertRange(20, 80)
        awaitThumbColors(50, Color.GREEN, 170, Color.GREEN)

        dispatchTouch(MotionEvent.ACTION_POINTER_UP, listOf(7 to 50f, 19 to 170f), actionIndex = 0)
        assertRange(20, 80)
        awaitThumbColors(50, Color.BLUE, 170, Color.GREEN)

        dispatchTouch(MotionEvent.ACTION_MOVE, listOf(19 to 190f))
        assertRange(20, 90)
        awaitThumbColors(50, Color.BLUE, 190, Color.GREEN)

        dispatchTouch(MotionEvent.ACTION_UP, listOf(19 to 190f))
        assertRange(20, 90)
        awaitThumbColors(50, Color.BLUE, 190, Color.BLUE)
    }

    @Test
    fun touch_finalReleaseCoordinatesAreCommittedBeforeInteractionEnd() {
        val events = mutableListOf<String>()
        onMainThread {
            activity.rangeBar.setOnRangeChangedListener { start, end -> events.add("change:$start,$end") }
            activity.rangeBar.setOnInteractionListener(object : KitRangeBar.OnInteractionListener {
                override fun onInteractionEnd(start: Int, end: Int) { events.add("end:$start,$end") }
            })
        }
        dispatchTouch(MotionEvent.ACTION_DOWN, listOf(7 to 50f))
        dispatchTouch(MotionEvent.ACTION_MOVE, listOf(7 to 70f))
        dispatchTouch(MotionEvent.ACTION_UP, listOf(7 to 90f))
        onMainThread {
            assertEquals(listOf("change:30,80", "change:40,80", "end:40,80"), events)
            activity.rangeBar.setSelectedRange(20, 80)
            events.clear()
        }
        dispatchTouch(MotionEvent.ACTION_DOWN, listOf(7 to 50f))
        dispatchTouch(MotionEvent.ACTION_POINTER_DOWN, listOf(7 to 50f, 19 to 170f), actionIndex = 1)
        dispatchTouch(MotionEvent.ACTION_POINTER_UP, listOf(7 to 90f, 19 to 190f), actionIndex = 0)
        onMainThread { assertEquals(listOf("change:40,90"), events) }
        dispatchTouch(MotionEvent.ACTION_UP, listOf(19 to 210f))
        onMainThread { assertEquals(listOf("change:40,90", "change:40,100", "end:40,100"), events) }
    }

    @Test
    @SdkSuppress(minSdkVersion = 33)
    fun touch_cancelledPointerKeepsItsValueWhileRemainingPointerFinishesInteraction() {
        val events = mutableListOf<String>()
        onMainThread {
            activity.rangeBar.setThumb(StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_pressed), thumb(20, Color.GREEN))
                addState(intArrayOf(), thumb(20, Color.BLUE))
            })
            activity.rangeBar.setOnRangeChangedListener { start, end -> events.add("change:$start,$end") }
            activity.rangeBar.setOnInteractionListener(object : KitRangeBar.OnInteractionListener {
                override fun onInteractionStart(start: Int, end: Int) { events.add("start:$start,$end") }
                override fun onInteractionEnd(start: Int, end: Int) { events.add("end:$start,$end") }
                override fun onInteractionCancel(start: Int, end: Int) { events.add("cancel:$start,$end") }
            })
        }
        dispatchTouch(MotionEvent.ACTION_DOWN, listOf(7 to 50f))
        dispatchTouch(MotionEvent.ACTION_POINTER_DOWN, listOf(7 to 50f, 19 to 170f), actionIndex = 1)
        dispatchTouch(MotionEvent.ACTION_MOVE, listOf(7 to 70f, 19 to 190f))
        dispatchTouch(MotionEvent.ACTION_POINTER_UP, listOf(7 to 130f, 19 to 210f),
            actionIndex = 0, flags = MotionEvent.FLAG_CANCELED)
        assertRange(30, 100)
        awaitThumbColors(70, Color.BLUE, 210, Color.GREEN)
        onMainThread { assertEquals(listOf("start:20,80", "change:30,90", "change:30,100"), events) }
        dispatchTouch(MotionEvent.ACTION_MOVE, listOf(19 to 190f))
        dispatchTouch(MotionEvent.ACTION_UP, listOf(19 to 170f))
        onMainThread {
            assertEquals(listOf("start:20,80", "change:30,90", "change:30,100", "change:30,90",
                "change:30,80", "end:30,80"), events)
        }
    }

    @Test
    fun accessibility_systemNodesExposeExactIntegersAndAdjustThroughRealProvider() {
        onMainThread {
            activity.rangeBar.setTotal(Int.MAX_VALUE)
            activity.rangeBar.setSelectedRange(Int.MAX_VALUE - 10, Int.MAX_VALUE - 2)
            activity.rangeBar.setMinSpan(3)
        }
        val start = awaitAccessibilityNode("Lower limit") {
            it.contentDescription.toString() == description("Lower limit", Int.MAX_VALUE - 10, 0, Int.MAX_VALUE - 5)
        }
        val end = awaitAccessibilityNode("Upper limit") {
            it.contentDescription.toString() == description("Upper limit", Int.MAX_VALUE - 2, Int.MAX_VALUE - 7, Int.MAX_VALUE)
        }
        assertNull(start.rangeInfo)
        assertNull(end.rangeInfo)
        assertFalse(start.actionList.any { it.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id })
        assertTrue(start.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))
        assertRange(Int.MAX_VALUE - 9, Int.MAX_VALUE - 2)
        awaitAccessibilityNode("Upper limit") {
            it.contentDescription.toString() == description("Upper limit", Int.MAX_VALUE - 2, Int.MAX_VALUE - 6, Int.MAX_VALUE)
        }

        onMainThread { activity.rangeBar.isEnabled = false }
        val disabled = awaitAccessibilityNode("Lower limit") { !it.isEnabled }
        assertFalse(disabled.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))
        assertRange(Int.MAX_VALUE - 9, Int.MAX_VALUE - 2)
        onMainThread { activity.rangeBar.isEnabled = true }
        val enabled = awaitAccessibilityNode("Lower limit") { it.isEnabled }
        assertTrue(enabled.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD))
        assertRange(Int.MAX_VALUE - 10, Int.MAX_VALUE - 2)
    }

    @Test
    fun keyboard_systemListenerCanConsumeArrowsAndTabBeforeRangeHandling() {
        val keys = mutableListOf<Int>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        onMainThread { assertTrue(activity.beforeRange.requestFocus()) }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_TAB)
        awaitAccessibilityNode("Lower limit") { it.isFocused }
        onMainThread {
            activity.rangeBar.setOnKeyListener { _, keyCode, _ ->
                keys.add(keyCode)
                true
            }
        }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_TAB)
        assertRange(20, 80)
        awaitAccessibilityNode("Lower limit") { it.isFocused }
        onMainThread {
            assertEquals(listOf(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_TAB), keys)
            activity.rangeBar.setOnKeyListener(null)
        }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertRange(21, 80)
    }

    @Test
    fun keyboard_systemTabEntersBothEndpointsAndLeavesViewWithOneRepeatInteraction() {
        val events = mutableListOf<String>()
        onMainThread {
            activity.rangeBar.setOnRangeChangedListener { start, end -> events.add("change:$start,$end") }
            activity.rangeBar.setOnInteractionListener(object : KitRangeBar.OnInteractionListener {
                override fun onInteractionStart(start: Int, end: Int) { events.add("start:$start,$end") }
                override fun onInteractionEnd(start: Int, end: Int) { events.add("end:$start,$end") }
                override fun onInteractionCancel(start: Int, end: Int) { events.add("cancel:$start,$end") }
            })
            assertTrue(activity.beforeRange.requestFocus())
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_TAB)
        awaitAccessibilityNode("Lower limit") { it.isFocused }
        val downTime = SystemClock.uptimeMillis()
        for (repeat in 0..2) {
            instrumentation.sendKeySync(KeyEvent(
                downTime, SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, repeat,
            ))
        }
        instrumentation.sendKeySync(KeyEvent(
            downTime, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT, 0,
        ))
        onMainThread {
            assertEquals(23 to 80, activity.rangeBar.start to activity.rangeBar.end)
            assertEquals(
                listOf("start:20,80", "change:21,80", "change:22,80", "change:23,80", "end:23,80"), events,
            )
        }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_TAB)
        awaitAccessibilityNode("Upper limit") { it.isFocused }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_TAB)
        onMainThread { assertTrue("Tab must leave the range bar after its second endpoint.", activity.afterRange.isFocused) }
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            instrumentation.sendKeySync(KeyEvent(
                downTime, SystemClock.uptimeMillis(), action, KeyEvent.KEYCODE_TAB, 0, KeyEvent.META_SHIFT_ON,
            ))
        }
        awaitAccessibilityNode("Upper limit") { it.isFocused }
    }

    @Test
    fun rtl_mirrorsForegroundAndSystemNodeBoundsAndCanKeepFixedLtrValues() {
        onMainThread {
            activity.rangeBar.layoutDirection = View.LAYOUT_DIRECTION_RTL
            activity.rangeBar.setSelectedRange(20, 60)
            activity.rangeBar.setStartThumb(thumb(20, Color.YELLOW))
            activity.rangeBar.setEndThumb(thumb(20, Color.CYAN))
        }
        awaitWindowPixels("RTL must clip the selected track between mirrored endpoint positions.") { bitmap ->
            bitmap.getPixel(170, 30) == Color.YELLOW && bitmap.getPixel(90, 30) == Color.CYAN &&
                bitmap.getPixel(130, 30) == Color.RED && bitmap.getPixel(50, 30) == Color.GRAY
        }
        val startBounds = Rect()
        val endBounds = Rect()
        awaitAccessibilityNode("Lower limit").getBoundsInScreen(startBounds)
        awaitAccessibilityNode("Upper limit").getBoundsInScreen(endBounds)
        assertTrue(startBounds.centerX() > endBounds.centerX())

        onMainThread { activity.rangeBar.isRtlMirroringEnabled = false }
        awaitWindowPixels("Fixed LTR must restore increasing values from left to right.") { bitmap ->
            bitmap.getPixel(50, 30) == Color.YELLOW && bitmap.getPixel(130, 30) == Color.CYAN &&
                bitmap.getPixel(90, 30) == Color.RED && bitmap.getPixel(170, 30) == Color.GRAY
        }
        awaitAccessibilityNode("Lower limit").getBoundsInScreen(startBounds)
        awaitAccessibilityNode("Upper limit").getBoundsInScreen(endBounds)
        assertTrue(startBounds.centerX() < endBounds.centerX())
        onMainThread { assertEquals(View.LAYOUT_DIRECTION_RTL, activity.rangeBar.layoutDirection) }
    }

    @Test
    fun savedState_realParcelRestoresAllConstraintsWithoutInteraction() {
        onMainThread {
            val context = InstrumentationRegistry.getInstrumentation().context
            val original = KitRangeBar(context, null, 0, R.style.kit_test_range_step_seven).apply {
                id = KitRangeBarTestActivity.RANGE_ID
                setTotal(140)
                setSelectedRange(28, 112)
                setStartMin(20)
                setStartMax(56)
                setEndMin(84)
                setMinSpan(35)
            }
            val saved = SparseArray<Parcelable>()
            original.saveHierarchyState(saved)
            val parcel = Parcel.obtain()
            try {
                parcel.writeParcelable(saved[original.id], 0)
                parcel.setDataPosition(0)
                val state = requireNotNull(parcel.readParcelable<Parcelable>(KitRangeBar::class.java.classLoader))
                val restored = KitRangeBar(context).apply { id = original.id }
                val events = mutableListOf<String>()
                restored.setOnRangeChangedListener { start, end -> events.add("change:$start,$end") }
                restored.setOnInteractionListener(object : KitRangeBar.OnInteractionListener {
                    override fun onInteractionStart(start: Int, end: Int) { events.add("start") }
                    override fun onInteractionEnd(start: Int, end: Int) { events.add("end") }
                    override fun onInteractionCancel(start: Int, end: Int) { events.add("cancel") }
                })
                restored.restoreHierarchyState(SparseArray<Parcelable>().apply { put(restored.id, state) })
                assertEquals(28 to 112, restored.start to restored.end)
                assertEquals(20, restored.getStartMin())
                assertEquals(listOf("change:28,112"), events)
                restored.start = 100
                assertEquals(56 to 112, restored.start to restored.end)
                restored.end = 0
                assertEquals(56 to 91, restored.start to restored.end)
                restored.start = 0
                restored.end = 0
                assertEquals(21 to 84, restored.start to restored.end)
                restored.end = Int.MAX_VALUE
                assertEquals(21 to 140, restored.start to restored.end)
                restored.start = 30
                assertEquals(28 to 140, restored.start to restored.end)
                assertFalse(events.any { it == "start" || it == "end" || it == "cancel" })
            } finally {
                parcel.recycle()
            }
        }
    }

    private fun description(label: String, value: Int, minimum: Int, maximum: Int): String =
        activity.getString(KitR.string.kit_range_accessibility_description, label, value, minimum, maximum)

    private fun awaitAccessibilityNode(
        label: String,
        matches: (AccessibilityNodeInfo) -> Boolean = { true },
    ): AccessibilityNodeInfo {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val deadline = SystemClock.uptimeMillis() + 5_000L
        while (SystemClock.uptimeMillis() < deadline) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            if (root != null && root.packageName?.toString() == instrumentation.targetContext.packageName) {
                val pending = ArrayDeque<AccessibilityNodeInfo>()
                pending.add(root)
                while (pending.isNotEmpty()) {
                    val node = pending.removeFirst()
                    if (node.contentDescription?.toString()?.startsWith("$label,") == true && matches(node)) {
                        return node
                    }
                    for (index in 0 until node.childCount) {
                        node.getChild(index)?.let(pending::add)
                    }
                }
            }
            SystemClock.sleep(50L)
        }
        error("The range endpoint '$label' was not exposed with the expected accessibility state.")
    }

    private fun thumb(width: Int, color: Int): GradientDrawable = GradientDrawable().apply {
        setSize(width, 20)
        setColor(color)
    }

    private fun assertRange(start: Int, end: Int) {
        onMainThread {
            assertEquals(start, activity.rangeBar.start)
            assertEquals(end, activity.rangeBar.end)
        }
    }

    private fun dispatchTouch(action: Int, pointers: List<Pair<Int, Float>>, actionIndex: Int = 0, flags: Int = 0) {
        onMainThread {
            val eventTime: Long = SystemClock.uptimeMillis()
            if (action == MotionEvent.ACTION_DOWN) {
                gestureDownTime = eventTime
            }
            val event = MotionEvent.obtain(
                gestureDownTime,
                eventTime,
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
                        y = 30f
                        pressure = 1f
                        size = 1f
                    }
                }.toTypedArray(),
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, flags,
            )
            try {
                assertTrue("The range bar must consume its thumb gesture.", activity.rangeBar.dispatchTouchEvent(event))
            } finally {
                event.recycle()
            }
        }
    }

    private fun awaitThumbColors(startX: Int, startColor: Int, endX: Int, endColor: Int) {
        awaitWindowPixels("The two thumbs must repaint with their independently resolved colors.") { bitmap ->
            bitmap.getPixel(startX, 30) == startColor && bitmap.getPixel(endX, 30) == endColor
        }
    }

    private fun awaitWindowPixels(message: String, matches: (Bitmap) -> Boolean) {
        val deadline: Long = SystemClock.uptimeMillis() + 5_000L
        val bitmap: Bitmap = Bitmap.createBitmap(220, 40, Bitmap.Config.ARGB_8888)
        try {
            do {
                val copied: CountDownLatch = CountDownLatch(1)
                var result: Int = PixelCopy.ERROR_UNKNOWN
                onMainThread {
                    val location: IntArray = IntArray(2)
                    activity.rangeBar.getLocationInWindow(location)
                    val bounds: Rect = Rect(location[0], location[1], location[0] + 220, location[1] + 40)
                    PixelCopy.request(activity.window, bounds, bitmap, { copyResult ->
                        result = copyResult
                        copied.countDown()
                    }, Handler(Looper.getMainLooper()))
                }
                assertTrue("The window pixel copy did not complete in time.", copied.await(2, TimeUnit.SECONDS))
                assertTrue(
                    "The window pixel copy failed with result $result.",
                    result == PixelCopy.SUCCESS || result == PixelCopy.ERROR_SOURCE_NO_DATA,
                )
                if (result == PixelCopy.SUCCESS && matches(bitmap)) {
                    return
                }
                // 等待属性或 Drawable callback 自己提交新帧, 不额外失效控件掩盖刷新遗漏.
                SystemClock.sleep(32L)
            } while (SystemClock.uptimeMillis() < deadline)
            throw AssertionError(message)
        } finally {
            bitmap.recycle()
        }
    }

    private fun onMainThread(block: () -> Unit) {
        var failure: Throwable? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try {
                block()
            } catch (error: Throwable) {
                failure = error
            }
        }
        failure?.let { throw it }
    }
}
