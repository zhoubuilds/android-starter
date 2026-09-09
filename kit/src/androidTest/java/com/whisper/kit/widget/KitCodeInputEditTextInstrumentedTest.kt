package com.whisper.kit.widget

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.TypedValue
import android.view.ActionMode
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewTreeObserver
import android.view.accessibility.AccessibilityNodeInfo
import android.view.autofill.AutofillValue
import android.view.inputmethod.EditorInfo
import androidx.appcompat.view.WindowCallbackWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.whisper.kit.test.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * 验证分格输入控件在真实窗口下的原生输入、布局和绘制生命周期.
 *
 * @author whisper
 * @since 2026/09/08
 */
@RunWith(AndroidJUnit4::class)
class KitCodeInputEditTextInstrumentedTest {

    private lateinit var activity: KitCodeInputEditTextTestActivity

    @Before
    fun launchActivity() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, KitCodeInputEditTextTestActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as KitCodeInputEditTextTestActivity
        awaitDraw { }
        awaitWindowFocus()
    }

    @After
    fun finishActivity() {
        if (::activity.isInitialized) {
            onMainThread { activity.finish() }
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = 26)
    fun textSizeChangesPreserveLayoutWithoutAdvertisingCharacterGeometry() {
        onMainThread { assertNativeLayoutWithoutCharacterGeometry() }

        awaitDraw {
            activity.input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32f)
        }

        onMainThread { assertNativeLayoutWithoutCharacterGeometry() }
    }

    @Test
    fun inputConnectionCommitsAndDeletesAtTheEndThroughNativeLengthFilter() {
        onMainThread {
            val input: KitCodeInputEditText = activity.input
            input.setText("")
            val connection = requireNotNull(input.onCreateInputConnection(EditorInfo()))

            assertTrue(connection.commitText("1234567", 1))
            assertEquals("123456", input.text.toString())
            assertTrue(connection.deleteSurroundingText(1, 0))
            assertEquals("12345", input.text.toString())
            connection.setSelection(0, 0)
            assertEquals(5, input.selectionStart)
            assertEquals(5, input.selectionEnd)
            assertTrue(connection.commitText("7", 1))
            assertEquals("123457", input.text.toString())
            assertTrue(connection.deleteSurroundingText(1, 0))
            assertEquals("12345", input.text.toString())
        }
    }

    @Test
    fun accessibilityExposesTheLabeledEditableFieldAndRejectsCharacterNavigation() {
        val node: AccessibilityNodeInfo = accessibilityInputNode()

        assertTrue(node.isEditable)
        assertEquals("1234", node.text.toString())
        assertEquals(6, node.maxTextLength)
        assertEquals(activity.getString(R.string.kit_test_code_input_label), node.labeledBy?.text?.toString())
        assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_FOCUS))
        assertTrue(node.refresh())
        assertTrue(node.isFocused)
        assertEquals(0, node.movementGranularities)
        val unsupportedActions: List<AccessibilityNodeInfo.AccessibilityAction> = listOf(
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_SELECTION,
            AccessibilityNodeInfo.AccessibilityAction.ACTION_NEXT_AT_MOVEMENT_GRANULARITY,
            AccessibilityNodeInfo.AccessibilityAction.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY,
        )
        assertTrue(unsupportedActions.none { it in node.actionList })
        if (Build.VERSION.SDK_INT >= 33) {
            assertFalse(node.isTextSelectable)
        }
        assertFalse(node.performAction(
            AccessibilityNodeInfo.ACTION_SET_SELECTION,
            Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, 1)
            },
        ))
        assertFalse(node.performAction(
            AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY,
            Bundle().apply {
                putInt(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT,
                    AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER,
                )
            },
        ))
    }

    @Test
    fun accessibilityReplacesAndClearsTheWholeFieldThroughTheLengthFilter() {
        val node: AccessibilityNodeInfo = accessibilityInputNode()

        assertTrue(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_TEXT in node.actionList)
        assertTrue(node.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT,
            Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "7654321")
            },
        ))
        onMainThread { assertEquals("765432", activity.input.text.toString()) }
        assertTrue(node.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT,
            Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
            },
        ))
        onMainThread { assertEquals("", activity.input.text.toString()) }
    }

    @Test
    @SdkSuppress(minSdkVersion = 28)
    fun accessibilityPastesAtTheEndWhenTheDeviceClipboardIsEmpty() {
        val clipboard: ClipboardManager = requireNotNull(activity.getSystemService(ClipboardManager::class.java))
        var occupied: Boolean = false
        onMainThread {
            assertTrue("Clipboard access requires a focused window.", activity.input.hasWindowFocus())
            assertTrue("The input must accept touch-mode focus.", activity.input.requestFocus())
            occupied = clipboard.hasPrimaryClip()
        }
        assumeFalse("The device clipboard contains existing data; leave it untouched.", occupied)

        try {
            onMainThread {
                activity.input.setText("12")
                clipboard.setPrimaryClip(ClipData.newPlainText("Kit input test", "87654"))
            }
            val node: AccessibilityNodeInfo = accessibilityInputNode()
            assertTrue(AccessibilityNodeInfo.AccessibilityAction.ACTION_PASTE in node.actionList)
            assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_PASTE))
            onMainThread { assertEquals("128765", activity.input.text.toString()) }
        } finally {
            onMainThread { clipboard.clearPrimaryClip() }
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = 28)
    fun longPressingAnEmptyFieldOpensAPasteMenuThatRespectsTheLengthFilter() {
        assertLongPressPastesThroughTheVisibleMenu("", "765432")
    }

    @Test
    @SdkSuppress(minSdkVersion = 28)
    fun longPressingBlankSlotsPastesAtTheEndThroughTheLengthFilter() {
        assertLongPressPastesThroughTheVisibleMenu("1234", "123476")
    }

    @Test
    @SdkSuppress(minSdkVersion = 28)
    fun tappingTheFieldDismissesTheLongPressMenuWithoutChangingText() {
        withVisibleLongPressPasteMenu("1234") { mode, _ ->
            val location: IntArray = IntArray(2)
            var x: Float = 0f
            var y: Float = 0f
            onMainThread {
                activity.input.getLocationOnScreen(location)
                x = location[0] + activity.input.width * 0.5f
                y = location[1] + activity.input.height * 0.5f
            }
            val downTime: Long = SystemClock.uptimeMillis()
            sendTouch(downTime, MotionEvent.ACTION_DOWN, x, y)
            sendTouch(downTime, MotionEvent.ACTION_UP, x, y)

            assertMenuDismissedWithoutChangingText(mode)
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = 28)
    fun backDismissesTheLongPressMenuWithoutFinishingTheActivity() {
        withVisibleLongPressPasteMenu("1234") { mode, _ ->
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)

            assertMenuDismissedWithoutChangingText(mode)
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = 26)
    fun autofillUpdatesTheNativeEditableAndRespectsLengthFilter() {
        onMainThread {
            activity.input.autofill(AutofillValue.forText("7654321"))

            assertEquals("765432", activity.input.text.toString())
        }
    }

    @Test
    fun staticItemBackgroundStopsDrawingAfterTheWindowSettles() {
        val frames: AtomicInteger = AtomicInteger()
        val listener: ViewTreeObserver.OnDrawListener = ViewTreeObserver.OnDrawListener {
            frames.incrementAndGet()
        }
        onMainThread {
            assertFalse(activity.input.hasFocus())
            activity.input.viewTreeObserver.addOnDrawListener(listener)
            activity.input.itemBackground = ColorDrawable(Color.LTGRAY)
        }
        try {
            // 等待窗口过渡和首帧结束, 再确认连续两个采样区间都保持静止.
            val deadline: Long = SystemClock.uptimeMillis() + 5_000L
            var quietIntervals: Int = 0
            var previousFrames: Int = frames.get()
            while (SystemClock.uptimeMillis() < deadline && quietIntervals < 2) {
                SystemClock.sleep(250L)
                val currentFrames: Int = frames.get()
                quietIntervals = if (currentFrames == previousFrames) quietIntervals + 1 else 0
                previousFrames = currentFrames
            }

            assertTrue("The static background must be drawn at least once.", frames.get() > 0)
            assertEquals("A static item background must not keep scheduling frames.", 2, quietIntervals)
        } finally {
            onMainThread {
                activity.input.viewTreeObserver.removeOnDrawListener(listener)
            }
        }
    }

    private fun assertNativeLayoutWithoutCharacterGeometry() {
        val input: KitCodeInputEditText = activity.input
        assertNotNull("A mounted EditText must retain its native Layout.", input.layout)
        val node: AccessibilityNodeInfo = input.createAccessibilityNodeInfo()
        assertFalse(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY in node.availableExtraData)
        input.addExtraDataToAccessibilityNodeInfo(
            node,
            AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY,
            Bundle().apply {
                putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX, 0)
                putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH, 4)
            },
        )
        assertFalse(node.extras.containsKey(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY))
    }

    private fun assertLongPressPastesThroughTheVisibleMenu(initialText: String, expectedText: String) {
        withVisibleLongPressPasteMenu(initialText) { _, pasteNode ->
            val bounds: Rect = Rect()
            pasteNode.getBoundsInScreen(bounds)
            assertFalse("The visible Paste menu item must have screen bounds.", bounds.isEmpty)
            val clickTime: Long = SystemClock.uptimeMillis()
            sendTouch(clickTime, MotionEvent.ACTION_DOWN, bounds.exactCenterX(), bounds.exactCenterY())
            sendTouch(clickTime, MotionEvent.ACTION_UP, bounds.exactCenterX(), bounds.exactCenterY())
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            onMainThread {
                assertEquals(expectedText, activity.input.text.toString())
                assertEquals(expectedText.length, activity.input.selectionStart)
                assertEquals(expectedText.length, activity.input.selectionEnd)
            }
        }
    }

    private fun assertMenuDismissedWithoutChangingText(mode: AtomicReference<ActionMode?>) {
        val deadline: Long = SystemClock.uptimeMillis() + 5_000L
        while (mode.get() != null && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(50L)
        }
        assertTrue("The long-press menu must finish after dismissal.", mode.get() == null)
        onMainThread {
            assertFalse("Dismissing the menu must not finish its Activity.", activity.isFinishing)
            assertFalse("Dismissing the menu must not destroy its Activity.", activity.isDestroyed)
            assertEquals("1234", activity.input.text.toString())
        }
    }

    private fun withVisibleLongPressPasteMenu(
        initialText: String,
        block: (AtomicReference<ActionMode?>, AccessibilityNodeInfo) -> Unit,
    ) {
        val clipboard: ClipboardManager = requireNotNull(activity.getSystemService(ClipboardManager::class.java))
        val mode: AtomicReference<ActionMode?> = AtomicReference()
        val originalCallback = activity.window.callback
        var occupied: Boolean = false
        onMainThread {
            assertTrue("Clipboard access requires a focused window.", activity.input.hasWindowFocus())
            occupied = clipboard.hasPrimaryClip()
        }
        assumeFalse("The device clipboard contains existing data; leave it untouched.", occupied)

        try {
            awaitDraw {
                activity.window.callback = object : WindowCallbackWrapper(originalCallback) {
                    override fun onActionModeStarted(actionMode: ActionMode) {
                        mode.set(actionMode)
                        super.onActionModeStarted(actionMode)
                    }

                    override fun onActionModeFinished(actionMode: ActionMode) {
                        mode.compareAndSet(actionMode, null)
                        super.onActionModeFinished(actionMode)
                    }
                }
                activity.input.setText(initialText)
                clipboard.setPrimaryClip(ClipData.newPlainText("Kit long press test", "7654321"))
            }
            val location: IntArray = IntArray(2)
            var x: Float = 0f
            var y: Float = 0f
            onMainThread {
                activity.input.getLocationOnScreen(location)
                x = location[0] + activity.input.width * 0.85f
                y = location[1] + activity.input.height * 0.5f
            }
            val downTime: Long = SystemClock.uptimeMillis()
            sendTouch(downTime, MotionEvent.ACTION_DOWN, x, y)
            SystemClock.sleep(ViewConfiguration.getLongPressTimeout().toLong() + 300L)
            sendTouch(downTime, MotionEvent.ACTION_UP, x, y)

            var pasteTitle: String? = null
            val deadline: Long = SystemClock.uptimeMillis() + 5_000L
            while (pasteTitle == null && SystemClock.uptimeMillis() < deadline) {
                onMainThread {
                    val item = mode.get()?.menu?.findItem(android.R.id.paste)
                    if (item != null && item.isVisible && item.isEnabled) {
                        pasteTitle = item.title.toString()
                    }
                }
                if (pasteTitle == null) {
                    SystemClock.sleep(50L)
                }
            }
            assertNotNull("A real long press must open an enabled Paste menu item.", pasteTitle)
            val pasteNode: AccessibilityNodeInfo = visibleMenuNode(requireNotNull(pasteTitle))
            block(mode, pasteNode)
        } finally {
            onMainThread {
                mode.get()?.finish()
                activity.window.callback = originalCallback
                clipboard.clearPrimaryClip()
            }
        }
    }

    private fun visibleMenuNode(title: String): AccessibilityNodeInfo {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        val originalFlags: Int = automation.serviceInfo.flags
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        try {
            val deadline: Long = SystemClock.uptimeMillis() + 5_000L
            while (SystemClock.uptimeMillis() < deadline) {
                // 浮动工具栏使用独立的非焦点窗口, 不属于 rootInActiveWindow 的子树.
                val roots: List<AccessibilityNodeInfo> = automation.windows.mapNotNull { it.root }
                for (root: AccessibilityNodeInfo in roots) {
                    val packageName: String? = root.packageName?.toString()
                    if (packageName != instrumentation.targetContext.packageName && packageName != "android" &&
                        packageName != "com.android.systemui") {
                        continue
                    }
                    val pending: ArrayDeque<AccessibilityNodeInfo> = ArrayDeque()
                    pending.add(root)
                    while (pending.isNotEmpty()) {
                        val node: AccessibilityNodeInfo = pending.removeFirst()
                        val matchesTitle: Boolean = node.text?.toString() == title ||
                            node.contentDescription?.toString() == title
                        if (node.isVisibleToUser && matchesTitle) {
                            return node
                        }
                        for (index: Int in 0 until node.childCount) {
                            node.getChild(index)?.let(pending::add)
                        }
                    }
                }
                SystemClock.sleep(50L)
            }
            error("The Paste menu item was not exposed in the test accessibility windows.")
        } finally {
            automation.serviceInfo = automation.serviceInfo.apply { flags = originalFlags }
        }
    }

    private fun sendTouch(downTime: Long, action: Int, x: Float, y: Float) {
        val event: MotionEvent = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
        try {
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            InstrumentationRegistry.getInstrumentation().sendPointerSync(event)
        } finally {
            event.recycle()
        }
    }

    private fun accessibilityInputNode(): AccessibilityNodeInfo {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val deadline: Long = SystemClock.uptimeMillis() + 5_000L
        while (SystemClock.uptimeMillis() < deadline) {
            val root: AccessibilityNodeInfo? = instrumentation.uiAutomation.rootInActiveWindow
            if (root != null && root.packageName?.toString() == instrumentation.targetContext.packageName) {
                val pending: ArrayDeque<AccessibilityNodeInfo> = ArrayDeque()
                pending.add(root)
                while (pending.isNotEmpty()) {
                    val node: AccessibilityNodeInfo = pending.removeFirst()
                    if (node.isEditable) {
                        return node
                    }
                    for (index: Int in 0 until node.childCount) {
                        node.getChild(index)?.let(pending::add)
                    }
                }
            }
            SystemClock.sleep(50L)
        }
        error("The test input was not exposed in the active accessibility window.")
    }

    private fun awaitDraw(change: () -> Unit) {
        val drawn: CountDownLatch = CountDownLatch(1)
        val listener: ViewTreeObserver.OnDrawListener = ViewTreeObserver.OnDrawListener {
            drawn.countDown()
        }
        onMainThread {
            activity.input.viewTreeObserver.addOnDrawListener(listener)
            change()
            activity.input.invalidate()
        }
        try {
            assertTrue("The test window did not draw in time.", drawn.await(5, TimeUnit.SECONDS))
        } finally {
            onMainThread {
                activity.input.viewTreeObserver.removeOnDrawListener(listener)
            }
        }
    }

    private fun awaitWindowFocus() {
        var focused: Boolean = false
        val deadline: Long = SystemClock.uptimeMillis() + 5_000L
        while (!focused && SystemClock.uptimeMillis() < deadline) {
            onMainThread { focused = activity.input.hasWindowFocus() }
            if (!focused) {
                SystemClock.sleep(50L)
            }
        }
        assertTrue("The test window did not gain focus in time.", focused)
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
