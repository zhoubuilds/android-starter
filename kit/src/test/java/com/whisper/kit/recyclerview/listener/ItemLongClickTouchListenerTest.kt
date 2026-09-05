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
 * 验证 RecyclerView 长按分发的目标过滤、超时和取消语义.
 *
 * @author whisper
 * @since 2026/09/04
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class ItemLongClickTouchListenerTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun longClick_whenTargetIsLongClickable_dispatchesAfterPlatformTimeout() {
        val fixture: LongClickFixture = createFixture(longClickable = true)
        val clickedViews: MutableList<View> = mutableListOf()
        val listener: ItemLongClickTouchListener = ItemLongClickTouchListener(
            fixture.recyclerView,
        ) { _, view, _ ->
            clickedViews += view
        }
        val downTime: Long = SystemClock.uptimeMillis()

        dispatch(listener, fixture.recyclerView, downTime, MotionEvent.ACTION_DOWN)
        idlePastLongPressTimeout()

        assertEquals(1, clickedViews.size)
        assertSame(fixture.targetView, clickedViews.single())

        dispatch(listener, fixture.recyclerView, downTime, MotionEvent.ACTION_UP)
        assertEquals(1, clickedViews.size)
    }

    @Test
    fun longClick_whenTargetIsNotLongClickable_doesNotDispatch() {
        val fixture: LongClickFixture = createFixture(longClickable = false)
        var longClickCount: Int = 0
        val listener: ItemLongClickTouchListener = ItemLongClickTouchListener(
            fixture.recyclerView,
        ) { _, _, _ ->
            longClickCount++
        }
        val downTime: Long = SystemClock.uptimeMillis()

        dispatch(listener, fixture.recyclerView, downTime, MotionEvent.ACTION_DOWN)
        idlePastLongPressTimeout()

        assertEquals(0, longClickCount)
    }

    @Test
    fun longClick_whenTargetLeavesPointerBeforeTimeout_doesNotDispatchAfterReturning() {
        val fixture: LongClickFixture = createFixture(longClickable = true)
        var longClickCount: Int = 0
        val listener: ItemLongClickTouchListener = ItemLongClickTouchListener(
            fixture.recyclerView,
        ) { _, _, _ ->
            longClickCount++
        }
        val downTime: Long = SystemClock.uptimeMillis()

        dispatch(listener, fixture.recyclerView, downTime, MotionEvent.ACTION_DOWN)
        fixture.targetView.translationX = TARGET_TRANSLATION_OUTSIDE
        dispatch(listener, fixture.recyclerView, downTime, MotionEvent.ACTION_MOVE)
        fixture.targetView.translationX = 0f
        idlePastLongPressTimeout()

        assertEquals(0, longClickCount)
    }

    @Test
    fun longClick_whenInterceptIsDisallowedAfterDown_clearsDownTarget() {
        val fixture: LongClickFixture = createFixture(longClickable = true)
        var longClickCount: Int = 0
        val listener: ItemLongClickTouchListener = ItemLongClickTouchListener(
            fixture.recyclerView,
        ) { _, _, _ ->
            longClickCount++
        }
        val downTime: Long = SystemClock.uptimeMillis()

        dispatch(listener, fixture.recyclerView, downTime, MotionEvent.ACTION_DOWN)
        listener.onRequestDisallowInterceptTouchEvent(true)
        idlePastLongPressTimeout()

        assertEquals(0, longClickCount)
    }

    @Test
    fun longClick_whenInterceptIsAllowedAfterDown_keepsDownTarget() {
        val fixture: LongClickFixture = createFixture(longClickable = true)
        var longClickCount: Int = 0
        val listener: ItemLongClickTouchListener = ItemLongClickTouchListener(
            fixture.recyclerView,
        ) { _, _, _ ->
            longClickCount++
        }
        val downTime: Long = SystemClock.uptimeMillis()

        dispatch(listener, fixture.recyclerView, downTime, MotionEvent.ACTION_DOWN)
        listener.onRequestDisallowInterceptTouchEvent(false)
        idlePastLongPressTimeout()

        assertEquals(1, longClickCount)
    }

    @Test
    fun longClick_whenRecyclerViewLosesWindowFocusBeforeTimeout_doesNotDispatch() {
        val fixture: LongClickFixture = createFixture(longClickable = true)
        var longClickCount: Int = 0
        val listener: ItemLongClickTouchListener = ItemLongClickTouchListener(
            fixture.recyclerView,
        ) { _, _, _ ->
            longClickCount++
        }
        val downTime: Long = SystemClock.uptimeMillis()

        dispatch(listener, fixture.recyclerView, downTime, MotionEvent.ACTION_DOWN)
        fixture.activityController.windowFocusChanged(false)
        idlePastLongPressTimeout()

        assertEquals(0, longClickCount)
    }

    @Test
    fun longClick_whenRecyclerViewDetachesBeforeTimeout_doesNotDispatch() {
        val fixture: LongClickFixture = createFixture(longClickable = true)
        var longClickCount: Int = 0
        val listener: ItemLongClickTouchListener = ItemLongClickTouchListener(
            fixture.recyclerView,
        ) { _, _, _ ->
            longClickCount++
        }
        val downTime: Long = SystemClock.uptimeMillis()

        dispatch(listener, fixture.recyclerView, downTime, MotionEvent.ACTION_DOWN)
        (fixture.recyclerView.parent as ViewGroup).removeView(fixture.recyclerView)
        idlePastLongPressTimeout()

        assertEquals(0, longClickCount)
    }

    @Test
    fun longClick_whenCrossAxisMovementExceedsTouchSlop_dispatchesForBothOrientations() {
        val movement: Float = ViewConfiguration.get(context).scaledTouchSlop.toFloat() + 1f

        assertLongClickCountAfterMove(
            orientation = RecyclerView.VERTICAL,
            moveX = TOUCH_X + movement,
            moveY = TOUCH_Y,
            expectedLongClickCount = 1,
        )
        assertLongClickCountAfterMove(
            orientation = RecyclerView.HORIZONTAL,
            moveX = TOUCH_X,
            moveY = TOUCH_Y + movement,
            expectedLongClickCount = 1,
        )
    }

    @Test
    fun longClick_whenCombinedDistanceExceedsSlopButScrollableAxisDoesNot_dispatchesLongClick() {
        val touchSlop: Float = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
        val movement: Float = touchSlop * DIAGONAL_MOVEMENT_FACTOR

        assertLongClickCountAfterMove(
            orientation = RecyclerView.VERTICAL,
            moveX = TOUCH_X + movement,
            moveY = TOUCH_Y + movement,
            expectedLongClickCount = 1,
        )
    }

    @Test
    fun longClick_whenCrossAxisMovementPassesThroughRecyclerView_dispatchesLongClick() {
        val movement: Float = ViewConfiguration.get(context).scaledTouchSlop.toFloat() + 1f
        val fixture: LongClickFixture = createFixture(longClickable = true)
        var longClickCount: Int = 0
        val listener: ItemLongClickTouchListener = ItemLongClickTouchListener(
            fixture.recyclerView,
        ) { _, _, _ ->
            longClickCount++
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
        idlePastLongPressTimeout()

        assertEquals(1, longClickCount)

        dispatchThroughRecyclerView(
            fixture.recyclerView,
            downTime,
            MotionEvent.ACTION_UP,
            TOUCH_X + movement,
            TOUCH_Y,
        )
    }

    @Test
    fun longClick_whenMainAxisMovementExceedsTouchSlop_cancelsForBothOrientations() {
        val movement: Float = ViewConfiguration.get(context).scaledTouchSlop.toFloat() + 1f

        assertLongClickCountAfterMove(
            orientation = RecyclerView.VERTICAL,
            moveX = TOUCH_X,
            moveY = TOUCH_Y + movement,
            expectedLongClickCount = 0,
        )
        assertLongClickCountAfterMove(
            orientation = RecyclerView.HORIZONTAL,
            moveX = TOUCH_X + movement,
            moveY = TOUCH_Y,
            expectedLongClickCount = 0,
        )
    }

    @Test
    fun longClick_whenClickAndLongClickObserversAreInstalled_dispatchesOnlyLongClick() {
        val fixture: LongClickFixture = createFixture(longClickable = true)
        fixture.targetView.isClickable = true
        var clickCount: Int = 0
        var longClickCount: Int = 0
        val clickListener: ItemClickTouchListener = ItemClickTouchListener(
            fixture.recyclerView,
        ) { _, _, _ ->
            clickCount++
        }
        val longClickListener: ItemLongClickTouchListener =
            ItemLongClickTouchListener(fixture.recyclerView) { _, _, _ ->
                longClickCount++
            }
        val downTime: Long = SystemClock.uptimeMillis()

        dispatch(clickListener, fixture.recyclerView, downTime, MotionEvent.ACTION_DOWN)
        dispatch(longClickListener, fixture.recyclerView, downTime, MotionEvent.ACTION_DOWN)
        idlePastLongPressTimeout()
        dispatch(clickListener, fixture.recyclerView, downTime, MotionEvent.ACTION_UP)
        dispatch(longClickListener, fixture.recyclerView, downTime, MotionEvent.ACTION_UP)

        assertEquals(0, clickCount)
        assertEquals(1, longClickCount)
    }

    private fun assertLongClickCountAfterMove(
        orientation: Int,
        moveX: Float,
        moveY: Float,
        expectedLongClickCount: Int,
    ) {
        val fixture: LongClickFixture = createFixture(
            longClickable = true,
            orientation = orientation,
        )
        var longClickCount: Int = 0
        val listener: ItemLongClickTouchListener = ItemLongClickTouchListener(
            fixture.recyclerView,
        ) { _, _, _ ->
            longClickCount++
        }
        val downTime: Long = SystemClock.uptimeMillis()

        dispatch(listener, fixture.recyclerView, downTime, MotionEvent.ACTION_DOWN)
        dispatch(listener, fixture.recyclerView, downTime, MotionEvent.ACTION_MOVE, moveX, moveY)
        idlePastLongPressTimeout()

        assertEquals(expectedLongClickCount, longClickCount)
    }

    private fun createFixture(
        longClickable: Boolean,
        orientation: Int = RecyclerView.VERTICAL,
    ): LongClickFixture {
        val activityController: ActivityController<Activity> =
            Robolectric.buildActivity(Activity::class.java).setup()
        val activity: Activity = activityController.get()
        val recyclerView: RecyclerView = RecyclerView(activity).apply {
            layoutManager = LinearLayoutManager(activity, orientation, false)
            adapter = LongClickAdapter(longClickable)
        }
        activity.setContentView(recyclerView)
        activityController.windowFocusChanged(true)
        val sizeSpec: Int = View.MeasureSpec.makeMeasureSpec(RECYCLER_SIZE, View.MeasureSpec.EXACTLY)
        recyclerView.measure(sizeSpec, sizeSpec)
        recyclerView.layout(0, 0, RECYCLER_SIZE, RECYCLER_SIZE)

        val itemView: FrameLayout = recyclerView.getChildAt(0) as FrameLayout
        return LongClickFixture(
            activityController = activityController,
            recyclerView = recyclerView,
            targetView = itemView.getChildAt(0),
        )
    }

    private fun dispatch(
        listener: RecyclerView.OnItemTouchListener,
        recyclerView: RecyclerView,
        downTime: Long,
        action: Int,
        x: Float = TOUCH_X,
        y: Float = TOUCH_Y,
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

    private class LongClickAdapter(
        private val longClickable: Boolean,
    ) : RecyclerView.Adapter<LongClickViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LongClickViewHolder {
            val itemView: FrameLayout = FrameLayout(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ITEM_HEIGHT,
                )
            }
            val targetView: View = View(parent.context).apply {
                isLongClickable = longClickable
            }
            itemView.addView(
                targetView,
                FrameLayout.LayoutParams(TARGET_SIZE, TARGET_SIZE).apply {
                    leftMargin = TARGET_LEFT
                    topMargin = TARGET_TOP
                },
            )
            return LongClickViewHolder(itemView)
        }

        override fun onBindViewHolder(holder: LongClickViewHolder, position: Int) = Unit

        override fun getItemCount(): Int = 1
    }

    private class LongClickViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    private data class LongClickFixture(
        val activityController: ActivityController<Activity>,
        val recyclerView: RecyclerView,
        val targetView: View,
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
