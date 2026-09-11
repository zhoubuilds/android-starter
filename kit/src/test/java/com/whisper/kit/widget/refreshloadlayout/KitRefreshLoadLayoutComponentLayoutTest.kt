package com.whisper.kit.widget.refreshloadlayout

import android.app.Activity
import android.content.Context
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/**
 * 验证组件更新文案引起的重新布局能够收敛, 并保留真实尺寸变化后的快照与初始定位.
 *
 * @author whisper
 * @since 2026/09/11
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KitRefreshLoadLayoutComponentLayoutTest {

    @Test
    fun wrapContentTextIndicator_updatesTextAndCompletesInitialBusyPosition() {
        verifyInitialBusyPosition(IndicatorStructure.TEXT)
    }

    @Test
    fun fixedContainerWithWrapContentText_updatesTextAndCompletesInitialBusyPosition() {
        verifyInitialBusyPosition(IndicatorStructure.CONTAINER)
    }

    @Test
    fun offsetCallbackChangesHeight_waitsForMeasuredSizeBeforeInitialBusyPosition() {
        for (structure: IndicatorStructure in IndicatorStructure.entries) {
            for (action: KitRefreshLoadLayout.RefreshLoadAction in KitRefreshLoadLayout.RefreshLoadAction.entries) {
                withWindowFixture(
                    action,
                    structure,
                    beforeAttach = { fixture: Fixture ->
                        var resized: Boolean = false
                        fixture.recorder.onSnapshot = { component: View, _: Snapshot ->
                            if (!resized) {
                                resized = true
                                component.layoutParams = component.layoutParams.apply { height = RESIZED_HEIGHT }
                            }
                        }
                    },
                ) { fixture: Fixture ->
                    assertEquals(RESIZED_HEIGHT, fixture.indicator.measuredHeight)
                    assertEquals(signedOffset(action, RESIZED_HEIGHT), fixture.layout.currentOffset)
                    assertSnapshot(fixture, RESIZED_HEIGHT)
                    assertConverged(fixture)
                    assertTrue(fixture.actions.isEmpty())
                }
            }
        }
    }

    @Test
    fun externalHeightChange_deliversUpdatedDistancesAndConvergesAgain() {
        for (structure: IndicatorStructure in IndicatorStructure.entries) {
            for (action: KitRefreshLoadLayout.RefreshLoadAction in KitRefreshLoadLayout.RefreshLoadAction.entries) {
                withWindowFixture(action, structure) { fixture: Fixture ->
                    assertConverged(fixture)
                    val previousCount: Int = fixture.recorder.snapshots.size

                    fixture.indicator.layoutParams = fixture.indicator.layoutParams.apply { height = RESIZED_HEIGHT }
                    advanceWindowLayouts()

                    assertTrue("Resizing the indicator must deliver its new distances", fixture.recorder.snapshots.size > previousCount)
                    assertEquals(signedOffset(action, INITIAL_HEIGHT), fixture.layout.currentOffset)
                    assertSnapshot(fixture, RESIZED_HEIGHT)
                    assertConverged(fixture)
                    assertTrue(fixture.actions.isEmpty())
                }
            }
        }
    }

    @Test
    fun offsetCallbackChangesOnlyWidth_deliversRemeasuredSnapshotBeforePositioning() {
        for (structure: IndicatorStructure in IndicatorStructure.entries) {
            for (action: KitRefreshLoadLayout.RefreshLoadAction in KitRefreshLoadLayout.RefreshLoadAction.entries) {
                withWindowFixture(
                    action,
                    structure,
                    beforeAttach = { fixture: Fixture ->
                        var resized: Boolean = false
                        fixture.recorder.onSnapshot = { component: View, _: Snapshot ->
                            if (!resized) {
                                resized = true
                                component.layoutParams = component.layoutParams.apply { width = RESIZED_WIDTH }
                            }
                        }
                    },
                ) { fixture: Fixture ->
                    assertEquals(RESIZED_WIDTH, fixture.indicator.measuredWidth)
                    assertTrue(
                        "A width change must deliver a new snapshot even when all distances are unchanged",
                        fixture.recorder.snapshots.any { snapshot: Snapshot -> snapshot.offset == 0 && snapshot.width == RESIZED_WIDTH },
                    )
                    assertEquals(signedOffset(action, INITIAL_HEIGHT), fixture.layout.currentOffset)
                    assertSnapshot(fixture, INITIAL_HEIGHT)
                    assertConverged(fixture)
                }
            }
        }
    }

    @Test
    fun removingAndReaddingSameIndicator_deliversInitialSnapshotAgain() {
        for (structure: IndicatorStructure in IndicatorStructure.entries) {
            for (action: KitRefreshLoadLayout.RefreshLoadAction in KitRefreshLoadLayout.RefreshLoadAction.entries) {
                withWindowFixture(action, structure) { fixture: Fixture ->
                    assertConverged(fixture)
                    val previousCount: Int = fixture.recorder.snapshots.size
                    val params: ViewGroup.LayoutParams = fixture.indicator.layoutParams

                    fixture.layout.removeView(fixture.indicator)
                    fixture.layout.addView(fixture.indicator, params)
                    advanceWindowLayouts()

                    assertTrue(
                        "Readding the same instance must initialize its current offset", fixture.recorder.snapshots.size > previousCount,
                    )
                    assertSnapshot(fixture, INITIAL_HEIGHT)
                    assertConverged(fixture)
                    assertTrue(fixture.actions.isEmpty())
                }
            }
        }
    }

    private fun verifyInitialBusyPosition(structure: IndicatorStructure) {
        for (action: KitRefreshLoadLayout.RefreshLoadAction in KitRefreshLoadLayout.RefreshLoadAction.entries) {
            withWindowFixture(action, structure) { fixture: Fixture ->
                assertEquals(signedOffset(action, INITIAL_HEIGHT), fixture.layout.currentOffset)
                assertSnapshot(fixture, INITIAL_HEIGHT)
                assertConverged(fixture)
                assertTrue(fixture.actions.isEmpty())
            }
        }
    }

    private fun assertSnapshot(fixture: Fixture, expectedHeight: Int) {
        val snapshot: Snapshot = fixture.recorder.snapshots.last()
        assertEquals(fixture.layout.currentOffset, snapshot.offset)
        assertEquals(expectedHeight, snapshot.height)
        assertEquals(expectedHeight, snapshot.triggerDistance)
        assertEquals(expectedHeight, snapshot.holdDistance)
        assertTrue("Offset snapshots require completed layout", snapshot.laidOut)
        assertFalse("Offset snapshots must wait for requested layout", snapshot.layoutRequested)
    }

    private fun assertConverged(fixture: Fixture) {
        assertFalse("Repeated offset snapshots must converge before the loop safety limit", fixture.recorder.hitLoopSafetyLimit)
        val previousCount: Int = fixture.recorder.snapshots.size
        advanceWindowLayouts()
        assertEquals("An unchanged indicator must stop receiving layout snapshots", previousCount, fixture.recorder.snapshots.size)
        assertFalse("The indicator must finish layout after rendering its text", fixture.indicator.isLayoutRequested)
        assertFalse("The container must finish its pending layout", fixture.layout.isLayoutRequested)
    }

    private fun withWindowFixture(
        action: KitRefreshLoadLayout.RefreshLoadAction,
        structure: IndicatorStructure,
        beforeAttach: (Fixture) -> Unit = {},
        block: (Fixture) -> Unit,
    ) {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val context: Context = controller.get()
            val recorder = SnapshotRecorder()
            val indicator: View = when (structure) {
                IndicatorStructure.TEXT -> TextIndicator(context, recorder)
                IndicatorStructure.CONTAINER -> ContainerIndicator(context, recorder)
            }
            val layout = KitRefreshLoadLayout(context)
            val actions: MutableList<KitRefreshLoadLayout.RefreshLoadAction> = mutableListOf()
            layout.addView(View(context))
            layout.addView(
                indicator,
                KitRefreshLoadLayout.LayoutParams(
                    if (structure == IndicatorStructure.TEXT) ViewGroup.LayoutParams.WRAP_CONTENT else ViewGroup.LayoutParams.MATCH_PARENT,
                    INITIAL_HEIGHT,
                ).apply {
                    role = when (action) {
                        KitRefreshLoadLayout.RefreshLoadAction.Refresh -> KitRefreshLoadLayout.ChildRole.Header
                        KitRefreshLoadLayout.RefreshLoadAction.LoadMore -> KitRefreshLoadLayout.ChildRole.Footer
                    }
                },
            )
            layout.setOnRefreshLoadListener(actions::add)
            val fixture = Fixture(layout, indicator, recorder, actions)
            beforeAttach(fixture)
            layout.setActiveAction(action)
            controller.get().setContentView(layout, ViewGroup.LayoutParams(300, 500))
            controller.visible()
            advanceWindowLayouts()
            assertTrue("Component layout tests require an attached view", layout.isAttachedToWindow)
            assertTrue("The window must complete initial indicator layout", indicator.isLaidOut)
            block(fixture)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun advanceWindowLayouts() {
        // 通过 ViewRoot 处理组件更新文案后的 requestLayout, 不手动测量和布局子 View.
        shadowOf(Looper.getMainLooper()).idleFor(200L, TimeUnit.MILLISECONDS)
    }

    private fun signedOffset(action: KitRefreshLoadLayout.RefreshLoadAction, height: Int): Int = when (action) {
        KitRefreshLoadLayout.RefreshLoadAction.Refresh -> height
        KitRefreshLoadLayout.RefreshLoadAction.LoadMore -> -height
    }

    /** 记录回调前的实际尺寸, 用更新普通文案产生真实的布局请求. */
    private class SnapshotRecorder {
        val snapshots: MutableList<Snapshot> = mutableListOf()
        var hitLoopSafetyLimit: Boolean = false
        var onSnapshot: ((View, Snapshot) -> Unit)? = null

        fun onOffset(component: View, label: TextView, offset: Int, triggerDistance: Int, holdDistance: Int) {
            val snapshot = Snapshot(
                offset, triggerDistance, holdDistance,
                component.measuredWidth, component.measuredHeight, component.isLaidOut, component.isLayoutRequested,
            )
            snapshots += snapshot
            onSnapshot?.invoke(component, snapshot)
            // 回归时停止继续制造布局请求, 让测试以明确断言失败而不是卡住主 Looper.
            if (snapshots.size >= LOOP_SAFETY_LIMIT) {
                hitLoopSafetyLimit = true
                return
            }
            label.text = "Offset $offset"
        }
    }

    /** 组件自身使用 wrap_content 宽度. */
    private class TextIndicator(context: Context, private val recorder: SnapshotRecorder) : TextView(context), KitRefreshLoadComponent {
        override fun onRefreshLoadStateChanged(layout: KitRefreshLoadLayout, state: KitRefreshLoadLayout.RefreshLoadState) = Unit

        override fun onRefreshLoadOffsetChanged(layout: KitRefreshLoadLayout, offset: Int, triggerDistance: Int, holdDistance: Int) {
            recorder.onOffset(this, this, offset, triggerDistance, holdDistance)
        }
    }

    /** 固定组件内的文案使用 wrap_content 宽度. */
    private class ContainerIndicator(context: Context, private val recorder: SnapshotRecorder) :
        FrameLayout(context), KitRefreshLoadComponent {
        private val label = TextView(context)

        init {
            addView(label, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        override fun onRefreshLoadStateChanged(layout: KitRefreshLoadLayout, state: KitRefreshLoadLayout.RefreshLoadState) = Unit

        override fun onRefreshLoadOffsetChanged(layout: KitRefreshLoadLayout, offset: Int, triggerDistance: Int, holdDistance: Int) {
            recorder.onOffset(this, label, offset, triggerDistance, holdDistance)
        }
    }

    private enum class IndicatorStructure { TEXT, CONTAINER }

    private data class Snapshot(
        val offset: Int,
        val triggerDistance: Int,
        val holdDistance: Int,
        val width: Int,
        val height: Int,
        val laidOut: Boolean,
        val layoutRequested: Boolean,
    )

    private data class Fixture(
        val layout: KitRefreshLoadLayout,
        val indicator: View,
        val recorder: SnapshotRecorder,
        val actions: MutableList<KitRefreshLoadLayout.RefreshLoadAction>,
    )

    private companion object {
        private const val INITIAL_HEIGHT: Int = 60
        private const val RESIZED_HEIGHT: Int = 96
        private const val RESIZED_WIDTH: Int = 180
        private const val LOOP_SAFETY_LIMIT: Int = 20
    }
}
