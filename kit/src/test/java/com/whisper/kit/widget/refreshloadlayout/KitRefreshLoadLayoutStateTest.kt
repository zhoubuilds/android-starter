package com.whisper.kit.widget.refreshloadlayout

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import com.whisper.kit.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 验证刷新加载容器的回弹生命周期、组件同步回调和角色布局.
 *
 * @author whisper
 * @since 2026/09/11
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KitRefreshLoadLayoutStateTest {

    @Test
    fun finishingAnimation_keepsSettlingAndCannotSubmitAgainOnNestedStop() {
        for (direction: Direction in Direction.entries) {
            withAttachedFixture { fixture: Fixture, _: FrameLayout ->
                begin(fixture, direction)
                fixture.layout.animationDurationMillis = ANIMATION_DURATION
                finish(fixture.layout, direction, animate = true)

                val animator: ValueAnimator = activeAnimator(fixture.layout)
                animator.currentPlayTime = 48L

                assertTrue("Animation must still be above the trigger", kotlin.math.abs(fixture.layout.currentOffset) >= TRIGGER)
                assertEquals(KitRefreshLoadLayout.RefreshLoadState.Settling, fixture.layout.state)
                fixture.layout.onStopNestedScroll(fixture.content, ViewCompat.TYPE_TOUCH)
                assertEquals(listOf(direction.action), fixture.actions)
                assertEquals(KitRefreshLoadLayout.RefreshLoadState.Settling, fixture.layout.state)

                animator.end()
                assertEquals(0, fixture.layout.currentOffset)
                assertEquals(KitRefreshLoadLayout.RefreshLoadState.Idle, fixture.layout.state)
                assertEquals(listOf(direction.action), fixture.actions)
            }
        }
    }

    @Test
    fun directDragTakingOverSettling_releasesAfterFirstInterceptedMoveWithoutAnotherMove() {
        for (direction: Direction in Direction.entries) {
            withAttachedFixture { fixture: Fixture, _: FrameLayout ->
                val contentEvents: MutableList<Int> = mutableListOf()
                fixture.content.isClickable = true
                fixture.content.setOnTouchListener { _, event: MotionEvent ->
                    contentEvents += event.actionMasked
                    true
                }
                begin(fixture, direction)
                fixture.layout.animationDurationMillis = ANIMATION_DURATION
                finish(fixture.layout, direction, animate = true)
                activeAnimator(fixture.layout).currentPlayTime = 96L
                val offsetBeforeGesture: Int = fixture.layout.currentOffset
                assertTrue(kotlin.math.abs(offsetBeforeGesture) in 1 until TRIGGER)

                val downY: Float = LAYOUT_HEIGHT / 2f
                val moveY: Float = downY - direction.scrollDistance
                dispatchTouch(fixture.layout, MotionEvent.ACTION_DOWN, downY, 0L)
                dispatchTouch(fixture.layout, MotionEvent.ACTION_MOVE, moveY, 16L)

                assertEquals(listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL), contentEvents)
                assertEquals("Intercepting alone must not move the content", offsetBeforeGesture, fixture.layout.currentOffset)
                dispatchTouch(fixture.layout, MotionEvent.ACTION_UP, moveY, 32L)
                activeAnimator(fixture.layout).end()

                assertEquals(0, fixture.layout.currentOffset)
                assertEquals(KitRefreshLoadLayout.RefreshLoadState.Idle, fixture.layout.state)
                assertEquals(listOf(direction.action), fixture.actions)
            }
        }
    }

    @Test
    fun nestedDragTakingOverSettling_commitsNewGestureWhenMaximumClampsOffsetUnchanged() {
        for (direction: Direction in Direction.entries) {
            withAttachedFixture { fixture: Fixture, _: FrameLayout ->
                fixture.layout.maxDragDistance = kotlin.math.abs(direction.scrollDistance)
                pull(fixture, direction)
                fixture.layout.animationDurationMillis = ANIMATION_DURATION
                finish(fixture.layout, direction, animate = true)
                fixture.layout.onStopNestedScroll(fixture.content, ViewCompat.TYPE_TOUCH)
                val offsetBeforeGesture: Int = fixture.layout.currentOffset
                assertEquals(fixture.layout.maxDragDistance, kotlin.math.abs(offsetBeforeGesture))
                assertEquals(KitRefreshLoadLayout.RefreshLoadState.Settling, fixture.layout.state)

                pull(fixture, direction)

                assertEquals("The outward drag must remain clamped", offsetBeforeGesture, fixture.layout.currentOffset)
                fixture.layout.onStopNestedScroll(fixture.content, ViewCompat.TYPE_TOUCH)

                assertEquals(direction.state, fixture.layout.state)
                assertEquals(listOf(direction.action), fixture.actions)
                activeAnimator(fixture.layout).end()
                assertEquals(direction.state, fixture.layout.state)
                assertEquals(fixture.indicator(direction).measuredHeight, kotlin.math.abs(fixture.layout.currentOffset))
            }
        }
    }

    @Test
    fun detachDuringFinishing_clearsOffsetBeforeReattachment() {
        for (direction: Direction in Direction.entries) {
            withAttachedFixture { fixture: Fixture, parent: FrameLayout ->
                begin(fixture, direction)
                fixture.layout.animationDurationMillis = ANIMATION_DURATION
                finish(fixture.layout, direction, animate = true)
                activeAnimator(fixture.layout).currentPlayTime = 48L
                assertTrue(fixture.layout.currentOffset != 0)

                parent.removeView(fixture.layout)

                assertFalse(fixture.layout.isAttachedToWindow)
                assertEquals(0, fixture.layout.currentOffset)
                assertEquals(KitRefreshLoadLayout.RefreshLoadState.Idle, fixture.layout.state)
                parent.addView(fixture.layout)
                measureAndLayout(fixture.layout)
                assertTrue(fixture.layout.isAttachedToWindow)
                assertEquals(0f, fixture.content.translationY, 0f)
                assertEquals(0f, fixture.header.translationY, 0f)
                assertEquals(0f, fixture.footer.translationY, 0f)
                assertEquals(listOf(direction.action), fixture.actions)
            }
        }
    }

    @Test
    fun detachDuringUnreleasedPull_clearsOffsetWithoutSubmitting() {
        for (direction: Direction in Direction.entries) {
            withAttachedFixture { fixture: Fixture, parent: FrameLayout ->
                pull(fixture, direction)
                assertTrue(fixture.layout.currentOffset != 0)

                parent.removeView(fixture.layout)
                parent.addView(fixture.layout)
                measureAndLayout(fixture.layout)
                fixture.layout.onStopNestedScroll(fixture.content, ViewCompat.TYPE_TOUCH)

                assertEquals(0, fixture.layout.currentOffset)
                assertEquals(KitRefreshLoadLayout.RefreshLoadState.Idle, fixture.layout.state)
                assertTrue(fixture.actions.isEmpty())
            }
        }
    }

    @Test
    fun detachDuringSubmittedTask_preservesTaskWithoutRepeatingCallback() {
        for (direction: Direction in Direction.entries) {
            withAttachedFixture { fixture: Fixture, parent: FrameLayout ->
                fixture.layout.animationDurationMillis = ANIMATION_DURATION
                begin(fixture, direction)
                activeAnimator(fixture.layout).currentPlayTime = 48L

                parent.removeView(fixture.layout)
                parent.addView(fixture.layout)
                measureAndLayout(fixture.layout)
                fixture.layout.onStopNestedScroll(fixture.content, ViewCompat.TYPE_TOUCH)

                assertEquals(direction.state, fixture.layout.state)
                assertEquals(listOf(direction.action), fixture.actions)
                finish(fixture.layout, direction, animate = false)
                assertEquals(0, fixture.layout.currentOffset)
                assertEquals(KitRefreshLoadLayout.RefreshLoadState.Idle, fixture.layout.state)
            }
        }
    }

    @Test
    fun synchronousFinishFromStateCallback_preventsStaleStateAndBusinessNotification() {
        for (direction: Direction in Direction.entries) {
            val fixture: Fixture = createFixture()
            fixture.header.onState = { layout: KitRefreshLoadLayout, state: KitRefreshLoadLayout.RefreshLoadState ->
                if (state == direction.state) {
                    finish(layout, direction, animate = false)
                }
            }
            fixture.footer.states.clear()

            begin(fixture, direction)

            assertEquals(0, fixture.layout.currentOffset)
            assertEquals(KitRefreshLoadLayout.RefreshLoadState.Idle, fixture.layout.state)
            assertTrue(fixture.actions.isEmpty())
            assertFalse("A completed operation must not reach the next component", direction.state in fixture.footer.states)
        }
    }

    @Test
    fun synchronousDirectionDisableFromStateCallback_preventsReopeningAndBusinessNotification() {
        for (direction: Direction in Direction.entries) {
            val fixture: Fixture = createFixture()
            fixture.header.onState = { layout: KitRefreshLoadLayout, state: KitRefreshLoadLayout.RefreshLoadState ->
                if (state == direction.state) {
                    when (direction) {
                        Direction.Refresh -> layout.refreshEnabled = false
                        Direction.LoadMore -> layout.loadMoreEnabled = false
                    }
                }
            }
            fixture.footer.states.clear()

            begin(fixture, direction)

            assertEquals(0, fixture.layout.currentOffset)
            assertEquals(KitRefreshLoadLayout.RefreshLoadState.Idle, fixture.layout.state)
            assertTrue(fixture.actions.isEmpty())
            assertFalse(direction.state in fixture.footer.states)
        }
    }

    @Test
    fun synchronousViewDisableBeforeBusinessNotification_abortsPendingOperation() {
        for (direction: Direction in Direction.entries) {
            val fixture: Fixture = createFixture()
            fixture.header.onState = { layout: KitRefreshLoadLayout, state: KitRefreshLoadLayout.RefreshLoadState ->
                if (state == direction.state) {
                    layout.isEnabled = false
                }
            }
            fixture.footer.states.clear()

            begin(fixture, direction)

            assertFalse(fixture.layout.isEnabled)
            assertEquals(0, fixture.layout.currentOffset)
            assertEquals(KitRefreshLoadLayout.RefreshLoadState.Idle, fixture.layout.state)
            assertTrue(fixture.actions.isEmpty())
            assertFalse(direction.state in fixture.footer.states)
        }
    }

    @Test
    fun synchronousFinishFromHoldOffsetCallback_preventsBusinessNotification() {
        for (direction: Direction in Direction.entries) {
            val fixture: Fixture = createFixture()
            val component: RecordingIndicator = fixture.indicator(direction)
            component.onOffset = { layout: KitRefreshLoadLayout, _: OffsetSnapshot ->
                if (layout.state == direction.state) {
                    finish(layout, direction, animate = false)
                }
            }

            begin(fixture, direction)

            assertEquals(0, fixture.layout.currentOffset)
            assertEquals(KitRefreshLoadLayout.RefreshLoadState.Idle, fixture.layout.state)
            assertTrue(fixture.actions.isEmpty())
        }
    }

    @Test
    fun componentReplacementFromStateCallback_doesNotNotifyRemovedView() {
        val fixture: Fixture = createFixture()
        val replacement = RecordingIndicator(fixture.layout.context)
        fixture.header.onState = { layout: KitRefreshLoadLayout, state: KitRefreshLoadLayout.RefreshLoadState ->
            if (state == KitRefreshLoadLayout.RefreshLoadState.Refreshing) {
                layout.removeView(fixture.footer)
                layout.addView(replacement, roleParams(KitRefreshLoadLayout.ChildRole.Footer, 80, 95))
            }
        }
        fixture.footer.states.clear()

        begin(fixture, Direction.Refresh)
        measureAndLayout(fixture.layout)

        assertFalse(KitRefreshLoadLayout.RefreshLoadState.Refreshing in fixture.footer.states)
        assertEquals(KitRefreshLoadLayout.RefreshLoadState.Refreshing, replacement.states.last())
        assertEquals(OffsetSnapshot(0, TRIGGER, 95, 95), replacement.offsets.last())
        assertEquals(listOf(KitRefreshLoadLayout.RefreshLoadAction.Refresh), fixture.actions)
    }

    @Test
    fun replacingActiveComponentWhileBusy_receivesMeasuredStateAndOffsetSnapshot() {
        for (direction: Direction in Direction.entries) {
            val fixture: Fixture = createFixture()
            begin(fixture, direction)
            val oldComponent: RecordingIndicator = fixture.indicator(direction)
            val replacement = RecordingIndicator(fixture.layout.context)

            fixture.layout.removeView(oldComponent)
            fixture.layout.addView(replacement, roleParams(direction.role, 80, 95))
            measureAndLayout(fixture.layout)

            assertEquals(direction.state, fixture.layout.state)
            assertEquals(direction.state, replacement.states.last())
            assertEquals(OffsetSnapshot(fixture.layout.currentOffset, TRIGGER, 95, 95), replacement.offsets.last())
            assertEquals(fixture.layout.currentOffset.toFloat(), replacement.translationY, 0f)
            assertEquals(listOf(direction.action), fixture.actions)
        }
    }

    @Test
    fun addingMissingComponents_receivesInitialMeasuredSnapshot() {
        val layout = KitRefreshLoadLayout(RuntimeEnvironment.getApplication())
        layout.addView(View(layout.context))
        measureAndLayout(layout)
        val header = RecordingIndicator(layout.context)
        val footer = RecordingIndicator(layout.context)

        layout.addView(header, roleParams(KitRefreshLoadLayout.ChildRole.Header, 80, HEADER_HEIGHT))
        layout.addView(footer, roleParams(KitRefreshLoadLayout.ChildRole.Footer, 80, FOOTER_HEIGHT))
        measureAndLayout(layout)

        assertTrue(layout.refreshEnabled)
        assertTrue(layout.loadMoreEnabled)
        assertEquals(KitRefreshLoadLayout.RefreshLoadState.Idle, header.states.last())
        assertEquals(KitRefreshLoadLayout.RefreshLoadState.Idle, footer.states.last())
        assertEquals(OffsetSnapshot(0, HEADER_HEIGHT, HEADER_HEIGHT, HEADER_HEIGHT), header.offsets.first())
        assertEquals(OffsetSnapshot(0, FOOTER_HEIGHT, FOOTER_HEIGHT, FOOTER_HEIGHT), footer.offsets.first())
    }

    @Test
    fun defaultComponentsAfterActiveComponentRemoval_receiveCurrentMeasuredSnapshot() {
        for (direction: Direction in Direction.entries) {
            val context = IndicatorInflationContext(RuntimeEnvironment.getApplication())
            val attrs: AttributeSet = Robolectric.buildAttributeSet()
                .addAttribute(R.attr.kitRefreshLoadHeaderLayout, "@android:layout/simple_list_item_1")
                .addAttribute(R.attr.kitRefreshLoadFooterLayout, "@android:layout/simple_list_item_1")
                .build()
            val fixture: Fixture = createFixture(context, attrs)
            begin(fixture, direction)

            fixture.layout.removeView(fixture.indicator(direction))
            measureAndLayout(fixture.layout)
            val replacement: RecordingIndicator = (0 until fixture.layout.childCount)
                .map(fixture.layout::getChildAt)
                .first { child: View ->
                    (child.layoutParams as KitRefreshLoadLayout.LayoutParams).role == direction.role
                } as RecordingIndicator

            assertEquals(direction.state, fixture.layout.state)
            assertEquals(direction.state, replacement.states.last())
            assertTrue("Default component must have measured dimensions", replacement.measuredHeight > 0)
            assertEquals(
                OffsetSnapshot(fixture.layout.currentOffset, TRIGGER, replacement.measuredHeight, replacement.measuredHeight),
                replacement.offsets.first(),
            )
            assertEquals(listOf(direction.action), fixture.actions)
        }
    }

    @Test
    fun contentGravity_matchesFrameLayoutWithPaddingMarginsAndRtl() {
        val context: Context = RuntimeEnvironment.getApplication()
        val originalFlags: Int = context.applicationInfo.flags
        context.applicationInfo.flags = originalFlags or ApplicationInfo.FLAG_SUPPORTS_RTL
        try {
            for (direction: Int in listOf(View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL)) {
                for (gravity: Int in listOf(-1, Gravity.CENTER, Gravity.END or Gravity.BOTTOM, Gravity.START or Gravity.TOP)) {
                    for (childWidth: Int in listOf(70, ViewGroup.LayoutParams.MATCH_PARENT)) {
                        val layout = KitRefreshLoadLayout(context)
                        val expectedParent = FrameLayout(context)
                        for (parent: FrameLayout in listOf(layout, expectedParent)) {
                            parent.layoutDirection = direction
                            parent.setPadding(11, 17, 23, 29)
                        }
                        val actual = View(context)
                        val expected = View(context)
                        val actualParams = KitRefreshLoadLayout.LayoutParams(childWidth, 90).apply {
                            this.gravity = gravity
                            setMargins(7, 13, 19, 31)
                        }
                        val expectedParams = FrameLayout.LayoutParams(childWidth, 90, gravity).apply {
                            setMargins(7, 13, 19, 31)
                        }
                        layout.addView(actual, actualParams)
                        expectedParent.addView(expected, expectedParams)

                        measureAndLayout(layout)
                        measureAndLayout(expectedParent)

                        assertEquals(expected.left, actual.left)
                        assertEquals(expected.top, actual.top)
                        assertEquals(expected.right, actual.right)
                        assertEquals(expected.bottom, actual.bottom)
                    }
                }
            }
        } finally {
            context.applicationInfo.flags = originalFlags
        }
    }

    @Test
    fun indicatorHorizontalGravity_matchesFrameLayoutWhilePreservingRoleVerticalBounds() {
        val context: Context = RuntimeEnvironment.getApplication()
        val originalFlags: Int = context.applicationInfo.flags
        context.applicationInfo.flags = originalFlags or ApplicationInfo.FLAG_SUPPORTS_RTL
        try {
            for (direction: Int in listOf(View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL)) {
                for (gravity: Int in listOf(Gravity.CENTER, Gravity.END or Gravity.BOTTOM, Gravity.START or Gravity.TOP)) {
                    for (role: KitRefreshLoadLayout.ChildRole in listOf(KitRefreshLoadLayout.ChildRole.Header, KitRefreshLoadLayout.ChildRole.Footer)) {
                        val layout = KitRefreshLoadLayout(context).apply {
                            layoutDirection = direction
                            setPadding(11, 17, 23, 29)
                        }
                        val expectedParent = FrameLayout(context).apply {
                            layoutDirection = direction
                            setPadding(11, 17, 23, 29)
                        }
                        val actual = View(context)
                        val expected = View(context)
                        layout.addView(actual, roleParams(role, 70, 60).apply {
                            this.gravity = gravity
                            setMargins(7, 13, 19, 31)
                        })
                        expectedParent.addView(expected, FrameLayout.LayoutParams(70, 60, gravity).apply {
                            setMargins(7, 13, 19, 31)
                        })

                        measureAndLayout(layout)
                        measureAndLayout(expectedParent)

                        assertEquals(expected.left, actual.left)
                        assertEquals(expected.right, actual.right)
                        val expectedTop: Int = when (role) {
                            KitRefreshLoadLayout.ChildRole.Header -> 17 - 60 - 31
                            KitRefreshLoadLayout.ChildRole.Footer -> LAYOUT_HEIGHT - 29 + 13
                            KitRefreshLoadLayout.ChildRole.Content -> error("Unexpected content role")
                        }
                        assertEquals(expectedTop, actual.top)
                        assertEquals(expectedTop + 60, actual.bottom)
                    }
                }
            }
        } finally {
            context.applicationInfo.flags = originalFlags
        }
    }

    private fun createFixture(
        context: Context = RuntimeEnvironment.getApplication(),
        attrs: AttributeSet? = null,
    ): Fixture {
        val layout = KitRefreshLoadLayout(context, attrs).apply {
            animationDurationMillis = 0L
            dragResistance = 1f
            refreshTriggerDistance = TRIGGER
            loadMoreTriggerDistance = TRIGGER
            maxDragDistance = 200
        }
        val header = RecordingIndicator(context)
        val footer = RecordingIndicator(context)
        val content = View(context)
        val actions: MutableList<KitRefreshLoadLayout.RefreshLoadAction> = mutableListOf()
        layout.addView(header, roleParams(KitRefreshLoadLayout.ChildRole.Header, 80, HEADER_HEIGHT))
        layout.addView(content)
        layout.addView(footer, roleParams(KitRefreshLoadLayout.ChildRole.Footer, 80, FOOTER_HEIGHT))
        layout.setOnRefreshLoadListener(actions::add)
        measureAndLayout(layout)
        return Fixture(layout, header, content, footer, actions)
    }

    private fun withAttachedFixture(block: (Fixture, FrameLayout) -> Unit) {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val fixture: Fixture = createFixture(controller.get())
            val parent = FrameLayout(controller.get())
            controller.get().setContentView(parent)
            parent.addView(fixture.layout)
            measureAndLayout(parent)
            measureAndLayout(fixture.layout)
            assertTrue("Lifecycle tests require an attached view", fixture.layout.isAttachedToWindow)
            block(fixture, parent)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun pull(fixture: Fixture, direction: Direction) {
        assertTrue(fixture.layout.onStartNestedScroll(fixture.content, fixture.content, ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH))
        fixture.layout.onNestedScrollAccepted(fixture.content, fixture.content, ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH)
        fixture.layout.onNestedScroll(fixture.content, 0, 0, 0, direction.scrollDistance, ViewCompat.TYPE_TOUCH, IntArray(2))
    }

    private fun begin(fixture: Fixture, direction: Direction) {
        pull(fixture, direction)
        fixture.layout.onStopNestedScroll(fixture.content, ViewCompat.TYPE_TOUCH)
    }

    private fun finish(layout: KitRefreshLoadLayout, direction: Direction, animate: Boolean) {
        when (direction) {
            Direction.Refresh -> layout.finishRefresh(animate)
            Direction.LoadMore -> layout.finishLoadMore(animate)
        }
    }

    private fun dispatchTouch(layout: KitRefreshLoadLayout, action: Int, y: Float, eventTime: Long) {
        val event: MotionEvent = MotionEvent.obtain(0L, eventTime, action, LAYOUT_WIDTH / 2f, y, 0)
        try {
            assertTrue("The gesture must be handled through ViewGroup dispatch", layout.dispatchTouchEvent(event))
        } finally {
            event.recycle()
        }
    }

    private fun activeAnimator(layout: KitRefreshLoadLayout): ValueAnimator {
        // 只读取动画实例, 再用公开播放进度 API 精确定位中间帧, 避免测试时钟直接跳到结束.
        val field: java.lang.reflect.Field = KitRefreshLoadLayout::class.java.getDeclaredField("activeAnimator")
        field.isAccessible = true
        val animator: ValueAnimator? = field.get(layout) as? ValueAnimator
        assertNotNull("Expected an active offset animation", animator)
        return requireNotNull(animator)
    }

    private fun roleParams(
        role: KitRefreshLoadLayout.ChildRole,
        width: Int,
        height: Int,
    ): KitRefreshLoadLayout.LayoutParams = KitRefreshLoadLayout.LayoutParams(width, height).apply {
        this.role = role
    }

    private fun measureAndLayout(view: View) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(LAYOUT_WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(LAYOUT_HEIGHT, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, LAYOUT_WIDTH, LAYOUT_HEIGHT)
    }

    /** 用同一组状态用例验证刷新和加载的对称行为. */
    private enum class Direction(
        val action: KitRefreshLoadLayout.RefreshLoadAction,
        val state: KitRefreshLoadLayout.RefreshLoadState,
        val role: KitRefreshLoadLayout.ChildRole,
        val scrollDistance: Int,
    ) {
        Refresh(
            KitRefreshLoadLayout.RefreshLoadAction.Refresh,
            KitRefreshLoadLayout.RefreshLoadState.Refreshing,
            KitRefreshLoadLayout.ChildRole.Header,
            -100,
        ),
        LoadMore(
            KitRefreshLoadLayout.RefreshLoadAction.LoadMore,
            KitRefreshLoadLayout.RefreshLoadState.LoadingMore,
            KitRefreshLoadLayout.ChildRole.Footer,
            100,
        ),
    }

    /** 容器与可观测组件组成的测试页面. */
    private data class Fixture(
        val layout: KitRefreshLoadLayout,
        val header: RecordingIndicator,
        val content: View,
        val footer: RecordingIndicator,
        val actions: MutableList<KitRefreshLoadLayout.RefreshLoadAction>,
    ) {
        fun indicator(direction: Direction): RecordingIndicator = when (direction) {
            Direction.Refresh -> header
            Direction.LoadMore -> footer
        }
    }

    /** 记录回调发生时的尺寸, 区分未测量的临时值和可用组件快照. */
    private data class OffsetSnapshot(
        val offset: Int,
        val triggerDistance: Int,
        val holdDistance: Int,
        val measuredHeight: Int,
    )

    /** 可以同步调用容器 API 的记录组件. */
    private class RecordingIndicator(context: Context) : View(context), KitRefreshLoadComponent {
        val states: MutableList<KitRefreshLoadLayout.RefreshLoadState> = mutableListOf()
        val offsets: MutableList<OffsetSnapshot> = mutableListOf()
        var onState: ((KitRefreshLoadLayout, KitRefreshLoadLayout.RefreshLoadState) -> Unit)? = null
        var onOffset: ((KitRefreshLoadLayout, OffsetSnapshot) -> Unit)? = null

        override fun onRefreshLoadStateChanged(layout: KitRefreshLoadLayout, state: KitRefreshLoadLayout.RefreshLoadState) {
            states += state
            onState?.invoke(layout, state)
        }

        override fun onRefreshLoadOffsetChanged(layout: KitRefreshLoadLayout, offset: Int, triggerDistance: Int, holdDistance: Int) {
            val snapshot = OffsetSnapshot(offset, triggerDistance, holdDistance, measuredHeight)
            offsets += snapshot
            onOffset?.invoke(layout, snapshot)
        }
    }

    /** 通过标准 inflater 注入记录组件, 保持默认布局配置走真实 XML 属性入口. */
    private class IndicatorInflationContext(base: Context) : ContextWrapper(base) {
        private val indicatorInflater: LayoutInflater by lazy {
            LayoutInflater.from(baseContext).cloneInContext(this).apply {
                factory2 = object : LayoutInflater.Factory2 {
                    override fun onCreateView(parent: View?, name: String, context: Context, attrs: AttributeSet): View? =
                        onCreateView(name, context, attrs)

                    override fun onCreateView(name: String, context: Context, attrs: AttributeSet): View? =
                        if (name == "TextView") {
                            RecordingIndicator(context).apply { minimumHeight = 75 }
                        } else {
                            null
                        }
                }
            }
        }

        override fun getSystemService(name: String): Any? =
            if (name == LAYOUT_INFLATER_SERVICE) indicatorInflater else super.getSystemService(name)
    }

    private companion object {
        const val LAYOUT_WIDTH: Int = 300
        const val LAYOUT_HEIGHT: Int = 500
        const val HEADER_HEIGHT: Int = 60
        const val FOOTER_HEIGHT: Int = 70
        const val TRIGGER: Int = 20
        const val ANIMATION_DURATION: Long = 240L
    }
}
