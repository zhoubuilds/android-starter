package com.whisper.kit.widget

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.os.SystemClock
import android.text.InputFilter
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.util.TypedValue
import android.view.ActionMode
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.EditText
import android.widget.FrameLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowInstrumentation
import kotlin.math.ceil
import kotlin.math.max

/**
 * 验证分格文本输入控件的输入和绘制边界.
 *
 * @author whisper
 * @since 2026/08/14
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KitCodeInputEditTextTest {

    /**
     * 验证原生 LengthFilter 是分格数量的 XML 和运行时数据源.
     */
    @Test
    fun nativeLengthFilter_controlsSlotCountAndLimitsNativeText() {
        val background = RecordingDrawable()
        val input: KitCodeInputEditText = createInput()

        input.filters = arrayOf(InputFilter.LengthFilter(4))
        input.itemBackground = background
        input.setText("123456")
        draw(input, width = 160, height = 40)

        assertEquals(4, background.drawnBounds.size)
        assertEquals("1234", input.text.toString())
    }

    @Test
    fun init_nativeLengthAttributeLimitsInitialText() {
        val attributes = Robolectric.buildAttributeSet()
            .addAttribute(android.R.attr.maxLength, "4")
            .addAttribute(android.R.attr.text, "123456")
            .build()

        val input = KitCodeInputEditText(RuntimeEnvironment.getApplication(), attributes)

        assertEquals(4, input.filters.filterIsInstance<InputFilter.LengthFilter>().single().max)
        assertEquals("1234", input.text.toString())
    }

    @Test
    fun init_singleLineInputWithoutExplicitLengthKeepsSixSlots() {
        val attributes = Robolectric.buildAttributeSet()
            .addAttribute(android.R.attr.singleLine, "true")
            .addAttribute(android.R.attr.inputType, "number")
            .addAttribute(android.R.attr.text, "12345678")
            .build()

        val input = KitCodeInputEditText(RuntimeEnvironment.getApplication(), attributes)

        assertEquals(6, input.filters.filterIsInstance<InputFilter.LengthFilter>().single().max)
        assertEquals("123456", input.text.toString())
        input.setText("87654321")
        assertEquals("876543", input.text.toString())
    }

    @Test
    @Config(sdk = [24, 34])
    @GraphicsMode(GraphicsMode.Mode.LEGACY)
    fun init_withoutComponentStyleCanReceiveFocusInTouchMode() {
        val instrumentation = ShadowInstrumentation.getInstrumentation()
        instrumentation.setInTouchMode(true)
        val controller: ActivityController<Activity> = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val input = KitCodeInputEditText(controller.get())
            val parent = FrameLayout(controller.get()).apply {
                isFocusableInTouchMode = true
                descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS
                addView(input, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 80))
            }
            controller.get().setContentView(parent)
            controller.visible().windowFocusChanged(true)

            assertTrue(input.isAttachedToWindow)
            assertTrue(input.isInTouchMode)
            assertTrue(parent.requestFocus())
            assertFalse(input.isFocused)
            assertTrue(input.isFocusable)
            assertTrue(input.isFocusableInTouchMode)
            assertTrue(input.requestFocus())
            assertTrue(input.isFocused)
        } finally {
            controller.pause().stop().destroy()
            instrumentation.setInTouchMode(false)
        }
    }

    @Test
    @Config(sdk = [24, 34])
    @GraphicsMode(GraphicsMode.Mode.LEGACY)
    fun init_explicitTouchFocusableFalseIsPreserved() {
        val attributes = Robolectric.buildAttributeSet()
            .addAttribute(android.R.attr.focusableInTouchMode, "false")
            .build()

        val input = KitCodeInputEditText(RuntimeEnvironment.getApplication(), attributes)

        assertTrue(input.isFocusable)
        assertFalse(input.isFocusableInTouchMode)
    }

    @Test
    @Config(sdk = [24, 34])
    @GraphicsMode(GraphicsMode.Mode.LEGACY)
    fun init_explicitFocusableFalseIsPreserved() {
        val attributes = Robolectric.buildAttributeSet()
            .addAttribute(android.R.attr.focusable, "false")
            .build()

        val input = KitCodeInputEditText(RuntimeEnvironment.getApplication(), attributes)

        assertFalse(input.isFocusable)
        assertFalse(input.isFocusableInTouchMode)
    }

    @Test
    fun nativeLengthFilter_reductionTrimsExistingEditableAndRequestsLayout() {
        val input: KitCodeInputEditText = createInput().apply {
            setText("123456")
        }
        val editable = input.text
        draw(input, width = 180, height = 40)
        assertFalse(input.isLayoutRequested)

        input.filters = arrayOf(InputFilter.LengthFilter(3))

        assertSame(editable, input.text)
        assertEquals("123", input.text.toString())
        assertEquals(3, input.filters.filterIsInstance<InputFilter.LengthFilter>().single().max)
        assertTrue(input.isLayoutRequested)
    }

    @Test
    fun nativeLengthFilter_missingLengthRestoresDefaultAndRetainsOtherFilters() {
        val capitalization = InputFilter.AllCaps()
        val input: KitCodeInputEditText = createInput().apply {
            filters = arrayOf(InputFilter.LengthFilter(8))
            setText("abcdefgh")
        }

        input.filters = arrayOf(capitalization)

        assertEquals(6, input.filters.filterIsInstance<InputFilter.LengthFilter>().single().max)
        assertEquals("abcdef", input.text.toString())
        assertTrue(input.filters.any { it === capitalization })
        input.setText("abcdefgh")
        assertEquals("ABCDEF", input.text.toString())
    }

    @Test
    fun nativeLengthFilter_clearingFiltersRestoresDefaultLimit() {
        val input: KitCodeInputEditText = createInput()

        input.filters = emptyArray()
        input.setText("12345678")

        assertEquals("123456", input.text.toString())
        assertEquals(6, input.filters.filterIsInstance<InputFilter.LengthFilter>().single().max)
    }

    @Test
    fun nativeLengthFilters_useSmallestLimitAndRetainOtherFilters() {
        val capitalization = InputFilter.AllCaps()
        val background = RecordingDrawable()
        val input: KitCodeInputEditText = createInput().apply {
            itemBackground = background
        }

        input.filters = arrayOf(
            InputFilter.LengthFilter(5),
            capitalization,
            InputFilter.LengthFilter(3),
        )
        input.setText("abcdef")
        draw(input, width = 120, height = 40)

        assertEquals(3, background.drawnBounds.size)
        assertEquals("ABC", input.text.toString())
        assertTrue(input.filters.any { it === capitalization })
    }

    @Test
    fun nativeLengthFilter_zeroLimitIsRejectedWithoutChangingExistingState() {
        val input: KitCodeInputEditText = createInput().apply {
            filters = arrayOf(InputFilter.LengthFilter(4))
            setText("1234")
        }
        val originalFilters: Array<InputFilter> = input.filters

        assertThrows(IllegalArgumentException::class.java) {
            input.filters = arrayOf(InputFilter.LengthFilter(0))
        }

        assertSame(originalFilters, input.filters)
        assertEquals(4, input.filters.filterIsInstance<InputFilter.LengthFilter>().single().max)
        assertEquals("1234", input.text.toString())
        input.setText("123456")
        assertEquals("1234", input.text.toString())
    }

    @Test
    fun nativeLengthFilter_reductionDoesNotLeaveHalfOfSurrogatePair() {
        val input: KitCodeInputEditText = createInput().apply {
            setText("A\uD835\uDFD9B")
        }

        input.filters = arrayOf(InputFilter.LengthFilter(2))

        assertEquals("A", input.text.toString())
        input.setText("\uD835\uDFD9B")
        assertEquals("\uD835\uDFD9", input.text.toString())
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

    @Test
    fun accessibilityNode_retainsEditablePasswordHintAndSetTextSemantics() {
        val controller: ActivityController<Activity> = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val input = KitCodeInputEditText(controller.get()).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                filters = arrayOf(InputFilter.LengthFilter(4))
                hint = "Verification code"
                setText("1234")
            }
            controller.get().setContentView(input, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 80))
            controller.visible()
            assertTrue(input.isAttachedToWindow)

            val node: AccessibilityNodeInfo = input.createAccessibilityNodeInfo()
            val actionIds: List<Int> = node.actionList.map { it.id }

            assertEquals(EditText::class.java.name, node.className.toString())
            assertTrue(node.isEditable)
            assertTrue(node.isPassword)
            assertTrue(node.isEnabled)
            assertEquals(input.inputType, node.inputType)
            assertEquals("Verification code", node.hintText.toString())
            assertTrue(actionIds.contains(AccessibilityNodeInfo.ACTION_SET_TEXT))
            assertFalse(actionIds.contains(AccessibilityNodeInfo.ACTION_SET_SELECTION))
            assertFalse(actionIds.contains(AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY))
            assertFalse(actionIds.contains(AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY))
            assertEquals(0, node.movementGranularities)
            assertFalse(node.isTextSelectable)

            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "67890")
            }
            assertTrue(input.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments))
            assertEquals("6789", input.text.toString())
            assertEquals(4, input.selectionStart)
            assertEquals(4, input.selectionEnd)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    @Config(sdk = [24, 34])
    @GraphicsMode(GraphicsMode.Mode.LEGACY)
    fun accessibilityActions_rejectUnsupportedSelectionAndCharacterNavigation() {
        val input: KitCodeInputEditText = createInput().apply {
            setText("A12")
        }
        val arguments = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, 1)
            putInt(
                AccessibilityNodeInfo.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT,
                AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER,
            )
            putBoolean(AccessibilityNodeInfo.ACTION_ARGUMENT_EXTEND_SELECTION_BOOLEAN, true)
        }
        val actions: List<Int> = listOf(
            AccessibilityNodeInfo.ACTION_SET_SELECTION,
            AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY,
            AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY,
        )

        for (action: Int in actions) {
            assertFalse(input.performAccessibilityAction(action, arguments))
            assertEquals("A12", input.text.toString())
            assertEquals(3, input.selectionStart)
            assertEquals(3, input.selectionEnd)
        }
    }

    @Test
    fun contextMenu_rejectsSelectAllAndRetainsNativePasteAtTextEnd() {
        val input: KitCodeInputEditText = createInput().apply {
            setText("123")
        }
        val clipboard: ClipboardManager = input.context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("Short code", "45"))

        assertFalse(input.onTextContextMenuItem(android.R.id.selectAll))
        assertEquals("123", input.text.toString())
        assertEquals(3, input.selectionStart)
        assertEquals(3, input.selectionEnd)

        assertTrue(input.onTextContextMenuItem(android.R.id.paste))
        assertEquals("12345", input.text.toString())
        assertEquals(5, input.selectionStart)
        assertEquals(5, input.selectionEnd)
    }

    @Test
    @Config(sdk = [24])
    @GraphicsMode(GraphicsMode.Mode.LEGACY)
    fun pasteActionMode_preImeBackClosesMenuWithoutChangingText() {
        val controller: ActivityController<Activity> = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            var createdMode: ActionMode? = null
            var destroyedMode: ActionMode? = null
            val input = KitCodeInputEditText(controller.get()).apply {
                setText("123")
                customInsertionActionModeCallback = object : ActionMode.Callback {
                    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                        createdMode = mode
                        return true
                    }

                    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

                    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean = false

                    override fun onDestroyActionMode(mode: ActionMode) {
                        destroyedMode = mode
                    }
                }
            }
            controller.get().setContentView(input, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 80))
            controller.visible().windowFocusChanged(true)
            val clipboard: ClipboardManager = input.context.getSystemService(ClipboardManager::class.java)
            clipboard.setPrimaryClip(ClipData.newPlainText("Short code", "45"))

            assertTrue(input.performLongClick())
            assertNotNull(createdMode)
            assertNotNull(createdMode?.menu?.findItem(android.R.id.paste))
            val downTime: Long = SystemClock.uptimeMillis()
            val down = KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0)
            val up = KeyEvent(downTime, downTime + 10, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0)

            assertTrue(input.dispatchKeyEventPreIme(down))
            assertNull(destroyedMode)
            assertTrue(input.dispatchKeyEventPreIme(up))

            assertSame(createdMode, destroyedMode)
            assertEquals("123", input.text.toString())
            assertEquals(3, input.selectionStart)
            assertEquals(3, input.selectionEnd)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun itemBackground_directionChangeRemeasuresItsNewIntrinsicSize() {
        val applicationInfo: ApplicationInfo = RuntimeEnvironment.getApplication().applicationInfo
        val originalFlags: Int = applicationInfo.flags
        applicationInfo.flags = originalFlags or ApplicationInfo.FLAG_SUPPORTS_RTL
        try {
            val slotBackground = DirectionalSizeDrawable()
            val input: KitCodeInputEditText = createInput().apply {
                layoutDirection = View.LAYOUT_DIRECTION_LTR
                background = null
                minWidth = 0
                minHeight = 0
                setPadding(0, 0, 0, 0)
                filters = arrayOf(InputFilter.LengthFilter(2))
                itemSpacing = 4f
                itemBackground = slotBackground
            }
            measureAtMost(input)
            input.layout(0, 0, input.measuredWidth, input.measuredHeight)

            assertEquals(44, input.measuredWidth)
            assertEquals(24, input.measuredHeight)
            assertFalse(input.isLayoutRequested)

            input.layoutDirection = View.LAYOUT_DIRECTION_RTL
            measureAtMost(input)

            assertEquals(View.LAYOUT_DIRECTION_RTL, slotBackground.layoutDirection)
            assertEquals(68, input.measuredWidth)
            assertEquals(36, input.measuredHeight)
        } finally {
            applicationInfo.flags = originalFlags
        }
    }

    @Test
    fun itemBackground_visibilityTracksEffectiveVisibilityAcrossLifecycle() {
        val controller: ActivityController<Activity> = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val parent = FrameLayout(controller.get())
            val input = KitCodeInputEditText(controller.get()).apply {
                layoutParams = FrameLayout.LayoutParams(120, 40)
            }
            val background = RecordingDrawable()
            input.itemBackground = background
            parent.addView(input)
            controller.get().setContentView(parent)
            controller.visible()
            background.visibilityRequests.clear()

            input.visibility = View.INVISIBLE
            parent.visibility = View.INVISIBLE
            parent.visibility = View.VISIBLE
            input.onVisibilityAggregated(false)
            parent.removeView(input)
            parent.addView(input)

            assertFalse(background.isVisible)
            assertTrue(background.visibilityRequests.none { it.first })

            input.visibility = View.VISIBLE

            assertTrue(background.isVisible)
            assertTrue(background.visibilityRequests.last().first)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun itemBackground_aggregatedVisibilityAndAttachmentControlReplacementBackground() {
        val controller: ActivityController<Activity> = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val parent = FrameLayout(controller.get())
            val input = KitCodeInputEditText(controller.get()).apply {
                layoutParams = FrameLayout.LayoutParams(120, 40)
            }
            val originalBackground = RecordingDrawable()
            input.itemBackground = originalBackground
            parent.addView(input)
            controller.get().setContentView(parent)
            controller.visible()
            assertTrue(originalBackground.isVisible)

            input.onVisibilityAggregated(false)

            assertFalse(originalBackground.isVisible)
            val replacementBackground = RecordingDrawable()
            input.itemBackground = replacementBackground
            assertFalse(replacementBackground.isVisible)

            input.onVisibilityAggregated(true)
            assertTrue(replacementBackground.isVisible)
            parent.removeView(input)
            assertFalse(input.isAttachedToWindow)
            assertFalse(replacementBackground.isVisible)

            val detachedBackground = RecordingDrawable()
            input.itemBackground = detachedBackground
            assertFalse(detachedBackground.isVisible)
            parent.addView(input)
            assertTrue(input.isAttachedToWindow)
            assertTrue(detachedBackground.isVisible)
            assertTrue(detachedBackground.visibilityRequests.none { it.second })
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun itemBackground_hotspotMapsViewPointToNearestSlotLocalCoordinates() {
        val background = RecordingDrawable()
        val input: KitCodeInputEditText = createInput().apply {
            this.background = null
            setPadding(10, 0, 10, 0)
            filters = arrayOf(InputFilter.LengthFilter(6))
            itemBackground = background
            itemWidth = 40f
            itemHeight = 40f
            itemSpacing = 8f
        }
        draw(input, width = 300, height = 80)
        background.hotspotRequests.clear()
        val stateBefore: IntArray = background.state.copyOf()

        input.drawableHotspotChanged(270f, 40f)

        assertEquals(listOf(20f to 20f), background.hotspotRequests)
        assertEquals(stateBefore.toList(), background.state.toList())
    }

    @Test
    fun setError_iconRemainsVisibleAfterFocusIsLost() {
        val input: KitCodeInputEditText = createInput().apply {
            background = null
            setPadding(0, 0, 0, 0)
            filters = arrayOf(InputFilter.LengthFilter(2))
            itemWidth = 30f
            itemHeight = 40f
        }
        val errorIcon = RecordingDrawable(22, 22, Color.RED).apply {
            setBounds(0, 0, 22, 22)
        }
        input.setError("Invalid code", errorIcon)
        input.clearFocus()
        val bitmap: Bitmap = drawBitmap(input, width = 120, height = 50)
        val nativeInput = EditText(input.context).apply {
            background = null
            setPadding(0, 0, 0, 0)
            setError("Invalid code", ColorDrawable(Color.RED).apply { setBounds(0, 0, 22, 22) })
            clearFocus()
        }
        val nativeBitmap: Bitmap = drawBitmap(nativeInput, width = 120, height = 50)

        assertFalse(input.isFocused)
        assertTrue(input.compoundDrawables.any { it === errorIcon })
        assertEquals(22 * 22, countPixels(nativeBitmap, Color.RED))
        assertEquals(countPixels(nativeBitmap, Color.RED), countPixels(bitmap, Color.RED))
        assertEquals(pixelBounds(nativeBitmap, Color.RED), pixelBounds(bitmap, Color.RED))
    }

    @Test
    fun compoundDrawables_areVisibleAndMeasuredOutsideSlotsInLtrAndRtl() {
        val applicationInfo: ApplicationInfo = RuntimeEnvironment.getApplication().applicationInfo
        applicationInfo.flags = applicationInfo.flags or ApplicationInfo.FLAG_SUPPORTS_RTL
        val controller: ActivityController<Activity> = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val startIcon = RecordingDrawable(34, 50, Color.RED).apply {
                setBounds(0, 0, 34, 50)
            }
            val endIcon = RecordingDrawable(38, 50, Color.BLUE).apply {
                setBounds(0, 0, 38, 50)
            }
            val input = KitCodeInputEditText(controller.get()).apply {
                background = null
                minWidth = 0
                minHeight = 0
                setPadding(4, 0, 6, 0)
                filters = arrayOf(InputFilter.LengthFilter(2))
                itemWidth = 20f
                itemHeight = 20f
                itemSpacing = 4f
                setCompoundDrawablesRelative(startIcon, null, endIcon, null)
                setText("12")
            }
            val parent = FrameLayout(controller.get()).apply {
                layoutDirection = View.LAYOUT_DIRECTION_LTR
                addView(input, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ))
            }
            controller.get().setContentView(parent)
            controller.visible()
            assertTrue(input.isAttachedToWindow)
            measureAtMost(input)
            val expectedWidth: Int = input.compoundPaddingLeft + 20 * 2 + 4 + input.compoundPaddingRight

            assertEquals(View.LAYOUT_DIRECTION_LTR, input.layoutDirection)
            assertSame(startIcon, input.compoundDrawables[0])
            assertSame(endIcon, input.compoundDrawables[2])
            assertEquals(expectedWidth, input.measuredWidth)
            assertEquals(50, input.measuredHeight)
            val ltrBitmap: Bitmap = drawBitmap(input, input.measuredWidth, input.measuredHeight)
            assertEquals(34 * 50, countPixels(ltrBitmap, Color.RED))
            assertEquals(38 * 50, countPixels(ltrBitmap, Color.BLUE))
            assertTrue(startIcon.drawnBounds.last().left < endIcon.drawnBounds.last().left)

            parent.layoutDirection = View.LAYOUT_DIRECTION_RTL
            measureAtMost(input)
            val rtlBitmap: Bitmap = drawBitmap(input, input.measuredWidth, input.measuredHeight)

            assertEquals(View.LAYOUT_DIRECTION_RTL, input.layoutDirection)
            assertSame(endIcon, input.compoundDrawables[0])
            assertSame(startIcon, input.compoundDrawables[2])
            assertEquals(34 * 50, countPixels(rtlBitmap, Color.RED))
            assertEquals(38 * 50, countPixels(rtlBitmap, Color.BLUE))
            assertTrue(endIcon.drawnBounds.last().left < startIcon.drawnBounds.last().left)
            assertEquals(expectedWidth, input.measuredWidth)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun compoundDrawables_allFourSidesLeaveSpaceAroundSlotsAndKeepWideIconsUnclipped() {
        val leftIcon = RecordingDrawable(12, 16, Color.RED).apply { setBounds(0, 0, 12, 16) }
        val topIcon = RecordingDrawable(130, 18, Color.GREEN).apply { setBounds(0, 0, 130, 18) }
        val rightIcon = RecordingDrawable(14, 16, Color.BLUE).apply { setBounds(0, 0, 14, 16) }
        val bottomIcon = RecordingDrawable(100, 22, Color.YELLOW).apply { setBounds(0, 0, 100, 22) }
        val slotBackground = RecordingDrawable()
        val input: KitCodeInputEditText = createInput().apply {
            background = null
            minWidth = 0
            minHeight = 0
            setPadding(4, 6, 8, 10)
            compoundDrawablePadding = 3
            setCompoundDrawables(leftIcon, topIcon, rightIcon, bottomIcon)
            filters = arrayOf(InputFilter.LengthFilter(2))
            itemWidth = 20f
            itemHeight = 20f
            itemSpacing = 4f
            itemBackground = slotBackground
        }

        measureAtMost(input)
        val bitmap: Bitmap = drawBitmap(input, input.measuredWidth, input.measuredHeight)

        assertEquals(130 + input.compoundPaddingLeft + input.compoundPaddingRight, input.measuredWidth)
        assertEquals(input.compoundPaddingTop + 20 + input.compoundPaddingBottom, input.measuredHeight)
        assertEquals(12 * 16, countPixels(bitmap, Color.RED))
        assertEquals(130 * 18, countPixels(bitmap, Color.GREEN))
        assertEquals(14 * 16, countPixels(bitmap, Color.BLUE))
        assertEquals(100 * 22, countPixels(bitmap, Color.YELLOW))
        assertEquals(input.compoundPaddingLeft, slotBackground.drawnBounds.first().left)
        assertEquals(input.compoundPaddingTop, slotBackground.drawnBounds.first().top)
        assertTrue(slotBackground.drawnBounds.last().right <= input.width - input.compoundPaddingRight)
        assertTrue(slotBackground.drawnBounds.last().bottom <= input.height - input.compoundPaddingBottom)
    }

    @Test
    @Config(sdk = [24, 34])
    @GraphicsMode(GraphicsMode.Mode.LEGACY)
    fun accessibilityNode_reportsSmallestNativeLengthLimit() {
        val input: KitCodeInputEditText = createInput().apply {
            filters = arrayOf(InputFilter.LengthFilter(3), InputFilter.LengthFilter(5))
            setText("12345")
        }

        assertEquals(3, input.createAccessibilityNodeInfo().maxTextLength)
        assertEquals("123", input.text.toString())

        input.filters = emptyArray()

        assertEquals(6, input.createAccessibilityNodeInfo().maxTextLength)
    }

    @Test
    fun accessibilityExtraData_doesNotAdvertiseOrReturnContinuousCharacterGeometry() {
        val input: KitCodeInputEditText = createInput().apply {
            setText("123")
        }
        draw(input, width = 180, height = 40)
        val node: AccessibilityNodeInfo = input.createAccessibilityNodeInfo()
        val arguments = Bundle().apply {
            putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX, 0)
            putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH, 3)
        }

        input.addExtraDataToAccessibilityNodeInfo(
            node,
            AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY,
            arguments,
        )

        assertFalse(node.availableExtraData.contains(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY))
        assertFalse(node.extras.containsKey(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY))
        assertTrue(node.availableExtraData.contains(AccessibilityNodeInfo.EXTRA_DATA_RENDERING_INFO_KEY))
    }

    /**
     * 验证配置输入格并存在部分文本时可以完成绘制.
     */
    @Test
    fun draw_withConfiguredItemsDoesNotCrash() {
        val input: KitCodeInputEditText = createInput().apply {
            background = null
            setPadding(0, 0, 0, 0)
            filters = arrayOf(InputFilter.LengthFilter(2))
            itemBackground = ColorDrawable(Color.RED)
            itemWidth = 60f
            itemHeight = 40f
            itemSpacing = 10f
            hint = "-"
            setText("1")
        }

        draw(input, width = 130, height = 40)
    }

    @Test
    fun draw_passwordTransformationDoesNotRenderPlaintext() {
        val input: KitCodeInputEditText = createInput().apply {
            filters = arrayOf(InputFilter.LengthFilter(4))
            setText("1234")
            transformationMethod = PasswordTransformationMethod.getInstance()
        }

        val canvas: RecordingCanvas = draw(input, width = 160, height = 40)
        val drawnText: String = canvas.drawnText.joinToString("") { it.text }

        assertEquals("\u2022\u2022\u2022\u2022", drawnText)
        assertEquals("1234", input.text.toString())
    }

    @Test
    fun draw_surrogatePairIsDrawnTogetherAndOccupiesTwoNativeLengthUnits() {
        val input: KitCodeInputEditText = createInput().apply {
            background = null
            setPadding(0, 0, 0, 0)
            filters = arrayOf(InputFilter.LengthFilter(4))
            itemWidth = 20f
            itemHeight = 20f
            setText("A\uD835\uDFD9B")
        }

        val canvas: RecordingCanvas = draw(input, width = 80, height = 20)

        assertEquals(listOf("A", "\uD835\uDFD9", "B"), canvas.drawnText.map { it.text })
        assertEquals(70f, canvas.drawnText.last().x, 0.01f)
        assertEquals(4, input.text?.length)
    }

    @Test
    fun draw_usesNativePaintForEnteredTextAndHint() {
        val hintTypeface: Typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        val input: KitCodeInputEditText = createInput().apply {
            filters = arrayOf(InputFilter.LengthFilter(2))
            textScaleX = 2f
            paint.textSkewX = -0.25f
            setTextColor(Color.RED)
            setHintTextColor(Color.BLUE)
            this.hintTypeface = hintTypeface
            hint = "-"
            setText("1")
        }

        val canvas: RecordingCanvas = draw(input, width = 120, height = 40)

        assertEquals(listOf("1", "-"), canvas.drawnText.map { it.text })
        assertEquals(2f, canvas.drawnText[0].paint.textScaleX, 0.01f)
        assertEquals(-0.25f, canvas.drawnText[0].paint.textSkewX, 0.01f)
        assertEquals(Color.RED, canvas.drawnText[0].paint.color)
        assertEquals(2f, canvas.drawnText[1].paint.textScaleX, 0.01f)
        assertEquals(Color.BLUE, canvas.drawnText[1].paint.color)
        assertEquals(hintTypeface, canvas.drawnText[1].paint.typeface)
    }

    @Test
    fun measure_textStyleChangesRebuildNativeLayout() {
        val input: KitCodeInputEditText = createInput().apply {
            setText("123")
        }
        draw(input, width = 180, height = 60)

        input.setTextSize(TypedValue.COMPLEX_UNIT_PX, 32f)
        draw(input, width = 180, height = 60)

        assertNotNull(input.layout)
        assertEquals("123", input.layout.text.toString())
        assertEquals(32f, input.layout.paint.textSize, 0.01f)
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
            filters = arrayOf(InputFilter.LengthFilter(3))
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

    @Test
    fun measure_nativeMinimumDimensionsApplyToItemLayout() {
        val input: KitCodeInputEditText = createInput().apply {
            background = null
            setPadding(0, 0, 0, 0)
            filters = arrayOf(InputFilter.LengthFilter(1))
            itemWidth = 20f
            itemHeight = 20f
            minWidth = 180
            minHeight = 90
        }

        measureAtMost(input)

        assertEquals(180, input.measuredWidth)
        assertEquals(90, input.measuredHeight)
    }

    @Test
    fun measure_nativeMaximumDimensionsApplyToItemLayout() {
        val input: KitCodeInputEditText = createInput().apply {
            background = null
            setPadding(0, 0, 0, 0)
            filters = arrayOf(InputFilter.LengthFilter(2))
            itemWidth = 60f
            itemHeight = 40f
            minWidth = 0
            minHeight = 0
            maxWidth = 80
            maxHeight = 30
        }

        measureAtMost(input)

        assertEquals(80, input.measuredWidth)
        assertEquals(30, input.measuredHeight)
    }

    @Test
    fun draw_unspecifiedWidthCappedByMaximumRedistributesItemSpacing() {
        val background = RecordingDrawable()
        val input: KitCodeInputEditText = createInput().apply {
            this.background = null
            minWidth = 0
            minHeight = 0
            maxWidth = 80
            setPadding(0, 0, 0, 0)
            filters = arrayOf(InputFilter.LengthFilter(2))
            itemBackground = background
            itemWidth = 60f
            itemHeight = 20f
        }
        input.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(20, View.MeasureSpec.EXACTLY),
        )
        input.layout(0, 0, input.measuredWidth, input.measuredHeight)

        input.draw(Canvas(Bitmap.createBitmap(80, 20, Bitmap.Config.ARGB_8888)))

        assertEquals(80, input.measuredWidth)
        assertEquals(
            listOf(Rect(0, 0, 60, 20), Rect(20, 0, 80, 20)),
            background.drawnBounds,
        )
    }

    @Test
    fun measure_parentLimitsPreserveTooSmallState() {
        val input: KitCodeInputEditText = createInput().apply {
            background = null
            minWidth = 0
            minHeight = 0
            setPadding(0, 0, 0, 0)
            filters = arrayOf(InputFilter.LengthFilter(3))
            itemWidth = 40f
            itemHeight = 40f
        }

        input.measure(
            View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(30, View.MeasureSpec.AT_MOST),
        )

        assertEquals(100, input.measuredWidth)
        assertEquals(30, input.measuredHeight)
        assertEquals(
            View.MEASURED_STATE_TOO_SMALL,
            input.measuredWidthAndState and View.MEASURED_STATE_MASK,
        )
        assertEquals(
            View.MEASURED_STATE_TOO_SMALL,
            input.measuredHeightAndState and View.MEASURED_STATE_MASK,
        )
    }

    @Test
    fun measure_usesHintTypefaceAndNativeTextScale() {
        val input: KitCodeInputEditText = createInput().apply {
            background = null
            minWidth = 0
            minHeight = 0
            setPadding(0, 0, 0, 0)
            filters = arrayOf(InputFilter.LengthFilter(2))
            typeface = Typeface.MONOSPACE
            hintTypeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textScaleX = 2f
            hint = "WWW"
        }
        val hintPaint = Paint(input.paint).apply {
            typeface = input.hintTypeface
        }
        val expectedWidth: Int = ceil(max(input.textSize, hintPaint.measureText("WWW")) * 2).toInt()
        val textMetrics: Paint.FontMetrics = input.paint.fontMetrics
        val hintMetrics: Paint.FontMetrics = hintPaint.fontMetrics
        val expectedHeight: Int = ceil(
            max(textMetrics.descent - textMetrics.ascent, hintMetrics.descent - hintMetrics.ascent),
        ).toInt()

        measureAtMost(input)

        assertEquals(expectedWidth, input.measuredWidth)
        assertEquals(expectedHeight, input.measuredHeight)
    }

    @Test
    fun drawableStateChanged_intrinsicSizeChangeRequestsLayout() {
        val background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), RecordingDrawable(40, 30))
            addState(intArrayOf(), RecordingDrawable(20, 10))
        }
        val input: KitCodeInputEditText = createInput().apply {
            this.background = null
            minWidth = 0
            minHeight = 0
            setPadding(0, 0, 0, 0)
            filters = arrayOf(InputFilter.LengthFilter(2))
            itemBackground = background
        }
        measureAtMost(input)
        input.layout(0, 0, input.measuredWidth, input.measuredHeight)
        assertEquals(40, input.measuredWidth)
        assertEquals(10, input.measuredHeight)
        assertFalse(input.isLayoutRequested)

        input.isPressed = true

        assertTrue(input.isLayoutRequested)
        measureAtMost(input)
        assertEquals(80, input.measuredWidth)
        assertEquals(30, input.measuredHeight)
    }

    @Test
    fun draw_unchangedBackgroundDoesNotInvalidateItselfForEachItem() {
        val background = RecordingDrawable()
        val input: KitCodeInputEditText = createInput().apply {
            this.background = null
            setPadding(0, 0, 0, 0)
            itemBackground = background
            itemWidth = 20f
            itemHeight = 20f
        }
        draw(input, width = 120, height = 20)
        background.invalidationCount = 0
        background.drawnBounds.clear()

        draw(input, width = 120, height = 20)

        assertEquals(6, background.drawnBounds.size)
        assertEquals(0, background.invalidationCount)
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
            filters = arrayOf(InputFilter.LengthFilter(3))
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

    @Test
    fun draw_parentPreservesItemPositionsWhenNativeTextScrolls() {
        val background = RecordingDrawable()
        val input: KitCodeInputEditText = createInput().apply {
            this.background = null
            setPadding(0, 0, 0, 0)
            filters = arrayOf(InputFilter.LengthFilter(3))
            itemBackground = background
            itemWidth = 20f
            itemHeight = 20f
        }
        val parent = FrameLayout(input.context).apply {
            setPadding(7, 9, 0, 0)
            addView(input, FrameLayout.LayoutParams(60, 20))
        }
        parent.measure(
            View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(40, View.MeasureSpec.EXACTLY),
        )
        parent.layout(0, 0, 100, 40)
        input.scrollTo(13, 5)

        parent.draw(Canvas(Bitmap.createBitmap(100, 40, Bitmap.Config.ARGB_8888)))

        assertEquals(13, input.scrollX)
        assertEquals(5, input.scrollY)
        assertEquals(
            listOf(Rect(7, 9, 27, 29), Rect(27, 9, 47, 29), Rect(47, 9, 67, 29)),
            background.drawnBounds,
        )
    }

    @Test
    fun draw_rtlParentKeepsAsciiCodeInLeftToRightSlotOrder() {
        val applicationInfo: ApplicationInfo = RuntimeEnvironment.getApplication().applicationInfo
        applicationInfo.flags = applicationInfo.flags or ApplicationInfo.FLAG_SUPPORTS_RTL
        val input: KitCodeInputEditText = createInput().apply {
            background = null
            setPadding(0, 0, 0, 0)
            filters = arrayOf(InputFilter.LengthFilter(4))
            itemWidth = 20f
            itemHeight = 20f
            setText("A1b2")
        }
        val parent = FrameLayout(input.context).apply {
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            addView(input, FrameLayout.LayoutParams(80, 20))
        }
        parent.measure(
            View.MeasureSpec.makeMeasureSpec(80, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(20, View.MeasureSpec.EXACTLY),
        )
        parent.layout(0, 0, 80, 20)
        val canvas = RecordingCanvas(Bitmap.createBitmap(80, 20, Bitmap.Config.ARGB_8888))

        parent.draw(canvas)

        assertEquals(View.LAYOUT_DIRECTION_RTL, parent.layoutDirection)
        assertEquals(View.LAYOUT_DIRECTION_RTL, input.layoutDirection)
        assertEquals(listOf("A", "1", "b", "2"), canvas.drawnText.map { it.text })
        assertEquals(listOf(10f, 30f, 50f, 70f), canvas.drawnText.map { it.x })
        assertEquals("A1b2", input.text.toString())
    }

    @Test
    fun draw_rtlParentMapsRelativePaddingWithoutReversingSlots() {
        val applicationInfo: ApplicationInfo = RuntimeEnvironment.getApplication().applicationInfo
        applicationInfo.flags = applicationInfo.flags or ApplicationInfo.FLAG_SUPPORTS_RTL
        val background = RecordingDrawable()
        val input: KitCodeInputEditText = createInput().apply {
            this.background = null
            setPaddingRelative(11, 3, 23, 5)
            filters = arrayOf(InputFilter.LengthFilter(3))
            itemBackground = background
            itemWidth = 20f
            itemHeight = 20f
        }
        val parent = FrameLayout(input.context).apply {
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            addView(input, FrameLayout.LayoutParams(94, 28))
        }
        parent.measure(
            View.MeasureSpec.makeMeasureSpec(94, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(28, View.MeasureSpec.EXACTLY),
        )
        parent.layout(0, 0, 94, 28)

        parent.draw(Canvas(Bitmap.createBitmap(94, 28, Bitmap.Config.ARGB_8888)))

        assertEquals(View.LAYOUT_DIRECTION_RTL, input.layoutDirection)
        assertEquals(11, input.paddingStart)
        assertEquals(23, input.paddingEnd)
        assertEquals(23, input.paddingLeft)
        assertEquals(11, input.paddingRight)
        assertEquals(
            listOf(Rect(23, 3, 43, 23), Rect(43, 3, 63, 23), Rect(63, 3, 83, 23)),
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
            filters = arrayOf(InputFilter.LengthFilter(3))
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
        KitCodeInputEditText(RuntimeEnvironment.getApplication()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

    private fun measureAtMost(input: KitCodeInputEditText) {
        val widthSpec: Int = View.MeasureSpec.makeMeasureSpec(1_000, View.MeasureSpec.AT_MOST)
        val heightSpec: Int = View.MeasureSpec.makeMeasureSpec(1_000, View.MeasureSpec.AT_MOST)
        input.measure(widthSpec, heightSpec)
    }

    private fun draw(input: KitCodeInputEditText, width: Int, height: Int): RecordingCanvas {
        val widthSpec: Int = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
        val heightSpec: Int = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        input.measure(widthSpec, heightSpec)
        input.layout(0, 0, width, height)
        val bitmap: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = RecordingCanvas(bitmap)
        input.draw(canvas)
        return canvas
    }

    private fun drawBitmap(input: View, width: Int, height: Int): Bitmap {
        val widthSpec: Int = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
        val heightSpec: Int = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        input.measure(widthSpec, heightSpec)
        input.layout(0, 0, width, height)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap: Bitmap ->
            input.draw(Canvas(bitmap))
        }
    }

    private fun countPixels(bitmap: Bitmap, color: Int): Int {
        var count: Int = 0
        for (x: Int in 0 until bitmap.width) {
            for (y: Int in 0 until bitmap.height) {
                if (bitmap.getPixel(x, y) == color) {
                    count++
                }
            }
        }
        return count
    }

    private fun pixelBounds(bitmap: Bitmap, color: Int): Rect {
        val bounds = Rect()
        for (x: Int in 0 until bitmap.width) {
            for (y: Int in 0 until bitmap.height) {
                if (bitmap.getPixel(x, y) == color) {
                    bounds.union(x, y, x + 1, y + 1)
                }
            }
        }
        return bounds
    }

    /**
     * 保存提交给 Canvas 的文本和绘制样式, 不依赖控件内部字段.
     */
    private class RecordingCanvas(bitmap: Bitmap) : Canvas(bitmap) {

        val drawnText: MutableList<DrawnText> = mutableListOf()

        override fun drawText(text: CharSequence, start: Int, end: Int, x: Float, y: Float, paint: Paint) {
            if (!clipBounds.isEmpty) {
                drawnText += DrawnText(text.subSequence(start, end).toString(), x, Paint(paint))
            }
        }
    }

    /**
     * 单次文本绘制的不可变快照.
     */
    private data class DrawnText(val text: String, val x: Float, val paint: Paint)

    /**
     * 将 Drawable 局部边界映射到 Canvas 中的视觉位置.
     */
    @Suppress("DEPRECATION")
    private fun Canvas.mapBounds(bounds: Rect): Rect {
        val transform = Matrix()
        getMatrix(transform)
        val visualBounds = RectF(bounds)
        transform.mapRect(visualBounds)
        return Rect().apply { visualBounds.round(this) }
    }

    /**
     * 根据布局方向提供不同固有尺寸, 不主动向 View 请求重新布局.
     */
    private class DirectionalSizeDrawable : Drawable() {

        override fun onLayoutDirectionChanged(layoutDirection: Int): Boolean = true

        override fun getIntrinsicWidth(): Int = if (layoutDirection == View.LAYOUT_DIRECTION_RTL) 32 else 20

        override fun getIntrinsicHeight(): Int = if (layoutDirection == View.LAYOUT_DIRECTION_RTL) 36 else 24

        override fun draw(canvas: Canvas) = Unit

        override fun setAlpha(alpha: Int) = Unit

        override fun setColorFilter(colorFilter: ColorFilter?) = Unit

        @Deprecated("Deprecated in Android SDK")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    /**
     * 记录每次绘制边界的测试 Drawable.
     */
    private inner class RecordingDrawable(
        private val desiredWidth: Int = -1,
        private val desiredHeight: Int = -1,
        private val fillColor: Int = Color.TRANSPARENT,
    ) : Drawable() {

        val drawnBounds: MutableList<Rect> = mutableListOf()
        var invalidationCount: Int = 0
        val visibilityRequests: MutableList<Pair<Boolean, Boolean>> = mutableListOf()
        val hotspotRequests: MutableList<Pair<Float, Float>> = mutableListOf()

        override fun draw(canvas: Canvas) {
            drawnBounds += canvas.mapBounds(bounds)
            if (fillColor != Color.TRANSPARENT) {
                canvas.drawRect(bounds, Paint().apply { color = fillColor })
            }
        }

        override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
            visibilityRequests += visible to restart
            return super.setVisible(visible, restart)
        }

        override fun setHotspot(x: Float, y: Float) {
            hotspotRequests += x to y
            super.setHotspot(x, y)
        }

        override fun invalidateSelf() {
            invalidationCount++
            super.invalidateSelf()
        }

        override fun getIntrinsicWidth(): Int = desiredWidth

        override fun getIntrinsicHeight(): Int = desiredHeight

        override fun setAlpha(alpha: Int) = Unit

        override fun setColorFilter(colorFilter: ColorFilter?) = Unit

        @Deprecated("Deprecated in Android SDK")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
