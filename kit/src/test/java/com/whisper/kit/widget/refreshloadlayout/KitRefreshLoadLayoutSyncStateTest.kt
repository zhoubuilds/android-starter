package com.whisper.kit.widget.refreshloadlayout

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/**
 * 验证业务状态同步只更新显示, 并正确处理布局、旧手势和组件回调重入.
 *
 * @author whisper
 * @since 2026/09/11
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KitRefreshLoadLayoutSyncStateTest {

    @Test
    fun synchronizeBusyAction_revealsMeasuredIndicatorWithoutRequestingBusinessAction() {
        for (direction: Direction in Direction.entries) {
            val fixture: Fixture = createFixture()

            fixture.layout.setActiveAction(direction.action)

            assertEquals(direction.state, fixture.layout.state)
            assertEquals(direction.holdOffset, fixture.layout.currentOffset)
            assertEquals(direction.state, fixture.indicator(direction).states.last())
            assertEquals(direction.holdOffset, fixture.indicator(direction).offsets.last())
            assertEquals(0, fixture.indicator(direction.opposite).offsets.last())
            assertTrue(fixture.actions.isEmpty())
        }
    }

    @Test
    fun synchronizeDifferentActionThenNull_switchesDirectionAndFinishesWithoutBusinessCallback() {
        for (direction: Direction in Direction.entries) {
            val fixture: Fixture = createFixture()
            fixture.layout.setActiveAction(direction.action)

            fixture.layout.setActiveAction(direction.opposite.action)

            assertEquals(direction.opposite.state, fixture.layout.state)
            assertEquals(direction.opposite.holdOffset, fixture.layout.currentOffset)
            fixture.layout.setActiveAction(null)

            assertEquals(KitRefreshLoadLayout.RefreshLoadState.Idle, fixture.layout.state)
            assertEquals(0, fixture.layout.currentOffset)
            assertEquals(0f, fixture.content.translationY, 0f)
            assertTrue(fixture.actions.isEmpty())
        }
    }

    @Test
    fun switchingBusyDirection_resetsPreviousComponentOffset() {
        for (direction: Direction in Direction.entries) {
            val fixture: Fixture = createFixture()
            fixture.layout.setActiveAction(direction.action)
            val previousIndicator: RecordingIndicator = fixture.indicator(direction)
            assertEquals(direction.holdOffset, previousIndicator.offsets.last())
            previousIndicator.offsets.clear()

            fixture.layout.setActiveAction(direction.opposite.action, animate = false)

            assertEquals(direction.opposite.state, fixture.layout.state)
            assertEquals(direction.opposite.holdOffset, fixture.layout.currentOffset)
            assertTrue("The previous indicator must receive an offset update", previousIndicator.offsets.isNotEmpty())
            assertEquals(0, previousIndicator.offsets.last())
            assertEquals(direction.opposite.holdOffset, fixture.indicator(direction.opposite).offsets.last())
            assertTrue(fixture.actions.isEmpty())
        }
    }

    @Test
    fun finishingOrDisablingPreviousDirectionBeforeAnimationCrossesZero_preservesNewBusyAction() {
        for (direction: Direction in Direction.entries) {
            for (disablePreviousDirection: Boolean in listOf(false, true)) {
                withAttachedFixture { fixture: Fixture ->
                    fixture.layout.setActiveAction(direction.action)
                    fixture.layout.animationDurationMillis = ANIMATION_DURATION
                    fixture.layout.setActiveAction(direction.opposite.action, animate = true)
                    val switchingAnimator: ValueAnimator = activeAnimator(fixture.layout)
                    switchingAnimator.currentPlayTime = 16L
                    assertTrue(
                        "The switching animation must still be on the previous indicator side",
                        fixture.layout.currentOffset * direction.holdOffset > 0,
                    )
                    val offsetBeforeOldActionFinished: Int = fixture.layout.currentOffset

                    if (disablePreviousDirection) {
                        disableDirection(fixture.layout, direction)
                    } else {
                        finish(fixture.layout, direction)
                    }

                    assertEquals(direction.opposite.state, fixture.layout.state)
                    assertEquals(offsetBeforeOldActionFinished, fixture.layout.currentOffset)
                    assertSame(switchingAnimator, activeAnimator(fixture.layout))
                    switchingAnimator.end()
                    assertEquals(direction.opposite.state, fixture.layout.state)
                    assertEquals(direction.opposite.holdOffset, fixture.layout.currentOffset)
                    assertEquals(0, fixture.indicator(direction).offsets.last())
                    assertTrue(fixture.actions.isEmpty())
                }
            }
        }
    }

    @Test
    fun repeatedBusySynchronization_preservesIndicatorPositionAfterUserScroll() {
        for (direction: Direction in Direction.entries) {
            val fixture: Fixture = createFixture()
            fixture.layout.setActiveAction(direction.action)
            startNestedTouch(fixture)
            fixture.layout.onNestedPreScroll(
                fixture.content, 0, direction.holdOffset, IntArray(2), ViewCompat.TYPE_TOUCH,
            )
            assertEquals(0, fixture.layout.currentOffset)
            val stateCount: Int = fixture.header.states.size
            val offsetCount: Int = fixture.header.offsets.size

            fixture.layout.setActiveAction(direction.action, animate = true)

            assertEquals(direction.state, fixture.layout.state)
            assertEquals(0, fixture.layout.currentOffset)
            assertEquals(stateCount, fixture.header.states.size)
            assertEquals(offsetCount, fixture.header.offsets.size)
            assertTrue(fixture.actions.isEmpty())
        }
    }

    @Test
    fun repeatedSynchronization_preservesRunningRevealAndFinishingAnimations() {
        for (direction: Direction in Direction.entries) {
            withAttachedFixture { fixture: Fixture ->
                fixture.layout.animationDurationMillis = ANIMATION_DURATION
                fixture.layout.setActiveAction(direction.action, animate = true)
                val revealAnimator: ValueAnimator = activeAnimator(fixture.layout)
                revealAnimator.currentPlayTime = 48L
                val revealedOffset: Int = fixture.layout.currentOffset
                assertTrue(kotlin.math.abs(revealedOffset) in 1 until kotlin.math.abs(direction.holdOffset))

                fixture.layout.setActiveAction(direction.action, animate = false)

                assertEquals(revealedOffset, fixture.layout.currentOffset)
                assertSame(revealAnimator, activeAnimator(fixture.layout))
                revealAnimator.end()
                assertEquals(direction.holdOffset, fixture.layout.currentOffset)
                fixture.layout.setActiveAction(null, animate = true)
                val finishingAnimator: ValueAnimator = activeAnimator(fixture.layout)
                finishingAnimator.currentPlayTime = 48L
                val finishingOffset: Int = fixture.layout.currentOffset

                fixture.layout.setActiveAction(null, animate = false)

                assertEquals(KitRefreshLoadLayout.RefreshLoadState.Settling, fixture.layout.state)
                assertEquals(finishingOffset, fixture.layout.currentOffset)
                assertSame(finishingAnimator, activeAnimator(fixture.layout))
                finishingAnimator.end()
                assertEquals(KitRefreshLoadLayout.RefreshLoadState.Idle, fixture.layout.state)
                assertEquals(0, fixture.layout.currentOffset)
                assertTrue(fixture.actions.isEmpty())
            }
        }
    }

    @Test
    fun synchronizationBeforeFirstLayout_usesMeasuredIndicatorHeight() {
        for (direction: Direction in Direction.entries) {
            val fixture: Fixture = createFixture(layOut = false)

            fixture.layout.setActiveAction(direction.action)

            assertEquals(0, fixture.layout.currentOffset)
            assertEquals(0, fixture.indicator(direction).measuredHeight)
            measureAndLayout(fixture.layout)
            assertEquals(direction.state, fixture.layout.state)
            assertEquals(direction.holdOffset, fixture.layout.currentOffset)
            assertEquals(direction.state, fixture.indicator(direction).states.last())
            assertEquals(direction.holdOffset, fixture.indicator(direction).offsets.last())
            assertTrue(fixture.actions.isEmpty())
        }
    }

    @Test
    fun synchronizationBeforeFirstLayout_stateCallbackChangesHeight_revealsUpdatedSize() {
        for (direction: Direction in Direction.entries) {
            withWindowFixture(
                beforeFirstLayout = { fixture: Fixture ->
                    resizeOnBusyState(fixture.indicator(direction), direction, RESIZED_HEIGHT)
                    fixture.layout.setActiveAction(direction.action)
                },
            ) { fixture: Fixture ->
                val indicator: RecordingIndicator = fixture.indicator(direction)
                assertEquals(RESIZED_HEIGHT, indicator.measuredHeight)
                assertEquals(direction.state, fixture.layout.state)
                assertEquals(1, indicator.states.count { state: KitRefreshLoadLayout.RefreshLoadState -> state == direction.state })
                assertEquals(direction.signedOffset(RESIZED_HEIGHT), fixture.layout.currentOffset)
                assertEquals(direction.signedOffset(RESIZED_HEIGHT), indicator.offsets.last())
                assertEquals(RESIZED_HEIGHT, indicator.offsetSnapshots.last().holdDistance)
                assertTrue(fixture.actions.isEmpty())
            }
        }
    }

    @Test
    fun synchronizationAfterFirstLayout_stateCallbackChangesHeight_revealsUpdatedSize() {
        for (direction: Direction in Direction.entries) {
            withWindowFixture { fixture: Fixture ->
                val indicator: RecordingIndicator = fixture.indicator(direction)
                resizeOnBusyState(indicator, direction, RESIZED_HEIGHT)

                fixture.layout.setActiveAction(direction.action)
                advanceWindowLayouts()

                assertEquals(RESIZED_HEIGHT, indicator.measuredHeight)
                assertEquals(direction.state, fixture.layout.state)
                assertEquals(1, indicator.states.count { state: KitRefreshLoadLayout.RefreshLoadState -> state == direction.state })
                assertEquals(direction.signedOffset(RESIZED_HEIGHT), fixture.layout.currentOffset)
                assertEquals(direction.signedOffset(RESIZED_HEIGHT), indicator.offsets.last())
                assertEquals(RESIZED_HEIGHT, indicator.offsetSnapshots.last().holdDistance)
                assertTrue(fixture.actions.isEmpty())
            }
        }
    }

    @Test
    fun firstOffsetSnapshotRequestsAnotherLayout_waitsForFinalSizeWithoutRepeatingState() {
        for (direction: Direction in Direction.entries) {
            withWindowFixture(
                beforeFirstLayout = { fixture: Fixture ->
                    val indicator: RecordingIndicator = fixture.indicator(direction)
                    resizeOnBusyState(indicator, direction, 90)
                    indicator.onOffset = { _: KitRefreshLoadLayout, _: Int ->
                        if (indicator.layoutParams.height != RESIZED_HEIGHT) {
                            indicator.layoutParams = indicator.layoutParams.apply { height = RESIZED_HEIGHT }
                        }
                    }
                    fixture.layout.setActiveAction(direction.action)
                },
            ) { fixture: Fixture ->
                val indicator: RecordingIndicator = fixture.indicator(direction)
                assertEquals(RESIZED_HEIGHT, indicator.measuredHeight)
                assertEquals(direction.state, fixture.layout.state)
                assertEquals(1, indicator.states.count { state: KitRefreshLoadLayout.RefreshLoadState -> state == direction.state })
                assertEquals(direction.signedOffset(RESIZED_HEIGHT), fixture.layout.currentOffset)
                assertEquals(direction.signedOffset(RESIZED_HEIGHT), indicator.offsets.last())
                assertEquals(RESIZED_HEIGHT, indicator.offsetSnapshots.last().holdDistance)
                assertFalse(indicator.isLayoutRequested)
                assertTrue(fixture.actions.isEmpty())
            }
        }
    }

    @Test
    fun firstStateSnapshotFinishesOrDisablesAction_doesNotRevealItOnLaterLayout() {
        for (direction: Direction in Direction.entries) {
            for (disableAction: Boolean in listOf(false, true)) {
                withWindowFixture(
                    beforeFirstLayout = { fixture: Fixture ->
                        val indicator: RecordingIndicator = fixture.indicator(direction)
                        indicator.onState = { layout: KitRefreshLoadLayout, state: KitRefreshLoadLayout.RefreshLoadState ->
                            if (state == direction.state) {
                                indicator.layoutParams = indicator.layoutParams.apply { height = RESIZED_HEIGHT }
                                if (disableAction) {
                                    disableDirection(layout, direction)
                                } else {
                                    finish(layout, direction)
                                }
                            }
                        }
                        fixture.layout.setActiveAction(direction.action)
                    },
                ) { fixture: Fixture ->
                    val indicator: RecordingIndicator = fixture.indicator(direction)
                    assertEquals(RESIZED_HEIGHT, indicator.measuredHeight)
                    assertEquals(KitRefreshLoadLayout.RefreshLoadState.Idle, fixture.layout.state)
                    assertEquals(0, fixture.layout.currentOffset)
                    assertEquals(0, indicator.offsets.last())
                    assertTrue(fixture.actions.isEmpty())

                    indicator.onState = null
                    fixture.layout.setActiveAction(direction.opposite.action)
                    advanceWindowLayouts()
                    assertEquals(direction.opposite.state, fixture.layout.state)
                    assertEquals(direction.opposite.holdOffset, fixture.layout.currentOffset)
                    assertTrue(fixture.actions.isEmpty())
                }
            }
        }
    }

    @Test
    fun componentReplacedFromDeferredOffsetCallback_receivesMeasuredAndLaidOutSnapshot() {
        for (direction: Direction in Direction.entries) {
            withWindowFixture { fixture: Fixture ->
                val previousComponent: RecordingIndicator = fixture.indicator(direction.opposite)
                val replacement = RecordingIndicator(fixture.layout.context)
                fixture.indicator(direction).onOffset = { layout: KitRefreshLoadLayout, offset: Int ->
                    if (offset == direction.holdOffset && previousComponent.parent === layout) {
                        layout.removeView(previousComponent)
                        layout.addView(replacement, roleParams(direction.opposite.role, REPLACEMENT_HEIGHT))
                    }
                }

                fixture.layout.requestLayout()
                fixture.layout.setActiveAction(direction.action)
                advanceWindowLayouts()

                assertEquals(REPLACEMENT_HEIGHT, replacement.measuredHeight)
                assertEquals(direction.state, fixture.layout.state)
                assertEquals(direction.state, replacement.states.last())
                assertTrue("The replacement must receive an initial offset snapshot", replacement.offsetSnapshots.isNotEmpty())
                for (snapshot: OffsetSnapshot in replacement.offsetSnapshots) {
                    assertEquals(0, snapshot.offset)
                    assertEquals(REPLACEMENT_HEIGHT, snapshot.measuredHeight)
                    assertEquals(REPLACEMENT_HEIGHT, snapshot.holdDistance)
                    assertTrue("The initial snapshot must follow layout", snapshot.laidOut)
                    assertFalse("The initial snapshot must use current layout dimensions", snapshot.layoutRequested)
                }
                assertTrue(fixture.actions.isEmpty())
            }
        }
    }

    @Test
    fun missingIndicator_keepsSynchronizedIntentUntilComponentIsAddedAndMeasured() {
        for (direction: Direction in Direction.entries) {
            val fixture: Fixture = createFixture()
            fixture.layout.removeView(fixture.indicator(direction))
            measureAndLayout(fixture.layout)

            fixture.layout.setActiveAction(direction.action)

            assertEquals(0, fixture.layout.currentOffset)
            val replacement = RecordingIndicator(fixture.layout.context)
            fixture.layout.addView(replacement, roleParams(direction.role, kotlin.math.abs(direction.holdOffset)))
            measureAndLayout(fixture.layout)
            assertEquals(direction.state, fixture.layout.state)
            assertEquals(direction.holdOffset, fixture.layout.currentOffset)
            assertEquals(direction.state, replacement.states.last())
            assertEquals(direction.holdOffset, replacement.offsets.last())
            assertTrue(fixture.actions.isEmpty())
        }
    }

    @Test
    fun clearingBeforeFirstLayout_discardsPendingReveal() {
        for (direction: Direction in Direction.entries) {
            val fixture: Fixture = createFixture(layOut = false)
            fixture.layout.setActiveAction(direction.action)

            fixture.layout.setActiveAction(null)
            measureAndLayout(fixture.layout)

            assertEquals(KitRefreshLoadLayout.RefreshLoadState.Idle, fixture.layout.state)
            assertEquals(0, fixture.layout.currentOffset)
            assertEquals(KitRefreshLoadLayout.RefreshLoadState.Idle, fixture.indicator(direction).states.last())
            assertTrue(fixture.indicator(direction).offsets.all { offset: Int -> offset == 0 })
            assertTrue(fixture.actions.isEmpty())
        }
    }

    @Test
    fun synchronizingDuringNestedPull_invalidatesOldStopButAllowsFreshGesture() {
        for (direction: Direction in Direction.entries) {
            val fixture: Fixture = createFixture()
            pull(fixture, direction)
            assertTrue(kotlin.math.abs(fixture.layout.currentOffset) >= TRIGGER)

            fixture.layout.setActiveAction(direction.opposite.action)
            fixture.layout.setActiveAction(null)
            fixture.layout.onNestedScroll(
                fixture.content, 0, 0, 0, direction.scrollDistance, ViewCompat.TYPE_TOUCH, IntArray(2),
            )
            fixture.layout.onStopNestedScroll(fixture.content, ViewCompat.TYPE_TOUCH)

            assertEquals(KitRefreshLoadLayout.RefreshLoadState.Idle, fixture.layout.state)
            assertEquals(0, fixture.layout.currentOffset)
            assertTrue(fixture.actions.isEmpty())
            pull(fixture, direction)
            fixture.layout.onStopNestedScroll(fixture.content, ViewCompat.TYPE_TOUCH)
            assertEquals(direction.state, fixture.layout.state)
            assertEquals(listOf(direction.action), fixture.actions)
        }
    }

    @Test
    fun clearingDuringDirectPull_invalidatesRemainingMoveAndRelease() {
        for (direction: Direction in Direction.entries) {
            val fixture: Fixture = createFixture()
            val downY: Float = LAYOUT_HEIGHT / 2f
            val moveY: Float = downY - direction.scrollDistance
            dispatchTouch(fixture.layout, MotionEvent.ACTION_DOWN, downY, 0L)
            dispatchTouch(fixture.layout, MotionEvent.ACTION_MOVE, moveY, 16L)
            assertTrue(kotlin.math.abs(fixture.layout.currentOffset) >= TRIGGER)

            fixture.layout.setActiveAction(null)
            dispatchTouch(fixture.layout, MotionEvent.ACTION_MOVE, moveY - direction.scrollDistance, 32L)
            dispatchTouch(fixture.layout, MotionEvent.ACTION_UP, moveY - direction.scrollDistance, 48L)

            assertEquals(KitRefreshLoadLayout.RefreshLoadState.Idle, fixture.layout.state)
            assertEquals(0, fixture.layout.currentOffset)
            assertTrue(fixture.actions.isEmpty())
        }
    }

    @Test
    fun synchronousFinishFromStateCallback_preventsRevealAndStaleComponentNotification() {
        for (direction: Direction in Direction.entries) {
            val fixture: Fixture = createFixture()
            fixture.header.onState = { layout: KitRefreshLoadLayout, state: KitRefreshLoadLayout.RefreshLoadState ->
                if (state == direction.state) {
                    finish(layout, direction)
                }
            }
            fixture.footer.states.clear()

            fixture.layout.setActiveAction(direction.action)

            assertEquals(KitRefreshLoadLayout.RefreshLoadState.Idle, fixture.layout.state)
            assertEquals(0, fixture.layout.currentOffset)
            assertFalse(direction.state in fixture.footer.states)
            assertTrue(fixture.actions.isEmpty())
        }
    }

    @Test
    fun synchronousFinishFromOffsetCallback_doesNotRestoreObsoleteBusyState() {
        for (direction: Direction in Direction.entries) {
            val fixture: Fixture = createFixture()
            fixture.indicator(direction).onOffset = { layout: KitRefreshLoadLayout, offset: Int ->
                if (offset == direction.holdOffset && layout.state == direction.state) {
                    finish(layout, direction)
                }
            }

            fixture.layout.setActiveAction(direction.action)

            assertEquals(KitRefreshLoadLayout.RefreshLoadState.Idle, fixture.layout.state)
            assertEquals(0, fixture.layout.currentOffset)
            assertEquals(0, fixture.indicator(direction).offsets.last())
            assertTrue(fixture.actions.isEmpty())
        }
    }

    @Test
    fun synchronousDirectionDisable_keepsExistingFinishSemantics() {
        for (direction: Direction in Direction.entries) {
            val fixture: Fixture = createFixture()
            fixture.header.onState = { layout: KitRefreshLoadLayout, state: KitRefreshLoadLayout.RefreshLoadState ->
                if (state == direction.state) {
                    disableDirection(layout, direction)
                }
            }
            fixture.footer.states.clear()

            fixture.layout.setActiveAction(direction.action)

            assertEquals(KitRefreshLoadLayout.RefreshLoadState.Idle, fixture.layout.state)
            assertEquals(0, fixture.layout.currentOffset)
            assertFalse(direction.state in fixture.footer.states)
            assertTrue(fixture.actions.isEmpty())
        }
    }

    @Test
    fun synchronousGlobalDisable_preservesSynchronizedBusinessState() {
        for (direction: Direction in Direction.entries) {
            val fixture: Fixture = createFixture()
            fixture.header.onState = { layout: KitRefreshLoadLayout, state: KitRefreshLoadLayout.RefreshLoadState ->
                if (state == direction.state) {
                    layout.isEnabled = false
                }
            }

            fixture.layout.setActiveAction(direction.action)

            assertFalse(fixture.layout.isEnabled)
            assertEquals(direction.state, fixture.layout.state)
            assertEquals(direction.holdOffset, fixture.layout.currentOffset)
            assertTrue(fixture.actions.isEmpty())
        }
    }

    @Test
    fun disabledUserInteractions_doNotPreventSynchronizingAndClearingBusinessState() {
        for (direction: Direction in Direction.entries) {
            val fixture: Fixture = createFixture()
            fixture.layout.isEnabled = false
            disableDirection(fixture.layout, direction)

            fixture.layout.setActiveAction(direction.action)

            assertEquals(direction.state, fixture.layout.state)
            assertEquals(direction.holdOffset, fixture.layout.currentOffset)
            fixture.layout.setActiveAction(null)
            assertEquals(KitRefreshLoadLayout.RefreshLoadState.Idle, fixture.layout.state)
            assertEquals(0, fixture.layout.currentOffset)
            assertTrue(fixture.actions.isEmpty())
        }
    }

    @Test
    fun replacingSynchronizedActiveComponent_receivesCurrentStateAndOffset() {
        for (direction: Direction in Direction.entries) {
            val fixture: Fixture = createFixture()
            fixture.layout.setActiveAction(direction.action)
            val replacement = RecordingIndicator(fixture.layout.context)

            fixture.layout.removeView(fixture.indicator(direction))
            fixture.layout.addView(replacement, roleParams(direction.role, 95))
            measureAndLayout(fixture.layout)

            assertEquals(direction.state, fixture.layout.state)
            assertEquals(direction.state, replacement.states.last())
            assertEquals(fixture.layout.currentOffset, replacement.offsets.last())
            assertEquals(fixture.layout.currentOffset.toFloat(), replacement.translationY, 0f)
            assertTrue(fixture.actions.isEmpty())
        }
    }

    @Test
    fun synchronousActionReplacement_preservesNewestActionAndDropsObsoleteNotification() {
        for (direction: Direction in Direction.entries) {
            val fixture: Fixture = createFixture()
            fixture.header.onState = { layout: KitRefreshLoadLayout, state: KitRefreshLoadLayout.RefreshLoadState ->
                if (state == direction.state) {
                    layout.setActiveAction(direction.opposite.action)
                }
            }
            fixture.footer.states.clear()

            fixture.layout.setActiveAction(direction.action)

            assertEquals(direction.opposite.state, fixture.layout.state)
            assertEquals(direction.opposite.holdOffset, fixture.layout.currentOffset)
            assertFalse(direction.state in fixture.footer.states)
            assertEquals(direction.opposite.state, fixture.footer.states.last())
            assertTrue(fixture.actions.isEmpty())
        }
    }

    private fun createFixture(context: Context = RuntimeEnvironment.getApplication(), layOut: Boolean = true): Fixture {
        val layout = KitRefreshLoadLayout(context).apply {
            animationDurationMillis = 0L
            dragResistance = 1f
            refreshTriggerDistance = TRIGGER
            loadMoreTriggerDistance = TRIGGER
            maxDragDistance = 200
        }
        val header = RecordingIndicator(context)
        val footer = RecordingIndicator(context)
        val content = View(context)
        val actions: MutableList<KitRefreshLoadLayout.RefreshLoadAction> = mutableListOf()
        layout.addView(header, roleParams(KitRefreshLoadLayout.ChildRole.Header, HEADER_HEIGHT))
        layout.addView(content)
        layout.addView(footer, roleParams(KitRefreshLoadLayout.ChildRole.Footer, FOOTER_HEIGHT))
        layout.setOnRefreshLoadListener(actions::add)
        if (layOut) {
            measureAndLayout(layout)
        }
        return Fixture(layout, header, content, footer, actions)
    }

    private fun resizeOnBusyState(indicator: RecordingIndicator, direction: Direction, height: Int) {
        indicator.onState = { _: KitRefreshLoadLayout, state: KitRefreshLoadLayout.RefreshLoadState ->
            if (state == direction.state && indicator.layoutParams.height != height) {
                indicator.layoutParams = indicator.layoutParams.apply { this.height = height }
            }
        }
    }

    private fun withWindowFixture(beforeFirstLayout: (Fixture) -> Unit = {}, block: (Fixture) -> Unit) {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val fixture: Fixture = createFixture(controller.get(), layOut = false)
            beforeFirstLayout(fixture)
            controller.get().setContentView(fixture.layout, ViewGroup.LayoutParams(LAYOUT_WIDTH, LAYOUT_HEIGHT))
            controller.visible()
            advanceWindowLayouts()
            assertTrue("Layout callbacks require an attached view", fixture.layout.isAttachedToWindow)
            assertTrue("The window must complete the initial layout", fixture.layout.isLaidOut)
            block(fixture)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun advanceWindowLayouts() {
        // 由 ViewRoot 处理布局回调内的 requestLayout, 不以手工 measure 绕过真实窗口调度.
        shadowOf(Looper.getMainLooper()).idleFor(100L, TimeUnit.MILLISECONDS)
    }

    private fun withAttachedFixture(block: (Fixture) -> Unit) {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val fixture: Fixture = createFixture(controller.get())
            val parent = FrameLayout(controller.get())
            controller.get().setContentView(parent)
            parent.addView(fixture.layout)
            measureAndLayout(parent)
            measureAndLayout(fixture.layout)
            assertTrue("Animation tests require an attached view", fixture.layout.isAttachedToWindow)
            block(fixture)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun startNestedTouch(fixture: Fixture) {
        assertTrue(fixture.layout.onStartNestedScroll(fixture.content, fixture.content, ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH))
        fixture.layout.onNestedScrollAccepted(fixture.content, fixture.content, ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH)
    }

    private fun pull(fixture: Fixture, direction: Direction) {
        startNestedTouch(fixture)
        fixture.layout.onNestedScroll(fixture.content, 0, 0, 0, direction.scrollDistance, ViewCompat.TYPE_TOUCH, IntArray(2))
    }

    private fun finish(layout: KitRefreshLoadLayout, direction: Direction) {
        when (direction) {
            Direction.Refresh -> layout.finishRefresh(animate = false)
            Direction.LoadMore -> layout.finishLoadMore(animate = false)
        }
    }

    private fun disableDirection(layout: KitRefreshLoadLayout, direction: Direction) {
        when (direction) {
            Direction.Refresh -> layout.refreshEnabled = false
            Direction.LoadMore -> layout.loadMoreEnabled = false
        }
    }

    private fun dispatchTouch(layout: KitRefreshLoadLayout, action: Int, y: Float, eventTime: Long) {
        val event: MotionEvent = MotionEvent.obtain(0L, eventTime, action, LAYOUT_WIDTH / 2f, y, 0)
        try {
            layout.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun activeAnimator(layout: KitRefreshLoadLayout): ValueAnimator {
        // 只读取动画身份和推进公开播放进度, 验证重复同步不会取消或重建动画.
        val field: java.lang.reflect.Field = KitRefreshLoadLayout::class.java.getDeclaredField("activeAnimator")
        field.isAccessible = true
        val animator: ValueAnimator? = field.get(layout) as? ValueAnimator
        assertNotNull("Expected an active offset animation", animator)
        return requireNotNull(animator)
    }

    private fun roleParams(role: KitRefreshLoadLayout.ChildRole, height: Int): KitRefreshLoadLayout.LayoutParams =
        KitRefreshLoadLayout.LayoutParams(80, height).apply {
            this.role = role
        }

    private fun measureAndLayout(view: View) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(LAYOUT_WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(LAYOUT_HEIGHT, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, LAYOUT_WIDTH, LAYOUT_HEIGHT)
    }

    /** 同时覆盖业务同步的两个数值方向. */
    private enum class Direction(
        val action: KitRefreshLoadLayout.RefreshLoadAction,
        val state: KitRefreshLoadLayout.RefreshLoadState,
        val role: KitRefreshLoadLayout.ChildRole,
        val holdOffset: Int,
        val scrollDistance: Int,
    ) {
        Refresh(
            KitRefreshLoadLayout.RefreshLoadAction.Refresh,
            KitRefreshLoadLayout.RefreshLoadState.Refreshing,
            KitRefreshLoadLayout.ChildRole.Header,
            HEADER_HEIGHT,
            -100,
        ),
        LoadMore(
            KitRefreshLoadLayout.RefreshLoadAction.LoadMore,
            KitRefreshLoadLayout.RefreshLoadState.LoadingMore,
            KitRefreshLoadLayout.ChildRole.Footer,
            -FOOTER_HEIGHT,
            100,
        );

        val opposite: Direction
            get() = when (this) {
                Refresh -> LoadMore
                LoadMore -> Refresh
            }

        fun signedOffset(distance: Int): Int = when (this) {
            Refresh -> distance
            LoadMore -> -distance
        }
    }

    /** 容器、组件及业务动作记录组成的测试页面. */
    private data class Fixture(
        val layout: KitRefreshLoadLayout,
        val header: RecordingIndicator,
        val content: View,
        val footer: RecordingIndicator,
        val actions: MutableList<KitRefreshLoadLayout.RefreshLoadAction>,
    ) {
        fun indicator(direction: Direction): RecordingIndicator = when (direction) {
            Direction.Refresh -> header
            Direction.LoadMore -> footer
        }
    }

    /** 位移通知时组件实际可用的测量和布局状态. */
    private data class OffsetSnapshot(
        val offset: Int,
        val holdDistance: Int,
        val measuredHeight: Int,
        val laidOut: Boolean,
        val layoutRequested: Boolean,
    )

    /** 记录状态和位移, 并允许同步调用公开 API 重入容器. */
    private class RecordingIndicator(context: Context) : View(context), KitRefreshLoadComponent {
        val states: MutableList<KitRefreshLoadLayout.RefreshLoadState> = mutableListOf()
        val offsets: MutableList<Int> = mutableListOf()
        val offsetSnapshots: MutableList<OffsetSnapshot> = mutableListOf()
        var onState: ((KitRefreshLoadLayout, KitRefreshLoadLayout.RefreshLoadState) -> Unit)? = null
        var onOffset: ((KitRefreshLoadLayout, Int) -> Unit)? = null

        override fun onRefreshLoadStateChanged(layout: KitRefreshLoadLayout, state: KitRefreshLoadLayout.RefreshLoadState) {
            states += state
            onState?.invoke(layout, state)
        }

        override fun onRefreshLoadOffsetChanged(layout: KitRefreshLoadLayout, offset: Int, triggerDistance: Int, holdDistance: Int) {
            offsets += offset
            offsetSnapshots += OffsetSnapshot(offset, holdDistance, measuredHeight, isLaidOut, isLayoutRequested)
            onOffset?.invoke(layout, offset)
        }
    }

    private companion object {
        const val LAYOUT_WIDTH: Int = 300
        const val LAYOUT_HEIGHT: Int = 500
        const val HEADER_HEIGHT: Int = 60
        const val FOOTER_HEIGHT: Int = 70
        const val RESIZED_HEIGHT: Int = 120
        const val REPLACEMENT_HEIGHT: Int = 95
        const val TRIGGER: Int = 20
        const val ANIMATION_DURATION: Long = 240L
    }
}
