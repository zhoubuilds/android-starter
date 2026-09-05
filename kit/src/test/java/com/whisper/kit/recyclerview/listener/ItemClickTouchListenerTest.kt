package com.whisper.kit.recyclerview.listener

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

/**
 * 验证 RecyclerView 点击分发的手势目标归属和取消语义.
 *
 * @author whisper
 * @since 2026/09/04
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class ItemClickTouchListenerTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun click_whenVisualOrderChangesAfterDown_keepsDownTarget() {
        val fixture: ClickFixture = createFixture()
        val clickedViews: MutableList<View> = mutableListOf()
        val listener: ItemClickTouchListener = ItemClickTouchListener(
            fixture.recyclerView,
        ) { _, view, _ ->
            clickedViews += view
        }
        val downTime: Long = SystemClock.uptimeMillis()

        dispatch(listener, fixture.recyclerView, downTime, MotionEvent.ACTION_DOWN)
        fixture.frontTarget.z = -1f
        fixture.backTarget.z = 10f
        dispatch(listener, fixture.recyclerView, downTime, MotionEvent.ACTION_UP)

        assertEquals(1, clickedViews.size)
        assertSame(fixture.frontTarget, clickedViews.single())
    }

    @Test
    fun click_whenTargetLeavesPointerDuringMove_doesNotRecoverAfterReturning() {
        val fixture: ClickFixture = createFixture()
        var clickCount: Int = 0
        val listener: ItemClickTouchListener = ItemClickTouchListener(
            fixture.recyclerView,
        ) { _, _, _ ->
            clickCount++
        }
        val downTime: Long = SystemClock.uptimeMillis()

        dispatch(listener, fixture.recyclerView, downTime, MotionEvent.ACTION_DOWN)
        fixture.frontTarget.translationX = TARGET_TRANSLATION_OUTSIDE
        dispatch(listener, fixture.recyclerView, downTime, MotionEvent.ACTION_MOVE)
        fixture.frontTarget.translationX = 0f
        dispatch(listener, fixture.recyclerView, downTime, MotionEvent.ACTION_UP)

        assertEquals(0, clickCount)
    }

    @Test
    fun click_whenDownTargetIsRemovedBeforeUp_doesNotRetargetBackground() {
        val fixture: ClickFixture = createFixture()
        var clickCount: Int = 0
        val listener: ItemClickTouchListener = ItemClickTouchListener(
            fixture.recyclerView,
        ) { _, _, _ ->
            clickCount++
        }
        val downTime: Long = SystemClock.uptimeMillis()

        dispatch(listener, fixture.recyclerView, downTime, MotionEvent.ACTION_DOWN)
        fixture.itemView.removeView(fixture.frontTarget)
        dispatch(listener, fixture.recyclerView, downTime, MotionEvent.ACTION_UP)

        assertEquals(0, clickCount)
    }

    @Test
    fun click_whenTwoTapsAreRapid_dispatchesTwoOrdinaryClicks() {
        val fixture: ClickFixture = createFixture()
        var clickCount: Int = 0
        val listener: ItemClickTouchListener = ItemClickTouchListener(
            fixture.recyclerView,
        ) { _, _, _ ->
            clickCount++
        }

        repeat(2) {
            val downTime: Long = SystemClock.uptimeMillis()
            dispatch(listener, fixture.recyclerView, downTime, MotionEvent.ACTION_DOWN)
            dispatch(listener, fixture.recyclerView, downTime, MotionEvent.ACTION_UP)
        }

        assertEquals(2, clickCount)
    }

    @Test
    fun click_whenTargetIsNotLongClickableAfterLongPressTimeout_dispatchesOnUp() {
        val fixture: ClickFixture = createFixture()
        var clickCount: Int = 0
        val listener: ItemClickTouchListener = ItemClickTouchListener(
            fixture.recyclerView,
        ) { _, _, _ ->
            clickCount++
        }
        val downTime: Long = SystemClock.uptimeMillis()

        dispatch(listener, fixture.recyclerView, downTime, MotionEvent.ACTION_DOWN)
        idlePastLongPressTimeout()
        dispatch(listener, fixture.recyclerView, downTime, MotionEvent.ACTION_UP)

        assertEquals(1, clickCount)
    }

    @Test
    fun click_whenTargetIsLongClickableAtLongPressTimeout_doesNotDispatchOnUp() {
        val fixture: ClickFixture = createFixture()
        fixture.frontTarget.isLongClickable = true
        var clickCount: Int = 0
        val listener: ItemClickTouchListener = ItemClickTouchListener(
            fixture.recyclerView,
        ) { _, _, _ ->
            clickCount++
        }
        val downTime: Long = SystemClock.uptimeMillis()

        dispatch(listener, fixture.recyclerView, downTime, MotionEvent.ACTION_DOWN)
        idlePastLongPressTimeout()
        dispatch(listener, fixture.recyclerView, downTime, MotionEvent.ACTION_UP)

        assertEquals(0, clickCount)
    }

    @Test
    fun click_whenTargetStopsBeingLongClickableBeforeTimeout_dispatchesOnUp() {
        val fixture: ClickFixture = createFixture()
        fixture.frontTarget.isLongClickable = true
        var clickCount: Int = 0
        val listener: ItemClickTouchListener = ItemClickTouchListener(
            fixture.recyclerView,
        ) { _, _, _ ->
            clickCount++
        }
        val downTime: Long = SystemClock.uptimeMillis()

        dispatch(listener, fixture.recyclerView, downTime, MotionEvent.ACTION_DOWN)
        fixture.frontTarget.isLongClickable = false
        idlePastLongPressTimeout()
        dispatch(listener, fixture.recyclerView, downTime, MotionEvent.ACTION_UP)

        assertEquals(1, clickCount)
    }

    @Test
    fun click_whenTargetIsDisabledAtLongPressTimeout_thenReenabled_dispatchesOnUp() {
        val fixture: ClickFixture = createFixture()
        fixture.frontTarget.isLongClickable = true
        var clickCount: Int = 0
        val listener: ItemClickTouchListener = ItemClickTouchListener(
            fixture.recyclerView,
        ) { _, _, _ ->
            clickCount++
        }
        val downTime: Long = SystemClock.uptimeMillis()

        dispatch(listener, fixture.recyclerView, downTime, MotionEvent.ACTION_DOWN)
        fixture.frontTarget.isEnabled = false
        idlePastLongPressTimeout()
        fixture.frontTarget.isEnabled = true
        dispatch(listener, fixture.recyclerView, downTime, MotionEvent.ACTION_UP)

        assertEquals(1, clickCount)
    }

    @Test
    fun click_whenInterceptIsDisallowedAfterDown_clearsDownTarget() {
        val fixture: ClickFixture = createFixture()
        var clickCount: Int = 0
        val listener: ItemClickTouchListener = ItemClickTouchListener(
            fixture.recyclerView,
        ) { _, _, _ ->
            clickCount++
        }
        val downTime: Long = SystemClock.uptimeMillis()

        dispatch(listener, fixture.recyclerView, downTime, MotionEvent.ACTION_DOWN)
        listener.onRequestDisallowInterceptTouchEvent(true)
        dispatch(listener, fixture.recyclerView, downTime, MotionEvent.ACTION_UP)

        assertEquals(0, clickCount)
    }

    @Test
    fun click_whenInterceptIsAllowedAfterDown_keepsDownTarget() {
        val fixture: ClickFixture = createFixture()
        var clickCount: Int = 0
        val listener: ItemClickTouchListener = ItemClickTouchListener(
            fixture.recyclerView,
        ) { _, _, _ ->
            clickCount++
        }
        val downTime: Long = SystemClock.uptimeMillis()

        dispatch(listener, fixture.recyclerView, downTime, MotionEvent.ACTION_DOWN)
        listener.onRequestDisallowInterceptTouchEvent(false)
        dispatch(listener, fixture.recyclerView, downTime, MotionEvent.ACTION_UP)

        assertEquals(1, clickCount)
    }

    @Test
    fun click_whenRecyclerViewLosesWindowFocusBeforeUp_doesNotDispatch() {
        val fixture: ClickFixture = createFixture()
        var clickCount: Int = 0
        val listener: ItemClickTouchListener = ItemClickTouchListener(
            fixture.recyclerView,
        ) { _, _, _ ->
            clickCount++
        }
        val downTime: Long = SystemClock.uptimeMillis()

        dispatch(listener, fixture.recyclerView, downTime, MotionEvent.ACTION_DOWN)
        fixture.activityController.windowFocusChanged(false)
        dispatch(listener, fixture.recyclerView, downTime, MotionEvent.ACTION_UP)

        assertEquals(0, clickCount)
    }

    @Test
    fun click_whenRecyclerViewDetachesBeforeUp_doesNotDispatch() {
        val fixture: ClickFixture = createFixture()
        var clickCount: Int = 0
        val listener: ItemClickTouchListener = ItemClickTouchListener(
            fixture.recyclerView,
        ) { _, _, _ ->
            clickCount++
        }
        val downTime: Long = SystemClock.uptimeMillis()

        dispatch(listener, fixture.recyclerView, downTime, MotionEvent.ACTION_DOWN)
        (fixture.recyclerView.parent as ViewGroup).removeView(fixture.recyclerView)
        dispatch(listener, fixture.recyclerView, downTime, MotionEvent.ACTION_UP)

        assertEquals(0, clickCount)
    }

    @Test
    fun click_whenCrossAxisMovementExceedsTouchSlop_dispatchesForBothOrientations() {
        val movement: Float = ViewConfiguration.get(context).scaledTouchSlop.toFloat() + 1f

        assertClickCountAfterMove(
            orientation = RecyclerView.VERTICAL,
            moveX = TOUCH_X + movement,
            moveY = TOUCH_Y,
            expectedClickCount = 1,
        )
        assertClickCountAfterMove(
            orientation = RecyclerView.HORIZONTAL,
            moveX = TOUCH_X,
            moveY = TOUCH_Y + movement,
            expectedClickCount = 1,
        )
    }

    @Test
    fun click_whenCombinedDistanceExceedsSlopButScrollableAxisDoesNot_dispatchesClick() {
        val touchSlop: Float = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
        val movement: Float = touchSlop * DIAGONAL_MOVEMENT_FACTOR

        assertClickCountAfterMove(
            orientation = RecyclerView.VERTICAL,
            moveX = TOUCH_X + movement,
            moveY = TOUCH_Y + movement,
            expectedClickCount = 1,
        )
    }

    @Test
    fun click_whenCrossAxisMovementPassesThroughRecyclerView_dispatchesClick() {
        val movement: Float = ViewConfiguration.get(context).scaledTouchSlop.toFloat() + 1f
        val fixture: ClickFixture = createFixture(RecyclerView.VERTICAL)
        var clickCount: Int = 0
        val listener: ItemClickTouchListener = ItemClickTouchListener(
            fixture.recyclerView,
        ) { _, _, _ ->
            clickCount++
        }
        fixture.recyclerView.addOnItemTouchListener(listener)
        val downTime: Long = SystemClock.uptimeMillis()

        dispatchThroughRecyclerView(
            fixture.recyclerView,
            downTime,
            MotionEvent.ACTION_DOWN,
            TOUCH_X,
            TOUCH_Y,
        )
        dispatchThroughRecyclerView(
            fixture.recyclerView,
            downTime,
            MotionEvent.ACTION_MOVE,
            TOUCH_X + movement,
            TOUCH_Y,
        )
        dispatchThroughRecyclerView(
            fixture.recyclerView,
            downTime,
            MotionEvent.ACTION_UP,
            TOUCH_X + movement,
            TOUCH_Y,
        )

        assertEquals(1, clickCount)
    }

    @Test
    fun click_whenMainAxisMovementExceedsTouchSlop_cancelsForBothOrientations() {
        val movement: Float = ViewConfiguration.get(context).scaledTouchSlop.toFloat() + 1f

        assertClickCountAfterMove(
            orientation = RecyclerView.VERTICAL,
            moveX = TOUCH_X,
            moveY = TOUCH_Y + movement,
            expectedClickCount = 0,
        )
        assertClickCountAfterMove(
            orientation = RecyclerView.HORIZONTAL,
            moveX = TOUCH_X + movement,
            moveY = TOUCH_Y,
            expectedClickCount = 0,
        )
    }

    private fun assertClickCountAfterMove(
        orientation: Int,
        moveX: Float,
        moveY: Float,
        expectedClickCount: Int,
    ) {
        val fixture: ClickFixture = createFixture(orientation)
        var clickCount: Int = 0
        val listener: ItemClickTouchListener = ItemClickTouchListener(
            fixture.recyclerView,
        ) { _, _, _ ->
            clickCount++
        }
        val downTime: Long = SystemClock.uptimeMillis()

        dispatch(listener, fixture.recyclerView, downTime, MotionEvent.ACTION_DOWN)
        dispatch(listener, fixture.recyclerView, downTime, MotionEvent.ACTION_MOVE, moveX, moveY)
        dispatch(listener, fixture.recyclerView, downTime, MotionEvent.ACTION_UP, moveX, moveY)

        assertEquals(expectedClickCount, clickCount)
    }

    private fun createFixture(
        orientation: Int = RecyclerView.VERTICAL,
    ): ClickFixture {
        val activityController: ActivityController<Activity> =
            Robolectric.buildActivity(Activity::class.java).setup()
        val activity: Activity = activityController.get()
        val adapter: ClickAdapter = ClickAdapter()
        val recyclerView: RecyclerView = RecyclerView(activity).apply {
            layoutManager = LinearLayoutManager(activity, orientation, false)
            this.adapter = adapter
        }
        activity.setContentView(recyclerView)
        activityController.windowFocusChanged(true)
        val sizeSpec: Int = View.MeasureSpec.makeMeasureSpec(RECYCLER_SIZE, View.MeasureSpec.EXACTLY)
        recyclerView.measure(sizeSpec, sizeSpec)
        recyclerView.layout(0, 0, RECYCLER_SIZE, RECYCLER_SIZE)

        val itemView: FrameLayout = recyclerView.getChildAt(0) as FrameLayout
        return ClickFixture(
            activityController = activityController,
            recyclerView = recyclerView,
            itemView = itemView,
            backTarget = itemView.getChildAt(0),
            frontTarget = itemView.getChildAt(1),
        )
    }

    private fun dispatch(
        listener: ItemClickTouchListener,
        recyclerView: RecyclerView,
        downTime: Long,
        action: Int,
        x: Float = TOUCH_X,
        y: Float = TOUCH_Y,
    ) {
        val eventTime: Long = SystemClock.uptimeMillis()
        val event: MotionEvent = MotionEvent.obtain(
            downTime,
            eventTime,
            action,
            x,
            y,
            0,
        )
        try {
            listener.onInterceptTouchEvent(recyclerView, event)
        } finally {
            event.recycle()
        }
    }

    private fun dispatchThroughRecyclerView(
        recyclerView: RecyclerView,
        downTime: Long,
        action: Int,
        x: Float,
        y: Float,
    ) {
        val event: MotionEvent = MotionEvent.obtain(
            downTime,
            SystemClock.uptimeMillis(),
            action,
            x,
            y,
            0,
        )
        try {
            recyclerView.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun idlePastLongPressTimeout() {
        ShadowLooper.idleMainLooper(
            ViewConfiguration.getLongPressTimeout().toLong() + 1L,
            TimeUnit.MILLISECONDS,
        )
    }

    private class ClickAdapter : RecyclerView.Adapter<ClickViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClickViewHolder {
            val itemView: FrameLayout = FrameLayout(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ITEM_HEIGHT,
                )
            }
            val backTarget: View = View(parent.context).apply {
                isClickable = true
                z = 0f
            }
            val frontTarget: View = View(parent.context).apply {
                isClickable = true
                z = 8f
            }
            itemView.addView(backTarget, targetLayoutParams())
            itemView.addView(frontTarget, targetLayoutParams())
            return ClickViewHolder(itemView)
        }

        override fun onBindViewHolder(holder: ClickViewHolder, position: Int) = Unit

        override fun getItemCount(): Int = 1

        private fun targetLayoutParams(): FrameLayout.LayoutParams =
            FrameLayout.LayoutParams(TARGET_SIZE, TARGET_SIZE).apply {
                leftMargin = TARGET_LEFT
                topMargin = TARGET_TOP
            }
    }

    private class ClickViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    private data class ClickFixture(
        val activityController: ActivityController<Activity>,
        val recyclerView: RecyclerView,
        val itemView: FrameLayout,
        val backTarget: View,
        val frontTarget: View,
    )

    private companion object {

        private const val RECYCLER_SIZE: Int = 300
        private const val ITEM_HEIGHT: Int = 200
        private const val TARGET_LEFT: Int = 40
        private const val TARGET_TOP: Int = 40
        private const val TARGET_SIZE: Int = 100
        private const val TOUCH_X: Float = 80f
        private const val TOUCH_Y: Float = 80f
        private const val TARGET_TRANSLATION_OUTSIDE: Float = 200f
        private const val DIAGONAL_MOVEMENT_FACTOR: Float = 0.75f
    }
}
