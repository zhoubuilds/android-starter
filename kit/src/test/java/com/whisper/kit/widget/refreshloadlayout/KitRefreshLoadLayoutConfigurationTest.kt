package com.whisper.kit.widget.refreshloadlayout

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 验证运行时触发距离与拖拽状态、组件快照及重入回调保持一致.
 *
 * @author whisper
 * @since 2026/09/11
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KitRefreshLoadLayoutConfigurationTest {

    @Test
    fun nonFiniteDragResistance_isRejectedWithoutChangingPreviousValue() {
        val layout = KitRefreshLoadLayout(RuntimeEnvironment.getApplication()).apply { dragResistance = 3f }
        for (value: Float in floatArrayOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { layout.dragResistance = value }
            assertEquals(3f, layout.dragResistance, 0f)
        }
    }

    @Test
    fun finiteDragResistance_preservesValueOrNormalizesToMinimum() {
        val layout = KitRefreshLoadLayout(RuntimeEnvironment.getApplication())
        for (value: Float in floatArrayOf(-Float.MAX_VALUE, -1f, 0f, 0.5f, 1f, 2.5f, Float.MAX_VALUE)) {
            layout.dragResistance = value

            assertEquals(value.coerceAtLeast(1f), layout.dragResistance, 0f)
        }
    }

    @Test
    fun increasingThreshold_updatesReleaseStateAndSnapshotBeforeReleaseCancels() {
        for (direction: Direction in Direction.entries) {
            val fixture: Fixture = createFixture()
            setThreshold(fixture.layout, direction, 40)
            startPull(fixture, direction)
            assertEquals(direction.releaseState, fixture.layout.state)
            fixture.clearNotifications()

            setThreshold(fixture.layout, direction, 100)

            assertEquals(direction.pullingState, fixture.layout.state)
            assertEquals(direction.offset, fixture.layout.currentOffset)
            assertEquals(listOf(direction.pullingState), fixture.header.states)
            assertEquals(listOf(direction.pullingState), fixture.footer.states)
            assertEquals(OffsetSnapshot(direction.offset, 100, INDICATOR_HEIGHT), fixture.indicator(direction).offsets.last())
            assertTrue(fixture.actions.isEmpty())

            fixture.layout.onStopNestedScroll(fixture.content, ViewCompat.TYPE_TOUCH)

            assertEquals(KitRefreshLoadLayout.RefreshLoadState.Idle, fixture.layout.state)
            assertEquals(0, fixture.layout.currentOffset)
            assertTrue(fixture.actions.isEmpty())
        }
    }

    @Test
    fun decreasingThreshold_updatesPullStateAndSnapshotWithoutSubmittingUntilRelease() {
        for (direction: Direction in Direction.entries) {
            val fixture: Fixture = createFixture()
            startPull(fixture, direction)
            assertEquals(direction.pullingState, fixture.layout.state)
            fixture.clearNotifications()

            setThreshold(fixture.layout, direction, 40)

            assertEquals(direction.releaseState, fixture.layout.state)
            assertEquals(direction.offset, fixture.layout.currentOffset)
            assertEquals(listOf(direction.releaseState), fixture.header.states)
            assertEquals(listOf(direction.releaseState), fixture.footer.states)
            assertEquals(OffsetSnapshot(direction.offset, 40, INDICATOR_HEIGHT), fixture.indicator(direction).offsets.last())
            assertTrue(fixture.actions.isEmpty())

            fixture.layout.onStopNestedScroll(fixture.content, ViewCompat.TYPE_TOUCH)

            assertEquals(direction.busyState, fixture.layout.state)
            assertEquals(listOf(direction.action), fixture.actions)
        }
    }

    @Test
    fun changingThresholdWithoutCrossingIt_updatesSnapshotWithoutRepeatingState() {
        for (direction: Direction in Direction.entries) {
            val fixture: Fixture = createFixture()
            startPull(fixture, direction)
            fixture.clearNotifications()

            setThreshold(fixture.layout, direction, 120)

            assertEquals(direction.pullingState, fixture.layout.state)
            assertEquals(listOf(OffsetSnapshot(direction.offset, 120, INDICATOR_HEIGHT)), fixture.indicator(direction).offsets)
            assertTrue(fixture.header.states.isEmpty())
            assertTrue(fixture.footer.states.isEmpty())
            assertTrue(fixture.actions.isEmpty())
        }
    }

    @Test
    fun equivalentEffectiveThreshold_doesNotRepeatStateOrOffsetCallbacks() {
        for (direction: Direction in Direction.entries) {
            for ((initial: Int, replacement: Int) in listOf(100 to 100, -1 to -20, -1 to INDICATOR_HEIGHT, 0 to 1)) {
                val fixture: Fixture = createFixture()
                setThreshold(fixture.layout, direction, initial)
                startPull(fixture, direction)
                val stateBefore: KitRefreshLoadLayout.RefreshLoadState = fixture.layout.state
                fixture.clearNotifications()

                setThreshold(fixture.layout, direction, replacement)

                assertEquals(replacement, threshold(fixture.layout, direction))
                assertEquals(stateBefore, fixture.layout.state)
                assertEquals(direction.offset, fixture.layout.currentOffset)
                assertTrue(fixture.header.states.isEmpty())
                assertTrue(fixture.footer.states.isEmpty())
                assertTrue(fixture.header.offsets.isEmpty())
                assertTrue(fixture.footer.offsets.isEmpty())
                assertTrue(fixture.actions.isEmpty())
            }
        }
    }

    @Test
    fun changingThresholdDuringBusyReveal_preservesStatePositionAndAnimator() {
        for (direction: Direction in Direction.entries) {
            withAttachedFixture { fixture: Fixture ->
                fixture.layout.animationDurationMillis = ANIMATION_DURATION
                fixture.layout.setActiveAction(direction.action, animate = true)
                val animator: ValueAnimator = activeAnimator(fixture.layout)
                animator.currentPlayTime = 48L
                val offsetBefore: Int = fixture.layout.currentOffset
                assertTrue(kotlin.math.abs(offsetBefore) in 1 until INDICATOR_HEIGHT)
                fixture.clearNotifications()

                setThreshold(fixture.layout, direction, 120)

                assertEquals(direction.busyState, fixture.layout.state)
                assertEquals(offsetBefore, fixture.layout.currentOffset)
                assertSame(animator, activeAnimator(fixture.layout))
                assertEquals(OffsetSnapshot(offsetBefore, 120, INDICATOR_HEIGHT), fixture.indicator(direction).offsets.last())
                assertTrue(fixture.header.states.isEmpty())
                assertTrue(fixture.footer.states.isEmpty())
                assertTrue(fixture.actions.isEmpty())
                animator.end()
                assertEquals(direction.busyState, fixture.layout.state)
            }
        }
    }

    @Test
    fun changingThresholdDuringSettling_preservesStatePositionAndAnimator() {
        for (direction: Direction in Direction.entries) {
            withAttachedFixture { fixture: Fixture ->
                fixture.layout.setActiveAction(direction.action)
                fixture.layout.animationDurationMillis = ANIMATION_DURATION
                fixture.layout.setActiveAction(null, animate = true)
                val animator: ValueAnimator = activeAnimator(fixture.layout)
                animator.currentPlayTime = 48L
                val offsetBefore: Int = fixture.layout.currentOffset
                assertTrue(kotlin.math.abs(offsetBefore) in 1 until INDICATOR_HEIGHT)
                fixture.clearNotifications()

                setThreshold(fixture.layout, direction, 120)

                assertEquals(KitRefreshLoadLayout.RefreshLoadState.Settling, fixture.layout.state)
                assertEquals(offsetBefore, fixture.layout.currentOffset)
                assertSame(animator, activeAnimator(fixture.layout))
                assertEquals(OffsetSnapshot(offsetBefore, 120, INDICATOR_HEIGHT), fixture.indicator(direction).offsets.last())
                assertTrue(fixture.header.states.isEmpty())
                assertTrue(fixture.footer.states.isEmpty())
                assertTrue(fixture.actions.isEmpty())
                animator.end()
                assertEquals(KitRefreshLoadLayout.RefreshLoadState.Idle, fixture.layout.state)
            }
        }
    }

    @Test
    fun stateCallbackFinishingPull_stopsOutdatedStateAndOffsetNotifications() {
        for (direction: Direction in Direction.entries) {
            val fixture: Fixture = createFixture()
            startPull(fixture, direction)
            fixture.clearNotifications()
            fixture.header.onState = { state: KitRefreshLoadLayout.RefreshLoadState ->
                if (state == direction.releaseState) finish(fixture.layout, direction)
            }

            setThreshold(fixture.layout, direction, 40)

            assertEquals(KitRefreshLoadLayout.RefreshLoadState.Idle, fixture.layout.state)
            assertEquals(0, fixture.layout.currentOffset)
            assertFalse(fixture.footer.states.contains(direction.releaseState))
            assertEquals(KitRefreshLoadLayout.RefreshLoadState.Idle, fixture.header.states.last())
            assertEquals(KitRefreshLoadLayout.RefreshLoadState.Idle, fixture.footer.states.last())
            assertEquals(0, fixture.header.offsets.last().offset)
            assertEquals(0, fixture.footer.offsets.last().offset)
            assertEquals(40, fixture.indicator(direction).offsets.last().triggerDistance)
            assertTrue(fixture.actions.isEmpty())
        }
    }

    @Test
    fun stateCallbackChangingOppositeThreshold_preservesBothCurrentSnapshots() {
        for (direction: Direction in Direction.entries) {
            val fixture: Fixture = createFixture()
            startPull(fixture, direction)
            fixture.clearNotifications()
            var changedOpposite: Boolean = false
            fixture.header.onState = { state: KitRefreshLoadLayout.RefreshLoadState ->
                if (state == direction.releaseState && !changedOpposite) {
                    changedOpposite = true
                    setThreshold(fixture.layout, direction.opposite, 140)
                }
            }

            setThreshold(fixture.layout, direction, 40)

            assertTrue(changedOpposite)
            assertEquals(direction.releaseState, fixture.layout.state)
            assertEquals(direction.releaseState, fixture.header.states.last())
            assertEquals(direction.releaseState, fixture.footer.states.last())
            assertEquals(OffsetSnapshot(direction.offset, 40, INDICATOR_HEIGHT), fixture.indicator(direction).offsets.last())
            assertEquals(OffsetSnapshot(0, 140, INDICATOR_HEIGHT), fixture.indicator(direction.opposite).offsets.last())
            assertEquals(direction.offset, fixture.layout.currentOffset)
            assertTrue(fixture.actions.isEmpty())
        }
    }

    @Test
    fun offsetCallbackChangingThresholdsAgain_keepsNewestStateAndSnapshots() {
        for (direction: Direction in Direction.entries) {
            val fixture: Fixture = createFixture()
            startPull(fixture, direction)
            fixture.clearNotifications()
            var changedAgain: Boolean = false
            fixture.indicator(direction).onOffset = { snapshot: OffsetSnapshot ->
                if (snapshot.triggerDistance == 40 && !changedAgain) {
                    changedAgain = true
                    setThreshold(fixture.layout, direction, 120)
                    setThreshold(fixture.layout, direction.opposite, 140)
                    fixture.indicator(direction).requestLayout()
                }
            }

            setThreshold(fixture.layout, direction, 40)

            assertTrue(changedAgain)
            assertEquals(direction.pullingState, fixture.layout.state)
            assertEquals(direction.pullingState, fixture.header.states.last())
            assertEquals(direction.pullingState, fixture.footer.states.last())
            val snapshots: List<OffsetSnapshot> = fixture.indicator(direction).offsets
            val latestIndex: Int = snapshots.indexOfFirst { it.triggerDistance == 120 }
            assertTrue(latestIndex >= 0)
            assertTrue(snapshots.drop(latestIndex).all { it.triggerDistance == 120 })
            assertEquals(OffsetSnapshot(direction.offset, 120, INDICATOR_HEIGHT), snapshots.last())
            assertEquals(OffsetSnapshot(0, 140, INDICATOR_HEIGHT), fixture.indicator(direction.opposite).offsets.last())
            assertEquals(direction.offset, fixture.layout.currentOffset)
            assertTrue(fixture.actions.isEmpty())

            val notificationCount: Int = snapshots.size
            measureAndLayout(fixture.layout)

            assertEquals(
                "Relayout must retain the snapshot delivered by the reentrant callback", notificationCount, snapshots.size,
            )
        }
    }

    @Test
    fun offsetCallbackFinishingPull_doesNotRestoreOutdatedNonzeroSnapshot() {
        for (direction: Direction in Direction.entries) {
            val fixture: Fixture = createFixture()
            startPull(fixture, direction)
            fixture.clearNotifications()
            fixture.indicator(direction).onOffset = { snapshot: OffsetSnapshot ->
                if (snapshot.offset != 0) finish(fixture.layout, direction)
            }

            setThreshold(fixture.layout, direction, 120)

            assertEquals(KitRefreshLoadLayout.RefreshLoadState.Idle, fixture.layout.state)
            assertEquals(0, fixture.layout.currentOffset)
            assertEquals(0, fixture.header.offsets.last().offset)
            assertEquals(0, fixture.footer.offsets.last().offset)
            assertEquals(120, fixture.indicator(direction).offsets.last().triggerDistance)
            assertTrue(fixture.actions.isEmpty())
        }
    }

    private fun createFixture(context: Context = RuntimeEnvironment.getApplication()): Fixture {
        val layout = KitRefreshLoadLayout(context).apply {
            dragResistance = 1f
            refreshTriggerDistance = 100
            loadMoreTriggerDistance = 100
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

    private fun withAttachedFixture(block: (Fixture) -> Unit) {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val fixture: Fixture = createFixture(controller.get())
            controller.get().setContentView(fixture.layout)
            measureAndLayout(fixture.layout)
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

    private fun setThreshold(layout: KitRefreshLoadLayout, direction: Direction, value: Int) {
        when (direction) {
            Direction.REFRESH -> layout.refreshTriggerDistance = value
            Direction.LOAD_MORE -> layout.loadMoreTriggerDistance = value
        }
    }

    private fun threshold(layout: KitRefreshLoadLayout, direction: Direction): Int = when (direction) {
        Direction.REFRESH -> layout.refreshTriggerDistance
        Direction.LOAD_MORE -> layout.loadMoreTriggerDistance
    }

    private fun finish(layout: KitRefreshLoadLayout, direction: Direction) {
        when (direction) {
            Direction.REFRESH -> layout.finishRefresh(animate = false)
            Direction.LOAD_MORE -> layout.finishLoadMore(animate = false)
        }
    }

    private fun roleParams(role: KitRefreshLoadLayout.ChildRole): KitRefreshLoadLayout.LayoutParams =
        KitRefreshLoadLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, INDICATOR_HEIGHT).apply {
            this.role = role
        }

    private fun measureAndLayout(layout: KitRefreshLoadLayout) {
        layout.measure(
            View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY),
        )
        layout.layout(0, 0, 320, 640)
    }

    private fun activeAnimator(layout: KitRefreshLoadLayout): ValueAnimator {
        val field = KitRefreshLoadLayout::class.java.getDeclaredField("activeAnimator").apply { isAccessible = true }
        val animator: Any? = field.get(layout)
        assertNotNull(animator)
        return animator as ValueAnimator
    }

    /** 收集组件公开回调, 并允许用例从回调中改变配置或完成动作. */
    private class RecordingIndicator(context: Context) : View(context), KitRefreshLoadComponent {
        val states: MutableList<KitRefreshLoadLayout.RefreshLoadState> = mutableListOf()
        val offsets: MutableList<OffsetSnapshot> = mutableListOf()
        var onState: ((KitRefreshLoadLayout.RefreshLoadState) -> Unit)? = null
        var onOffset: ((OffsetSnapshot) -> Unit)? = null

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
            val snapshot = OffsetSnapshot(offset, triggerDistance, holdDistance)
            offsets += snapshot
            onOffset?.invoke(snapshot)
        }
    }

    /** 用来比较一次组件通知中的完整几何快照. */
    private data class OffsetSnapshot(val offset: Int, val triggerDistance: Int, val holdDistance: Int)

    /** 双向配置验证共用相同参数和回调断言. */
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

    /** 刷新与加载在阈值更新时应当保持对称. */
    private enum class Direction(
        val offset: Int,
        val action: KitRefreshLoadLayout.RefreshLoadAction,
        val pullingState: KitRefreshLoadLayout.RefreshLoadState,
        val releaseState: KitRefreshLoadLayout.RefreshLoadState,
        val busyState: KitRefreshLoadLayout.RefreshLoadState,
    ) {
        REFRESH(
            80,
            KitRefreshLoadLayout.RefreshLoadAction.Refresh,
            KitRefreshLoadLayout.RefreshLoadState.PullingDown,
            KitRefreshLoadLayout.RefreshLoadState.ReleaseToRefresh,
            KitRefreshLoadLayout.RefreshLoadState.Refreshing,
        ),
        LOAD_MORE(
            -80,
            KitRefreshLoadLayout.RefreshLoadAction.LoadMore,
            KitRefreshLoadLayout.RefreshLoadState.PullingUp,
            KitRefreshLoadLayout.RefreshLoadState.ReleaseToLoadMore,
            KitRefreshLoadLayout.RefreshLoadState.LoadingMore,
        );

        val opposite: Direction
            get() = if (this == REFRESH) LOAD_MORE else REFRESH
    }

    private companion object {
        const val INDICATOR_HEIGHT: Int = 60
        const val ANIMATION_DURATION: Long = 240L
    }
}
