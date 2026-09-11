package com.whisper.kit.widget.refreshloadlayout

import android.app.Activity
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.widget.FrameLayout
import android.widget.ScrollView
import androidx.core.view.ViewCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowSystemClock
import java.util.concurrent.TimeUnit

/**
 * 验证 Framework 嵌套滚动桥接及其与带类型滚动会话的协作.
 *
 * @author whisper
 * @since 2026/09/11
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 34])
@LooperMode(LooperMode.Mode.PAUSED)
class KitRefreshLoadLayoutLegacyNestedScrollTest {

    @Test
    fun frameworkScrollView_reachesEitherBoundaryAndCommitsContinuousPullOnRelease() {
        for (direction: Direction in Direction.entries) {
            val fixture: Fixture = createScrollFixture(direction)

            dragScrollViewToBoundary(fixture, direction)
            dispatchTouch(fixture.layout, MotionEvent.ACTION_UP, 250f + direction.offsetSign * 220f, 1_000L)

            assertEquals(listOf(direction.action), fixture.actions)
            assertEquals(direction.busyState, fixture.layout.state)
            assertEquals(direction.offsetSign * INDICATOR_HEIGHT, fixture.layout.currentOffset)
            assertEquals(ViewCompat.SCROLL_AXIS_NONE, fixture.layout.nestedScrollAxes)
        }
    }

    @Test
    fun frameworkScrollView_cancelAtEitherBoundaryRetractsWithoutSubmitting() {
        for (direction: Direction in Direction.entries) {
            val fixture: Fixture = createScrollFixture(direction)

            dragScrollViewToBoundary(fixture, direction)
            dispatchTouch(fixture.layout, MotionEvent.ACTION_CANCEL, 250f + direction.offsetSign * 220f, 1_000L)

            assertTrue(fixture.actions.isEmpty())
            assertEquals(KitRefreshLoadLayout.RefreshLoadState.Idle, fixture.layout.state)
            assertEquals(0, fixture.layout.currentOffset)
            assertEquals(ViewCompat.SCROLL_AXIS_NONE, fixture.layout.nestedScrollAxes)
        }
    }

    @Test
    fun legacyPreScroll_hidesBusyIndicatorAndAccumulatesOnlyActualConsumption() {
        for (direction: Direction in Direction.entries) {
            val fixture: Fixture = createFixture()
            startLegacyScroll(fixture)
            fixture.parent.onNestedScroll(fixture.content, 0, 0, 0, -direction.offsetSign * 80)
            fixture.parent.onStopNestedScroll(fixture.content)
            assertEquals(direction.busyState, fixture.layout.state)
            startLegacyScroll(fixture)
            val consumed: IntArray = intArrayOf(7, 11)

            fixture.parent.onNestedPreScroll(fixture.content, 0, direction.offsetSign * 100, consumed)

            assertEquals(7, consumed[0])
            assertEquals(11 + direction.offsetSign * INDICATOR_HEIGHT, consumed[1])
            assertEquals(0, fixture.layout.currentOffset)
            assertEquals(direction.busyState, fixture.layout.state)
            assertEquals(listOf(direction.action), fixture.actions)
            fixture.parent.onStopNestedScroll(fixture.content)
            assertEquals(listOf(direction.action), fixture.actions)
        }
    }

    @Test
    fun legacyPreScroll_collapsesUncommittedPullBeforeContentScrolls() {
        for (direction: Direction in Direction.entries) {
            val fixture: Fixture = createFixture()
            startLegacyScroll(fixture)
            fixture.parent.onNestedScroll(fixture.content, 0, 0, 0, -direction.offsetSign * 25)
            assertEquals(direction.offsetSign * 25, fixture.layout.currentOffset)
            val consumed: IntArray = intArrayOf(3, 5)

            fixture.parent.onNestedPreScroll(fixture.content, 0, direction.offsetSign * 60, consumed)
            fixture.parent.onStopNestedScroll(fixture.content)

            assertEquals(3, consumed[0])
            assertEquals(5 + direction.offsetSign * 25, consumed[1])
            assertEquals(0, fixture.layout.currentOffset)
            assertEquals(KitRefreshLoadLayout.RefreshLoadState.Idle, fixture.layout.state)
            assertTrue(fixture.actions.isEmpty())
        }
    }

    @Test
    fun legacyAndNonTouchSessions_trackAxesUntilEachSessionStops() {
        for (stopLegacyFirst: Boolean in listOf(true, false)) {
            val fixture: Fixture = createFixture()
            fixture.layout.autoLoadMoreEnabled = true
            startLegacyScroll(fixture)
            startNonTouchScroll(fixture)
            val combinedAxes: Int = ViewCompat.SCROLL_AXIS_HORIZONTAL or ViewCompat.SCROLL_AXIS_VERTICAL
            assertEquals(combinedAxes, fixture.layout.nestedScrollAxes)

            if (stopLegacyFirst) {
                fixture.parent.onStopNestedScroll(fixture.content)
                assertEquals(combinedAxes, fixture.layout.nestedScrollAxes)
                fixture.layout.onStopNestedScroll(fixture.content, ViewCompat.TYPE_NON_TOUCH)
            } else {
                fixture.layout.onStopNestedScroll(fixture.content, ViewCompat.TYPE_NON_TOUCH)
                assertEquals(ViewCompat.SCROLL_AXIS_VERTICAL, fixture.layout.nestedScrollAxes)
                fixture.parent.onStopNestedScroll(fixture.content)
            }

            assertEquals(ViewCompat.SCROLL_AXIS_NONE, fixture.layout.nestedScrollAxes)
            assertTrue(fixture.actions.isEmpty())
        }
    }

    @Test
    fun legacyStart_rejectsHorizontalOnlyAndAcceptsVerticalAxes() {
        val fixture: Fixture = createFixture()

        assertFalse(fixture.parent.onStartNestedScroll(fixture.content, fixture.content, ViewCompat.SCROLL_AXIS_HORIZONTAL))
        assertFalse(fixture.parent.onStartNestedScroll(fixture.content, fixture.content, ViewCompat.SCROLL_AXIS_NONE))
        assertTrue(fixture.parent.onStartNestedScroll(fixture.content, fixture.content, ViewCompat.SCROLL_AXIS_VERTICAL))
        assertEquals(ViewCompat.SCROLL_AXIS_NONE, fixture.layout.nestedScrollAxes)
    }

    @Test
    fun disabledLegacyStart_rejectsScrollAndDoesNotConsumeOldSession() {
        val fixture: Fixture = createFixture()
        startLegacyScroll(fixture)
        fixture.parent.onNestedScroll(fixture.content, 0, 0, 0, -80)

        fixture.layout.isEnabled = false
        val consumed: IntArray = intArrayOf(3, 5)
        assertFalse(fixture.parent.onStartNestedScroll(fixture.content, fixture.content, ViewCompat.SCROLL_AXIS_VERTICAL))
        fixture.parent.onNestedPreScroll(fixture.content, 0, 80, consumed)
        fixture.parent.onNestedScroll(fixture.content, 0, 0, 0, -80)
        fixture.parent.onStopNestedScroll(fixture.content)

        assertEquals(3, consumed[0])
        assertEquals(5, consumed[1])
        assertEquals(0, fixture.layout.currentOffset)
        assertEquals(KitRefreshLoadLayout.RefreshLoadState.Idle, fixture.layout.state)
        assertTrue(fixture.actions.isEmpty())
        assertEquals(ViewCompat.SCROLL_AXIS_NONE, fixture.layout.nestedScrollAxes)
    }

    @Test
    fun completion_blocksOldLegacyEventsAndAcceptsNewLegacySession() {
        val fixture: Fixture = createFixture()
        startLegacyScroll(fixture)
        fixture.parent.onNestedScroll(fixture.content, 0, 0, 0, -80)
        fixture.parent.onStopNestedScroll(fixture.content)
        fixture.layout.finishRefresh(animate = false)

        fixture.parent.onNestedScroll(fixture.content, 0, 0, 0, -80)
        fixture.parent.onStopNestedScroll(fixture.content)

        assertEquals(1, fixture.actions.size)
        assertEquals(0, fixture.layout.currentOffset)
        startLegacyScroll(fixture)
        fixture.parent.onNestedScroll(fixture.content, 0, 0, 0, -80)
        fixture.parent.onStopNestedScroll(fixture.content)
        assertEquals(List(2) { KitRefreshLoadLayout.RefreshLoadAction.Refresh }, fixture.actions)
    }

    @Test
    fun legacyStop_discardsFlingVelocityBeforeIndependentNonTouchSession() {
        for (direction: Direction in Direction.entries) {
            val fixture: Fixture = createBusyLegacyFixtureWithHiddenIndicator(direction)
            assertFalse(fixture.parent.onNestedPreFling(fixture.content, 0f, -direction.offsetSign * 6_000f))

            fixture.parent.onStopNestedScroll(fixture.content)
            startNonTouchScroll(fixture)
            fixture.layout.onNestedPreScroll(fixture.content, 0, -direction.offsetSign, IntArray(2), ViewCompat.TYPE_NON_TOUCH)
            advanceIndicatorFling(fixture.layout)

            assertEquals(direction.offsetSign, fixture.layout.currentOffset)
            assertEquals(direction.busyState, fixture.layout.state)
            assertEquals(listOf(direction.action), fixture.actions)
        }
    }

    @Test
    fun legacyStop_preservesFlingVelocityAlreadyHandedToActiveNonTouchSession() {
        for (direction: Direction in Direction.entries) {
            val fixture: Fixture = createBusyLegacyFixtureWithHiddenIndicator(direction)
            assertFalse(fixture.parent.onNestedPreFling(fixture.content, 0f, -direction.offsetSign * 6_000f))
            startNonTouchScroll(fixture)

            fixture.parent.onStopNestedScroll(fixture.content)
            fixture.layout.onNestedPreScroll(fixture.content, 0, -direction.offsetSign, IntArray(2), ViewCompat.TYPE_NON_TOUCH)
            advanceIndicatorFling(fixture.layout)

            assertTrue(fixture.layout.currentOffset * direction.offsetSign > 1)
            assertEquals(direction.busyState, fixture.layout.state)
            assertEquals(listOf(direction.action), fixture.actions)
        }
    }

    @Test
    fun detach_clearsLegacyAndNonTouchAxesBeforeReattachment() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val fixture: Fixture = createFixture(context = controller.get())
            val container = FrameLayout(controller.get())
            controller.get().setContentView(container)
            container.addView(fixture.layout)
            assertTrue(fixture.layout.isAttachedToWindow)
            fixture.layout.autoLoadMoreEnabled = true
            startLegacyScroll(fixture)
            startNonTouchScroll(fixture)

            container.removeView(fixture.layout)

            assertEquals(ViewCompat.SCROLL_AXIS_NONE, fixture.layout.nestedScrollAxes)
            container.addView(fixture.layout)
            assertEquals(ViewCompat.SCROLL_AXIS_NONE, fixture.layout.nestedScrollAxes)
            startLegacyScroll(fixture)
            assertEquals(ViewCompat.SCROLL_AXIS_VERTICAL, fixture.layout.nestedScrollAxes)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun createScrollFixture(direction: Direction): Fixture {
        val context: Context = RuntimeEnvironment.getApplication()
        val scrollView: ScrollView = ScrollView(context).apply {
            isNestedScrollingEnabled = true
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(
                View(context).apply { minimumHeight = CONTENT_HEIGHT },
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
        }
        val fixture: Fixture = createFixture(scrollView)
        val maximumScroll: Int = scrollView.getChildAt(0).height - scrollView.height
        assertTrue(maximumScroll > 200)
        scrollView.scrollTo(0, if (direction == Direction.Refresh) 100 else maximumScroll - 100)
        assertTrue(scrollView.canScrollVertically(-1))
        assertTrue(scrollView.canScrollVertically(1))
        return fixture
    }

    private fun createBusyLegacyFixtureWithHiddenIndicator(direction: Direction): Fixture {
        val fixture: Fixture = createFixture()
        startLegacyScroll(fixture)
        fixture.parent.onNestedScroll(fixture.content, 0, 0, 0, -direction.offsetSign * 80)
        fixture.parent.onStopNestedScroll(fixture.content)
        startLegacyScroll(fixture)
        fixture.parent.onNestedPreScroll(fixture.content, 0, direction.offsetSign * INDICATOR_HEIGHT, IntArray(2))
        assertEquals(0, fixture.layout.currentOffset)
        return fixture
    }

    private fun advanceIndicatorFling(layout: KitRefreshLoadLayout) {
        repeat(5) {
            ShadowSystemClock.advanceBy(16, TimeUnit.MILLISECONDS)
            layout.computeScroll()
        }
    }

    private fun dragScrollViewToBoundary(fixture: Fixture, direction: Direction) {
        dispatchTouch(fixture.layout, MotionEvent.ACTION_DOWN, 250f, 0L)
        assertTrue(fixture.content.hasNestedScrollingParent())
        assertEquals(ViewCompat.SCROLL_AXIS_VERTICAL, fixture.layout.nestedScrollAxes)
        dispatchTouch(fixture.layout, MotionEvent.ACTION_MOVE, 250f + direction.offsetSign * 60f, 100L)
        assertEquals(0, fixture.layout.currentOffset)
        assertTrue(fixture.content.canScrollVertically(-direction.offsetSign))

        dispatchTouch(fixture.layout, MotionEvent.ACTION_MOVE, 250f + direction.offsetSign * 220f, 200L)

        assertFalse(fixture.content.canScrollVertically(-direction.offsetSign))
        assertTrue(fixture.layout.currentOffset * direction.offsetSign > TRIGGER_DISTANCE)
        assertTrue(fixture.actions.isEmpty())
    }

    private fun createFixture(
        content: View? = null,
        context: Context = RuntimeEnvironment.getApplication(),
    ): Fixture {
        val child: View = content ?: View(context)
        val layout: KitRefreshLoadLayout = KitRefreshLoadLayout(context).apply {
            animationDurationMillis = 0L
            dragResistance = 1f
            maxDragDistance = 10_000
            refreshTriggerDistance = TRIGGER_DISTANCE
            loadMoreTriggerDistance = TRIGGER_DISTANCE
            addView(View(context), indicatorParams(KitRefreshLoadLayout.ChildRole.Header))
            addView(child, KitRefreshLoadLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(View(context), indicatorParams(KitRefreshLoadLayout.ChildRole.Footer))
        }
        val actions: MutableList<KitRefreshLoadLayout.RefreshLoadAction> = mutableListOf()
        layout.setOnRefreshLoadListener { action: KitRefreshLoadLayout.RefreshLoadAction -> actions += action }
        layout.measure(
            View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY),
        )
        layout.layout(0, 0, 300, 500)
        return Fixture(layout, child, actions)
    }

    private fun indicatorParams(childRole: KitRefreshLoadLayout.ChildRole): KitRefreshLoadLayout.LayoutParams =
        KitRefreshLoadLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, INDICATOR_HEIGHT).apply {
            role = childRole
        }

    private fun startLegacyScroll(fixture: Fixture) {
        assertTrue(fixture.parent.onStartNestedScroll(fixture.content, fixture.content, ViewCompat.SCROLL_AXIS_VERTICAL))
        fixture.parent.onNestedScrollAccepted(fixture.content, fixture.content, ViewCompat.SCROLL_AXIS_VERTICAL)
    }

    private fun startNonTouchScroll(fixture: Fixture) {
        val axes: Int = ViewCompat.SCROLL_AXIS_HORIZONTAL or ViewCompat.SCROLL_AXIS_VERTICAL
        assertTrue(fixture.layout.onStartNestedScroll(fixture.content, fixture.content, axes, ViewCompat.TYPE_NON_TOUCH))
        fixture.layout.onNestedScrollAccepted(fixture.content, fixture.content, axes, ViewCompat.TYPE_NON_TOUCH)
    }

    private fun dispatchTouch(layout: KitRefreshLoadLayout, action: Int, y: Float, eventTime: Long) {
        val event: MotionEvent = MotionEvent.obtain(0L, eventTime, action, 150f, y, 0)
        try {
            layout.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    /** 测试所需的真实容器、内容和业务通知. */
    private data class Fixture(
        val layout: KitRefreshLoadLayout,
        val content: View,
        val actions: MutableList<KitRefreshLoadLayout.RefreshLoadAction>,
    ) {

        val parent: ViewParent = layout
    }

    /** 同一组边界断言覆盖刷新和加载两种方向. */
    private enum class Direction(
        val offsetSign: Int,
        val action: KitRefreshLoadLayout.RefreshLoadAction,
        val busyState: KitRefreshLoadLayout.RefreshLoadState,
    ) {
        Refresh(1, KitRefreshLoadLayout.RefreshLoadAction.Refresh, KitRefreshLoadLayout.RefreshLoadState.Refreshing),
        LoadMore(-1, KitRefreshLoadLayout.RefreshLoadAction.LoadMore, KitRefreshLoadLayout.RefreshLoadState.LoadingMore),
    }

    private companion object {

        private const val INDICATOR_HEIGHT: Int = 60
        private const val TRIGGER_DISTANCE: Int = 40
        private const val CONTENT_HEIGHT: Int = 2_000
    }
}
