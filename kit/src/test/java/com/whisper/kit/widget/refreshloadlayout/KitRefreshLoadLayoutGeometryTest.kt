package com.whisper.kit.widget.refreshloadlayout

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Looper
import android.view.Gravity
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
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.TimeUnit

/**
 * 验证组件尺寸变化后的拖拽阶段、距离快照、边距及布局参数保持一致.
 *
 * @author whisper
 * @since 2026/09/11
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KitRefreshLoadLayoutGeometryTest {

    @Test
    fun increasingDefaultThresholdHeight_updatesReleaseStateBeforeReleaseCancels() {
        for (direction: Direction in Direction.entries) {
            val fixture: Fixture = createFixture(indicatorHeight = SMALL_HEIGHT)
            startPull(fixture, direction)
            assertEquals(direction.releaseState, fixture.layout.state)
            fixture.clearNotifications()

            resize(fixture.indicator(direction), LARGE_HEIGHT)
            measureAndLayout(fixture.layout)

            assertEquals(direction.pullingState, fixture.layout.state)
            assertEquals(direction.offset, fixture.layout.currentOffset)
            assertEquals(listOf(direction.pullingState), fixture.header.states)
            assertEquals(listOf(direction.pullingState), fixture.footer.states)
            assertSnapshot(fixture, direction, LARGE_HEIGHT, LARGE_HEIGHT)
            assertTrue(fixture.actions.isEmpty())

            fixture.layout.onStopNestedScroll(fixture.content, ViewCompat.TYPE_TOUCH)

            assertEquals(KitRefreshLoadLayout.RefreshLoadState.Idle, fixture.layout.state)
            assertEquals(0, fixture.layout.currentOffset)
            assertTrue(fixture.actions.isEmpty())
        }
    }

    @Test
    fun decreasingDefaultThresholdHeight_updatesPullStateWithoutSubmittingUntilRelease() {
        for (direction: Direction in Direction.entries) {
            val fixture: Fixture = createFixture(indicatorHeight = LARGE_HEIGHT)
            startPull(fixture, direction)
            assertEquals(direction.pullingState, fixture.layout.state)
            fixture.clearNotifications()

            resize(fixture.indicator(direction), SMALL_HEIGHT)
            measureAndLayout(fixture.layout)

            assertEquals(direction.releaseState, fixture.layout.state)
            assertEquals(direction.offset, fixture.layout.currentOffset)
            assertEquals(listOf(direction.releaseState), fixture.header.states)
            assertEquals(listOf(direction.releaseState), fixture.footer.states)
            assertSnapshot(fixture, direction, SMALL_HEIGHT, SMALL_HEIGHT)
            assertTrue(fixture.actions.isEmpty())

            fixture.layout.onStopNestedScroll(fixture.content, ViewCompat.TYPE_TOUCH)

            assertEquals(direction.busyState, fixture.layout.state)
            assertEquals(listOf(direction.action), fixture.actions)
        }
    }

    @Test
    fun changingHeightWithFixedThreshold_updatesHoldSnapshotWithoutChangingPullStage() {
        for (direction: Direction in Direction.entries) {
            val fixture: Fixture = createFixture(indicatorHeight = SMALL_HEIGHT)
            fixture.layout.refreshTriggerDistance = LARGE_HEIGHT
            fixture.layout.loadMoreTriggerDistance = LARGE_HEIGHT
            startPull(fixture, direction)
            fixture.clearNotifications()

            resize(fixture.indicator(direction), LARGE_HEIGHT)
            measureAndLayout(fixture.layout)

            assertEquals(direction.pullingState, fixture.layout.state)
            assertEquals(direction.offset, fixture.layout.currentOffset)
            assertSnapshot(fixture, direction, LARGE_HEIGHT, LARGE_HEIGHT)
            assertTrue(fixture.header.states.isEmpty())
            assertTrue(fixture.footer.states.isEmpty())
            assertTrue(fixture.actions.isEmpty())
        }
    }

    @Test
    fun repeatedLayoutWithUnchangedDistances_doesNotRepeatStateOrOffsetNotifications() {
        for (direction: Direction in Direction.entries) {
            val fixture: Fixture = createFixture(indicatorHeight = SMALL_HEIGHT)
            startPull(fixture, direction)
            resize(fixture.indicator(direction), LARGE_HEIGHT)
            measureAndLayout(fixture.layout)
            fixture.clearNotifications()

            repeat(2) {
                fixture.layout.requestLayout()
                measureAndLayout(fixture.layout)
            }

            assertEquals(direction.pullingState, fixture.layout.state)
            assertTrue(fixture.header.states.isEmpty())
            assertTrue(fixture.footer.states.isEmpty())
            assertTrue(fixture.header.offsets.isEmpty())
            assertTrue(fixture.footer.offsets.isEmpty())
            assertTrue(fixture.actions.isEmpty())
        }
    }

    @Test
    fun changingVerticalMargins_updatesHoldSnapshotWithoutChangingDefaultThreshold() {
        for (direction: Direction in Direction.entries) {
            val fixture: Fixture = createFixture()
            startPull(fixture, direction)
            fixture.clearNotifications()
            val indicator: RecordingIndicator = fixture.indicator(direction)

            indicator.layoutParams = (indicator.layoutParams as KitRefreshLoadLayout.LayoutParams).apply {
                topMargin = 12
                bottomMargin = 9
            }
            measureAndLayout(fixture.layout)

            assertEquals(direction.releaseState, fixture.layout.state)
            assertEquals(direction.offset, fixture.layout.currentOffset)
            assertSnapshot(fixture, direction, SMALL_HEIGHT, SMALL_HEIGHT + 21)
            assertTrue(fixture.header.states.isEmpty())
            assertTrue(fixture.footer.states.isEmpty())
            assertTrue(fixture.actions.isEmpty())
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun positiveVerticalMargins_hideIdleIndicatorsAndRemainVisibleAfterReveal() {
        for (direction: Direction in Direction.entries) {
            for ((topMargin: Int, bottomMargin: Int) in listOf(12 to 0, 0 to 12, 12 to 9)) {
                val fixture: Fixture = createFixture()
                fixture.layout.setBackgroundColor(Color.WHITE)
                fixture.content.setBackgroundColor(Color.BLUE)
                fixture.header.setBackgroundColor(Color.RED)
                fixture.footer.setBackgroundColor(Color.GREEN)
                for (indicator: View in listOf(fixture.header, fixture.footer)) {
                    indicator.layoutParams = (indicator.layoutParams as KitRefreshLoadLayout.LayoutParams).apply {
                        this.topMargin = topMargin
                        this.bottomMargin = bottomMargin
                    }
                }
                measureAndLayout(fixture.layout)
                val idleBitmap: Bitmap = render(fixture.layout)
                try {
                    for (y: Int in 0 until LAYOUT_HEIGHT) {
                        assertEquals("The idle indicator must not cover content at y=$y", Color.BLUE, idleBitmap.getPixel(160, y))
                    }
                } finally {
                    idleBitmap.recycle()
                }

                fixture.layout.setActiveAction(direction.action)

                val holdDistance: Int = SMALL_HEIGHT + topMargin + bottomMargin
                val indicator: RecordingIndicator = fixture.indicator(direction)
                val expectedTop: Int = when (direction) {
                    Direction.REFRESH -> topMargin
                    Direction.LOAD_MORE -> LAYOUT_HEIGHT - bottomMargin - SMALL_HEIGHT
                }
                val regionTop: Int = when (direction) {
                    Direction.REFRESH -> 0
                    Direction.LOAD_MORE -> LAYOUT_HEIGHT - holdDistance
                }
                assertEquals(holdDistance, kotlin.math.abs(fixture.layout.currentOffset))
                assertEquals(expectedTop.toFloat(), indicator.y, 0f)
                assertSnapshot(fixture, direction, SMALL_HEIGHT, holdDistance)
                val revealedBitmap: Bitmap = render(fixture.layout)
                try {
                    for (y: Int in regionTop until regionTop + holdDistance) {
                        val expectedColor: Int = if (y in expectedTop until expectedTop + SMALL_HEIGHT) {
                            if (direction == Direction.REFRESH) Color.RED else Color.GREEN
                        } else {
                            Color.WHITE
                        }
                        assertEquals("The expanded indicator must preserve both margins at y=$y", expectedColor, revealedBitmap.getPixel(160, y))
                    }
                } finally {
                    revealedBitmap.recycle()
                }
                assertTrue(fixture.actions.isEmpty())
            }
        }
    }

    @Test
    fun externalHeightChangeInWindow_updatesExistingComponentAfterViewRootLayout() {
        for (direction: Direction in Direction.entries) {
            withWindowFixture(SMALL_HEIGHT) { fixture: Fixture ->
                startPull(fixture, direction)
                fixture.clearNotifications()

                resize(fixture.indicator(direction), LARGE_HEIGHT)
                advanceWindowLayouts()

                assertEquals(direction.pullingState, fixture.layout.state)
                assertEquals(direction.offset, fixture.layout.currentOffset)
                assertSnapshot(fixture, direction, LARGE_HEIGHT, LARGE_HEIGHT)
                assertReadySnapshot(fixture.indicator(direction).offsets.last(), LARGE_HEIGHT)
                assertTrue(fixture.actions.isEmpty())
            }
        }
    }

    @Test
    fun heightChangeFromLayoutStateCallback_waitsForFinalSizeAndSkipsUnchangedSnapshot() {
        for (direction: Direction in Direction.entries) {
            for (finalHeight: Int in listOf(LARGE_HEIGHT, LARGE_HEIGHT + 20)) {
                withWindowFixture(LARGE_HEIGHT) { fixture: Fixture ->
                    startPull(fixture, direction)
                    fixture.clearNotifications()
                    val indicator: RecordingIndicator = fixture.indicator(direction)
                    var resizedAgain: Boolean = false
                    fixture.header.onState = { state: KitRefreshLoadLayout.RefreshLoadState ->
                        if (state == direction.releaseState && !resizedAgain) {
                            resizedAgain = true
                            resize(indicator, finalHeight)
                        }
                    }

                    resize(indicator, SMALL_HEIGHT)
                    advanceWindowLayouts()

                    assertTrue(resizedAgain)
                    assertEquals(finalHeight, indicator.measuredHeight)
                    assertEquals(direction.offset, fixture.layout.currentOffset)
                    assertEquals(direction.pullingState, fixture.layout.state)
                    assertEquals(direction.pullingState, fixture.header.states.last())
                    assertEquals(direction.pullingState, fixture.footer.states.last())
                    if (finalHeight == LARGE_HEIGHT) {
                        assertTrue("Returning to the last delivered size must not repeat its snapshot", indicator.offsets.isEmpty())
                    } else {
                        assertSnapshot(fixture, direction, finalHeight, finalHeight)
                    }
                    for (snapshot: OffsetSnapshot in indicator.offsets) {
                        assertReadySnapshot(snapshot, finalHeight)
                    }
                    assertTrue(fixture.actions.isEmpty())
                }
            }
        }
    }

    @Test
    fun changingHeightDuringBusyReveal_preservesCurrentTaskPositionAndAnimator() {
        for (direction: Direction in Direction.entries) {
            withAttachedFixture { fixture: Fixture ->
                fixture.layout.animationDurationMillis = ANIMATION_DURATION
                fixture.layout.setActiveAction(direction.action, animate = true)
                val animator: ValueAnimator = activeAnimator(fixture.layout)
                animator.currentPlayTime = 48L
                val offsetBefore: Int = fixture.layout.currentOffset
                assertTrue(kotlin.math.abs(offsetBefore) in 1 until SMALL_HEIGHT)
                fixture.clearNotifications()

                resize(fixture.indicator(direction), LARGE_HEIGHT)
                measureAndLayout(fixture.layout)

                assertEquals(direction.busyState, fixture.layout.state)
                assertEquals(offsetBefore, fixture.layout.currentOffset)
                assertSame(animator, activeAnimator(fixture.layout))
                assertSnapshot(fixture, direction, LARGE_HEIGHT, LARGE_HEIGHT)
                assertTrue(fixture.header.states.isEmpty())
                assertTrue(fixture.footer.states.isEmpty())
                assertTrue(fixture.actions.isEmpty())
                animator.end()
                assertEquals(direction.busyState, fixture.layout.state)
            }
        }
    }

    @Test
    fun changingHeightDuringSettling_preservesCurrentPositionAndAnimator() {
        for (direction: Direction in Direction.entries) {
            withAttachedFixture { fixture: Fixture ->
                fixture.layout.setActiveAction(direction.action)
                fixture.layout.animationDurationMillis = ANIMATION_DURATION
                fixture.layout.setActiveAction(null, animate = true)
                val animator: ValueAnimator = activeAnimator(fixture.layout)
                animator.currentPlayTime = 48L
                val offsetBefore: Int = fixture.layout.currentOffset
                assertTrue(kotlin.math.abs(offsetBefore) in 1 until SMALL_HEIGHT)
                fixture.clearNotifications()

                resize(fixture.indicator(direction), LARGE_HEIGHT)
                measureAndLayout(fixture.layout)

                assertEquals(KitRefreshLoadLayout.RefreshLoadState.Settling, fixture.layout.state)
                assertEquals(offsetBefore, fixture.layout.currentOffset)
                assertSame(animator, activeAnimator(fixture.layout))
                assertSnapshot(fixture, direction, LARGE_HEIGHT, LARGE_HEIGHT)
                assertTrue(fixture.header.states.isEmpty())
                assertTrue(fixture.footer.states.isEmpty())
                assertTrue(fixture.actions.isEmpty())
                animator.end()
                assertEquals(KitRefreshLoadLayout.RefreshLoadState.Idle, fixture.layout.state)
            }
        }
    }

    @Test
    fun standardFrameLayoutParams_preserveGravityAndMarginsWhenContentIsAddedDynamically() {
        val context: Context = RuntimeEnvironment.getApplication()
        val originalFlags: Int = context.applicationInfo.flags
        context.applicationInfo.flags = originalFlags or ApplicationInfo.FLAG_SUPPORTS_RTL
        try {
            for (direction: Int in listOf(View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL)) {
                for (gravity: Int in listOf(Gravity.CENTER, Gravity.END or Gravity.BOTTOM, Gravity.START or Gravity.TOP)) {
                    val actualParent = KitRefreshLoadLayout(context)
                    val expectedParent = FrameLayout(context)
                    for (parent: FrameLayout in listOf(actualParent, expectedParent)) {
                        parent.layoutDirection = direction
                        parent.setPadding(11, 17, 23, 29)
                    }
                    val actual = View(context)
                    val expected = View(context)
                    for ((parent: FrameLayout, child: View) in listOf(actualParent to actual, expectedParent to expected)) {
                        parent.addView(child, FrameLayout.LayoutParams(70, 90, gravity).apply {
                            setMargins(7, 13, 19, 31)
                            marginStart = 5
                            marginEnd = 9
                        })
                    }

                    measureAndLayout(actualParent)
                    measureAndLayout(expectedParent)

                    assertTrue(actual.layoutParams is KitRefreshLoadLayout.LayoutParams)
                    assertEquals(gravity, (actual.layoutParams as FrameLayout.LayoutParams).gravity)
                    assertEquals(expected.left, actual.left)
                    assertEquals(expected.top, actual.top)
                    assertEquals(expected.right, actual.right)
                    assertEquals(expected.bottom, actual.bottom)
                }
            }
        } finally {
            context.applicationInfo.flags = originalFlags
        }
    }

    private fun createFixture(
        context: Context = RuntimeEnvironment.getApplication(),
        indicatorHeight: Int = SMALL_HEIGHT,
        layOut: Boolean = true,
    ): Fixture {
        val layout = KitRefreshLoadLayout(context).apply {
            dragResistance = 1f
            maxDragDistance = 10_000
            animationDurationMillis = 0L
        }
        val content = View(context)
        val header = RecordingIndicator(context)
        val footer = RecordingIndicator(context)
        layout.addView(content)
        layout.addView(header, roleParams(KitRefreshLoadLayout.ChildRole.Header, indicatorHeight))
        layout.addView(footer, roleParams(KitRefreshLoadLayout.ChildRole.Footer, indicatorHeight))
        val actions: MutableList<KitRefreshLoadLayout.RefreshLoadAction> = mutableListOf()
        layout.setOnRefreshLoadListener(actions::add)
        if (layOut) {
            measureAndLayout(layout)
        }
        return Fixture(layout, content, header, footer, actions)
    }

    private fun withWindowFixture(indicatorHeight: Int, block: (Fixture) -> Unit) {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val fixture: Fixture = createFixture(controller.get(), indicatorHeight, layOut = false)
            controller.get().setContentView(fixture.layout, ViewGroup.LayoutParams(LAYOUT_WIDTH, LAYOUT_HEIGHT))
            controller.visible()
            advanceWindowLayouts()
            assertTrue("Window geometry tests require an attached view", fixture.layout.isAttachedToWindow)
            assertTrue("The window must complete initial layout", fixture.layout.isLaidOut)
            block(fixture)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun advanceWindowLayouts() {
        // 通过 ViewRoot 调度真实窗口布局, 让回调中的 requestLayout 遵守平台限制.
        shadowOf(Looper.getMainLooper()).idleFor(100L, TimeUnit.MILLISECONDS)
    }

    private fun withAttachedFixture(block: (Fixture) -> Unit) {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val fixture: Fixture = createFixture(controller.get())
            controller.get().setContentView(fixture.layout)
            measureAndLayout(fixture.layout)
            assertTrue("Animation tests require an attached view", fixture.layout.isAttachedToWindow)
            block(fixture)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun startPull(fixture: Fixture, direction: Direction) {
        fixture.layout.onNestedScrollAccepted(
            fixture.content, fixture.content, ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH,
        )
        fixture.layout.onNestedScroll(
            fixture.content, 0, 0, 0, -direction.offset, ViewCompat.TYPE_TOUCH, IntArray(2),
        )
        assertEquals(direction.offset, fixture.layout.currentOffset)
    }

    private fun resize(indicator: View, height: Int) {
        indicator.layoutParams = indicator.layoutParams.apply { this.height = height }
    }

    private fun assertSnapshot(fixture: Fixture, direction: Direction, triggerDistance: Int, holdDistance: Int) {
        val snapshots: List<OffsetSnapshot> = fixture.indicator(direction).offsets
        assertTrue("The existing component must receive updated distances", snapshots.isNotEmpty())
        val snapshot: OffsetSnapshot = snapshots.last()
        assertEquals(fixture.layout.currentOffset, snapshot.offset)
        assertEquals(triggerDistance, snapshot.triggerDistance)
        assertEquals(holdDistance, snapshot.holdDistance)
    }

    private fun assertReadySnapshot(snapshot: OffsetSnapshot, measuredHeight: Int) {
        assertEquals(measuredHeight, snapshot.measuredHeight)
        assertTrue("The distance snapshot must follow layout", snapshot.laidOut)
        assertFalse("A pending remeasure must not receive an outdated distance snapshot", snapshot.layoutRequested)
    }

    private fun roleParams(role: KitRefreshLoadLayout.ChildRole, height: Int): KitRefreshLoadLayout.LayoutParams =
        KitRefreshLoadLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, height).apply {
            this.role = role
        }

    private fun measureAndLayout(view: View) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(LAYOUT_WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(LAYOUT_HEIGHT, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, LAYOUT_WIDTH, LAYOUT_HEIGHT)
    }

    private fun render(layout: KitRefreshLoadLayout): Bitmap =
        Bitmap.createBitmap(LAYOUT_WIDTH, LAYOUT_HEIGHT, Bitmap.Config.ARGB_8888).also { bitmap: Bitmap ->
            layout.draw(Canvas(bitmap))
        }

    private fun activeAnimator(layout: KitRefreshLoadLayout): ValueAnimator {
        val field = KitRefreshLoadLayout::class.java.getDeclaredField("activeAnimator").apply { isAccessible = true }
        val animator: Any? = field.get(layout)
        assertNotNull(animator)
        return animator as ValueAnimator
    }

    /** 记录组件最终接收到的尺寸, 以识别未完成重测时发出的快照. */
    private data class OffsetSnapshot(
        val offset: Int,
        val triggerDistance: Int,
        val holdDistance: Int,
        val measuredHeight: Int,
        val laidOut: Boolean,
        val layoutRequested: Boolean,
    )

    /** 提供可在状态回调中改变高度的普通指示器. */
    private class RecordingIndicator(context: Context) : View(context), KitRefreshLoadComponent {
        val states: MutableList<KitRefreshLoadLayout.RefreshLoadState> = mutableListOf()
        val offsets: MutableList<OffsetSnapshot> = mutableListOf()
        var onState: ((KitRefreshLoadLayout.RefreshLoadState) -> Unit)? = null

        override fun onRefreshLoadStateChanged(layout: KitRefreshLoadLayout, state: KitRefreshLoadLayout.RefreshLoadState) {
            states += state
            onState?.invoke(state)
        }

        override fun onRefreshLoadOffsetChanged(
            layout: KitRefreshLoadLayout,
            offset: Int,
            triggerDistance: Int,
            holdDistance: Int,
        ) {
            offsets += OffsetSnapshot(offset, triggerDistance, holdDistance, measuredHeight, isLaidOut, isLayoutRequested)
        }
    }

    /** 高度变化的两种方向共用相同夹具, 避免只覆盖下拉路径. */
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

        fun clearNotifications() {
            header.states.clear()
            header.offsets.clear()
            footer.states.clear()
            footer.offsets.clear()
        }
    }

    /** 刷新和加载的位移符号与状态期望. */
    private enum class Direction(
        val offset: Int,
        val action: KitRefreshLoadLayout.RefreshLoadAction,
        val pullingState: KitRefreshLoadLayout.RefreshLoadState,
        val releaseState: KitRefreshLoadLayout.RefreshLoadState,
        val busyState: KitRefreshLoadLayout.RefreshLoadState,
    ) {
        REFRESH(
            60,
            KitRefreshLoadLayout.RefreshLoadAction.Refresh,
            KitRefreshLoadLayout.RefreshLoadState.PullingDown,
            KitRefreshLoadLayout.RefreshLoadState.ReleaseToRefresh,
            KitRefreshLoadLayout.RefreshLoadState.Refreshing,
        ),
        LOAD_MORE(
            -60,
            KitRefreshLoadLayout.RefreshLoadAction.LoadMore,
            KitRefreshLoadLayout.RefreshLoadState.PullingUp,
            KitRefreshLoadLayout.RefreshLoadState.ReleaseToLoadMore,
            KitRefreshLoadLayout.RefreshLoadState.LoadingMore,
        ),
    }

    private companion object {
        const val SMALL_HEIGHT: Int = 40
        const val LARGE_HEIGHT: Int = 100
        const val LAYOUT_WIDTH: Int = 320
        const val LAYOUT_HEIGHT: Int = 640
        const val ANIMATION_DURATION: Long = 240L
    }
}
