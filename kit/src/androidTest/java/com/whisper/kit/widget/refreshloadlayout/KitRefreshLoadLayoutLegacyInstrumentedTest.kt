package com.whisper.kit.widget.refreshloadlayout

import android.content.Intent
import android.os.SystemClock
import android.view.MotionEvent
import android.widget.ScrollView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 验证原生 ScrollView 的旧版嵌套滚动协议在真实窗口中连续交接、取消和消费忙碌指示器.
 *
 * @author whisper
 * @since 2026/09/11
 */
@RunWith(AndroidJUnit4::class)
class KitRefreshLoadLayoutLegacyInstrumentedTest {

    private lateinit var activity: KitRefreshLoadLayoutTestActivity
    private lateinit var scrollView: ScrollView
    private var gestureDownTime: Long = 0L

    @Before
    fun launchActivity() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, KitRefreshLoadLayoutTestActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as KitRefreshLoadLayoutTestActivity
        onMainThread { scrollView = activity.useFrameworkScrollContent() }
        awaitCondition("The framework ScrollView must be attached and laid out in a focused window.") {
            activity.refreshLayout.hasWindowFocus() && scrollView.isAttachedToWindow && scrollView.isLaidOut
        }
        onMainThread {
            assertTrue(scrollView.isNestedScrollingEnabled)
            assertTrue(scrollView.canScrollVertically(1))
        }
    }

    @After
    fun finishActivity() {
        if (::activity.isInitialized) {
            onMainThread { activity.finish() }
        }
    }

    @Test
    fun frameworkScrollView_continuousDragFromContentReachesTopAndRefreshesOnRelease() {
        onMainThread {
            scrollView.scrollTo(0, 120)
            assertTrue(scrollView.canScrollVertically(-1))
            dispatchTouch(MotionEvent.ACTION_DOWN, 100f)
            assertTrue(scrollView.hasNestedScrollingParent())

            dispatchTouch(MotionEvent.ACTION_MOVE, 250f)
            dispatchTouch(MotionEvent.ACTION_MOVE, 430f)

            assertEquals(0, scrollView.scrollY)
            assertTrue(activity.refreshLayout.currentOffset > activity.refreshLayout.refreshTriggerDistance)
            assertTrue(activity.actions.isEmpty())
            dispatchTouch(MotionEvent.ACTION_UP, 430f)
            assertEquals(listOf(KitRefreshLoadLayout.RefreshLoadAction.Refresh), activity.actions)
            assertEquals(KitRefreshLoadLayout.RefreshLoadState.Refreshing, activity.refreshLayout.state)
            assertFalse(scrollView.hasNestedScrollingParent())
        }
    }

    @Test
    fun frameworkScrollView_cancelledLoadMoreRetractsAndNextGestureCanSubmit() {
        onMainThread {
            scrollToBottom()
            beginFrameworkDrag(pullDown = false)
            assertTrue(activity.refreshLayout.currentOffset < -activity.refreshLayout.loadMoreTriggerDistance)

            dispatchTouch(MotionEvent.ACTION_CANCEL, 90f)

            assertFalse(scrollView.hasNestedScrollingParent())
            assertTrue(activity.actions.isEmpty())
        }
        awaitCondition("A cancelled framework nested drag must return to idle without submitting.") {
            activity.refreshLayout.currentOffset == 0 &&
                activity.refreshLayout.state == KitRefreshLoadLayout.RefreshLoadState.Idle
        }
        onMainThread {
            assertTrue(activity.actions.isEmpty())
            beginFrameworkDrag(pullDown = false)
            dispatchTouch(MotionEvent.ACTION_UP, 90f)

            assertEquals(listOf(KitRefreshLoadLayout.RefreshLoadAction.LoadMore), activity.actions)
            assertEquals(KitRefreshLoadLayout.RefreshLoadState.LoadingMore, activity.refreshLayout.state)
        }
    }

    @Test
    fun frameworkScrollView_preScrollRetractsBusyHeaderAndFooterWithoutEndingTasks() {
        verifyBusyIndicatorRetraction(pullDown = true)
        onMainThread { activity.refreshLayout.finishRefresh(animate = false) }
        verifyBusyIndicatorRetraction(pullDown = false)
        onMainThread {
            assertEquals(
                listOf(KitRefreshLoadLayout.RefreshLoadAction.Refresh, KitRefreshLoadLayout.RefreshLoadAction.LoadMore),
                activity.actions,
            )
        }
    }

    private fun verifyBusyIndicatorRetraction(pullDown: Boolean) {
        val state: KitRefreshLoadLayout.RefreshLoadState = if (pullDown) {
            KitRefreshLoadLayout.RefreshLoadState.Refreshing
        } else {
            KitRefreshLoadLayout.RefreshLoadState.LoadingMore
        }
        val holdOffset: Int = if (pullDown) 60 else -60
        onMainThread {
            if (!pullDown) {
                scrollToBottom()
            }
            beginFrameworkDrag(pullDown)
            dispatchTouch(MotionEvent.ACTION_UP, if (pullDown) 410f else 90f)
            assertEquals(state, activity.refreshLayout.state)
        }
        awaitCondition("The busy indicator must finish moving to its hold position.") {
            activity.refreshLayout.currentOffset == holdOffset
        }
        onMainThread {
            dispatchTouch(MotionEvent.ACTION_DOWN, 250f)
            assertTrue(scrollView.hasNestedScrollingParent())
            dispatchTouch(MotionEvent.ACTION_MOVE, if (pullDown) 90f else 410f)

            assertEquals(0, activity.refreshLayout.currentOffset)
            assertEquals(state, activity.refreshLayout.state)
            dispatchTouch(MotionEvent.ACTION_CANCEL, if (pullDown) 90f else 410f)
            assertEquals(state, activity.refreshLayout.state)
        }
    }

    private fun beginFrameworkDrag(pullDown: Boolean) {
        dispatchTouch(MotionEvent.ACTION_DOWN, 250f)
        assertTrue(scrollView.hasNestedScrollingParent())
        dispatchTouch(MotionEvent.ACTION_MOVE, if (pullDown) 410f else 90f)
    }

    private fun scrollToBottom() {
        scrollView.scrollTo(0, 2_000)
        assertFalse(scrollView.canScrollVertically(1))
        assertTrue(scrollView.canScrollVertically(-1))
    }

    private fun dispatchTouch(action: Int, y: Float) {
        val eventTime: Long = SystemClock.uptimeMillis()
        if (action == MotionEvent.ACTION_DOWN) {
            gestureDownTime = eventTime
        }
        val event: MotionEvent = MotionEvent.obtain(gestureDownTime, eventTime, action, 160f, y, 0)
        try {
            activity.host.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun awaitCondition(message: String, condition: () -> Boolean) {
        val deadline: Long = SystemClock.uptimeMillis() + 5_000L
        var satisfied = false
        while (!satisfied && SystemClock.uptimeMillis() < deadline) {
            onMainThread { satisfied = condition() }
            if (!satisfied) {
                SystemClock.sleep(25L)
            }
        }
        assertTrue(message, satisfied)
    }

    private fun onMainThread(action: () -> Unit) {
        var failure: Throwable? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try {
                action()
            } catch (error: Throwable) {
                failure = error
            }
        }
        failure?.let { throw it }
    }
}
