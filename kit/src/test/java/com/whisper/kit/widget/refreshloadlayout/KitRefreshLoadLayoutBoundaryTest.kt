package com.whisper.kit.widget.refreshloadlayout

import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowSystemClock
import java.util.concurrent.TimeUnit

/**
 * 验证统一滚动边界判定在拖拽、自动加载和忙碌指示器交接中的一致性.
 *
 * @author whisper
 * @since 2026/09/11
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
@LooperMode(LooperMode.Mode.PAUSED)
class KitRefreshLoadLayoutBoundaryTest {

    @Test
    fun callback_blocksDirectDragAtEitherBoundary() {
        for (direction: Int in intArrayOf(-1, 1)) {
            val fixture: Fixture = createFixture()
            fixture.layout.setScrollBoundaryCallback { _, _, requestedDirection ->
                requestedDirection == direction
            }

            directDrag(fixture.layout, direction)

            assertEquals(0, fixture.layout.currentOffset)
            assertTrue(fixture.actions.isEmpty())
        }
    }

    @Test
    fun callback_overridesDirectContentAndReceivesAndroidDirection() {
        for (direction: Int in intArrayOf(-1, 1)) {
            val fixture: Fixture = createFixture(canScroll = true)
            val directions: MutableList<Int> = mutableListOf()
            fixture.layout.setScrollBoundaryCallback { layout, target, requestedDirection ->
                assertSame(fixture.layout, layout)
                assertSame(fixture.content, target)
                directions += requestedDirection
                false
            }

            directDrag(fixture.layout, direction)

            assertEquals(listOf(actionFor(direction)), fixture.actions)
            assertTrue(directions.contains(direction))
            assertTrue(directions.all { it == -1 || it == 1 })
        }
    }

    @Test
    fun callback_blocksNestedDragAtEitherBoundary() {
        for (direction: Int in intArrayOf(-1, 1)) {
            val fixture: Fixture = createFixture()
            fixture.layout.setScrollBoundaryCallback { _, _, requestedDirection ->
                requestedDirection == direction
            }
            startNestedScroll(fixture)

            val consumed: IntArray = nestedScroll(fixture, direction * 80)
            fixture.layout.onStopNestedScroll(fixture.content, ViewCompat.TYPE_TOUCH)

            assertEquals(0, consumed[1])
            assertEquals(0, fixture.layout.currentOffset)
            assertTrue(fixture.actions.isEmpty())
        }
    }

    @Test
    fun callback_receivesNestedTargetInsteadOfDirectContent() {
        for (direction: Int in intArrayOf(-1, 1)) {
            val fixture: Fixture = createFixture(canScroll = true)
            val nestedTarget: View = View(RuntimeEnvironment.getApplication())
            fixture.content.addView(nestedTarget)
            fixture.layout.setScrollBoundaryCallback { layout, target, requestedDirection ->
                assertSame(fixture.layout, layout)
                assertSame(nestedTarget, target)
                assertTrue(requestedDirection == -1 || requestedDirection == 1)
                false
            }
            startNestedScroll(fixture, target = nestedTarget)

            nestedScroll(fixture, direction * 80, target = nestedTarget)
            fixture.layout.onStopNestedScroll(nestedTarget, ViewCompat.TYPE_TOUCH)

            assertEquals(listOf(actionFor(direction)), fixture.actions)
        }
    }

    @Test
    fun clearingCallback_restoresCurrentTargetsDefaultInBothDirections() {
        for (direction: Int in intArrayOf(-1, 1)) {
            for (defaultCanScroll: Boolean in listOf(false, true)) {
                val fixture: Fixture = createFixture(canScroll = defaultCanScroll)
                fixture.layout.setScrollBoundaryCallback { _, _, _ -> !defaultCanScroll }
                startNestedScroll(fixture)
                nestedScroll(fixture, direction * 10)
                assertEquals(defaultCanScroll, fixture.layout.currentOffset != 0)
                fixture.layout.onStopNestedScroll(fixture.content, ViewCompat.TYPE_TOUCH)
                assertEquals(0, fixture.layout.currentOffset)

                fixture.layout.setScrollBoundaryCallback(null)
                startNestedScroll(fixture)
                nestedScroll(fixture, direction * 80)
                fixture.layout.onStopNestedScroll(fixture.content, ViewCompat.TYPE_TOUCH)

                val expected: List<KitRefreshLoadLayout.RefreshLoadAction> = if (defaultCanScroll) {
                    emptyList()
                } else {
                    listOf(actionFor(direction))
                }
                assertEquals(expected, fixture.actions)
            }
        }
    }

    @Test
    fun callback_receivesNullWhenThereIsNoContent() {
        val fixture: Fixture = createFixture()
        fixture.layout.removeView(fixture.content)
        var invoked: Boolean = false
        fixture.layout.setScrollBoundaryCallback { _, target, _ ->
            invoked = true
            assertNull(target)
            true
        }

        directDrag(fixture.layout, -1)

        assertTrue(invoked)
        assertEquals(0, fixture.layout.currentOffset)
        assertTrue(fixture.actions.isEmpty())
    }

    @Test
    fun autoLoadMore_usesBottomCallbackForTouchAndNonTouch() {
        for (type: Int in intArrayOf(ViewCompat.TYPE_TOUCH, ViewCompat.TYPE_NON_TOUCH)) {
            for (callbackCanScroll: Boolean in listOf(false, true)) {
                val fixture: Fixture = createFixture(canScroll = !callbackCanScroll)
                fixture.layout.autoLoadMoreEnabled = true
                fixture.layout.setScrollBoundaryCallback { _, target, direction ->
                    assertSame(fixture.content, target)
                    assertEquals(1, direction)
                    callbackCanScroll
                }
                startNestedScroll(fixture, type)

                nestedScroll(fixture, dyUnconsumed = 0, type = type, dyConsumed = 20)

                assertEquals(if (callbackCanScroll) 0 else 1, fixture.actions.size)
                if (!callbackCanScroll) {
                    assertEquals(KitRefreshLoadLayout.RefreshLoadAction.LoadMore, fixture.actions.single())
                }
            }
        }
    }

    @Test
    fun boundaryDoesNotBypassDirectionEnabled() {
        for (direction: Int in intArrayOf(-1, 1)) {
            val fixture: Fixture = createFixture(canScroll = true)
            fixture.layout.setScrollBoundaryCallback { _, _, _ -> false }
            fixture.layout.autoLoadMoreEnabled = true
            if (direction < 0) {
                fixture.layout.refreshEnabled = false
            } else {
                fixture.layout.loadMoreEnabled = false
            }
            startNestedScroll(fixture)

            nestedScroll(fixture, direction * 80)
            fixture.layout.onStopNestedScroll(fixture.content, ViewCompat.TYPE_TOUCH)
            directDrag(fixture.layout, direction)

            assertEquals(0, fixture.layout.currentOffset)
            assertTrue(fixture.actions.isEmpty())
        }
    }

    @Test
    fun boundaryDoesNotBypassGlobalEnabled() {
        val fixture: Fixture = createFixture(canScroll = true)
        fixture.layout.setScrollBoundaryCallback { _, _, _ -> false }
        fixture.layout.autoLoadMoreEnabled = true
        startNestedScroll(fixture)
        fixture.layout.isEnabled = false

        nestedScroll(fixture, -80)
        nestedScroll(fixture, 80)
        fixture.layout.onStopNestedScroll(fixture.content, ViewCompat.TYPE_TOUCH)

        assertEquals(0, fixture.layout.currentOffset)
        assertTrue(fixture.actions.isEmpty())
    }

    @Test
    fun boundaryDoesNotBypassTriggerDistance() {
        for (direction: Int in intArrayOf(-1, 1)) {
            val fixture: Fixture = createFixture(canScroll = true)
            fixture.layout.setScrollBoundaryCallback { _, _, _ -> false }
            startNestedScroll(fixture)

            nestedScroll(fixture, direction * 10)
            assertTrue(fixture.layout.currentOffset != 0)
            fixture.layout.onStopNestedScroll(fixture.content, ViewCompat.TYPE_TOUCH)

            assertEquals(0, fixture.layout.currentOffset)
            assertTrue(fixture.actions.isEmpty())
        }
    }

    @Test
    fun busyPreScroll_usesCallbackInBothDirectionsAndScrollTypes() {
        verifyBusyBoundary(usePreScroll = true)
    }

    @Test
    fun busyPostScroll_usesCallbackInBothDirectionsAndScrollTypes() {
        verifyBusyBoundary(usePreScroll = false)
    }

    @Test
    fun busyFling_usesCallbackToContinueShowingEitherIndicator() {
        for (direction: Int in intArrayOf(-1, 1)) {
            val fixture: Fixture = createBusyFixture(direction)
            fixture.content.canScroll = true
            var callbackCanScroll: Boolean = true
            fixture.layout.setScrollBoundaryCallback { _, _, requestedDirection ->
                assertEquals(direction, requestedDirection)
                callbackCanScroll
            }
            assertFalse(fixture.layout.onNestedPreFling(fixture.content, 0f, direction * 6_000f))
            startNestedScroll(fixture, ViewCompat.TYPE_NON_TOUCH)

            nestedScroll(fixture, direction, ViewCompat.TYPE_NON_TOUCH)
            advanceIndicatorFling(fixture.layout)
            assertEquals(0, fixture.layout.currentOffset)

            callbackCanScroll = false
            nestedScroll(fixture, direction, ViewCompat.TYPE_NON_TOUCH)
            advanceIndicatorFling(fixture.layout)

            assertTrue("Fling must use the custom boundary", fixture.layout.currentOffset * -direction > 1)
            assertEquals(listOf(actionFor(direction)), fixture.actions)
        }
    }

    @Test
    fun busyPreScroll_whenBoundaryCallbackFinishesAction_doesNotConsumeOldScroll() {
        verifyBusyScrollAfterBoundaryFinishesAction(usePreScroll = true)
    }

    @Test
    fun busyPostScroll_whenBoundaryCallbackFinishesAction_doesNotConsumeOldScroll() {
        verifyBusyScrollAfterBoundaryFinishesAction(usePreScroll = false)
    }

    @Test
    fun directDrag_whenLaterBoundaryCallbackSwitchesAction_preservesNewActionPosition() {
        for (direction: Int in intArrayOf(-1, 1)) {
            val fixture: Fixture = createFixture()
            var queries: Int = 0
            fixture.layout.setScrollBoundaryCallback { layout, _, _ ->
                if (++queries == 2) {
                    layout.setActiveAction(actionFor(-direction))
                }
                false
            }

            directDrag(fixture.layout, direction)

            assertTrue("The callback must switch the active action", queries >= 2)
            assertActiveAction(fixture, -direction)
            assertTrue(fixture.actions.isEmpty())
        }
    }

    @Test
    fun nestedDrag_whenLaterBoundaryCallbackSwitchesAction_preservesNewActionPosition() {
        for (direction: Int in intArrayOf(-1, 1)) {
            val fixture: Fixture = createFixture()
            var queries: Int = 0
            fixture.layout.setScrollBoundaryCallback { layout, _, _ ->
                if (++queries == 2) {
                    layout.setActiveAction(actionFor(-direction))
                }
                false
            }
            startNestedScroll(fixture)

            val consumed: IntArray = nestedScroll(fixture, direction * 80)
            fixture.layout.onStopNestedScroll(fixture.content, ViewCompat.TYPE_TOUCH)

            assertTrue("The callback must switch the active action", queries >= 2)
            assertEquals(0, consumed[1])
            assertActiveAction(fixture, -direction)
            assertTrue(fixture.actions.isEmpty())
        }
    }

    @Test
    fun nestedDrag_whenOppositeBoundaryCallbackDisablesActiveDirection_doesNotMoveOrConsume() {
        verifyOppositeBoundaryInvalidatesDrag { fixture: Fixture, direction: Int ->
            if (direction < 0) {
                fixture.layout.refreshEnabled = false
            } else {
                fixture.layout.loadMoreEnabled = false
            }
        }
    }

    @Test
    fun nestedDrag_whenOppositeBoundaryCallbackHidesActiveIndicator_doesNotMoveOrConsume() {
        verifyOppositeBoundaryInvalidatesDrag { fixture: Fixture, direction: Int ->
            val role: KitRefreshLoadLayout.ChildRole = if (direction < 0) {
                KitRefreshLoadLayout.ChildRole.Header
            } else {
                KitRefreshLoadLayout.ChildRole.Footer
            }
            val indicator: View = (0 until fixture.layout.childCount)
                .map(fixture.layout::getChildAt)
                .first { child: View -> (child.layoutParams as KitRefreshLoadLayout.LayoutParams).role == role }
            indicator.visibility = View.GONE
        }
    }

    @Test
    fun autoLoadMore_whenBoundaryCallbackDisablesEntry_doesNotSubmitAction() {
        val disableEntries: List<(KitRefreshLoadLayout) -> Unit> = listOf(
            { it.autoLoadMoreEnabled = false },
            { it.loadMoreEnabled = false },
            { it.isEnabled = false },
        )
        for (type: Int in intArrayOf(ViewCompat.TYPE_TOUCH, ViewCompat.TYPE_NON_TOUCH)) {
            for (disable: (KitRefreshLoadLayout) -> Unit in disableEntries) {
                val fixture: Fixture = createFixture()
                fixture.layout.autoLoadMoreEnabled = true
                var queried: Boolean = false
                fixture.layout.setScrollBoundaryCallback { layout, _, _ ->
                    queried = true
                    disable(layout)
                    false
                }
                startNestedScroll(fixture, type)

                val consumed: IntArray = nestedScroll(fixture, 0, type, dyConsumed = 20)

                assertTrue(queried)
                assertEquals(0, consumed[1])
                assertEquals(KitRefreshLoadLayout.RefreshLoadState.Idle, fixture.layout.state)
                assertEquals(0, fixture.layout.currentOffset)
                assertTrue(fixture.actions.isEmpty())
            }
        }
    }

    @Test
    fun autoLoadMore_whenBoundaryCallbackReplacesSession_ignoresOldScrollAndAcceptsNewScroll() {
        for (type: Int in intArrayOf(ViewCompat.TYPE_TOUCH, ViewCompat.TYPE_NON_TOUCH)) {
            val fixture: Fixture = createFixture()
            fixture.layout.autoLoadMoreEnabled = true
            var queried: Boolean = false
            fixture.layout.setScrollBoundaryCallback { layout, _, _ ->
                queried = true
                layout.setScrollBoundaryCallback(null)
                layout.onStopNestedScroll(fixture.content, type)
                startNestedScroll(fixture, type)
                false
            }
            startNestedScroll(fixture, type)

            nestedScroll(fixture, 0, type, dyConsumed = 20)

            assertTrue(queried)
            assertEquals(KitRefreshLoadLayout.RefreshLoadState.Idle, fixture.layout.state)
            assertEquals(0, fixture.layout.currentOffset)
            assertTrue(fixture.actions.isEmpty())

            nestedScroll(fixture, 0, type, dyConsumed = 20)

            assertActiveAction(fixture, 1)
            assertEquals(listOf(KitRefreshLoadLayout.RefreshLoadAction.LoadMore), fixture.actions)
        }
    }

    @Test
    fun busyScroll_whenBoundaryCallbackReplacesSession_ignoresOldScrollAndAcceptsNewScroll() {
        for (direction: Int in intArrayOf(-1, 1)) {
            for (type: Int in intArrayOf(ViewCompat.TYPE_TOUCH, ViewCompat.TYPE_NON_TOUCH)) {
                for (usePreScroll: Boolean in listOf(false, true)) {
                    val fixture: Fixture = createBusyFixture(direction)
                    fixture.layout.setScrollBoundaryCallback { layout, _, _ ->
                        layout.setScrollBoundaryCallback(null)
                        layout.onStopNestedScroll(fixture.content, type)
                        startNestedScroll(fixture, type)
                        false
                    }
                    startNestedScroll(fixture, type)

                    val oldConsumed: IntArray = if (usePreScroll) {
                        nestedPreScroll(fixture, direction * 15, type)
                    } else {
                        nestedScroll(fixture, direction * 15, type)
                    }

                    assertEquals(0, oldConsumed[1])
                    assertEquals(0, fixture.layout.currentOffset)

                    val newConsumed: IntArray = if (usePreScroll) {
                        nestedPreScroll(fixture, direction * 15, type)
                    } else {
                        nestedScroll(fixture, direction * 15, type)
                    }

                    assertEquals(direction * 15, newConsumed[1])
                    assertEquals(-direction * 15, fixture.layout.currentOffset)
                    assertEquals(listOf(actionFor(direction)), fixture.actions)
                }
            }
        }
    }

    @Test
    fun directDrag_whenBoundaryCallbackStartsNewTouch_ignoresOldMoveAndAcceptsNewMove() {
        for (direction: Int in intArrayOf(-1, 1)) {
            val fixture: Fixture = createFixture()
            fixture.layout.setScrollBoundaryCallback { layout, _, _ ->
                layout.setScrollBoundaryCallback(null)
                sendTouch(layout, MotionEvent.ACTION_DOWN, 400f)
                false
            }
            sendTouch(fixture.layout, MotionEvent.ACTION_DOWN, 200f)

            sendTouch(fixture.layout, MotionEvent.ACTION_MOVE, 200f - direction * 80f)

            assertEquals(0, fixture.layout.currentOffset)
            assertEquals(KitRefreshLoadLayout.RefreshLoadState.Idle, fixture.layout.state)
            assertTrue(fixture.actions.isEmpty())

            sendTouch(fixture.layout, MotionEvent.ACTION_MOVE, 400f - direction * 80f)
            sendTouch(fixture.layout, MotionEvent.ACTION_UP, 400f - direction * 80f)

            assertActiveAction(fixture, direction)
            assertEquals(listOf(actionFor(direction)), fixture.actions)
        }
    }

    @Test
    fun busyFling_whenBoundaryCallbackSwitchesAction_doesNotMoveNewIndicator() {
        for (direction: Int in intArrayOf(-1, 1)) {
            val fixture: Fixture = createBusyFixture(direction)
            assertFalse(fixture.layout.onNestedPreFling(fixture.content, 0f, direction * 6_000f))
            var switched: Boolean = false
            fixture.layout.setScrollBoundaryCallback { layout, _, _ ->
                if (layout.currentOffset == -direction) {
                    switched = true
                    layout.setActiveAction(actionFor(-direction))
                }
                false
            }
            startNestedScroll(fixture, ViewCompat.TYPE_NON_TOUCH)

            val consumed: IntArray = nestedScroll(fixture, direction, ViewCompat.TYPE_NON_TOUCH)
            advanceIndicatorFling(fixture.layout)

            assertTrue("The fling boundary callback must switch the active action", switched)
            assertEquals(direction, consumed[1])
            assertActiveAction(fixture, -direction)
            assertEquals(listOf(actionFor(direction)), fixture.actions)
        }
    }

    @Test
    fun reverseDirectDrag_rechecksOppositeCustomBoundary() {
        for (initialDirection: Int in intArrayOf(-1, 1)) {
            val fixture: Fixture = createFixture()
            fixture.layout.setScrollBoundaryCallback { _, _, direction -> direction != initialDirection }
            sendTouch(fixture.layout, MotionEvent.ACTION_DOWN, 200f)
            sendTouch(fixture.layout, MotionEvent.ACTION_MOVE, 200f - initialDirection * 80f)
            assertTrue(fixture.layout.currentOffset * -initialDirection > 0)

            sendTouch(fixture.layout, MotionEvent.ACTION_MOVE, 200f + initialDirection * 160f)
            sendTouch(fixture.layout, MotionEvent.ACTION_UP, 200f + initialDirection * 160f)

            assertEquals(0, fixture.layout.currentOffset)
            assertTrue(fixture.actions.isEmpty())
        }
    }

    private fun verifyOppositeBoundaryInvalidatesDrag(invalidate: (Fixture, Int) -> Unit) {
        for (direction: Int in intArrayOf(-1, 1)) {
            val fixture: Fixture = createFixture()
            val queries: MutableList<Int> = mutableListOf()
            var invalidated: Boolean = false
            fixture.layout.setScrollBoundaryCallback { _, _, requestedDirection ->
                if (requestedDirection == -direction && !invalidated) {
                    assertTrue(
                        "The active direction must have been accepted before the opposite query",
                        queries.contains(direction),
                    )
                    invalidated = true
                    invalidate(fixture, direction)
                }
                queries += requestedDirection
                false
            }
            startNestedScroll(fixture)

            val consumed: IntArray = nestedScroll(fixture, direction * 80)

            assertTrue("The opposite boundary callback must invalidate the active direction", invalidated)
            assertEquals(0, consumed[1])
            assertEquals(0, fixture.layout.currentOffset)
            assertEquals(KitRefreshLoadLayout.RefreshLoadState.Idle, fixture.layout.state)
            fixture.layout.onStopNestedScroll(fixture.content, ViewCompat.TYPE_TOUCH)
            assertTrue(fixture.actions.isEmpty())
        }
    }

    private fun verifyBusyScrollAfterBoundaryFinishesAction(usePreScroll: Boolean) {
        for (direction: Int in intArrayOf(-1, 1)) {
            for (type: Int in intArrayOf(ViewCompat.TYPE_TOUCH, ViewCompat.TYPE_NON_TOUCH)) {
                val fixture: Fixture = createBusyFixture(direction)
                var queried: Boolean = false
                fixture.layout.setScrollBoundaryCallback { layout, _, _ ->
                    queried = true
                    layout.setActiveAction(null, animate = false)
                    false
                }
                startNestedScroll(fixture, type)

                val consumed: IntArray = if (usePreScroll) {
                    nestedPreScroll(fixture, direction * 15, type)
                } else {
                    nestedScroll(fixture, direction * 15, type)
                }

                assertTrue(queried)
                assertEquals(0, consumed[1])
                assertEquals(KitRefreshLoadLayout.RefreshLoadState.Idle, fixture.layout.state)
                assertEquals(0, fixture.layout.currentOffset)
                assertEquals(listOf(actionFor(direction)), fixture.actions)
            }
        }
    }

    private fun assertActiveAction(fixture: Fixture, direction: Int) {
        val expectedState: KitRefreshLoadLayout.RefreshLoadState = if (direction < 0) {
            KitRefreshLoadLayout.RefreshLoadState.Refreshing
        } else {
            KitRefreshLoadLayout.RefreshLoadState.LoadingMore
        }
        assertEquals(expectedState, fixture.layout.state)
        assertEquals(if (direction < 0) 48 else -64, fixture.layout.currentOffset)
    }

    private fun verifyBusyBoundary(usePreScroll: Boolean) {
        for (direction: Int in intArrayOf(-1, 1)) {
            for (type: Int in intArrayOf(ViewCompat.TYPE_TOUCH, ViewCompat.TYPE_NON_TOUCH)) {
                val fixture: Fixture = createBusyFixture(direction)
                fixture.content.canScroll = true
                var callbackCanScroll: Boolean = true
                fixture.layout.setScrollBoundaryCallback { layout, target, requestedDirection ->
                    assertSame(fixture.layout, layout)
                    assertSame(fixture.content, target)
                    assertEquals(direction, requestedDirection)
                    callbackCanScroll
                }
                startNestedScroll(fixture, type)

                val blocked: IntArray = if (usePreScroll) {
                    nestedPreScroll(fixture, direction * 15, type)
                } else {
                    nestedScroll(fixture, direction * 15, type)
                }
                assertEquals(0, blocked[1])
                assertEquals(0, fixture.layout.currentOffset)

                callbackCanScroll = false
                val allowed: IntArray = if (usePreScroll) {
                    nestedPreScroll(fixture, direction * 15, type)
                } else {
                    nestedScroll(fixture, direction * 15, type)
                }
                assertEquals(direction * 15, allowed[1])
                assertEquals(-direction * 15, fixture.layout.currentOffset)
                assertEquals(listOf(actionFor(direction)), fixture.actions)
            }
        }
    }

    private fun createBusyFixture(direction: Int): Fixture {
        val fixture: Fixture = createFixture()
        startNestedScroll(fixture)
        nestedScroll(fixture, direction * 80)
        fixture.layout.onStopNestedScroll(fixture.content, ViewCompat.TYPE_TOUCH)
        assertEquals(listOf(actionFor(direction)), fixture.actions)
        nestedPreScroll(fixture, fixture.layout.currentOffset)
        assertEquals(0, fixture.layout.currentOffset)
        return fixture
    }

    private fun createFixture(canScroll: Boolean = false): Fixture {
        val layout: KitRefreshLoadLayout = KitRefreshLoadLayout(RuntimeEnvironment.getApplication()).apply {
            animationDurationMillis = 0L
            dragResistance = 1f
            maxDragDistance = 10_000
            refreshTriggerDistance = 30
            loadMoreTriggerDistance = 30
        }
        val content: ScrollableContent = ScrollableContent().apply { this.canScroll = canScroll }
        val actions: MutableList<KitRefreshLoadLayout.RefreshLoadAction> = mutableListOf()
        layout.addView(
            View(layout.context),
            KitRefreshLoadLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 48).apply {
                role = KitRefreshLoadLayout.ChildRole.Header
            },
        )
        layout.addView(
            content,
            KitRefreshLoadLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        layout.addView(
            View(layout.context),
            KitRefreshLoadLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 64).apply {
                role = KitRefreshLoadLayout.ChildRole.Footer
            },
        )
        layout.setOnRefreshLoadListener { actions += it }
        layout.measure(
            View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY),
        )
        layout.layout(0, 0, 300, 500)
        return Fixture(layout, content, actions)
    }

    private fun startNestedScroll(
        fixture: Fixture,
        type: Int = ViewCompat.TYPE_TOUCH,
        target: View = fixture.content,
    ) {
        assertTrue(fixture.layout.onStartNestedScroll(fixture.content, target, ViewCompat.SCROLL_AXIS_VERTICAL, type))
        fixture.layout.onNestedScrollAccepted(fixture.content, target, ViewCompat.SCROLL_AXIS_VERTICAL, type)
    }

    private fun nestedScroll(
        fixture: Fixture,
        dyUnconsumed: Int,
        type: Int = ViewCompat.TYPE_TOUCH,
        dyConsumed: Int = 0,
        target: View = fixture.content,
    ): IntArray {
        val consumed: IntArray = IntArray(2)
        fixture.layout.onNestedScroll(target, 0, dyConsumed, 0, dyUnconsumed, type, consumed)
        return consumed
    }

    private fun nestedPreScroll(
        fixture: Fixture,
        dy: Int,
        type: Int = ViewCompat.TYPE_TOUCH,
    ): IntArray {
        val consumed: IntArray = IntArray(2)
        fixture.layout.onNestedPreScroll(fixture.content, 0, dy, consumed, type)
        return consumed
    }

    private fun directDrag(layout: KitRefreshLoadLayout, direction: Int) {
        sendTouch(layout, MotionEvent.ACTION_DOWN, 200f)
        sendTouch(layout, MotionEvent.ACTION_MOVE, 200f - direction * 80f)
        sendTouch(layout, MotionEvent.ACTION_UP, 200f - direction * 80f)
    }

    private fun sendTouch(layout: KitRefreshLoadLayout, action: Int, y: Float) {
        val event: MotionEvent = MotionEvent.obtain(0L, 0L, action, 100f, y, 0)
        try {
            layout.onTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun advanceIndicatorFling(layout: KitRefreshLoadLayout) {
        repeat(5) {
            ShadowSystemClock.advanceBy(16, TimeUnit.MILLISECONDS)
            layout.computeScroll()
        }
    }

    private fun actionFor(direction: Int): KitRefreshLoadLayout.RefreshLoadAction =
        if (direction < 0) {
            KitRefreshLoadLayout.RefreshLoadAction.Refresh
        } else {
            KitRefreshLoadLayout.RefreshLoadAction.LoadMore
        }

    /** 测试布局及可观测业务回调. */
    private data class Fixture(
        val layout: KitRefreshLoadLayout,
        val content: ScrollableContent,
        val actions: MutableList<KitRefreshLoadLayout.RefreshLoadAction>,
    )

    /** 用显式滚动能力模拟直接容器与真实内容边界不一致. */
    private class ScrollableContent : FrameLayout(RuntimeEnvironment.getApplication()) {
        var canScroll: Boolean = false

        override fun canScrollVertically(direction: Int): Boolean = canScroll
    }
}
