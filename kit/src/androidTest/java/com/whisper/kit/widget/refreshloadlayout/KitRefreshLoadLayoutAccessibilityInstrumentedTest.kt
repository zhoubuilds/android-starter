package com.whisper.kit.widget.refreshloadlayout

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.FrameLayout
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.whisper.kit.R as KitR
import com.whisper.kit.test.R as TestR
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 通过系统无障碍服务验证容器动作、内容节点和新窗口业务状态同步.
 *
 * @author whisper
 * @since 2026/09/11
 */
@RunWith(AndroidJUnit4::class)
class KitRefreshLoadLayoutAccessibilityInstrumentedTest {

    private lateinit var activity: KitRefreshLoadLayoutTestActivity
    private var originalAccessibilityFlags: Int? = null

    @Before
    fun launchActivity() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        originalAccessibilityFlags = automation.serviceInfo.flags
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = (flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE) and
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS.inv()
        }
        activity = startActivity()
    }

    @After
    fun finishActivity() {
        try {
            if (::activity.isInitialized) {
                onMainThread { activity.finish() }
            }
        } finally {
            originalAccessibilityFlags?.let { originalFlags: Int ->
                val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
                automation.serviceInfo = automation.serviceInfo.apply { flags = originalFlags }
            }
        }
    }

    @Test
    fun containerActionsAndContentButton_remainSeparatelyAccessible() {
        val container: AccessibilityNodeInfo = node(TestR.id.kit_test_refresh_load_container)
        assertEquals(FrameLayout::class.java.name, container.className.toString())
        assertNull(container.contentDescription)
        for (direction: Direction in Direction.entries) {
            val action: AccessibilityNodeInfo.AccessibilityAction = container.actionList.single {
                it.id == direction.actionId
            }
            assertEquals(activity.getString(direction.actionLabelId), action.label.toString())
        }
        assertTrue(container.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS))
        assertTrue(container.refresh())
        assertTrue(container.isAccessibilityFocused)

        val button: AccessibilityNodeInfo = node(TestR.id.kit_test_refresh_load_content_button)
        assertEquals(Button::class.java.name, button.className.toString())
        assertEquals(activity.getString(TestR.string.kit_test_refresh_load_content_button), button.text.toString())
        assertTrue(isDescendantOf(button, TestR.id.kit_test_refresh_load_container))
        assertTrue(button.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS))
        assertTrue(button.refresh())
        assertTrue(button.isAccessibilityFocused)
        assertTrue(button.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        onMainThread {
            assertEquals(1, activity.contentClickCount)
            assertTrue(activity.actions.isEmpty())
        }
        assertTrue(container.refresh())
        assertFalse(container.isAccessibilityFocused)
    }

    @Test
    fun refreshAndLoadActions_notifyOnceAndExposeCurrentBusyState() {
        onMainThread { activity.refreshLayout.animationDurationMillis = 0L }
        val expectedActions: MutableList<KitRefreshLoadLayout.RefreshLoadAction> = mutableListOf()
        for (direction: Direction in Direction.entries) {
            val container: AccessibilityNodeInfo = node(TestR.id.kit_test_refresh_load_container)
            assertTrue(container.performAction(direction.actionId))
            expectedActions += direction.action
            onMainThread {
                assertEquals(expectedActions, activity.actions)
                assertEquals(direction.state, activity.refreshLayout.state)
                assertEquals(direction.holdOffset, activity.refreshLayout.currentOffset)
            }

            assertTrue(container.refresh())
            assertEquals(activity.getString(direction.stateLabelId), stateDescription(container))
            for (other: Direction in Direction.entries) {
                assertFalse(container.actionList.any { it.id == other.actionId })
                assertFalse(container.performAction(other.actionId))
            }
            onMainThread {
                assertEquals(expectedActions, activity.actions)
                when (direction) {
                    Direction.REFRESH -> activity.refreshLayout.finishRefresh(animate = false)
                    Direction.LOAD_MORE -> activity.refreshLayout.finishLoadMore(animate = false)
                }
            }
            assertTrue(container.refresh())
            assertNull(stateDescription(container))
            assertTrue(container.actionList.any { it.id == direction.actionId })
        }
    }

    @Test
    fun newWindow_synchronizesExistingTaskBeforeLayoutWithoutIssuingAnotherRequest() {
        for (direction: Direction in Direction.entries) {
            assertTrue(node(TestR.id.kit_test_refresh_load_container).performAction(direction.actionId))
            val previousActivity: KitRefreshLoadLayoutTestActivity = activity
            onMainThread {
                assertEquals(listOf(direction.action), previousActivity.actions)
                previousActivity.finish()
            }

            // 页面重建后由业务重新提供仍在运行的动作, 不依赖控件自动保存 busy 状态.
            activity = startActivity(direction.action)

            onMainThread {
                assertEquals(direction.state, activity.refreshLayout.state)
                assertEquals(direction.holdOffset, activity.refreshLayout.currentOffset)
                assertTrue(activity.actions.isEmpty())
                activity.refreshLayout.setActiveAction(direction.action, animate = false)
                assertEquals(direction.holdOffset, activity.refreshLayout.currentOffset)
                assertTrue(activity.actions.isEmpty())
                assertEquals(listOf(direction.action), previousActivity.actions)
            }
            val container: AccessibilityNodeInfo = node(TestR.id.kit_test_refresh_load_container)
            assertEquals(activity.getString(direction.stateLabelId), stateDescription(container))
            assertFalse(container.performAction(direction.actionId))

            onMainThread {
                activity.refreshLayout.setActiveAction(null, animate = false)
                assertEquals(KitRefreshLoadLayout.RefreshLoadState.Idle, activity.refreshLayout.state)
                assertTrue(activity.actions.isEmpty())
            }
        }
    }

    private fun startActivity(action: KitRefreshLoadLayout.RefreshLoadAction? = null): KitRefreshLoadLayoutTestActivity {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val intent = Intent(instrumentation.targetContext, KitRefreshLoadLayoutTestActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (action != null) {
            intent.putExtra(KitRefreshLoadLayoutTestActivity.EXTRA_ACTIVE_ACTION, action.name)
        }
        val launchedActivity: KitRefreshLoadLayoutTestActivity =
            instrumentation.startActivitySync(intent) as KitRefreshLoadLayoutTestActivity
        awaitCondition("The accessibility test window must be focused and laid out.") {
            launchedActivity.refreshLayout.hasWindowFocus() && launchedActivity.refreshLayout.isLaidOut &&
                !launchedActivity.refreshLayout.isLayoutRequested
        }
        return launchedActivity
    }

    private fun node(id: Int): AccessibilityNodeInfo {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val resourceName: String = activity.resources.getResourceName(id)
        val deadline: Long = SystemClock.uptimeMillis() + 5_000L
        while (SystemClock.uptimeMillis() < deadline) {
            val root: AccessibilityNodeInfo? = instrumentation.uiAutomation.rootInActiveWindow
            if (root != null && root.packageName?.toString() == instrumentation.targetContext.packageName) {
                root.findAccessibilityNodeInfosByViewId(resourceName)
                    .firstOrNull { it.isVisibleToUser }
                    ?.let { return it }
            }
            SystemClock.sleep(25L)
        }
        error("The expected view was not exposed in the accessibility tree: $resourceName")
    }

    private fun isDescendantOf(node: AccessibilityNodeInfo, ancestorId: Int): Boolean {
        val ancestorResourceName: String = activity.resources.getResourceName(ancestorId)
        var parent: AccessibilityNodeInfo? = node.parent
        while (parent != null) {
            if (parent.viewIdResourceName == ancestorResourceName) {
                return true
            }
            parent = parent.parent
        }
        return false
    }

    private fun stateDescription(node: AccessibilityNodeInfo): String? =
        AccessibilityNodeInfoCompat.wrap(node).stateDescription?.toString()

    private fun awaitCondition(message: String, condition: () -> Boolean) {
        val deadline: Long = SystemClock.uptimeMillis() + 5_000L
        var satisfied: Boolean = false
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

    /** 对刷新与加载运行同一套实际服务动作断言. */
    private enum class Direction(
        val actionId: Int,
        val actionLabelId: Int,
        val stateLabelId: Int,
        val action: KitRefreshLoadLayout.RefreshLoadAction,
        val state: KitRefreshLoadLayout.RefreshLoadState,
        val holdOffset: Int,
    ) {
        REFRESH(
            KitR.id.kit_accessibility_action_refresh,
            KitR.string.kit_refresh_load_action_refresh,
            KitR.string.kit_refresh_load_state_refreshing,
            KitRefreshLoadLayout.RefreshLoadAction.Refresh,
            KitRefreshLoadLayout.RefreshLoadState.Refreshing,
            60,
        ),
        LOAD_MORE(
            KitR.id.kit_accessibility_action_load_more,
            KitR.string.kit_refresh_load_action_load_more,
            KitR.string.kit_refresh_load_state_loading_more,
            KitRefreshLoadLayout.RefreshLoadAction.LoadMore,
            KitRefreshLoadLayout.RefreshLoadState.LoadingMore,
            -60,
        ),
    }
}
