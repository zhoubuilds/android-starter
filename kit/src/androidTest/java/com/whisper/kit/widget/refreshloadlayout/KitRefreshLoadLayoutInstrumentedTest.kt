package com.whisper.kit.widget.refreshloadlayout

import android.animation.ValueAnimator
import android.content.Intent
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 验证真实 NestedScrollView 取消、窗口移除、回弹接管和回调重入不会遗留位移或误提交请求.
 *
 * @author whisper
 * @since 2026/09/11
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 26)
class KitRefreshLoadLayoutInstrumentedTest {

    private lateinit var activity: KitRefreshLoadLayoutTestActivity
    private var gestureDownTime: Long = 0L

    @Before
    fun launchActivity() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, KitRefreshLoadLayoutTestActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as KitRefreshLoadLayoutTestActivity
        awaitCondition("The test window must be focused and laid out.") {
            activity.refreshLayout.hasWindowFocus() && activity.refreshLayout.isLaidOut
        }
        onMainThread {
            assertTrue(activity.refreshLayout.isAttachedToWindow)
            assertTrue(activity.scrollView.canScrollVertically(1))
        }
    }

    @After
    fun finishActivity() {
        if (::activity.isInitialized) {
            onMainThread { activity.finish() }
        }
    }

    @Test
    fun nestedScrollView_cancelledRefreshRetractsWithoutSubmittingAndAllowsNextGesture() {
        verifyNestedCancellation(pullDown = true)
    }

    @Test
    fun nestedScrollView_cancelledLoadMoreRetractsWithoutSubmittingAndAllowsNextGesture() {
        verifyNestedCancellation(pullDown = false)
    }

    @Test
    fun detachingUncommittedDragDispatchedThroughHost_clearsOffsetBeforeReattachment() {
        verifyDetachingUncommittedDrag(throughHost = true)
    }

    @Test
    fun detachingUncommittedDragDispatchedAtContainer_clearsOffsetBeforeReattachment() {
        // 没有祖先触摸目标时, ViewGroup 在 detach 内直接取消子 View, 不经过容器的事件分发入口.
        verifyDetachingUncommittedDrag(throughHost = false)
    }

    @Test
    fun newDirectGestureTakingOverRetraction_thenImmediatelyReleasedEventuallyRests() {
        assumeTrue("This regression requires enabled system animations.", ValueAnimator.areAnimatorsEnabled())
        onMainThread {
            activity.useDirectContent()
            activity.refreshLayout.refreshTriggerDistance = 500
            activity.refreshLayout.animationDurationMillis = 5_000L
        }
        awaitCondition("The replacement content must complete layout.") {
            !activity.refreshLayout.isLayoutRequested
        }
        onMainThread {
            val touchSlop: Float = ViewConfiguration.get(activity).scaledTouchSlop.toFloat()
            val startY: Float = 120f
            val interceptY: Float = startY + touchSlop + 10f
            val dragY: Float = interceptY + 40f
            dispatchTouch(MotionEvent.ACTION_DOWN, startY)
            dispatchTouch(MotionEvent.ACTION_MOVE, interceptY)
            dispatchTouch(MotionEvent.ACTION_MOVE, dragY)
            assertTrue(activity.refreshLayout.currentOffset in 1 until activity.refreshLayout.refreshTriggerDistance)
            dispatchTouch(MotionEvent.ACTION_UP, dragY)
            assertEquals(KitRefreshLoadLayout.RefreshLoadState.Settling, activity.refreshLayout.state)
            assertTrue(activity.refreshLayout.currentOffset > 0)

            // 同一次主线程调用内交接, 避免测试线程调度导致原动画已结束.
            activity.refreshLayout.animationDurationMillis = 120L
            dispatchTouch(MotionEvent.ACTION_DOWN, startY)
            dispatchTouch(MotionEvent.ACTION_MOVE, interceptY)
            dispatchTouch(MotionEvent.ACTION_UP, interceptY)
        }

        awaitCondition("The new gesture must not leave an interrupted retraction frozen.") {
            activity.refreshLayout.currentOffset == 0 &&
                activity.refreshLayout.state == KitRefreshLoadLayout.RefreshLoadState.Idle
        }
        onMainThread { assertRestingWithoutActions() }
    }

    @Test
    fun directCancel_preservesReentrantBusyPositionAnimationInBothDirections() {
        verifyCancellationPreservesNewAction(nested = false)
    }

    @Test
    fun nestedCancel_preservesReentrantBusyPositionAnimationInBothDirections() {
        verifyCancellationPreservesNewAction(nested = true)
    }

    @Test
    fun firstBusySnapshotResizingIndicator_positionsUsingFinalHeightInBothDirections() {
        verifyInitialSnapshotResizingIndicator(resizeAgainInOffsetCallback = false)
    }

    @Test
    fun firstOffsetSnapshotResizingIndicatorAgain_positionsUsingFinalHeightInBothDirections() {
        verifyInitialSnapshotResizingIndicator(resizeAgainInOffsetCallback = true)
    }

    @Test
    fun replacingOppositeComponentFromDeferredOffsetCallback_deliversOnlyLaidOutSnapshots() {
        for (action: KitRefreshLoadLayout.RefreshLoadAction in KitRefreshLoadLayout.RefreshLoadAction.entries) {
            val refreshing: Boolean = action == KitRefreshLoadLayout.RefreshLoadAction.Refresh
            val activeRole: KitRefreshLoadLayout.ChildRole = if (refreshing) {
                KitRefreshLoadLayout.ChildRole.Header
            } else {
                KitRefreshLoadLayout.ChildRole.Footer
            }
            val replacedRole: KitRefreshLoadLayout.ChildRole = if (refreshing) {
                KitRefreshLoadLayout.ChildRole.Footer
            } else {
                KitRefreshLoadLayout.ChildRole.Header
            }
            val expectedState: KitRefreshLoadLayout.RefreshLoadState = if (refreshing) {
                KitRefreshLoadLayout.RefreshLoadState.Refreshing
            } else {
                KitRefreshLoadLayout.RefreshLoadState.LoadingMore
            }
            val expectedOffset: Int = if (refreshing) 60 else -60
            val actions: MutableList<KitRefreshLoadLayout.RefreshLoadAction> = mutableListOf()
            val replacementStates: MutableList<KitRefreshLoadLayout.RefreshLoadState> = mutableListOf()
            val replacementSnapshots: MutableList<ComponentOffsetSnapshot> = mutableListOf()
            lateinit var layout: KitRefreshLoadLayout
            lateinit var replacement: View
            onMainThread {
                activity.host.removeAllViews()
                val previousComponent: View = View(activity)
                replacement = object : View(activity), KitRefreshLoadComponent {
                    override fun onRefreshLoadStateChanged(layout: KitRefreshLoadLayout, state: KitRefreshLoadLayout.RefreshLoadState) {
                        replacementStates += state
                    }

                    override fun onRefreshLoadOffsetChanged(
                        layout: KitRefreshLoadLayout,
                        offset: Int,
                        triggerDistance: Int,
                        holdDistance: Int,
                    ) {
                        replacementSnapshots += ComponentOffsetSnapshot(offset, measuredHeight, holdDistance, isLaidOut, isLayoutRequested)
                    }
                }
                val activeIndicator: View = object : View(activity), KitRefreshLoadComponent {
                    override fun onRefreshLoadStateChanged(layout: KitRefreshLoadLayout, state: KitRefreshLoadLayout.RefreshLoadState) = Unit

                    override fun onRefreshLoadOffsetChanged(
                        layout: KitRefreshLoadLayout,
                        offset: Int,
                        triggerDistance: Int,
                        holdDistance: Int,
                    ) {
                        if (offset == expectedOffset && previousComponent.parent === layout) {
                            layout.removeView(previousComponent)
                            layout.addView(
                                replacement,
                                KitRefreshLoadLayout.LayoutParams(320, 95).apply { role = replacedRole },
                            )
                        }
                    }
                }
                layout = KitRefreshLoadLayout(activity).apply {
                    addView(activeIndicator, KitRefreshLoadLayout.LayoutParams(320, 60).apply { role = activeRole })
                    addView(previousComponent, KitRefreshLoadLayout.LayoutParams(320, 60).apply { role = replacedRole })
                    addView(View(activity), KitRefreshLoadLayout.LayoutParams(320, 600))
                    setOnRefreshLoadListener { requestedAction: KitRefreshLoadLayout.RefreshLoadAction -> actions += requestedAction }
                }
                activity.host.addView(layout, FrameLayout.LayoutParams(320, 600))
            }
            awaitCondition("The initial components must complete layout.") {
                layout.isAttachedToWindow && layout.isLaidOut && !layout.isLayoutRequested
            }
            onMainThread {
                // 只在动作开始前制造待布局状态; 回调添加组件后的重排交给 ViewRoot 自行调度.
                layout.requestLayout()
                layout.setActiveAction(action)
            }

            awaitCondition("The replacement component must be laid out and receive its initial snapshot.") {
                replacement.isLaidOut && !replacement.isLayoutRequested && replacement.height == 95 && replacementSnapshots.isNotEmpty()
            }

            onMainThread {
                assertEquals(expectedState, layout.state)
                assertEquals(expectedOffset, layout.currentOffset)
                assertEquals(listOf(expectedState), replacementStates)
                for (snapshot: ComponentOffsetSnapshot in replacementSnapshots) {
                    assertEquals(0, snapshot.offset)
                    assertEquals(95, snapshot.measuredHeight)
                    assertEquals(95, snapshot.holdDistance)
                    assertTrue(snapshot.laidOut)
                    assertFalse(snapshot.layoutRequested)
                }
                assertTrue(actions.isEmpty())
            }
        }
    }

    private fun verifyInitialSnapshotResizingIndicator(resizeAgainInOffsetCallback: Boolean) {
        for (action: KitRefreshLoadLayout.RefreshLoadAction in KitRefreshLoadLayout.RefreshLoadAction.entries) {
            val refreshing: Boolean = action == KitRefreshLoadLayout.RefreshLoadAction.Refresh
            val expectedState: KitRefreshLoadLayout.RefreshLoadState = if (refreshing) {
                KitRefreshLoadLayout.RefreshLoadState.Refreshing
            } else {
                KitRefreshLoadLayout.RefreshLoadState.LoadingMore
            }
            val expectedOffset: Int = if (refreshing) 120 else -120
            var busyNotificationCount: Int = 0
            val actions: MutableList<KitRefreshLoadLayout.RefreshLoadAction> = mutableListOf()
            val snapshots: MutableList<ComponentOffsetSnapshot> = mutableListOf()
            lateinit var layout: KitRefreshLoadLayout
            lateinit var indicator: View
            onMainThread {
                activity.host.removeAllViews()
                indicator = object : View(activity), KitRefreshLoadComponent {
                    override fun onRefreshLoadStateChanged(layout: KitRefreshLoadLayout, state: KitRefreshLoadLayout.RefreshLoadState) {
                        if (state != expectedState) return
                        busyNotificationCount++
                        val stateHeight: Int = if (resizeAgainInOffsetCallback) 90 else 120
                        if (layoutParams.height != stateHeight) {
                            layoutParams = KitRefreshLoadLayout.LayoutParams(layoutParams as KitRefreshLoadLayout.LayoutParams).apply {
                                height = stateHeight
                            }
                        }
                    }

                    override fun onRefreshLoadOffsetChanged(
                        layout: KitRefreshLoadLayout,
                        offset: Int,
                        triggerDistance: Int,
                        holdDistance: Int,
                    ) {
                        snapshots += ComponentOffsetSnapshot(offset, measuredHeight, holdDistance, isLaidOut, isLayoutRequested)
                        if (resizeAgainInOffsetCallback && layoutParams.height != 120) {
                            layoutParams = KitRefreshLoadLayout.LayoutParams(layoutParams as KitRefreshLoadLayout.LayoutParams).apply {
                                height = 120
                            }
                        }
                    }
                }
                layout = KitRefreshLoadLayout(activity).apply {
                    addView(
                        if (refreshing) indicator else View(activity),
                        KitRefreshLoadLayout.LayoutParams(320, 60).apply { role = KitRefreshLoadLayout.ChildRole.Header },
                    )
                    addView(View(activity), KitRefreshLoadLayout.LayoutParams(320, 600))
                    addView(
                        if (refreshing) View(activity) else indicator,
                        KitRefreshLoadLayout.LayoutParams(320, 60).apply { role = KitRefreshLoadLayout.ChildRole.Footer },
                    )
                    setOnRefreshLoadListener { requestedAction: KitRefreshLoadLayout.RefreshLoadAction -> actions += requestedAction }
                    setActiveAction(action)
                }
                activity.host.addView(layout, FrameLayout.LayoutParams(320, 600))
            }

            awaitCondition("The first busy snapshot must position the indicator using its final measured height.") {
                layout.isAttachedToWindow && layout.isLaidOut && !layout.isLayoutRequested &&
                    indicator.height == 120 && layout.currentOffset == expectedOffset
            }

            onMainThread {
                assertEquals(expectedState, layout.state)
                assertEquals(1, busyNotificationCount)
                assertEquals(expectedOffset.toFloat(), indicator.translationY, 0f)
                assertEquals(expectedOffset, snapshots.last().offset)
                assertEquals(120, snapshots.last().measuredHeight)
                assertEquals(120, snapshots.last().holdDistance)
                assertTrue(snapshots.last().laidOut)
                assertFalse(snapshots.last().layoutRequested)
                assertTrue(actions.isEmpty())
            }
        }
    }

    private fun verifyCancellationPreservesNewAction(nested: Boolean) {
        assumeTrue("This regression requires enabled system animations.", ValueAnimator.areAnimatorsEnabled())
        if (!nested) onMainThread { activity.useDirectContent() }
        for (action: KitRefreshLoadLayout.RefreshLoadAction in KitRefreshLoadLayout.RefreshLoadAction.entries) {
            var synchronizationCount: Int = 0
            onMainThread {
                val layout: KitRefreshLoadLayout = activity.refreshLayout
                val oldHeader: View = (0 until layout.childCount)
                    .map(layout::getChildAt)
                    .first { child: View ->
                        (child.layoutParams as KitRefreshLoadLayout.LayoutParams).role == KitRefreshLoadLayout.ChildRole.Header
                    }
                val params: KitRefreshLoadLayout.LayoutParams = KitRefreshLoadLayout.LayoutParams(
                    oldHeader.layoutParams as KitRefreshLoadLayout.LayoutParams,
                )
                val header: View = object : View(activity), KitRefreshLoadComponent {
                    override fun onRefreshLoadStateChanged(layout: KitRefreshLoadLayout, state: KitRefreshLoadLayout.RefreshLoadState) {
                        if (state == KitRefreshLoadLayout.RefreshLoadState.Settling && synchronizationCount == 0) {
                            synchronizationCount++
                            layout.setActiveAction(action, animate = true)
                        }
                    }

                    override fun onRefreshLoadOffsetChanged(
                        layout: KitRefreshLoadLayout,
                        offset: Int,
                        triggerDistance: Int,
                        holdDistance: Int,
                    ) = Unit
                }
                layout.removeView(oldHeader)
                layout.addView(header, params)
            }
            awaitCondition("The replacement component must complete layout.") {
                !activity.refreshLayout.isLayoutRequested
            }
            val pullDown: Boolean = action == KitRefreshLoadLayout.RefreshLoadAction.LoadMore
            val direction: Int = if (pullDown) 1 else -1
            val expectedState: KitRefreshLoadLayout.RefreshLoadState = if (pullDown) {
                KitRefreshLoadLayout.RefreshLoadState.LoadingMore
            } else {
                KitRefreshLoadLayout.RefreshLoadState.Refreshing
            }
            val expectedOffset: Int = if (pullDown) -60 else 60
            onMainThread {
                if (nested) {
                    activity.scrollView.scrollTo(0, if (pullDown) 0 else 2_000)
                    beginNestedDrag(pullDown)
                } else {
                    dispatchTouch(MotionEvent.ACTION_DOWN, 250f)
                    dispatchTouch(MotionEvent.ACTION_MOVE, 250f + direction * 60f)
                    dispatchTouch(MotionEvent.ACTION_MOVE, 250f + direction * 160f)
                }
                assertTrue(activity.refreshLayout.currentOffset * direction > 0)

                dispatchTouch(MotionEvent.ACTION_CANCEL, 250f + direction * 160f)

                assertEquals(expectedState, activity.refreshLayout.state)
                assertEquals(1, synchronizationCount)
                assertTrue(activity.actions.isEmpty())
            }
            awaitCondition("The action synchronized during cancellation must finish its position animation.") {
                activity.refreshLayout.state == expectedState && activity.refreshLayout.currentOffset == expectedOffset
            }
            onMainThread {
                assertEquals(1, synchronizationCount)
                assertTrue(activity.actions.isEmpty())
                activity.refreshLayout.setActiveAction(null, animate = false)
                assertRestingWithoutActions()
            }
        }
    }

    private fun verifyNestedCancellation(pullDown: Boolean) {
        val endY: Float = if (pullDown) 410f else 90f
        onMainThread {
            if (!pullDown) {
                activity.scrollView.scrollTo(0, 2_000)
                assertFalse(activity.scrollView.canScrollVertically(1))
                assertTrue(activity.scrollView.canScrollVertically(-1))
            }
            beginNestedDrag(pullDown)
            val direction: Int = if (pullDown) 1 else -1
            assertTrue(activity.refreshLayout.currentOffset * direction > 40)
            dispatchTouch(MotionEvent.ACTION_CANCEL, endY)
            assertFalse(activity.scrollView.hasNestedScrollingParent(ViewCompat.TYPE_TOUCH))
            assertTrue(activity.actions.isEmpty())
        }
        awaitCondition("A cancelled nested drag must return to the idle position.") {
            activity.refreshLayout.currentOffset == 0 &&
                activity.refreshLayout.state == KitRefreshLoadLayout.RefreshLoadState.Idle
        }
        onMainThread {
            assertRestingWithoutActions()
            beginNestedDrag(pullDown)
            dispatchTouch(MotionEvent.ACTION_UP, endY)
            val expected: KitRefreshLoadLayout.RefreshLoadAction = if (pullDown) {
                KitRefreshLoadLayout.RefreshLoadAction.Refresh
            } else {
                KitRefreshLoadLayout.RefreshLoadAction.LoadMore
            }
            assertEquals(listOf(expected), activity.actions)
        }
    }

    private fun verifyDetachingUncommittedDrag(throughHost: Boolean) {
        onMainThread {
            beginNestedDrag(pullDown = true, throughHost = throughHost)
            assertTrue(activity.refreshLayout.currentOffset > activity.refreshLayout.refreshTriggerDistance)

            activity.host.removeView(activity.refreshLayout)

            assertFalse(activity.refreshLayout.isAttachedToWindow)
            assertRestingWithoutActions()
            activity.host.addView(activity.refreshLayout)
        }
        awaitCondition("The refresh layout must be attached and laid out again.") {
            activity.refreshLayout.isAttachedToWindow && activity.refreshLayout.isLaidOut
        }
        onMainThread { assertRestingWithoutActions() }
    }

    private fun beginNestedDrag(pullDown: Boolean, throughHost: Boolean = true) {
        dispatchTouch(MotionEvent.ACTION_DOWN, 250f, throughHost)
        assertTrue(activity.scrollView.hasNestedScrollingParent(ViewCompat.TYPE_TOUCH))
        // 子 View 保持触摸目标, 让 CANCEL 经过真实嵌套滚动停止路径.
        activity.refreshLayout.requestDisallowInterceptTouchEvent(true)
        dispatchTouch(MotionEvent.ACTION_MOVE, if (pullDown) 410f else 90f, throughHost)
    }

    private fun dispatchTouch(action: Int, y: Float, throughHost: Boolean = true) {
        val eventTime: Long = SystemClock.uptimeMillis()
        if (action == MotionEvent.ACTION_DOWN) {
            gestureDownTime = eventTime
        }
        val event: MotionEvent = MotionEvent.obtain(gestureDownTime, eventTime, action, 160f, y, 0)
        try {
            if (throughHost) {
                activity.host.dispatchTouchEvent(event)
            } else {
                activity.refreshLayout.dispatchTouchEvent(event)
            }
        } finally {
            event.recycle()
        }
    }

    private fun assertRestingWithoutActions() {
        assertEquals(0, activity.refreshLayout.currentOffset)
        assertEquals(KitRefreshLoadLayout.RefreshLoadState.Idle, activity.refreshLayout.state)
        assertTrue(activity.actions.isEmpty())
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

    /** 组件回调开始时看到的布局与位移快照. */
    private data class ComponentOffsetSnapshot(
        val offset: Int,
        val measuredHeight: Int,
        val holdDistance: Int,
        val laidOut: Boolean,
        val layoutRequested: Boolean,
    )
}
