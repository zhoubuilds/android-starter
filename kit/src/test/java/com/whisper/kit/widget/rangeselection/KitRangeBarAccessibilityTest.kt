package com.whisper.kit.widget.rangeselection

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import com.whisper.kit.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 验证范围端点虚拟节点的整数语义、键盘生命周期和 RTL 几何.
 *
 * @author whisper
 * @since 2026/09/10
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KitRangeBarAccessibilityTest {

    @Test
    fun nodes_preserveExactIntegerValuesAndLegalBoundsNearIntMaximum() = withBar(
        R.attr.kitStep to "1",
    ) { bar ->
        bar.setTotal(Int.MAX_VALUE)
        bar.setSelectedRange(Int.MAX_VALUE - 10, Int.MAX_VALUE - 2)
        bar.setMinSpan(3)
        val host = node(bar, View.NO_ID)
        assertEquals(2, host.childCount)
        assertDescription(bar, 1, Int.MAX_VALUE - 10, 0, Int.MAX_VALUE - 5)
        assertDescription(bar, 2, Int.MAX_VALUE - 2, Int.MAX_VALUE - 7, Int.MAX_VALUE)
        assertNull(node(bar, 1).rangeInfo)
        assertFalse(node(bar, 1).actionList.any {
            it.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id
        })
        assertFalse(perform(bar, 1, AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id, Bundle().apply {
            putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, 0f)
        }))
        assertEquals(Int.MAX_VALUE - 10, bar.start)
    }

    @Test
    fun nodes_updateLegalLimitsAfterOtherEndpointAndConstraintsChange() = withBar { bar ->
        bar.setStartMax(45)
        bar.setEndMin(65)
        bar.setMinSpan(25)
        assertDescription(bar, 1, 20, 0, 40)
        assertDescription(bar, 2, 80, 70, 100)

        bar.setSelectedRange(10, 40)
        assertDescription(bar, 1, 10, 0, 40)
        assertDescription(bar, 2, 70, 70, 100)

        bar.setStartMax(KitRangeBar.THUMB_UNLIMITED)
        bar.setEndMin(KitRangeBar.THUMB_UNLIMITED)
        bar.end = 50
        assertDescription(bar, 1, 10, 0, 20)
        assertDescription(bar, 2, 50, 40, 100)
    }

    @Test
    fun actions_adjustOneStepAndProduceOneCompleteInteraction() = withBar { bar ->
        val events = recordEvents(bar)
        assertTrue(perform(bar, 1, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))
        assertEquals(30 to 80, bar.start to bar.end)
        assertEquals(listOf("start:20,80", "change:30,80", "end:30,80"), events)

        events.clear()
        assertTrue(perform(bar, 2, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD))
        assertEquals(30 to 70, bar.start to bar.end)
        assertEquals(listOf("start:30,80", "change:30,70", "end:30,70"), events)
    }

    @Test
    fun actions_atLimitAreAbsentAndRejectedWithoutInteraction() = withBar { bar ->
        bar.setSelectedRange(0, 100)
        val events = recordEvents(bar)
        assertFalse(hasAction(node(bar, 1), AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD))
        assertFalse(hasAction(node(bar, 2), AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))
        assertFalse(perform(bar, 1, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD))
        assertFalse(perform(bar, 2, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))
        assertTrue(events.isEmpty())
    }

    @Test
    fun startMinimum_updatesNodeBoundsAndBlocksBothEndpointActionsBelowFirstTick() = withBar(
        R.attr.kitStartMin to "21",
        R.attr.kitEnd to "30",
    ) { bar ->
        assertEquals(30 to 30, bar.start to bar.end)
        assertDescription(bar, 1, 30, 30, 30)
        assertDescription(bar, 2, 30, 30, 100)
        val events = recordEvents(bar)
        for (id in 1..2) {
            assertFalse(hasAction(node(bar, id), AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD))
            assertFalse(perform(bar, id, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD))
        }
        assertTrue(events.isEmpty())

        bar.setStartMin(41)
        assertEquals(50 to 50, bar.start to bar.end)
        assertDescription(bar, 1, 50, 50, 50)
        assertDescription(bar, 2, 50, 50, 100)
        bar.setStartMin(KitRangeBar.THUMB_UNLIMITED)
        assertDescription(bar, 1, 50, 0, 50)
        assertTrue(perform(bar, 1, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD))
        assertEquals(40 to 50, bar.start to bar.end)
    }

    @Test
    fun startMinimum_keyboardDecrementsStopAtBoundaryInLtrAndRtl() {
        for (rtl in listOf(false, true)) {
            withBar(R.attr.kitStartMin to "21", R.attr.kitStart to "40", R.attr.kitEnd to "40") { bar ->
                bar.layoutDirection = if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
                val decrementKey = if (rtl) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
                for (id in 1..2) {
                    focus(bar, id)
                    key(bar, KeyEvent.ACTION_DOWN, decrementKey)
                    key(bar, KeyEvent.ACTION_DOWN, decrementKey, repeat = 1)
                    key(bar, KeyEvent.ACTION_UP, decrementKey)
                }
                assertEquals(30 to 30, bar.start to bar.end)
                assertDescription(bar, 1, 30, 30, 30)
                assertDescription(bar, 2, 30, 30, 100)
            }
        }
    }

    @Test
    fun disabledEndpointRemainsReadableAndOtherEndpointRemainsAdjustable() = withBar(
        R.attr.kitStartThumbEnable to "false",
    ) { bar ->
        assertEquals(2, node(bar, View.NO_ID).childCount)
        assertDescription(bar, 1, 20, 0, 80)
        assertFalse(node(bar, 1).isEnabled)
        assertTrue(node(bar, 1).isFocusable)
        assertFalse(hasAction(node(bar, 1), AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))
        assertFalse(perform(bar, 1, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))
        assertTrue(perform(bar, 2, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD))
        assertEquals(20 to 70, bar.start to bar.end)

        bar.isEnabled = false
        assertFalse(node(bar, 2).isEnabled)
        assertFalse(perform(bar, 2, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD))
        bar.isEnabled = true
        assertTrue(node(bar, 2).isEnabled)
        assertTrue(perform(bar, 2, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD))
    }

    @Test
    fun overlappingEndpointsRemainSeparateAndIndependentlyKeyboardFocusable() = withBar { bar ->
        bar.setSelectedRange(50, 50)
        assertEquals(2, node(bar, View.NO_ID).childCount)
        assertEquals(bounds(node(bar, 1)), bounds(node(bar, 2)))
        assertNotEquals(node(bar, 1).contentDescription, node(bar, 2).contentDescription)
        focus(bar, 1)
        assertTrue(node(bar, 1).isFocused)
        assertFalse(node(bar, 2).isFocused)
        assertTrue(perform(bar, 2, AccessibilityNodeInfo.ACTION_FOCUS))
        assertFalse(node(bar, 1).isFocused)
        assertTrue(node(bar, 2).isFocused)
        assertTrue(perform(bar, 2, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))
        assertEquals(50 to 60, bar.start to bar.end)
    }

    @Test
    fun labels_acceptXmlAndRuntimeValuesAndNullRestoresDefaults() = withBar(
        R.attr.kitStartThumbLabel to "Minimum price",
        R.attr.kitEndThumbLabel to "Maximum price",
    ) { bar ->
        assertEquals("Minimum price", bar.startThumbLabel.toString())
        assertEquals("Maximum price", bar.endThumbLabel.toString())
        assertTrue(node(bar, 1).contentDescription.toString().startsWith("Minimum price"))
        assertTrue(node(bar, 2).contentDescription.toString().startsWith("Maximum price"))

        bar.startThumbLabel = "Earliest time"
        bar.endThumbLabel = "Latest time"
        assertTrue(node(bar, 1).contentDescription.toString().startsWith("Earliest time"))
        assertTrue(node(bar, 2).contentDescription.toString().startsWith("Latest time"))
        bar.startThumbLabel = null
        bar.endThumbLabel = null
        assertDescription(bar, 1, 20, 0, 80)
        assertDescription(bar, 2, 80, 20, 100)
    }

    @Test
    fun tabSwitchesEndpointsAndFallsThroughAtBothEdges() = withBar { bar ->
        val sibling = View(bar.context).apply { isFocusableInTouchMode = true }
        (bar.parent as FrameLayout).addView(sibling, FrameLayout.LayoutParams(20, 20))
        assertTrue(sibling.requestFocus())
        assertTrue(bar.requestFocus(View.FOCUS_FORWARD))
        assertTrue(node(bar, 1).isFocused)
        assertTrue(key(bar, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB))
        assertTrue(node(bar, 2).isFocused)
        assertFalse(key(bar, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB))

        assertTrue(key(bar, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB, metaState = KeyEvent.META_SHIFT_ON))
        assertTrue(node(bar, 1).isFocused)
        assertFalse(key(bar, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB, metaState = KeyEvent.META_SHIFT_ON))
        assertTrue(sibling.requestFocus())
        assertTrue(bar.requestFocus(View.FOCUS_BACKWARD))
        assertTrue(node(bar, 2).isFocused)
    }

    @Test
    fun keyboardRepeatAdjustsOneStepPerEventWithinOneInteraction() = withBar { bar ->
        focus(bar, 1)
        val events = recordEvents(bar)
        assertTrue(key(bar, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
        assertTrue(key(bar, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, repeat = 1))
        assertTrue(key(bar, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, repeat = 2))
        assertTrue(key(bar, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(50 to 80, bar.start to bar.end)
        assertEquals(
            listOf("start:20,80", "change:30,80", "change:40,80", "change:50,80", "end:50,80"),
            events,
        )
    }

    @Test
    fun keyboardListenerCanConsumeAdjustmentAndEndpointNavigationBeforeControlHandling() = withBar { bar ->
        focus(bar, 1)
        val events = recordEvents(bar)
        val deliveredKeys = mutableListOf<Pair<Int, Int>>()
        bar.setOnKeyListener { _, code, event ->
            deliveredKeys.add(event.action to code)
            true
        }

        for (code in listOf(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_TAB)) {
            assertTrue(key(bar, KeyEvent.ACTION_DOWN, code))
            assertTrue(key(bar, KeyEvent.ACTION_UP, code))
        }

        assertEquals(20 to 80, bar.start to bar.end)
        assertTrue(node(bar, 1).isFocused)
        assertFalse(node(bar, 2).isFocused)
        assertTrue(events.isEmpty())
        assertEquals(
            listOf(
                KeyEvent.ACTION_DOWN to KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.ACTION_UP to KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.ACTION_DOWN to KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.ACTION_UP to KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.ACTION_DOWN to KeyEvent.KEYCODE_TAB,
                KeyEvent.ACTION_UP to KeyEvent.KEYCODE_TAB,
            ),
            deliveredKeys,
        )
    }

    @Test
    fun keyboardNonConsumingListenerRunsBeforeAdjustmentAndAllowsNavigation() = withBar { bar ->
        focus(bar, 1)
        val events = recordEvents(bar)
        bar.setOnKeyListener { _, code, event ->
            events.add("key:${event.action},$code")
            false
        }

        assertTrue(key(bar, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
        assertTrue(key(bar, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT))
        assertTrue(key(bar, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB))

        assertEquals(30 to 80, bar.start to bar.end)
        assertTrue(node(bar, 2).isFocused)
        assertEquals(
            listOf(
                "key:${KeyEvent.ACTION_DOWN},${KeyEvent.KEYCODE_DPAD_RIGHT}",
                "start:20,80",
                "change:30,80",
                "key:${KeyEvent.ACTION_UP},${KeyEvent.KEYCODE_DPAD_RIGHT}",
                "end:30,80",
                "key:${KeyEvent.ACTION_DOWN},${KeyEvent.KEYCODE_TAB}",
            ),
            events,
        )
    }

    @Test
    fun keyboardListenerConsumingReleaseStillClosesAcceptedInteraction() {
        for (flags in listOf(0, KeyEvent.FLAG_CANCELED)) {
            withBar { bar ->
                focus(bar, 1)
                val events = recordEvents(bar)
                bar.setOnKeyListener { _, _, event -> event.action == KeyEvent.ACTION_UP }

                assertTrue(key(bar, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
                assertTrue(key(bar, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT, flags = flags))

                val finish = if (flags == KeyEvent.FLAG_CANCELED) "cancel" else "end"
                assertEquals(listOf("start:20,80", "change:30,80", "$finish:30,80"), events)
                bar.setOnKeyListener(null)
                assertTrue(key(bar, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
                assertTrue(key(bar, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT))
                assertEquals(40 to 80, bar.start to bar.end)
                assertEquals(listOf("start:30,80", "change:40,80", "end:40,80"), events.takeLast(3))
            }
        }
    }

    @Test
    fun keyboardReleaseListenerStartingReplacementDoesNotFinishNewInteraction() = withBar { bar ->
        focus(bar, 1)
        val events = recordEvents(bar)
        var replacementStarted = false
        bar.setOnKeyListener { _, code, event ->
            if (event.action == KeyEvent.ACTION_UP && !replacementStarted) {
                replacementStarted = true
                bar.isEnabled = false
                bar.isEnabled = true
                focus(bar, 1)
                assertTrue(key(bar, KeyEvent.ACTION_DOWN, code))
            }
            event.action == KeyEvent.ACTION_UP
        }
        assertTrue(key(bar, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))

        assertTrue(key(bar, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT))

        assertEquals(40 to 80, bar.start to bar.end)
        assertEquals(
            listOf("start:20,80", "change:30,80", "cancel:30,80", "start:30,80", "change:40,80"),
            events,
        )
        assertTrue(key(bar, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals("end:40,80", events.last())
        assertEquals(6, events.size)
    }

    @Test
    fun legacyMultipleDoesNotSynthesizeUnpairedAdjustmentOrBlockAccessibility() = withBar { bar ->
        focus(bar, 1)
        val events = recordEvents(bar)
        val deliveredKeys = mutableListOf<Int>()
        bar.setOnKeyListener { _, code, _ ->
            deliveredKeys.add(code)
            false
        }
        for (code in listOf(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_TAB)) {
            assertTrue(key(bar, KeyEvent.ACTION_MULTIPLE, code, repeat = 3))
        }
        assertEquals(3, deliveredKeys.size)
        assertEquals(20 to 80, bar.start to bar.end)
        assertTrue(node(bar, 1).isFocused)
        assertTrue(events.isEmpty())
        assertTrue(perform(bar, 1, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))
        assertEquals(listOf("start:20,80", "change:30,80", "end:30,80"), events)
    }

    @Test
    fun keyboardCancelledReleaseRetainsValueAndReportsCancellation() = withBar { bar ->
        focus(bar, 1)
        val events = recordEvents(bar)
        key(bar, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT)
        key(bar, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT, flags = KeyEvent.FLAG_CANCELED)
        assertEquals(10 to 80, bar.start to bar.end)
        assertEquals(listOf("start:20,80", "change:10,80", "cancel:10,80"), events)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun keyboardFocusTransferCancelsOngoingAdjustmentAndMovesThumbFocusState() = withBar { bar ->
        bar.setThumb(StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), GradientDrawable().apply {
                setSize(20, 20)
                setColor(Color.GREEN)
            })
            addState(intArrayOf(), GradientDrawable().apply {
                setSize(20, 20)
                setColor(Color.BLUE)
            })
        })
        fun assertFocusColors(startX: Int, startColor: Int, endColor: Int) {
            val bitmap = Bitmap.createBitmap(220, 40, Bitmap.Config.ARGB_8888)
            try {
                bar.draw(Canvas(bitmap))
                assertEquals(startColor, bitmap.getPixel(startX, 30))
                assertEquals(endColor, bitmap.getPixel(170, 30))
            } finally {
                bitmap.recycle()
            }
        }
        focus(bar, 1)
        assertFocusColors(50, Color.GREEN, Color.BLUE)
        val events = recordEvents(bar)
        key(bar, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)
        assertTrue(key(bar, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB))
        assertEquals(listOf("start:20,80", "change:30,80", "cancel:30,80"), events)
        assertFocusColors(70, Color.BLUE, Color.GREEN)
    }

    @Test
    fun rtlMirrorsNodeBoundsAndKeyboardDirectionWhileAccessibilityKeepsNumericDirection() = withBar { bar ->
        bar.layoutDirection = View.LAYOUT_DIRECTION_RTL
        assertTrue(bounds(node(bar, 1)).centerX() > bounds(node(bar, 2)).centerX())
        focus(bar, 1)
        key(bar, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)
        key(bar, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals(10 to 80, bar.start to bar.end)
        assertTrue(perform(bar, 1, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))
        assertEquals(20 to 80, bar.start to bar.end)

        bar.isRtlMirroringEnabled = false
        assertEquals(View.LAYOUT_DIRECTION_RTL, bar.layoutDirection)
        assertTrue(bounds(node(bar, 1)).centerX() < bounds(node(bar, 2)).centerX())
        key(bar, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)
        key(bar, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals(30 to 80, bar.start to bar.end)
    }

    @Test
    fun fixedLtrDirectionCanBeConfiguredInXml() = withBar(
        R.attr.kitRtlMirroringEnabled to "false",
    ) { bar ->
        bar.layoutDirection = View.LAYOUT_DIRECTION_RTL
        assertFalse(bar.isRtlMirroringEnabled)
        assertTrue(bounds(node(bar, 1)).centerX() < bounds(node(bar, 2)).centerX())
    }

    @Test
    fun keyboardDirectionChangeWithDisablingCancelCallbackDoesNotLoseNextInteractionStart() = withBar { bar ->
        focus(bar, 1)
        val events = mutableListOf<String>()
        bar.setOnInteractionListener(object : KitRangeBar.OnInteractionListener {
            override fun onInteractionStart(start: Int, end: Int) { events.add("start") }
            override fun onInteractionEnd(start: Int, end: Int) { events.add("end") }
            override fun onInteractionCancel(start: Int, end: Int) {
                events.add("cancel")
                bar.isEnabled = false
            }
        })
        key(bar, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)
        key(bar, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT)
        assertEquals(listOf("start", "cancel"), events)
        assertEquals(30 to 80, bar.start to bar.end)
        bar.isEnabled = true
        focus(bar, 1)
        key(bar, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT)
        key(bar, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT)
        assertEquals(listOf("start", "cancel", "start", "end"), events)
        assertEquals(20 to 80, bar.start to bar.end)
    }

    @Test
    @Config(sdk = [26, 34])
    fun focusability_preservesPlatformAutoAndExplicitTouchModeConfiguration() {
        val automatic = bareBar(android.R.attr.focusable to "auto")
        assertEquals(View.FOCUSABLE_AUTO, automatic.focusable)
        val disabled = bareBar(android.R.attr.focusable to "false")
        assertFalse(disabled.isFocusable)
        val touchEnabled = bareBar(android.R.attr.focusableInTouchMode to "true")
        assertTrue(touchEnabled.isFocusable)
        assertTrue(touchEnabled.isFocusableInTouchMode)
        val touchDisabled = bareBar(android.R.attr.focusable to "true", android.R.attr.focusableInTouchMode to "false")
        assertTrue(touchDisabled.isFocusable)
        assertFalse(touchDisabled.isFocusableInTouchMode)
    }

    @Test
    @Config(sdk = [24, 25])
    fun preApi26PreservesExplicitNonFocusableConfiguration() {
        assertFalse(bareBar(android.R.attr.focusable to "false").isFocusable)
        assertTrue(bareBar(android.R.attr.focusableInTouchMode to "true").isFocusableInTouchMode)
    }

    @Test
    @Config(sdk = [24])
    fun api24ExposesBothEndpointsAndUsesExactStepActions() = withBar { bar ->
        assertEquals(2, node(bar, View.NO_ID).childCount)
        assertDescription(bar, 1, 20, 0, 80)
        assertTrue(perform(bar, 1, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))
        assertEquals(30 to 80, bar.start to bar.end)
    }

    private fun withBar(vararg attrs: Pair<Int, String>, action: (KitRangeBar) -> Unit) {
        val controller = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val applicationInfo = controller.get().applicationInfo
        val originalFlags = applicationInfo.flags
        applicationInfo.flags = originalFlags or ApplicationInfo.FLAG_SUPPORTS_RTL
        try {
            val values = linkedMapOf(R.attr.kitStep to "10", R.attr.kitStart to "20", R.attr.kitEnd to "80")
            values.putAll(attrs)
            val attributes = Robolectric.buildAttributeSet().apply {
                values.forEach { (id, value) -> addAttribute(id, value) }
            }.build()
            val bar = KitRangeBar(controller.get(), attributes).apply {
                id = 101
                isFocusableInTouchMode = true
                setThumb(GradientDrawable().apply { setSize(20, 20) })
            }
            val root = FrameLayout(controller.get()).apply {
                addView(bar, FrameLayout.LayoutParams(220, 40))
            }
            controller.get().setContentView(root)
            root.measure(
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
            )
            root.layout(0, 0, 400, 300)
            action(bar)
        } finally {
            applicationInfo.flags = originalFlags
            controller.pause().stop().destroy()
        }
    }

    private fun node(bar: KitRangeBar, id: Int): AccessibilityNodeInfo =
        requireNotNull(bar.accessibilityNodeProvider.createAccessibilityNodeInfo(id))

    private fun perform(bar: KitRangeBar, id: Int, action: Int, arguments: Bundle? = null): Boolean =
        bar.accessibilityNodeProvider.performAction(id, action, arguments)

    private fun focus(bar: KitRangeBar, id: Int) {
        assertTrue(bar.requestFocus())
        if (!node(bar, id).isFocused) {
            assertTrue(perform(bar, id, AccessibilityNodeInfo.ACTION_FOCUS))
        }
        assertTrue(node(bar, id).isFocused)
    }

    private fun bareBar(vararg attrs: Pair<Int, String>): KitRangeBar {
        val attributes = Robolectric.buildAttributeSet().apply {
            attrs.forEach { (id, value) -> addAttribute(id, value) }
        }.build()
        return KitRangeBar(RuntimeEnvironment.getApplication(), attributes)
    }

    private fun assertDescription(bar: KitRangeBar, id: Int, value: Int, minimum: Int, maximum: Int) {
        val label = bar.context.getText(if (id == 1) R.string.kit_range_start_label else R.string.kit_range_end_label)
        assertEquals(
            bar.context.getString(R.string.kit_range_accessibility_description, label, value, minimum, maximum),
            node(bar, id).contentDescription.toString(),
        )
    }

    private fun hasAction(node: AccessibilityNodeInfo, action: Int): Boolean = node.actionList.any { it.id == action }

    private fun bounds(node: AccessibilityNodeInfo): Rect = Rect().also(node::getBoundsInParent)

    private fun key(
        bar: KitRangeBar,
        action: Int,
        code: Int,
        repeat: Int = 0,
        metaState: Int = 0,
        flags: Int = 0,
    ): Boolean {
        val time = SystemClock.uptimeMillis()
        return bar.dispatchKeyEvent(KeyEvent(time, time, action, code, repeat, metaState, -1, 0, flags))
    }

    private fun recordEvents(bar: KitRangeBar): MutableList<String> {
        val events = mutableListOf<String>()
        bar.setOnRangeChangedListener { start, end -> events.add("change:$start,$end") }
        bar.setOnInteractionListener(object : KitRangeBar.OnInteractionListener {
            override fun onInteractionStart(start: Int, end: Int) {
                events.add("start:$start,$end")
            }

            override fun onInteractionEnd(start: Int, end: Int) {
                events.add("end:$start,$end")
            }

            override fun onInteractionCancel(start: Int, end: Int) {
                events.add("cancel:$start,$end")
            }
        })
        return events
    }
}
