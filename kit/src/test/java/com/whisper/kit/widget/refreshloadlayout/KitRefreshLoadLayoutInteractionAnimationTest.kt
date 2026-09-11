package com.whisper.kit.widget.refreshloadlayout

import android.app.Activity
import android.content.Context
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.widget.NestedScrollView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowSystemClock
import java.util.concurrent.TimeUnit

/**
 * 验证触摸与指示器动画交接, 以及取消回调重入业务显示同步的边界.
 *
 * @author whisper
 * @since 2026/09/11
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
@LooperMode(LooperMode.Mode.PAUSED)
class KitRefreshLoadLayoutInteractionAnimationTest {

    @Test
    fun newDown_stopsRefreshingIndicatorFlingAndPreservesBusyState() {
        verifyNewDownStopsFling(KitRefreshLoadLayout.RefreshLoadAction.Refresh)
    }

    @Test
    fun newDown_stopsLoadingMoreIndicatorFlingAndPreservesBusyState() {
        verifyNewDownStopsFling(KitRefreshLoadLayout.RefreshLoadAction.LoadMore)
    }

    @Test
    fun directCancel_preservesReentrantBusinessSynchronizationAnimationInBothDirections() {
        verifyCancellationPreservesNewAction(nested = false)
    }

    @Test
    fun nestedCancel_preservesReentrantBusinessSynchronizationAnimationInBothDirections() {
        verifyCancellationPreservesNewAction(nested = true)
    }

    @Test
    fun childCancelChangingThreshold_stillRetractsUncommittedDragInBothDirections() {
        verifyChildCancellationCallback(synchronizeBusyAction = false)
    }

    @Test
    fun childCancelSynchronizingBusyAction_preservesNewAnimationInBothDirections() {
        verifyChildCancellationCallback(synchronizeBusyAction = true)
    }

    private fun verifyChildCancellationCallback(synchronizeBusyAction: Boolean) {
        for (pullDown: Boolean in listOf(true, false)) {
            withFixture(nested = true) { fixture: Fixture ->
                val layout: KitRefreshLoadLayout = fixture.layout
                val content: NestedScrollView = fixture.content as NestedScrollView
                val dragDirection: Int = if (pullDown) 1 else -1
                val synchronizedAction: KitRefreshLoadLayout.RefreshLoadAction = if (pullDown) {
                    KitRefreshLoadLayout.RefreshLoadAction.LoadMore
                } else {
                    KitRefreshLoadLayout.RefreshLoadAction.Refresh
                }
                layout.refreshTriggerDistance = 500
                layout.loadMoreTriggerDistance = 500
                if (!pullDown) content.scrollTo(0, 2_000)
                var cancellationCount: Int = 0
                content.setOnTouchListener { _: View, event: MotionEvent ->
                    if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
                        cancellationCount++
                        if (synchronizeBusyAction) {
                            layout.setActiveAction(synchronizedAction, animate = true)
                        } else if (pullDown) {
                            layout.refreshTriggerDistance = 1
                            assertEquals(KitRefreshLoadLayout.RefreshLoadState.ReleaseToRefresh, layout.state)
                        } else {
                            layout.loadMoreTriggerDistance = 1
                            assertEquals(KitRefreshLoadLayout.RefreshLoadState.ReleaseToLoadMore, layout.state)
                        }
                    }
                    false
                }
                dispatchTouch(layout, MotionEvent.ACTION_DOWN, 250f)
                assertTrue(content.hasNestedScrollingParent(ViewCompat.TYPE_TOUCH))
                layout.requestDisallowInterceptTouchEvent(true)
                dispatchTouch(layout, MotionEvent.ACTION_MOVE, 250f + dragDirection * 160f)
                assertTrue(layout.currentOffset * dragDirection > 0)
                assertEquals(
                    if (pullDown) KitRefreshLoadLayout.RefreshLoadState.PullingDown else KitRefreshLoadLayout.RefreshLoadState.PullingUp,
                    layout.state,
                )

                dispatchTouch(layout, MotionEvent.ACTION_CANCEL, 250f + dragDirection * 160f)
                shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)

                assertEquals(1, cancellationCount)
                assertFalse(content.hasNestedScrollingParent(ViewCompat.TYPE_TOUCH))
                assertEquals(
                    if (synchronizeBusyAction) busyState(synchronizedAction) else KitRefreshLoadLayout.RefreshLoadState.Idle,
                    layout.state,
                )
                assertEquals(if (synchronizeBusyAction) holdOffset(synchronizedAction) else 0, layout.currentOffset)
                assertTrue(fixture.actions.isEmpty())
            }
        }
    }

    private fun verifyNewDownStopsFling(action: KitRefreshLoadLayout.RefreshLoadAction) {
        withFixture(nested = true) { fixture: Fixture ->
            val layout: KitRefreshLoadLayout = fixture.layout
            val content: NestedScrollView = fixture.content as NestedScrollView
            val direction: Int = if (action == KitRefreshLoadLayout.RefreshLoadAction.Refresh) -1 else 1
            if (direction > 0) content.scrollTo(0, 2_000)
            layout.setActiveAction(action)
            startNestedScroll(fixture, ViewCompat.TYPE_TOUCH)
            layout.onNestedPreScroll(content, 0, holdOffset(action), IntArray(2), ViewCompat.TYPE_TOUCH)
            assertEquals(0, layout.currentOffset)
            assertFalse(layout.onNestedPreFling(content, 0f, direction * 6_000f))
            startNestedScroll(fixture, ViewCompat.TYPE_NON_TOUCH)
            layout.onStopNestedScroll(content, ViewCompat.TYPE_TOUCH)
            layout.onNestedPreScroll(content, 0, direction, IntArray(2), ViewCompat.TYPE_NON_TOUCH)
            layout.onStopNestedScroll(content, ViewCompat.TYPE_NON_TOUCH)
            assertEquals(-direction, layout.currentOffset)

            // 指示器已接管惯性, 用真实子 View 分发下一轮按下, 不直接调用拦截入口.
            dispatchTouch(layout, MotionEvent.ACTION_DOWN, 250f)
            assertTrue(content.hasNestedScrollingParent(ViewCompat.TYPE_TOUCH))
            val offsetAtDown: Int = layout.currentOffset
            repeat(5) {
                ShadowSystemClock.advanceBy(16, TimeUnit.MILLISECONDS)
                layout.computeScroll()
            }

            assertEquals("A new touch must stop the old indicator fling", offsetAtDown, layout.currentOffset)
            assertEquals(busyState(action), layout.state)
            assertTrue(fixture.actions.isEmpty())
            dispatchTouch(layout, MotionEvent.ACTION_CANCEL, 250f)
            assertEquals(busyState(action), layout.state)
        }
    }

    private fun verifyCancellationPreservesNewAction(nested: Boolean) {
        for (action: KitRefreshLoadLayout.RefreshLoadAction in KitRefreshLoadLayout.RefreshLoadAction.entries) {
            withFixture(nested) { fixture: Fixture ->
                val layout: KitRefreshLoadLayout = fixture.layout
                val dragDirection: Int = if (action == KitRefreshLoadLayout.RefreshLoadAction.Refresh) -1 else 1
                if (nested && dragDirection < 0) fixture.content.scrollTo(0, 2_000)
                var synchronizationCount: Int = 0
                fixture.header.onStateChanged = { state: KitRefreshLoadLayout.RefreshLoadState ->
                    if (state == KitRefreshLoadLayout.RefreshLoadState.Settling) {
                        synchronizationCount++
                        layout.setActiveAction(action, animate = true)
                    }
                }
                dispatchTouch(layout, MotionEvent.ACTION_DOWN, 250f)
                if (nested) layout.requestDisallowInterceptTouchEvent(true)
                dispatchTouch(layout, MotionEvent.ACTION_MOVE, 250f + dragDirection * 60f)
                dispatchTouch(layout, MotionEvent.ACTION_MOVE, 250f + dragDirection * 160f)
                assertTrue(layout.currentOffset * dragDirection > 0)

                dispatchTouch(layout, MotionEvent.ACTION_CANCEL, 250f + dragDirection * 160f)
                assertEquals(busyState(action), layout.state)
                shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)

                assertEquals(1, synchronizationCount)
                assertEquals(busyState(action), layout.state)
                assertEquals(holdOffset(action), layout.currentOffset)
                assertEquals(holdOffset(action).toFloat(), fixture.content.translationY, 0f)
                assertTrue(fixture.actions.isEmpty())
            }
        }
    }

    private fun withFixture(nested: Boolean, block: (Fixture) -> Unit) {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val activity: Activity = controller.get()
            val header: StateObserver = StateObserver(activity)
            val content: View = if (nested) {
                NestedScrollView(activity).apply {
                    addView(View(activity), ViewGroup.LayoutParams(300, 2_000))
                }
            } else {
                View(activity).apply { isClickable = true }
            }
            val actions: MutableList<KitRefreshLoadLayout.RefreshLoadAction> = mutableListOf()
            val layout: KitRefreshLoadLayout = KitRefreshLoadLayout(activity).apply {
                dragResistance = 1f
                refreshTriggerDistance = 40
                loadMoreTriggerDistance = 40
                maxDragDistance = 1_000
                animationDurationMillis = 120L
                addView(header, roleParams(KitRefreshLoadLayout.ChildRole.Header, HEADER_HEIGHT))
                addView(content, roleParams(KitRefreshLoadLayout.ChildRole.Content, 500))
                addView(View(activity), roleParams(KitRefreshLoadLayout.ChildRole.Footer, FOOTER_HEIGHT))
                setOnRefreshLoadListener { action: KitRefreshLoadLayout.RefreshLoadAction -> actions += action }
            }
            activity.setContentView(layout)
            layout.measure(
                View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY),
            )
            layout.layout(0, 0, 300, 500)
            block(Fixture(layout, header, content, actions))
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun roleParams(role: KitRefreshLoadLayout.ChildRole, height: Int): KitRefreshLoadLayout.LayoutParams =
        KitRefreshLoadLayout.LayoutParams(300, height).apply { this.role = role }

    private fun startNestedScroll(fixture: Fixture, type: Int) {
        assertTrue(fixture.layout.onStartNestedScroll(fixture.content, fixture.content, ViewCompat.SCROLL_AXIS_VERTICAL, type))
        fixture.layout.onNestedScrollAccepted(fixture.content, fixture.content, ViewCompat.SCROLL_AXIS_VERTICAL, type)
    }

    private fun dispatchTouch(layout: KitRefreshLoadLayout, action: Int, y: Float) {
        val event: MotionEvent = MotionEvent.obtain(0L, 16L, action, 150f, y, 0)
        try {
            assertTrue(layout.dispatchTouchEvent(event))
        } finally {
            event.recycle()
        }
    }

    private fun holdOffset(action: KitRefreshLoadLayout.RefreshLoadAction): Int = when (action) {
        KitRefreshLoadLayout.RefreshLoadAction.Refresh -> HEADER_HEIGHT
        KitRefreshLoadLayout.RefreshLoadAction.LoadMore -> -FOOTER_HEIGHT
    }

    private fun busyState(action: KitRefreshLoadLayout.RefreshLoadAction): KitRefreshLoadLayout.RefreshLoadState = when (action) {
        KitRefreshLoadLayout.RefreshLoadAction.Refresh -> KitRefreshLoadLayout.RefreshLoadState.Refreshing
        KitRefreshLoadLayout.RefreshLoadAction.LoadMore -> KitRefreshLoadLayout.RefreshLoadState.LoadingMore
    }

    /** 在取消导致的状态通知中同步业务任务. */
    private class StateObserver(context: Context) : View(context), KitRefreshLoadComponent {

        var onStateChanged: ((KitRefreshLoadLayout.RefreshLoadState) -> Unit)? = null

        override fun onRefreshLoadStateChanged(layout: KitRefreshLoadLayout, state: KitRefreshLoadLayout.RefreshLoadState) {
            onStateChanged?.invoke(state)
        }

        override fun onRefreshLoadOffsetChanged(layout: KitRefreshLoadLayout, offset: Int, triggerDistance: Int, holdDistance: Int) = Unit
    }

    /** 已附着窗口的布局及可观测请求回调. */
    private data class Fixture(
        val layout: KitRefreshLoadLayout,
        val header: StateObserver,
        val content: View,
        val actions: MutableList<KitRefreshLoadLayout.RefreshLoadAction>,
    )

    private companion object {
        private const val HEADER_HEIGHT: Int = 60
        private const val FOOTER_HEIGHT: Int = 70
    }
}
