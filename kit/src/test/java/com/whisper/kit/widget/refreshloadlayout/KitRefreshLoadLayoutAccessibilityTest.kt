package com.whisper.kit.widget.refreshloadlayout

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.FrameLayout
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.whisper.kit.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 验证刷新加载的无障碍动作、状态描述与调用方及内容节点的共存行为.
 *
 * @author whisper
 * @since 2026/09/11
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 34])
class KitRefreshLoadLayoutAccessibilityTest {

    @Test
    fun idleNode_exposesBothLabeledActionsWithoutChangingViewIdentity() = withFixture { fixture: Fixture ->
        val node: AccessibilityNodeInfo = fixture.layout.createAccessibilityNodeInfo()

        assertEquals(FrameLayout::class.java.name, node.className.toString())
        assertNull(node.contentDescription)
        assertNull(AccessibilityNodeInfoCompat.wrap(node).stateDescription)
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_YES, fixture.layout.importantForAccessibility)
        assertFalse(fixture.layout.isFocusable)
        assertFalse(ViewCompat.isScreenReaderFocusable(fixture.layout))
        for (direction: Direction in Direction.entries) {
            val action: AccessibilityNodeInfo.AccessibilityAction = node.actionList.single { it.id == direction.actionId }
            assertEquals(fixture.layout.context.getString(direction.labelId), action.label.toString())
        }
    }

    @Test
    fun explicitAction_startsOnceAtAnyScrollPositionAndPreservesContentScroll() {
        for (direction: Direction in Direction.entries) {
            withFixture { fixture: Fixture ->
                fixture.content.scrollTo(0, 123)
                assertTrue(fixture.content.canScrollVertically(-1))
                assertTrue(fixture.content.canScrollVertically(1))

                assertTrue(perform(fixture, direction))

                assertEquals(direction.state, fixture.layout.state)
                assertEquals(direction.holdOffset, fixture.layout.currentOffset)
                assertEquals(123, fixture.content.scrollY)
                assertEquals(listOf(direction.action), fixture.actions)
                for (other: Direction in Direction.entries) {
                    assertFalse(hasAction(fixture, other))
                    assertFalse(perform(fixture, other))
                }
                assertEquals(listOf(direction.action), fixture.actions)
            }
        }
    }

    @Test
    fun disabledLayout_hidesAndRejectsBothActionsThenRestoresThem() = withFixture { fixture: Fixture ->
        fixture.layout.isEnabled = false

        for (direction: Direction in Direction.entries) {
            assertFalse(hasAction(fixture, direction))
            assertFalse(perform(fixture, direction))
        }
        assertTrue(fixture.actions.isEmpty())
        fixture.layout.isEnabled = true
        for (direction: Direction in Direction.entries) {
            assertTrue(hasAction(fixture, direction))
        }
    }

    @Test
    fun disabledDirection_hidesOnlyItsActionAndRejectsPreviouslyAvailableAction() {
        for (direction: Direction in Direction.entries) {
            withFixture { fixture: Fixture ->
                assertTrue(hasAction(fixture, direction))
                setDirectionEnabled(fixture.layout, direction, enabled = false)

                assertFalse(hasAction(fixture, direction))
                assertFalse(perform(fixture, direction))
                assertTrue(hasAction(fixture, direction.other()))
                assertTrue(fixture.actions.isEmpty())

                setDirectionEnabled(fixture.layout, direction, enabled = true)
                assertTrue(hasAction(fixture, direction))
            }
        }
    }

    @Test
    fun removedIndicator_rejectsStaleActionAndRestoresItAfterReplacement() {
        for (direction: Direction in Direction.entries) {
            withFixture { fixture: Fixture ->
                assertTrue(hasAction(fixture, direction))
                val indicator: View = fixture.indicator(direction)
                fixture.layout.removeView(indicator)

                assertFalse(hasAction(fixture, direction))
                assertFalse(perform(fixture, direction))
                assertTrue(fixture.actions.isEmpty())
                measureAndLayout(fixture.layout)
                assertFalse(hasAction(fixture, direction))

                fixture.layout.addView(indicator)
                measureAndLayout(fixture.layout)
                assertTrue(hasAction(fixture, direction))
            }
        }
    }

    @Test
    fun uncommittedPull_hidesAndRejectsActionsUntilThePullIsCleared() = withFixture { fixture: Fixture ->
        // 此用例的内容始终可滚动, 通过容器的边界回调模拟实际列表已到顶部.
        fixture.layout.setScrollBoundaryCallback { _, _, _ -> false }
        fixture.layout.onNestedScrollAccepted(
            fixture.content, fixture.content, ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH,
        )
        fixture.layout.onNestedScroll(
            fixture.content, 0, 0, 0, -20, ViewCompat.TYPE_TOUCH, IntArray(2),
        )
        assertEquals(KitRefreshLoadLayout.RefreshLoadState.PullingDown, fixture.layout.state)

        for (direction: Direction in Direction.entries) {
            assertFalse(hasAction(fixture, direction))
            assertFalse(perform(fixture, direction))
        }
        fixture.layout.setActiveAction(null, animate = false)
        assertEquals(KitRefreshLoadLayout.RefreshLoadState.Idle, fixture.layout.state)
        assertTrue(fixture.actions.isEmpty())
        for (direction: Direction in Direction.entries) {
            assertTrue(hasAction(fixture, direction))
        }
    }

    @Test
    fun programmaticSynchronization_updatesDescriptionWithoutBusinessCallbacks() = withFixture { fixture: Fixture ->
        for (direction: Direction in Direction.entries) {
            fixture.layout.setActiveAction(direction.action, animate = false)

            assertEquals(direction.state, fixture.layout.state)
            assertEquals(fixture.layout.context.getString(direction.stateLabelId), stateDescription(fixture))
            assertFalse(hasAction(fixture, Direction.REFRESH))
            assertFalse(hasAction(fixture, Direction.LOAD_MORE))

            fixture.layout.setActiveAction(direction.action, animate = false)
            assertEquals(fixture.layout.context.getString(direction.stateLabelId), stateDescription(fixture))
        }

        fixture.layout.setActiveAction(null, animate = false)

        assertNull(stateDescription(fixture))
        assertTrue(hasAction(fixture, Direction.REFRESH))
        assertTrue(hasAction(fixture, Direction.LOAD_MORE))
        assertTrue(fixture.actions.isEmpty())
    }

    @Test
    fun finishingUserAction_clearsBusyDescriptionAndAllowsNextAction() {
        for (direction: Direction in Direction.entries) {
            withFixture { fixture: Fixture ->
                assertTrue(perform(fixture, direction))
                assertEquals(fixture.layout.context.getString(direction.stateLabelId), stateDescription(fixture))

                when (direction) {
                    Direction.REFRESH -> fixture.layout.finishRefresh(animate = false)
                    Direction.LOAD_MORE -> fixture.layout.finishLoadMore(animate = false)
                }

                assertNull(stateDescription(fixture))
                assertTrue(perform(fixture, direction))
                assertEquals(listOf(direction.action, direction.action), fixture.actions)
            }
        }
    }

    @Test
    fun callerContentDescriptionAndStateDescription_surviveStateChanges() = withFixture { fixture: Fixture ->
        fixture.layout.contentDescription = "Orders"
        ViewCompat.setStateDescription(fixture.layout, "Three selected")

        for (action: KitRefreshLoadLayout.RefreshLoadAction? in listOf(
            KitRefreshLoadLayout.RefreshLoadAction.Refresh,
            KitRefreshLoadLayout.RefreshLoadAction.LoadMore,
            null,
        )) {
            fixture.layout.setActiveAction(action, animate = false)
            val node: AccessibilityNodeInfo = fixture.layout.createAccessibilityNodeInfo()
            assertEquals("Orders", node.contentDescription.toString())
            assertEquals("Three selected", AccessibilityNodeInfoCompat.wrap(node).stateDescription.toString())
            assertEquals("Three selected", ViewCompat.getStateDescription(fixture.layout).toString())
        }

        ViewCompat.setStateDescription(fixture.layout, null)
        fixture.layout.setActiveAction(KitRefreshLoadLayout.RefreshLoadAction.Refresh, animate = false)
        assertEquals(fixture.layout.context.getString(R.string.kit_refresh_load_state_refreshing), stateDescription(fixture))
    }

    @Test
    fun callerDelegate_preservesNodeOverridesAndUnrelatedActionHandling() = withFixture { fixture: Fixture ->
        var customActionCalls: Int = 0
        ViewCompat.setAccessibilityDelegate(fixture.layout, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = "example.OrdersContainer"
                info.contentDescription = "Recent orders"
                info.stateDescription = "Caller status"
                info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(CUSTOM_ACTION_ID, "Archive"))
            }

            override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
                if (action == CUSTOM_ACTION_ID) {
                    customActionCalls++
                    return true
                }
                return super.performAccessibilityAction(host, action, args)
            }
        })

        fixture.layout.setActiveAction(KitRefreshLoadLayout.RefreshLoadAction.Refresh, animate = false)
        fixture.layout.setActiveAction(null, animate = false)
        val node: AccessibilityNodeInfo = fixture.layout.createAccessibilityNodeInfo()

        assertEquals("example.OrdersContainer", node.className.toString())
        assertEquals("Recent orders", node.contentDescription.toString())
        assertEquals("Caller status", AccessibilityNodeInfoCompat.wrap(node).stateDescription.toString())
        assertTrue(node.actionList.any { it.id == CUSTOM_ACTION_ID })
        assertTrue(node.actionList.any { it.id == Direction.REFRESH.actionId })
        assertTrue(fixture.layout.performAccessibilityAction(CUSTOM_ACTION_ID, null))
        assertEquals(1, customActionCalls)
        assertTrue(perform(fixture, Direction.REFRESH))
        assertEquals(listOf(KitRefreshLoadLayout.RefreshLoadAction.Refresh), fixture.actions)
    }

    @Test
    fun contentAndIndicatorAccessibilityNodes_remainIndependent() = withFixture { fixture: Fixture ->
        val contentChildren: ArrayList<View> = arrayListOf()
        fixture.content.addChildrenForAccessibility(contentChildren)
        assertEquals(listOf(fixture.contentButton), contentChildren)
        assertEquals(Button::class.java.name, fixture.contentButton.createAccessibilityNodeInfo().className.toString())
        assertTrue(fixture.contentButton.createAccessibilityNodeInfo().isClickable)

        for (action: KitRefreshLoadLayout.RefreshLoadAction? in listOf(
            null,
            KitRefreshLoadLayout.RefreshLoadAction.Refresh,
            KitRefreshLoadLayout.RefreshLoadAction.LoadMore,
        )) {
            fixture.layout.setActiveAction(action, animate = false)
            val accessibleChildren: ArrayList<View> = arrayListOf()
            fixture.layout.addChildrenForAccessibility(accessibleChildren)
            assertEquals(setOf(fixture.header, fixture.content, fixture.footer), accessibleChildren.toSet())
            assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_YES, fixture.content.importantForAccessibility)
            assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_YES, fixture.contentButton.importantForAccessibility)
        }
    }

    @Test
    fun explicitXmlAccessibilityImportance_isPreserved() {
        val context: Context = RuntimeEnvironment.getApplication()
        for (importance: String in listOf("no", "noHideDescendants")) {
            val attrs = Robolectric.buildAttributeSet()
                .addAttribute(android.R.attr.importantForAccessibility, importance)
                .build()
            val layout = KitRefreshLoadLayout(context, attrs)
            val expected: Int = if (importance == "no") {
                View.IMPORTANT_FOR_ACCESSIBILITY_NO
            } else {
                View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            }

            assertEquals(expected, layout.importantForAccessibility)
        }
    }

    private fun withFixture(block: (Fixture) -> Unit) {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val context: Context = controller.get()
            val layout = KitRefreshLoadLayout(context).apply {
                animationDurationMillis = 0L
                dragResistance = 1f
                refreshTriggerDistance = 100
                loadMoreTriggerDistance = 100
            }
            val header = View(context).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES }
            val footer = View(context).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES }
            val content = ScrollableContent(context).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            }
            val button = Button(context).apply {
                text = "Order details"
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            }
            content.addView(button, FrameLayout.LayoutParams(200, 80))
            layout.addView(header, roleParams(KitRefreshLoadLayout.ChildRole.Header, HEADER_HEIGHT))
            layout.addView(content)
            layout.addView(footer, roleParams(KitRefreshLoadLayout.ChildRole.Footer, FOOTER_HEIGHT))
            val actions: MutableList<KitRefreshLoadLayout.RefreshLoadAction> = mutableListOf()
            layout.setOnRefreshLoadListener(actions::add)
            controller.get().setContentView(layout)
            measureAndLayout(layout)
            block(Fixture(layout, header, content, button, footer, actions))
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun roleParams(role: KitRefreshLoadLayout.ChildRole, height: Int): KitRefreshLoadLayout.LayoutParams =
        KitRefreshLoadLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, height).apply {
            this.role = role
        }

    private fun measureAndLayout(view: View) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, 320, 640)
    }

    private fun perform(fixture: Fixture, direction: Direction): Boolean =
        fixture.layout.performAccessibilityAction(direction.actionId, null)

    private fun hasAction(fixture: Fixture, direction: Direction): Boolean =
        fixture.layout.createAccessibilityNodeInfo().actionList.any { it.id == direction.actionId }

    private fun stateDescription(fixture: Fixture): String? =
        AccessibilityNodeInfoCompat.wrap(fixture.layout.createAccessibilityNodeInfo()).stateDescription?.toString()

    private fun setDirectionEnabled(layout: KitRefreshLoadLayout, direction: Direction, enabled: Boolean) {
        when (direction) {
            Direction.REFRESH -> layout.refreshEnabled = enabled
            Direction.LOAD_MORE -> layout.loadMoreEnabled = enabled
        }
    }

    /** 用可滚动内容验证显式无障碍动作不依赖触摸边界. */
    private class ScrollableContent(context: Context) : FrameLayout(context) {
        override fun canScrollVertically(direction: Int): Boolean = true
    }

    /** 无障碍操作使用和手势相同的动作与状态, 两个方向独立验证. */
    private enum class Direction(
        val actionId: Int,
        val labelId: Int,
        val stateLabelId: Int,
        val action: KitRefreshLoadLayout.RefreshLoadAction,
        val state: KitRefreshLoadLayout.RefreshLoadState,
        val holdOffset: Int,
    ) {
        REFRESH(
            R.id.kit_accessibility_action_refresh,
            R.string.kit_refresh_load_action_refresh,
            R.string.kit_refresh_load_state_refreshing,
            KitRefreshLoadLayout.RefreshLoadAction.Refresh,
            KitRefreshLoadLayout.RefreshLoadState.Refreshing,
            HEADER_HEIGHT,
        ),
        LOAD_MORE(
            R.id.kit_accessibility_action_load_more,
            R.string.kit_refresh_load_action_load_more,
            R.string.kit_refresh_load_state_loading_more,
            KitRefreshLoadLayout.RefreshLoadAction.LoadMore,
            KitRefreshLoadLayout.RefreshLoadState.LoadingMore,
            -FOOTER_HEIGHT,
        );

        fun other(): Direction = when (this) {
            REFRESH -> LOAD_MORE
            LOAD_MORE -> REFRESH
        }
    }

    /** 内容和头尾均保留独立无障碍节点的真实窗口测试夹具. */
    private data class Fixture(
        val layout: KitRefreshLoadLayout,
        val header: View,
        val content: ScrollableContent,
        val contentButton: Button,
        val footer: View,
        val actions: MutableList<KitRefreshLoadLayout.RefreshLoadAction>,
    ) {
        fun indicator(direction: Direction): View = when (direction) {
            Direction.REFRESH -> header
            Direction.LOAD_MORE -> footer
        }
    }

    private companion object {
        const val HEADER_HEIGHT: Int = 60
        const val FOOTER_HEIGHT: Int = 70
        const val CUSTOM_ACTION_ID: Int = 0x01030000
    }
}
