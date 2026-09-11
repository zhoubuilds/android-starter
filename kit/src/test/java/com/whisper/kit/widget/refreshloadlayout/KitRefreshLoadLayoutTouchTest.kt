package com.whisper.kit.widget.refreshloadlayout

import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.widget.NestedScrollView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/**
 * 验证刷新加载的触摸取消、滚动消费和会话边界.
 *
 * @author whisper
 * @since 2026/09/11
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
@LooperMode(LooperMode.Mode.PAUSED)
class KitRefreshLoadLayoutTouchTest {

    @Test
    fun pullDownBelowRefreshThreshold_doesNotUseLowerLoadMoreThreshold() {
        val fixture: Fixture = createFixture()
        fixture.layout.refreshTriggerDistance = 100
        fixture.layout.loadMoreTriggerDistance = 20

        startNestedScroll(fixture)
        nestedScroll(fixture, -50)
        fixture.layout.onStopNestedScroll(fixture.content, ViewCompat.TYPE_TOUCH)

        assertTrue(fixture.actions.isEmpty())
        assertEquals(0, fixture.layout.currentOffset)
        assertEquals(KitRefreshLoadLayout.RefreshLoadState.Idle, fixture.layout.state)
    }

    @Test
    fun pullUpBelowLoadMoreThreshold_doesNotUseLowerRefreshThreshold() {
        val fixture: Fixture = createFixture()
        fixture.layout.refreshTriggerDistance = 20
        fixture.layout.loadMoreTriggerDistance = 100

        startNestedScroll(fixture)
        nestedScroll(fixture, 50)
        fixture.layout.onStopNestedScroll(fixture.content, ViewCompat.TYPE_TOUCH)

        assertTrue(fixture.actions.isEmpty())
        assertEquals(0, fixture.layout.currentOffset)
        assertEquals(KitRefreshLoadLayout.RefreshLoadState.Idle, fixture.layout.state)
    }

    @Test
    fun unequalThresholds_releaseCommitsOnlyTheDraggedDirection() {
        for (direction: Int in intArrayOf(-1, 1)) {
            val fixture: Fixture = createFixture()
            fixture.layout.refreshTriggerDistance = if (direction < 0) 20 else 100
            fixture.layout.loadMoreTriggerDistance = if (direction > 0) 20 else 100

            startNestedScroll(fixture)
            nestedScroll(fixture, direction * 50)
            fixture.layout.onStopNestedScroll(fixture.content, ViewCompat.TYPE_TOUCH)

            val expected: KitRefreshLoadLayout.RefreshLoadAction = if (direction < 0) {
                KitRefreshLoadLayout.RefreshLoadAction.Refresh
            } else {
                KitRefreshLoadLayout.RefreshLoadAction.LoadMore
            }
            assertEquals(listOf(expected), fixture.actions)
        }
    }

    @Test
    fun nestedScrollView_cancelRetractsRefreshWithoutSubmittingAndAllowsNextGesture() {
        verifyNestedScrollViewCancellation(pullDown = true)
    }

    @Test
    fun nestedScrollView_cancelRetractsLoadMoreWithoutSubmittingAndAllowsNextGesture() {
        verifyNestedScrollViewCancellation(pullDown = false)
    }

    @Test
    fun refreshing_postScrollReportsOnlyActualConsumedDistanceAndPreservesExistingValues() {
        verifyBusyPostScrollConsumption(refresh = true)
    }

    @Test
    fun loadingMore_postScrollReportsOnlyActualConsumedDistanceAndPreservesExistingValues() {
        verifyBusyPostScrollConsumption(refresh = false)
    }

    @Test
    fun reverseDirectDrag_doesNotRevealDisabledFooter() {
        val fixture: Fixture = createFixture()
        fixture.layout.loadMoreEnabled = false

        verifyDirectDragStopsAtZero(fixture, initialDirection = 1)
    }

    @Test
    fun reverseDirectDrag_doesNotRevealDisabledHeader() {
        val fixture: Fixture = createFixture()
        fixture.layout.refreshEnabled = false

        verifyDirectDragStopsAtZero(fixture, initialDirection = -1)
    }

    @Test
    fun reverseDirectDrag_doesNotRevealFooterBeforeContentReachesBottom() {
        val content: ScrollableContent = ScrollableContent().apply { canScrollDown = true }
        val fixture: Fixture = createFixture(content)

        verifyDirectDragStopsAtZero(fixture, initialDirection = 1)
    }

    @Test
    fun reverseDirectDrag_doesNotRevealHeaderBeforeContentReachesTop() {
        val content: ScrollableContent = ScrollableContent().apply { canScrollUp = true }
        val fixture: Fixture = createFixture(content)

        verifyDirectDragStopsAtZero(fixture, initialDirection = -1)
    }

    @Test
    fun directDrag_accumulatesSmallMovesInBothDirections() {
        for (direction: Int in intArrayOf(-1, 1)) {
            val fixture: Fixture = createFixture()
            fixture.layout.dragResistance = 3f
            fixture.layout.maxDragDistance = 10_000
            sendTouch(fixture.layout, MotionEvent.ACTION_DOWN, Pointer(0, 200f))
            sendTouch(fixture.layout, MotionEvent.ACTION_MOVE, Pointer(0, 200f + direction * 30f))
            val initialOffset: Int = fixture.layout.currentOffset

            repeat(100) { index: Int ->
                val y: Float = 200f + direction * (31f + index)
                sendTouch(fixture.layout, MotionEvent.ACTION_MOVE, Pointer(0, y))
            }

            val accumulatedOffset: Int = (fixture.layout.currentOffset - initialOffset) * direction
            assertTrue("Small direct moves must accumulate into visible movement", accumulatedOffset in 25..40)
            assertTrue(fixture.actions.isEmpty())
            sendTouch(fixture.layout, MotionEvent.ACTION_CANCEL, Pointer(0, 200f + direction * 130f))
        }
    }

    @Test
    fun nestedDrag_accumulatesSmallMovesInBothDirections() {
        for (direction: Int in intArrayOf(-1, 1)) {
            val fixture: Fixture = createFixture()
            fixture.layout.dragResistance = 3f
            fixture.layout.maxDragDistance = 10_000
            startNestedScroll(fixture)

            repeat(100) {
                val consumed: IntArray = nestedScroll(fixture, direction)
                assertEquals(direction, consumed[1])
            }

            val accumulatedOffset: Int = -fixture.layout.currentOffset * direction
            assertTrue("Small nested moves must accumulate into visible movement", accumulatedOffset in 25..40)
            assertTrue(fixture.actions.isEmpty())
        }
    }

    @Test
    fun directPointerHandoff_discardsPreviousPointerRemainder() {
        val fixture: Fixture = createFixture()
        fixture.layout.dragResistance = 100f
        fixture.layout.maxDragDistance = 10_000
        sendTouch(fixture.layout, MotionEvent.ACTION_DOWN, Pointer(0, 0f))
        sendTouch(fixture.layout, MotionEvent.ACTION_MOVE, Pointer(0, 40f))
        assertEquals(0, fixture.layout.currentOffset)
        sendTouch(
            fixture.layout,
            MotionEvent.ACTION_POINTER_DOWN,
            Pointer(0, 40f),
            Pointer(1, 200f),
            actionIndex = 1,
        )
        sendTouch(
            fixture.layout,
            MotionEvent.ACTION_POINTER_UP,
            Pointer(0, 40f),
            Pointer(1, 200f),
        )

        sendTouch(fixture.layout, MotionEvent.ACTION_MOVE, Pointer(1, 220f))

        assertEquals(0, fixture.layout.currentOffset)
        sendTouch(fixture.layout, MotionEvent.ACTION_MOVE, Pointer(1, 320f))
        assertTrue(fixture.layout.currentOffset > 0)
    }

    @Test
    fun newDirectGesture_doesNotInheritCancelledGestureRemainder() {
        val fixture: Fixture = createFixture()
        fixture.layout.dragResistance = 100f
        fixture.layout.maxDragDistance = 10_000
        sendTouch(fixture.layout, MotionEvent.ACTION_DOWN, Pointer(0, 0f))
        sendTouch(fixture.layout, MotionEvent.ACTION_MOVE, Pointer(0, 40f))
        sendTouch(fixture.layout, MotionEvent.ACTION_CANCEL, Pointer(0, 40f))

        sendTouch(fixture.layout, MotionEvent.ACTION_DOWN, Pointer(0, 0f))
        sendTouch(fixture.layout, MotionEvent.ACTION_MOVE, Pointer(0, 40f))

        assertEquals(0, fixture.layout.currentOffset)
        assertTrue(fixture.actions.isEmpty())
    }

    @Test
    fun newNestedGesture_doesNotInheritPreviousGestureRemainder() {
        val fixture: Fixture = createFixture()
        fixture.layout.dragResistance = 100f
        fixture.layout.maxDragDistance = 10_000
        startNestedScroll(fixture)
        nestedScroll(fixture, -40)
        fixture.layout.onStopNestedScroll(fixture.content, ViewCompat.TYPE_TOUCH)

        startNestedScroll(fixture)
        nestedScroll(fixture, -40)

        assertEquals(0, fixture.layout.currentOffset)
        assertTrue(fixture.actions.isEmpty())
    }

    @Test
    fun disablingDirectDrag_invalidatesOldEventsAndAllowsFreshGestureAfterReenable() {
        val fixture: Fixture = createFixture()
        sendTouch(fixture.layout, MotionEvent.ACTION_DOWN, Pointer(0, 0f))
        sendTouch(fixture.layout, MotionEvent.ACTION_MOVE, Pointer(0, 80f))
        assertTrue(fixture.layout.currentOffset > fixture.layout.refreshTriggerDistance)

        fixture.layout.isEnabled = false

        assertEquals(0, fixture.layout.currentOffset)
        assertEquals(KitRefreshLoadLayout.RefreshLoadState.Idle, fixture.layout.state)
        fixture.layout.isEnabled = true
        sendTouch(fixture.layout, MotionEvent.ACTION_MOVE, Pointer(0, 160f))
        sendTouch(fixture.layout, MotionEvent.ACTION_UP, Pointer(0, 160f))
        assertTrue(fixture.actions.isEmpty())
        assertEquals(0, fixture.layout.currentOffset)

        sendTouch(fixture.layout, MotionEvent.ACTION_DOWN, Pointer(0, 0f))
        sendTouch(fixture.layout, MotionEvent.ACTION_MOVE, Pointer(0, 80f))
        sendTouch(fixture.layout, MotionEvent.ACTION_UP, Pointer(0, 80f))

        assertEquals(listOf(KitRefreshLoadLayout.RefreshLoadAction.Refresh), fixture.actions)
    }

    @Test
    fun disablingNestedDrag_invalidatesOldEventsAndAllowsFreshGestureAfterReenable() {
        val fixture: Fixture = createFixture()
        startNestedScroll(fixture)
        nestedScroll(fixture, -80)
        assertTrue(fixture.layout.currentOffset > fixture.layout.refreshTriggerDistance)

        fixture.layout.isEnabled = false

        assertEquals(0, fixture.layout.currentOffset)
        assertEquals(KitRefreshLoadLayout.RefreshLoadState.Idle, fixture.layout.state)
        assertEquals(0, nestedScroll(fixture, -80)[1])
        fixture.layout.isEnabled = true
        assertEquals(0, nestedScroll(fixture, -80)[1])
        fixture.layout.onStopNestedScroll(fixture.content, ViewCompat.TYPE_TOUCH)
        assertTrue(fixture.actions.isEmpty())
        assertEquals(0, fixture.layout.currentOffset)

        startNestedScroll(fixture)
        nestedScroll(fixture, -80)
        fixture.layout.onStopNestedScroll(fixture.content, ViewCompat.TYPE_TOUCH)

        assertEquals(listOf(KitRefreshLoadLayout.RefreshLoadAction.Refresh), fixture.actions)
    }

    @Test
    fun finishingRefresh_blocksOldNonTouchEventsAndAllowsNewAutomaticLoadSession() {
        val fixture: Fixture = createFixture()
        fixture.layout.autoLoadMoreEnabled = true
        startNestedScroll(fixture)
        nestedScroll(fixture, -80)
        fixture.layout.onStopNestedScroll(fixture.content, ViewCompat.TYPE_TOUCH)
        startNestedScroll(fixture, ViewCompat.TYPE_NON_TOUCH)

        fixture.layout.finishRefresh(animate = false)

        assertEquals(0, nestedScroll(fixture, 80, ViewCompat.TYPE_NON_TOUCH)[1])
        assertEquals(listOf(KitRefreshLoadLayout.RefreshLoadAction.Refresh), fixture.actions)
        assertEquals(KitRefreshLoadLayout.RefreshLoadState.Idle, fixture.layout.state)
        fixture.layout.onStopNestedScroll(fixture.content, ViewCompat.TYPE_NON_TOUCH)

        startNestedScroll(fixture, ViewCompat.TYPE_NON_TOUCH)
        nestedScroll(fixture, 80, ViewCompat.TYPE_NON_TOUCH)

        assertEquals(
            listOf(KitRefreshLoadLayout.RefreshLoadAction.Refresh, KitRefreshLoadLayout.RefreshLoadAction.LoadMore),
            fixture.actions,
        )
        assertEquals(KitRefreshLoadLayout.RefreshLoadState.LoadingMore, fixture.layout.state)
    }

    @Test
    fun cancellingTouch_blocksOldNonTouchEventsAndAllowsNewAutomaticLoadSession() {
        val fixture: Fixture = createFixture()
        fixture.layout.autoLoadMoreEnabled = true
        startNestedScroll(fixture)
        nestedScroll(fixture, -80)
        startNestedScroll(fixture, ViewCompat.TYPE_NON_TOUCH)

        dispatchTouch(fixture.layout, MotionEvent.ACTION_CANCEL, 80f)
        fixture.layout.onStopNestedScroll(fixture.content, ViewCompat.TYPE_TOUCH)

        assertEquals(0, nestedScroll(fixture, 80, ViewCompat.TYPE_NON_TOUCH)[1])
        assertTrue(fixture.actions.isEmpty())
        assertEquals(KitRefreshLoadLayout.RefreshLoadState.Idle, fixture.layout.state)
        fixture.layout.onStopNestedScroll(fixture.content, ViewCompat.TYPE_NON_TOUCH)

        startNestedScroll(fixture, ViewCompat.TYPE_NON_TOUCH)
        nestedScroll(fixture, 80, ViewCompat.TYPE_NON_TOUCH)

        assertEquals(listOf(KitRefreshLoadLayout.RefreshLoadAction.LoadMore), fixture.actions)
        assertEquals(KitRefreshLoadLayout.RefreshLoadState.LoadingMore, fixture.layout.state)
    }

    @Test
    fun disablingNonTouchScroll_blocksOldEventsAndAllowsNewAutomaticLoadSessionAfterReenable() {
        val fixture: Fixture = createFixture()
        fixture.layout.autoLoadMoreEnabled = true
        startNestedScroll(fixture, ViewCompat.TYPE_NON_TOUCH)

        fixture.layout.isEnabled = false
        fixture.layout.isEnabled = true

        assertEquals(0, nestedScroll(fixture, 80, ViewCompat.TYPE_NON_TOUCH)[1])
        assertTrue(fixture.actions.isEmpty())
        assertEquals(KitRefreshLoadLayout.RefreshLoadState.Idle, fixture.layout.state)
        fixture.layout.onStopNestedScroll(fixture.content, ViewCompat.TYPE_NON_TOUCH)

        startNestedScroll(fixture, ViewCompat.TYPE_NON_TOUCH)
        nestedScroll(fixture, 80, ViewCompat.TYPE_NON_TOUCH)

        assertEquals(listOf(KitRefreshLoadLayout.RefreshLoadAction.LoadMore), fixture.actions)
        assertEquals(KitRefreshLoadLayout.RefreshLoadState.LoadingMore, fixture.layout.state)
    }

    private fun verifyNestedScrollViewCancellation(pullDown: Boolean) {
        val scrollView: NestedScrollView = NestedScrollView(RuntimeEnvironment.getApplication())
        scrollView.addView(
            View(RuntimeEnvironment.getApplication()).apply { minimumHeight = 2_000 },
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        val fixture: Fixture = createFixture(scrollView)
        assertTrue(scrollView.canScrollVertically(1))
        if (!pullDown) {
            scrollView.scrollTo(0, 2_000)
            assertTrue(scrollView.canScrollVertically(-1))
        }
        val direction: Int = if (pullDown) 1 else -1
        dispatchTouch(fixture.layout, MotionEvent.ACTION_DOWN, 250f)
        // 保持子 View 持有触摸目标, 确保取消经过真实嵌套滚动停止路径.
        fixture.layout.requestDisallowInterceptTouchEvent(true)
        dispatchTouch(fixture.layout, MotionEvent.ACTION_MOVE, 250f + direction * 160f)
        assertTrue(fixture.layout.currentOffset * direction > 40)

        dispatchTouch(fixture.layout, MotionEvent.ACTION_CANCEL, 250f + direction * 160f)

        assertTrue(fixture.actions.isEmpty())
        assertEquals(0, fixture.layout.currentOffset)
        assertEquals(KitRefreshLoadLayout.RefreshLoadState.Idle, fixture.layout.state)

        dispatchTouch(fixture.layout, MotionEvent.ACTION_DOWN, 250f)
        fixture.layout.requestDisallowInterceptTouchEvent(true)
        dispatchTouch(fixture.layout, MotionEvent.ACTION_MOVE, 250f + direction * 160f)
        dispatchTouch(fixture.layout, MotionEvent.ACTION_UP, 250f + direction * 160f)

        val expected: KitRefreshLoadLayout.RefreshLoadAction = if (pullDown) {
            KitRefreshLoadLayout.RefreshLoadAction.Refresh
        } else {
            KitRefreshLoadLayout.RefreshLoadAction.LoadMore
        }
        assertEquals(listOf(expected), fixture.actions)
    }

    private fun verifyBusyPostScrollConsumption(refresh: Boolean) {
        for (type: Int in intArrayOf(ViewCompat.TYPE_TOUCH, ViewCompat.TYPE_NON_TOUCH)) {
            val fixture: Fixture = createFixture()
            val direction: Int = if (refresh) -1 else 1
            startNestedScroll(fixture)
            nestedScroll(fixture, direction * 80)
            fixture.layout.onStopNestedScroll(fixture.content, ViewCompat.TYPE_TOUCH)
            val expectedState: KitRefreshLoadLayout.RefreshLoadState = if (refresh) {
                KitRefreshLoadLayout.RefreshLoadState.Refreshing
            } else {
                KitRefreshLoadLayout.RefreshLoadState.LoadingMore
            }
            assertEquals(expectedState, fixture.layout.state)
            val holdDistance: Int = if (refresh) HEADER_HEIGHT else FOOTER_HEIGHT
            startNestedScroll(fixture, type)
            fixture.layout.onNestedPreScroll(fixture.content, 0, -direction * holdDistance, IntArray(2), type)
            assertEquals(0, fixture.layout.currentOffset)

            val firstConsumed: IntArray = intArrayOf(7, 11)
            nestedScroll(fixture, direction * 20, type, firstConsumed)

            assertEquals(7, firstConsumed[0])
            assertEquals(11 + direction * 20, firstConsumed[1])
            assertEquals(-direction * 20, fixture.layout.currentOffset)

            val remainingConsumed: IntArray = intArrayOf(7, 11)
            nestedScroll(fixture, direction * 100, type, remainingConsumed)

            assertEquals(7, remainingConsumed[0])
            assertEquals(11 + direction * (holdDistance - 20), remainingConsumed[1])
            assertEquals(-direction * holdDistance, fixture.layout.currentOffset)
            assertEquals(11, nestedScroll(fixture, direction * 20, type, intArrayOf(7, 11))[1])
            assertEquals(expectedState, fixture.layout.state)
            assertEquals(1, fixture.actions.size)
        }
    }

    private fun verifyDirectDragStopsAtZero(fixture: Fixture, initialDirection: Int) {
        sendTouch(fixture.layout, MotionEvent.ACTION_DOWN, Pointer(0, 200f))
        sendTouch(fixture.layout, MotionEvent.ACTION_MOVE, Pointer(0, 200f + initialDirection * 80f))
        assertTrue(fixture.layout.currentOffset * initialDirection > 0)

        sendTouch(fixture.layout, MotionEvent.ACTION_MOVE, Pointer(0, 200f - initialDirection * 160f))

        assertEquals(0, fixture.layout.currentOffset)
        sendTouch(fixture.layout, MotionEvent.ACTION_UP, Pointer(0, 200f - initialDirection * 160f))
        assertEquals(KitRefreshLoadLayout.RefreshLoadState.Idle, fixture.layout.state)
        assertTrue(fixture.actions.isEmpty())
    }

    private fun createFixture(content: View = View(RuntimeEnvironment.getApplication())): Fixture {
        val layout: KitRefreshLoadLayout = KitRefreshLoadLayout(RuntimeEnvironment.getApplication()).apply {
            animationDurationMillis = 0L
            dragResistance = 1f
            refreshTriggerDistance = 40
            loadMoreTriggerDistance = 40
            addView(
                View(context),
                KitRefreshLoadLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, HEADER_HEIGHT).apply {
                    role = KitRefreshLoadLayout.ChildRole.Header
                },
            )
            addView(
                content,
                KitRefreshLoadLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
            )
            addView(
                View(context),
                KitRefreshLoadLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, FOOTER_HEIGHT).apply {
                    role = KitRefreshLoadLayout.ChildRole.Footer
                },
            )
        }
        val actions: MutableList<KitRefreshLoadLayout.RefreshLoadAction> = mutableListOf()
        layout.setOnRefreshLoadListener { action: KitRefreshLoadLayout.RefreshLoadAction -> actions += action }
        layout.measure(
            View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY),
        )
        layout.layout(0, 0, 300, 500)
        return Fixture(layout, content, actions)
    }

    private fun startNestedScroll(fixture: Fixture, type: Int = ViewCompat.TYPE_TOUCH) {
        assertTrue(
            fixture.layout.onStartNestedScroll(fixture.content, fixture.content, ViewCompat.SCROLL_AXIS_VERTICAL, type),
        )
        fixture.layout.onNestedScrollAccepted(fixture.content, fixture.content, ViewCompat.SCROLL_AXIS_VERTICAL, type)
    }

    private fun nestedScroll(
        fixture: Fixture,
        dy: Int,
        type: Int = ViewCompat.TYPE_TOUCH,
        consumed: IntArray = IntArray(2),
    ): IntArray {
        fixture.layout.onNestedScroll(fixture.content, 0, 0, 0, dy, type, consumed)
        return consumed
    }

    private fun dispatchTouch(layout: KitRefreshLoadLayout, action: Int, y: Float) {
        val event: MotionEvent = touchEvent(action, arrayOf(Pointer(0, y)), 0)
        try {
            layout.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun sendTouch(
        layout: KitRefreshLoadLayout,
        action: Int,
        vararg pointers: Pointer,
        actionIndex: Int = 0,
    ) {
        val event: MotionEvent = touchEvent(action, pointers, actionIndex)
        try {
            layout.onTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun touchEvent(action: Int, pointers: Array<out Pointer>, actionIndex: Int): MotionEvent {
        val properties: Array<MotionEvent.PointerProperties> = Array(pointers.size) { index: Int ->
            MotionEvent.PointerProperties().apply {
                id = pointers[index].id
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        }
        val coordinates: Array<MotionEvent.PointerCoords> = Array(pointers.size) { index: Int ->
            MotionEvent.PointerCoords().apply {
                x = 150f
                y = pointers[index].y
            }
        }
        return MotionEvent.obtain(
            0L,
            16L,
            action or (actionIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            pointers.size,
            properties,
            coordinates,
            0,
            0,
            1f,
            1f,
            0,
            0,
            0,
            0,
        )
    }

    /** 测试所需的内容边界. */
    private class ScrollableContent : View(RuntimeEnvironment.getApplication()) {

        var canScrollUp: Boolean = false
        var canScrollDown: Boolean = false

        override fun canScrollVertically(direction: Int): Boolean =
            if (direction < 0) canScrollUp else canScrollDown
    }

    /** 单个用例的容器、内容和业务事件. */
    private data class Fixture(
        val layout: KitRefreshLoadLayout,
        val content: View,
        val actions: MutableList<KitRefreshLoadLayout.RefreshLoadAction>,
    )

    /** 触摸事件使用的指针坐标. */
    private data class Pointer(val id: Int, val y: Float)

    private companion object {

        private const val HEADER_HEIGHT: Int = 60
        private const val FOOTER_HEIGHT: Int = 70
    }
}
