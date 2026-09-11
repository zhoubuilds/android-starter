package com.whisper.kit.widget.refreshloadlayout

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * 验证头尾组件为 GONE 时停止对应请求入口, 不干扰已提交或由业务同步的任务.
 *
 * @author whisper
 * @since 2026/09/11
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@LooperMode(LooperMode.Mode.PAUSED)
class KitRefreshLoadLayoutVisibilityTest {

    @Test
    fun goneIndicator_doesNotInterceptDirectDrag() {
        for (direction: Direction in Direction.entries) {
            val fixture: Fixture = createFixture()
            fixture.indicator(direction).visibility = View.GONE

            assertFalse(interceptTouch(fixture.layout, MotionEvent.ACTION_DOWN, INITIAL_Y))
            assertFalse(interceptTouch(fixture.layout, MotionEvent.ACTION_MOVE, INITIAL_Y + direction.offset))
            assertIdle(fixture)
        }
    }

    @Test
    fun goneIndicator_blocksDirectDragWithoutWaitingForLayout() {
        for (direction: Direction in Direction.entries) {
            val fixture: Fixture = createFixture()
            fixture.indicator(direction).visibility = View.GONE

            directDrag(fixture, direction)

            assertIdle(fixture)
        }
    }

    @Test
    fun goneIndicator_blocksNestedDragWithoutConsumingScroll() {
        for (direction: Direction in Direction.entries) {
            val fixture: Fixture = createFixture()
            fixture.indicator(direction).visibility = View.GONE
            startNestedScroll(fixture)

            val consumed: IntArray = nestedScroll(fixture, -direction.offset)
            fixture.layout.onStopNestedScroll(fixture.content, ViewCompat.TYPE_TOUCH)

            assertEquals(0, consumed[1])
            assertIdle(fixture)
        }
    }

    @Test
    fun boundaryCallbackHidingIndicator_doesNotConsumeNestedDrag() {
        for (direction: Direction in Direction.entries) {
            val fixture: Fixture = createFixture()
            fixture.layout.setScrollBoundaryCallback { _, _, _ ->
                fixture.indicator(direction).visibility = View.GONE
                false
            }
            startNestedScroll(fixture)

            assertEquals(0, nestedScroll(fixture, -direction.offset)[1])
            fixture.layout.onStopNestedScroll(fixture.content, ViewCompat.TYPE_TOUCH)

            assertIdle(fixture)
        }
    }

    @Test
    fun goneFooter_blocksAutomaticLoadForTouchAndNonTouchScroll() {
        for (type: Int in intArrayOf(ViewCompat.TYPE_TOUCH, ViewCompat.TYPE_NON_TOUCH)) {
            for (unconsumed: Int in intArrayOf(0, 20)) {
                val fixture: Fixture = createFixture()
                fixture.layout.autoLoadMoreEnabled = true
                fixture.footer.visibility = View.GONE
                startNestedScroll(fixture, type)

                val consumed: IntArray = nestedScroll(fixture, unconsumed, type, dyConsumed = 20)
                fixture.layout.onStopNestedScroll(fixture.content, type)

                assertEquals(0, consumed[1])
                assertIdle(fixture)
            }
        }
    }

    @Test
    fun hidingIndicatorBeforeDirectRelease_cancelsUnsubmittedDrag() {
        for (direction: Direction in Direction.entries) {
            val fixture: Fixture = createFixture()
            sendTouch(fixture.layout, MotionEvent.ACTION_DOWN, INITIAL_Y)
            sendTouch(fixture.layout, MotionEvent.ACTION_MOVE, INITIAL_Y + direction.offset)
            assertEquals(direction.releaseState, fixture.layout.state)

            fixture.indicator(direction).visibility = View.GONE
            sendTouch(fixture.layout, MotionEvent.ACTION_UP, INITIAL_Y + direction.offset)

            assertIdle(fixture)
        }
    }

    @Test
    fun hidingIndicatorBeforeNestedRelease_cancelsUnsubmittedDrag() {
        for (direction: Direction in Direction.entries) {
            val fixture: Fixture = createFixture()
            startNestedScroll(fixture)
            nestedScroll(fixture, -direction.offset)
            assertEquals(direction.releaseState, fixture.layout.state)

            fixture.indicator(direction).visibility = View.GONE
            fixture.layout.onStopNestedScroll(fixture.content, ViewCompat.TYPE_TOUCH)

            assertIdle(fixture)
        }
    }

    @Test
    fun hidingIndicatorDuringDrag_blocksFurtherExpansion() {
        for (direction: Direction in Direction.entries) {
            for (nested: Boolean in listOf(false, true)) {
                val fixture: Fixture = createFixture()
                if (nested) {
                    startNestedScroll(fixture)
                    nestedScroll(fixture, -direction.offset)
                } else {
                    sendTouch(fixture.layout, MotionEvent.ACTION_DOWN, INITIAL_Y)
                    sendTouch(fixture.layout, MotionEvent.ACTION_MOVE, INITIAL_Y + direction.offset)
                }
                val initialOffset: Int = fixture.layout.currentOffset
                fixture.indicator(direction).visibility = View.GONE

                if (nested) {
                    assertEquals(0, nestedScroll(fixture, -direction.offset)[1])
                    fixture.layout.onStopNestedScroll(fixture.content, ViewCompat.TYPE_TOUCH)
                } else {
                    sendTouch(fixture.layout, MotionEvent.ACTION_MOVE, INITIAL_Y + direction.offset * 2)
                    assertTrue(kotlin.math.abs(fixture.layout.currentOffset) <= kotlin.math.abs(initialOffset))
                    sendTouch(fixture.layout, MotionEvent.ACTION_UP, INITIAL_Y + direction.offset * 2)
                }

                assertIdle(fixture)
            }
        }
    }

    @Test
    fun hidingIndicatorInBusyStateCallback_doesNotSubmitGestureAction() {
        for (direction: Direction in Direction.entries) {
            for (nested: Boolean in listOf(false, true)) {
                val fixture: Fixture = createFixture()
                var hidden: Boolean = false
                fixture.header.onState = { state: KitRefreshLoadLayout.RefreshLoadState ->
                    if (state == direction.busyState) {
                        fixture.indicator(direction).visibility = View.GONE
                        hidden = true
                    }
                }

                if (nested) {
                    startNestedScroll(fixture)
                    nestedScroll(fixture, -direction.offset)
                    fixture.layout.onStopNestedScroll(fixture.content, ViewCompat.TYPE_TOUCH)
                } else {
                    directDrag(fixture, direction)
                }

                assertTrue(hidden)
                assertIdle(fixture)
            }
        }
    }

    @Test
    fun hidingFooterInBusyStateCallback_doesNotSubmitAutomaticLoad() {
        for (type: Int in intArrayOf(ViewCompat.TYPE_TOUCH, ViewCompat.TYPE_NON_TOUCH)) {
            val fixture: Fixture = createFixture()
            fixture.layout.autoLoadMoreEnabled = true
            var hidden: Boolean = false
            fixture.header.onState = { state: KitRefreshLoadLayout.RefreshLoadState ->
                if (state == KitRefreshLoadLayout.RefreshLoadState.LoadingMore) {
                    fixture.footer.visibility = View.GONE
                    hidden = true
                }
            }
            startNestedScroll(fixture, type)

            nestedScroll(fixture, 0, type, dyConsumed = 20)

            assertTrue(hidden)
            assertIdle(fixture)
        }
    }

    @Test
    fun hidingIndicatorInInitialBusyOffsetCallback_doesNotSubmitAction() {
        for (direction: Direction in Direction.entries) {
            val fixture: Fixture = createFixture()
            var hidden: Boolean = false
            fixture.indicator(direction).onOffset = { offset: Int ->
                if (fixture.layout.state == direction.busyState && offset != 0) {
                    fixture.indicator(direction).visibility = View.GONE
                    hidden = true
                }
            }

            directDrag(fixture, direction)

            assertTrue(hidden)
            assertIdle(fixture)
        }
    }

    @Test
    fun invisibleIndicator_preservesGestureSupport() {
        for (direction: Direction in Direction.entries) {
            val fixture: Fixture = createFixture()
            fixture.indicator(direction).visibility = View.INVISIBLE

            directDrag(fixture, direction)

            assertEquals(direction.busyState, fixture.layout.state)
            assertEquals(listOf(direction.action), fixture.actions)
        }
    }

    @Test
    fun visibleIndicator_restoresGestureWithoutChangingDirectionSwitch() {
        for (direction: Direction in Direction.entries) {
            val fixture: Fixture = createFixture()
            fixture.indicator(direction).visibility = View.GONE
            directDrag(fixture, direction)
            assertIdle(fixture)

            fixture.indicator(direction).visibility = View.VISIBLE
            measureAndLayout(fixture.layout)
            directDrag(fixture, direction)

            assertEquals(direction.busyState, fixture.layout.state)
            assertEquals(listOf(direction.action), fixture.actions)
        }
    }

    @Test
    fun visibleFooter_restoresAutomaticLoad() {
        for (type: Int in intArrayOf(ViewCompat.TYPE_TOUCH, ViewCompat.TYPE_NON_TOUCH)) {
            val fixture: Fixture = createFixture()
            fixture.layout.autoLoadMoreEnabled = true
            fixture.footer.visibility = View.GONE
            startNestedScroll(fixture, type)
            nestedScroll(fixture, 0, type, dyConsumed = 20)
            assertIdle(fixture)

            fixture.footer.visibility = View.VISIBLE
            measureAndLayout(fixture.layout)
            nestedScroll(fixture, 0, type, dyConsumed = 20)

            assertEquals(KitRefreshLoadLayout.RefreshLoadState.LoadingMore, fixture.layout.state)
            assertEquals(listOf(KitRefreshLoadLayout.RefreshLoadAction.LoadMore), fixture.actions)
        }
    }

    @Test
    fun goneIndicator_doesNotDisableOppositeDirection() {
        for (direction: Direction in Direction.entries) {
            val fixture: Fixture = createFixture()
            fixture.indicator(direction.opposite).visibility = View.GONE

            directDrag(fixture, direction)

            assertEquals(direction.busyState, fixture.layout.state)
            assertEquals(listOf(direction.action), fixture.actions)
        }
    }

    @Test
    fun goneIndicator_preservesAlreadySubmittedTask() {
        for (direction: Direction in Direction.entries) {
            val fixture: Fixture = createFixture()
            directDrag(fixture, direction)
            assertEquals(listOf(direction.action), fixture.actions)

            fixture.indicator(direction).visibility = View.GONE
            measureAndLayout(fixture.layout)
            startNestedScroll(fixture)
            nestedScroll(fixture, -direction.offset)
            fixture.layout.onStopNestedScroll(fixture.content, ViewCompat.TYPE_TOUCH)

            assertEquals(direction.busyState, fixture.layout.state)
            assertEquals(listOf(direction.action), fixture.actions)
            fixture.layout.setActiveAction(null)
            assertEquals(KitRefreshLoadLayout.RefreshLoadState.Idle, fixture.layout.state)
        }
    }

    @Test
    fun goneBusyIndicator_doesNotConsumePreOrPostScroll() {
        for (direction: Direction in Direction.entries) {
            for (type: Int in intArrayOf(ViewCompat.TYPE_TOUCH, ViewCompat.TYPE_NON_TOUCH)) {
                val fixture: Fixture = createFixture()
                fixture.layout.setActiveAction(direction.action)
                startNestedScroll(fixture, type)
                fixture.layout.onNestedPreScroll(fixture.content, 0, direction.offset / 4, IntArray(2), type)
                assertEquals(direction.offset / 4, fixture.layout.currentOffset)
                fixture.indicator(direction).visibility = View.GONE

                for (dy: Int in intArrayOf(-10, 10)) {
                    val preConsumed = IntArray(2)
                    fixture.layout.onNestedPreScroll(fixture.content, 0, dy, preConsumed, type)
                    assertEquals(0, preConsumed[1])
                    assertEquals(0, nestedScroll(fixture, dy, type)[1])
                    assertEquals(direction.offset / 4, fixture.layout.currentOffset)
                }

                assertEquals(direction.busyState, fixture.layout.state)
                assertTrue(fixture.actions.isEmpty())
                fixture.layout.setActiveAction(null)
                assertIdle(fixture)
            }
        }
    }

    @Test
    @Config(sdk = [24])
    fun goneBusyIndicator_doesNotRecordFlingForLaterVisibility() {
        for (direction: Direction in Direction.entries) {
            val fixture: Fixture = createFixture()
            fixture.layout.setActiveAction(direction.action)
            startNestedScroll(fixture)
            fixture.layout.onNestedPreScroll(fixture.content, 0, direction.offset / 2, IntArray(2), ViewCompat.TYPE_TOUCH)
            assertEquals(0, fixture.layout.currentOffset)
            fixture.indicator(direction).visibility = View.GONE

            assertFalse(fixture.layout.onNestedPreFling(fixture.content, 0f, -direction.offset * 75f))
            startNestedScroll(fixture, ViewCompat.TYPE_NON_TOUCH)
            assertEquals(0, nestedScroll(fixture, -direction.offset / 80, ViewCompat.TYPE_NON_TOUCH)[1])
            advanceIndicatorFling(fixture.layout)
            assertEquals(0, fixture.layout.currentOffset)

            fixture.indicator(direction).visibility = View.VISIBLE
            measureAndLayout(fixture.layout)
            nestedScroll(fixture, -direction.offset / 80, ViewCompat.TYPE_NON_TOUCH)
            assertEquals(direction.offset / 80, fixture.layout.currentOffset)
            advanceIndicatorFling(fixture.layout)

            assertEquals(direction.offset / 80, fixture.layout.currentOffset)
            assertEquals(direction.busyState, fixture.layout.state)
            assertTrue(fixture.actions.isEmpty())
        }
    }

    @Test
    @Config(sdk = [24])
    fun hidingBusyIndicator_stopsCurrentFlingAndPreservesPositionAndTask() {
        for (direction: Direction in Direction.entries) {
            val fixture: Fixture = createFixture()
            fixture.layout.setActiveAction(direction.action)
            startNestedScroll(fixture)
            fixture.layout.onNestedPreScroll(fixture.content, 0, direction.offset / 2, IntArray(2), ViewCompat.TYPE_TOUCH)
            assertEquals(0, fixture.layout.currentOffset)
            assertFalse(fixture.layout.onNestedPreFling(fixture.content, 0f, -direction.offset * 75f))
            startNestedScroll(fixture, ViewCompat.TYPE_NON_TOUCH)
            nestedScroll(fixture, -direction.offset / 80, ViewCompat.TYPE_NON_TOUCH)
            assertEquals(direction.offset / 80, fixture.layout.currentOffset)

            fixture.indicator(direction).visibility = View.GONE
            advanceIndicatorFling(fixture.layout)

            assertEquals(direction.offset / 80, fixture.layout.currentOffset)
            assertEquals(direction.busyState, fixture.layout.state)
            assertTrue(fixture.actions.isEmpty())
        }
    }

    @Test
    fun goneIndicator_allowsBusinessStateSyncAndPositionsWhenVisible() {
        for (direction: Direction in Direction.entries) {
            val fixture: Fixture = createFixture()
            fixture.indicator(direction).visibility = View.GONE
            measureAndLayout(fixture.layout)

            fixture.layout.setActiveAction(direction.action)

            assertEquals(direction.busyState, fixture.layout.state)
            assertEquals(0, fixture.layout.currentOffset)
            assertTrue(fixture.actions.isEmpty())

            fixture.indicator(direction).visibility = View.VISIBLE
            measureAndLayout(fixture.layout)

            assertEquals(direction.busyState, fixture.layout.state)
            assertEquals(direction.offset / 2, fixture.layout.currentOffset)
            assertTrue(fixture.actions.isEmpty())
        }
    }

    private fun createFixture(): Fixture {
        val context: Context = RuntimeEnvironment.getApplication()
        val layout = KitRefreshLoadLayout(context).apply {
            dragResistance = 1f
            refreshTriggerDistance = 30
            loadMoreTriggerDistance = 30
            maxDragDistance = 10_000
            animationDurationMillis = 0L
        }
        val content = View(context)
        val header = RecordingIndicator(context)
        val footer = RecordingIndicator(context)
        layout.addView(content)
        layout.addView(header, roleParams(KitRefreshLoadLayout.ChildRole.Header))
        layout.addView(footer, roleParams(KitRefreshLoadLayout.ChildRole.Footer))
        val actions: MutableList<KitRefreshLoadLayout.RefreshLoadAction> = mutableListOf()
        layout.setOnRefreshLoadListener(actions::add)
        measureAndLayout(layout)
        return Fixture(layout, content, header, footer, actions)
    }

    private fun roleParams(role: KitRefreshLoadLayout.ChildRole): KitRefreshLoadLayout.LayoutParams =
        KitRefreshLoadLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 40).apply { this.role = role }

    private fun measureAndLayout(layout: KitRefreshLoadLayout) {
        layout.measure(
            View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY),
        )
        layout.layout(0, 0, 320, 640)
    }

    private fun directDrag(fixture: Fixture, direction: Direction) {
        sendTouch(fixture.layout, MotionEvent.ACTION_DOWN, INITIAL_Y)
        sendTouch(fixture.layout, MotionEvent.ACTION_MOVE, INITIAL_Y + direction.offset)
        sendTouch(fixture.layout, MotionEvent.ACTION_UP, INITIAL_Y + direction.offset)
    }

    private fun sendTouch(layout: KitRefreshLoadLayout, action: Int, y: Float) {
        val event: MotionEvent = MotionEvent.obtain(0L, 0L, action, 100f, y, 0)
        try {
            layout.onTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun interceptTouch(layout: KitRefreshLoadLayout, action: Int, y: Float): Boolean {
        val event: MotionEvent = MotionEvent.obtain(0L, 0L, action, 100f, y, 0)
        return try {
            layout.onInterceptTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun startNestedScroll(fixture: Fixture, type: Int = ViewCompat.TYPE_TOUCH) {
        assertTrue(fixture.layout.onStartNestedScroll(fixture.content, fixture.content, ViewCompat.SCROLL_AXIS_VERTICAL, type))
        fixture.layout.onNestedScrollAccepted(fixture.content, fixture.content, ViewCompat.SCROLL_AXIS_VERTICAL, type)
    }

    private fun nestedScroll(
        fixture: Fixture,
        dyUnconsumed: Int,
        type: Int = ViewCompat.TYPE_TOUCH,
        dyConsumed: Int = 0,
    ): IntArray {
        val consumed: IntArray = IntArray(2)
        fixture.layout.onNestedScroll(fixture.content, 0, dyConsumed, 0, dyUnconsumed, type, consumed)
        return consumed
    }

    private fun assertIdle(fixture: Fixture) {
        assertEquals(KitRefreshLoadLayout.RefreshLoadState.Idle, fixture.layout.state)
        assertEquals(0, fixture.layout.currentOffset)
        assertTrue(fixture.actions.isEmpty())
    }

    private fun advanceIndicatorFling(layout: KitRefreshLoadLayout) {
        repeat(5) {
            ShadowSystemClock.advanceBy(16, TimeUnit.MILLISECONDS)
            layout.computeScroll()
        }
    }

    /** 通过真实组件状态回调模拟业务切换可见性. */
    private class RecordingIndicator(context: Context) : View(context), KitRefreshLoadComponent {
        var onState: ((KitRefreshLoadLayout.RefreshLoadState) -> Unit)? = null
        var onOffset: ((Int) -> Unit)? = null

        override fun onRefreshLoadStateChanged(layout: KitRefreshLoadLayout, state: KitRefreshLoadLayout.RefreshLoadState) {
            onState?.invoke(state)
        }

        override fun onRefreshLoadOffsetChanged(layout: KitRefreshLoadLayout, offset: Int, triggerDistance: Int, holdDistance: Int) {
            onOffset?.invoke(offset)
        }
    }

    /** 使用同一组夹具对称验证刷新和加载入口. */
    private data class Fixture(
        val layout: KitRefreshLoadLayout,
        val content: View,
        val header: RecordingIndicator,
        val footer: RecordingIndicator,
        val actions: MutableList<KitRefreshLoadLayout.RefreshLoadAction>,
    ) {
        fun indicator(direction: Direction): RecordingIndicator = when (direction) {
            Direction.REFRESH -> header
            Direction.LOAD_MORE -> footer
        }
    }

    /** 偏移方向始终与对应动作、状态成对. */
    private enum class Direction(
        val offset: Int,
        val action: KitRefreshLoadLayout.RefreshLoadAction,
        val releaseState: KitRefreshLoadLayout.RefreshLoadState,
        val busyState: KitRefreshLoadLayout.RefreshLoadState,
    ) {
        REFRESH(
            80,
            KitRefreshLoadLayout.RefreshLoadAction.Refresh,
            KitRefreshLoadLayout.RefreshLoadState.ReleaseToRefresh,
            KitRefreshLoadLayout.RefreshLoadState.Refreshing,
        ),
        LOAD_MORE(
            -80,
            KitRefreshLoadLayout.RefreshLoadAction.LoadMore,
            KitRefreshLoadLayout.RefreshLoadState.ReleaseToLoadMore,
            KitRefreshLoadLayout.RefreshLoadState.LoadingMore,
        );

        val opposite: Direction
            get() = if (this == REFRESH) LOAD_MORE else REFRESH
    }

    private companion object {
        const val INITIAL_Y: Float = 200f
    }
}
