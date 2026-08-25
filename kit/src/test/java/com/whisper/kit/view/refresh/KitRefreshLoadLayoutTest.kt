package com.whisper.kit.view.refresh

import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowSystemClock
import java.util.concurrent.TimeUnit

/**
 * 验证刷新加载容器的基础布局和拖拽状态.
 *
 * @author whisper
 * @since 2026/07/30
 */
@RunWith(RobolectricTestRunner::class)
class KitRefreshLoadLayoutTest {

    /**
     * 验证 header、默认 content 和 footer 按角色布局在正确位置.
     */
    @Test
    fun layout_placesChildrenByRole() {
        val layout: KitRefreshLoadLayout = createLayout()
        val header: View = createChild(width = MATCH_PARENT, height = HEADER_HEIGHT)
        val content: View = createChild(width = MATCH_PARENT, height = MATCH_PARENT)
        val footer: View = createChild(width = MATCH_PARENT, height = FOOTER_HEIGHT)

        layout.addView(header, roleParams(KitRefreshLoadLayout.ChildRole.Header, MATCH_PARENT, HEADER_HEIGHT))
        layout.addView(content)
        layout.addView(footer, roleParams(KitRefreshLoadLayout.ChildRole.Footer, MATCH_PARENT, FOOTER_HEIGHT))

        measureAndLayout(layout)

        assertEquals(-HEADER_HEIGHT, header.top)
        assertEquals(0, content.top)
        assertEquals(LAYOUT_HEIGHT, footer.top)
    }

    /**
     * 验证下拉到触发距离后松手会进入刷新状态并回调监听器.
     */
    @Test
    fun nestedPullDown_triggersRefreshWhenOverThreshold() {
        val layout: KitRefreshLoadLayout = createLayout()
        val header: View = createChild(width = MATCH_PARENT, height = HEADER_HEIGHT)
        val content: View = createChild(width = MATCH_PARENT, height = MATCH_PARENT)
        var refreshCount: Int = 0

        layout.dragResistance = 1f
        layout.animationDurationMillis = 0L
        layout.refreshTriggerDistance = REFRESH_TRIGGER_DISTANCE
        layout.addView(header, roleParams(KitRefreshLoadLayout.ChildRole.Header, MATCH_PARENT, HEADER_HEIGHT))
        layout.addView(content)
        layout.setOnRefreshLoadListener { action: KitRefreshLoadLayout.RefreshLoadAction ->
            if (action == KitRefreshLoadLayout.RefreshLoadAction.Refresh) {
                refreshCount += 1
            }
        }
        measureAndLayout(layout)

        val consumed: IntArray = IntArray(2)
        layout.onNestedScroll(
            target = content,
            dxConsumed = 0,
            dyConsumed = 0,
            dxUnconsumed = 0,
            dyUnconsumed = -REFRESH_DRAG_DISTANCE,
            type = ViewCompat.TYPE_TOUCH,
            consumed = consumed,
        )
        layout.onStopNestedScroll(content, ViewCompat.TYPE_TOUCH)

        assertEquals(-REFRESH_DRAG_DISTANCE, consumed[1])
        assertEquals(1, refreshCount)
        assertEquals(KitRefreshLoadLayout.RefreshLoadState.Refreshing, layout.state)
        assertEquals(HEADER_HEIGHT, layout.currentOffset)
    }

    /**
     * 验证外部滚动边界判定可以阻止下拉刷新.
     */
    @Test
    fun childScrollUpCallback_blocksRefreshWhenContentCanScrollUp() {
        val layout: KitRefreshLoadLayout = createLayout()
        val header: View = createChild(width = MATCH_PARENT, height = HEADER_HEIGHT)
        val content: View = createChild(width = MATCH_PARENT, height = MATCH_PARENT)
        var refreshCount: Int = 0

        layout.dragResistance = 1f
        layout.animationDurationMillis = 0L
        layout.refreshTriggerDistance = REFRESH_TRIGGER_DISTANCE
        layout.addView(header, roleParams(KitRefreshLoadLayout.ChildRole.Header, MATCH_PARENT, HEADER_HEIGHT))
        layout.addView(content)
        layout.setOnChildScrollUpCallback { _, _ -> true }
        layout.setOnRefreshLoadListener { action: KitRefreshLoadLayout.RefreshLoadAction ->
            if (action == KitRefreshLoadLayout.RefreshLoadAction.Refresh) {
                refreshCount += 1
            }
        }
        measureAndLayout(layout)

        val consumed: IntArray = IntArray(2)
        layout.onNestedScroll(
            target = content,
            dxConsumed = 0,
            dyConsumed = 0,
            dxUnconsumed = 0,
            dyUnconsumed = -REFRESH_DRAG_DISTANCE,
            type = ViewCompat.TYPE_TOUCH,
            consumed = consumed,
        )
        layout.onStopNestedScroll(content, ViewCompat.TYPE_TOUCH)
        layout.onTouchEvent(touchEvent(MotionEvent.ACTION_DOWN, pointer(id = 0, y = 0f)))
        layout.onTouchEvent(touchEvent(MotionEvent.ACTION_MOVE, pointer(id = 0, y = REFRESH_DRAG_DISTANCE.toFloat())))

        assertEquals(0, consumed[1])
        assertEquals(0, refreshCount)
        assertEquals(KitRefreshLoadLayout.RefreshLoadState.Idle, layout.state)
        assertEquals(0, layout.currentOffset)
    }

    /**
     * 验证刷新中 content 继续跟随 header 位移.
     */
    @Test
    fun refreshing_movesContentWithHeader() {
        val layout: KitRefreshLoadLayout = createLayout()
        val header: View = createChild(width = MATCH_PARENT, height = HEADER_HEIGHT)
        val content: View = createChild(width = MATCH_PARENT, height = MATCH_PARENT)

        layout.dragResistance = 1f
        layout.animationDurationMillis = 0L
        layout.refreshTriggerDistance = REFRESH_TRIGGER_DISTANCE
        layout.addView(header, roleParams(KitRefreshLoadLayout.ChildRole.Header, MATCH_PARENT, HEADER_HEIGHT))
        layout.addView(content)
        measureAndLayout(layout)

        layout.onNestedScroll(
            target = content,
            dxConsumed = 0,
            dyConsumed = 0,
            dxUnconsumed = 0,
            dyUnconsumed = -REFRESH_DRAG_DISTANCE,
            type = ViewCompat.TYPE_TOUCH,
            consumed = IntArray(2),
        )
        layout.onStopNestedScroll(content, ViewCompat.TYPE_TOUCH)

        assertEquals(KitRefreshLoadLayout.RefreshLoadState.Refreshing, layout.state)
        assertEquals(HEADER_HEIGHT.toFloat(), content.translationY, 0f)
        assertEquals(HEADER_HEIGHT.toFloat(), header.translationY, 0f)

        layout.animationDurationMillis = ANIMATION_DURATION_MILLIS
        layout.finishRefresh()

        assertEquals(KitRefreshLoadLayout.RefreshLoadState.Settling, layout.state)
        assertEquals(HEADER_HEIGHT.toFloat(), content.translationY, 0f)
    }

    /**
     * 验证刷新中的 header 会跟随 content 触摸滚动和惯性滚动离开屏幕.
     */
    @Test
    fun refreshing_scrollsHeaderOutWithContentScrollAndFling() {
        val layout: KitRefreshLoadLayout = createLayout()
        val header: View = createChild(width = MATCH_PARENT, height = HEADER_HEIGHT)
        val content: View = createChild(width = MATCH_PARENT, height = MATCH_PARENT)

        layout.dragResistance = 1f
        layout.animationDurationMillis = 0L
        layout.refreshTriggerDistance = REFRESH_TRIGGER_DISTANCE
        layout.addView(header, roleParams(KitRefreshLoadLayout.ChildRole.Header, MATCH_PARENT, HEADER_HEIGHT))
        layout.addView(content)
        measureAndLayout(layout)

        layout.onNestedScroll(
            target = content,
            dxConsumed = 0,
            dyConsumed = 0,
            dxUnconsumed = 0,
            dyUnconsumed = -REFRESH_DRAG_DISTANCE,
            type = ViewCompat.TYPE_TOUCH,
            consumed = IntArray(2),
        )
        layout.onStopNestedScroll(content, ViewCompat.TYPE_TOUCH)
        val touchConsumed: IntArray = IntArray(2)
        layout.onNestedPreScroll(
            target = content,
            dx = 0,
            dy = HEADER_HEIGHT / 2,
            consumed = touchConsumed,
            type = ViewCompat.TYPE_TOUCH,
        )

        assertEquals(HEADER_HEIGHT / 2, touchConsumed[1])
        assertEquals(HEADER_HEIGHT / 2, layout.currentOffset)
        assertEquals((HEADER_HEIGHT / 2).toFloat(), header.translationY, 0f)
        assertEquals((HEADER_HEIGHT / 2).toFloat(), content.translationY, 0f)

        val flingConsumed: IntArray = IntArray(2)
        layout.onNestedPreScroll(
            target = content,
            dx = 0,
            dy = HEADER_HEIGHT,
            consumed = flingConsumed,
            type = ViewCompat.TYPE_NON_TOUCH,
        )

        assertEquals(HEADER_HEIGHT / 2, flingConsumed[1])
        assertEquals(0, layout.currentOffset)
        assertEquals(0f, header.translationY, 0f)
        assertEquals(0f, content.translationY, 0f)
        assertEquals(KitRefreshLoadLayout.RefreshLoadState.Refreshing, layout.state)

        val returnFlingConsumed: IntArray = IntArray(2)
        layout.onNestedPreScroll(
            target = content,
            dx = 0,
            dy = -HEADER_HEIGHT,
            consumed = returnFlingConsumed,
            type = ViewCompat.TYPE_NON_TOUCH,
        )

        assertEquals(-HEADER_HEIGHT, returnFlingConsumed[1])
        assertEquals(HEADER_HEIGHT, layout.currentOffset)
        assertEquals(HEADER_HEIGHT.toFloat(), header.translationY, 0f)
        assertEquals(HEADER_HEIGHT.toFloat(), content.translationY, 0f)
    }

    /**
     * 验证刷新中惯性滚动到顶部时, 会用 fling 速度继续自然拉出 header.
     */
    @Test
    fun refreshing_usesFlingVelocityToContinueShowingHeaderAtTop() {
        val layout: KitRefreshLoadLayout = createLayout()
        val header: View = createChild(width = MATCH_PARENT, height = HEADER_HEIGHT)
        val content: View = createChild(width = MATCH_PARENT, height = MATCH_PARENT)

        layout.dragResistance = 1f
        layout.animationDurationMillis = 0L
        layout.refreshTriggerDistance = REFRESH_TRIGGER_DISTANCE
        layout.addView(header, roleParams(KitRefreshLoadLayout.ChildRole.Header, MATCH_PARENT, HEADER_HEIGHT))
        layout.addView(content)
        measureAndLayout(layout)

        layout.onNestedScroll(
            target = content,
            dxConsumed = 0,
            dyConsumed = 0,
            dxUnconsumed = 0,
            dyUnconsumed = -REFRESH_DRAG_DISTANCE,
            type = ViewCompat.TYPE_TOUCH,
            consumed = IntArray(2),
        )
        layout.onStopNestedScroll(content, ViewCompat.TYPE_TOUCH)
        layout.onNestedPreScroll(
            target = content,
            dx = 0,
            dy = HEADER_HEIGHT,
            consumed = IntArray(2),
            type = ViewCompat.TYPE_NON_TOUCH,
        )

        assertEquals(0, layout.currentOffset)
        assertFalse(layout.onNestedPreFling(content, velocityX = 0f, velocityY = -6_000f))

        layout.onNestedPreScroll(
            target = content,
            dx = 0,
            dy = -1,
            consumed = IntArray(2),
            type = ViewCompat.TYPE_NON_TOUCH,
        )
        repeat(5) {
            ShadowSystemClock.advanceBy(16, TimeUnit.MILLISECONDS)
            layout.computeScroll()
        }

        assertTrue(layout.currentOffset > 1)
        assertEquals(layout.currentOffset.toFloat(), header.translationY, 0f)
        assertEquals(layout.currentOffset.toFloat(), content.translationY, 0f)
    }

    /**
     * 验证默认刷新触发距离使用 header 高度.
     */
    @Test
    fun nestedPullDown_usesHeaderHeightAsDefaultTriggerDistance() {
        val layout: KitRefreshLoadLayout = createLayout()
        val header: View = createChild(width = MATCH_PARENT, height = HEADER_HEIGHT)
        val content: View = createChild(width = MATCH_PARENT, height = MATCH_PARENT)
        var refreshCount: Int = 0

        layout.dragResistance = 1f
        layout.animationDurationMillis = 0L
        layout.addView(header, roleParams(KitRefreshLoadLayout.ChildRole.Header, MATCH_PARENT, HEADER_HEIGHT))
        layout.addView(content)
        layout.setOnRefreshLoadListener { action: KitRefreshLoadLayout.RefreshLoadAction ->
            if (action == KitRefreshLoadLayout.RefreshLoadAction.Refresh) {
                refreshCount += 1
            }
        }
        measureAndLayout(layout)

        layout.onNestedScroll(
            target = content,
            dxConsumed = 0,
            dyConsumed = 0,
            dxUnconsumed = 0,
            dyUnconsumed = -HEADER_HEIGHT,
            type = ViewCompat.TYPE_TOUCH,
            consumed = IntArray(2),
        )
        layout.onStopNestedScroll(content, ViewCompat.TYPE_TOUCH)

        assertEquals(1, refreshCount)
        assertEquals(KitRefreshLoadLayout.RefreshLoadState.Refreshing, layout.state)
        assertEquals(HEADER_HEIGHT, layout.currentOffset)
    }

    /**
     * 验证负数刷新触发距离都使用 header 高度.
     */
    @Test
    fun nestedPullDown_usesHeaderHeightWhenTriggerDistanceIsNegative() {
        val layout: KitRefreshLoadLayout = createLayout()
        val header: View = createChild(width = MATCH_PARENT, height = HEADER_HEIGHT)
        val content: View = createChild(width = MATCH_PARENT, height = MATCH_PARENT)
        var refreshCount: Int = 0

        layout.dragResistance = 1f
        layout.animationDurationMillis = 0L
        layout.refreshTriggerDistance = -100
        layout.addView(header, roleParams(KitRefreshLoadLayout.ChildRole.Header, MATCH_PARENT, HEADER_HEIGHT))
        layout.addView(content)
        layout.setOnRefreshLoadListener { action: KitRefreshLoadLayout.RefreshLoadAction ->
            if (action == KitRefreshLoadLayout.RefreshLoadAction.Refresh) {
                refreshCount += 1
            }
        }
        measureAndLayout(layout)

        layout.onNestedScroll(
            target = content,
            dxConsumed = 0,
            dyConsumed = 0,
            dxUnconsumed = 0,
            dyUnconsumed = -HEADER_HEIGHT,
            type = ViewCompat.TYPE_TOUCH,
            consumed = IntArray(2),
        )
        layout.onStopNestedScroll(content, ViewCompat.TYPE_TOUCH)

        assertEquals(1, refreshCount)
        assertEquals(KitRefreshLoadLayout.RefreshLoadState.Refreshing, layout.state)
        assertEquals(HEADER_HEIGHT, layout.currentOffset)
    }

    /**
     * 验证默认加载触发距离使用 footer 高度.
     */
    @Test
    fun nestedPullUp_usesFooterHeightAsDefaultTriggerDistance() {
        val layout: KitRefreshLoadLayout = createLayout()
        val content: View = createChild(width = MATCH_PARENT, height = MATCH_PARENT)
        val footer: View = createChild(width = MATCH_PARENT, height = FOOTER_HEIGHT)
        var loadMoreCount: Int = 0

        layout.dragResistance = 1f
        layout.animationDurationMillis = 0L
        layout.addView(content)
        layout.addView(footer, roleParams(KitRefreshLoadLayout.ChildRole.Footer, MATCH_PARENT, FOOTER_HEIGHT))
        layout.setOnRefreshLoadListener { action: KitRefreshLoadLayout.RefreshLoadAction ->
            if (action == KitRefreshLoadLayout.RefreshLoadAction.LoadMore) {
                loadMoreCount += 1
            }
        }
        measureAndLayout(layout)

        layout.onNestedScroll(
            target = content,
            dxConsumed = 0,
            dyConsumed = 0,
            dxUnconsumed = 0,
            dyUnconsumed = FOOTER_HEIGHT,
            type = ViewCompat.TYPE_TOUCH,
            consumed = IntArray(2),
        )
        layout.onStopNestedScroll(content, ViewCompat.TYPE_TOUCH)

        assertEquals(1, loadMoreCount)
        assertEquals(KitRefreshLoadLayout.RefreshLoadState.LoadingMore, layout.state)
        assertEquals(-FOOTER_HEIGHT, layout.currentOffset)
    }

    /**
     * 验证非触摸滚动结束后会清理待处理的 header 惯性速度.
     */
    @Test
    fun nonTouchStop_clearsPendingHeaderFlingVelocity() {
        val layout: KitRefreshLoadLayout = createLayout()
        val header: View = createChild(width = MATCH_PARENT, height = HEADER_HEIGHT)
        val content: View = createChild(width = MATCH_PARENT, height = MATCH_PARENT)

        layout.dragResistance = 1f
        layout.animationDurationMillis = 0L
        layout.refreshTriggerDistance = REFRESH_TRIGGER_DISTANCE
        layout.addView(header, roleParams(KitRefreshLoadLayout.ChildRole.Header, MATCH_PARENT, HEADER_HEIGHT))
        layout.addView(content)
        measureAndLayout(layout)

        layout.onNestedScroll(
            target = content,
            dxConsumed = 0,
            dyConsumed = 0,
            dxUnconsumed = 0,
            dyUnconsumed = -REFRESH_DRAG_DISTANCE,
            type = ViewCompat.TYPE_TOUCH,
            consumed = IntArray(2),
        )
        layout.onStopNestedScroll(content, ViewCompat.TYPE_TOUCH)
        layout.onNestedPreScroll(
            target = content,
            dx = 0,
            dy = HEADER_HEIGHT,
            consumed = IntArray(2),
            type = ViewCompat.TYPE_NON_TOUCH,
        )

        assertEquals(0, layout.currentOffset)
        assertFalse(layout.onNestedPreFling(content, velocityX = 0f, velocityY = -6_000f))

        layout.onStopNestedScroll(content, ViewCompat.TYPE_NON_TOUCH)
        layout.onNestedPreScroll(
            target = content,
            dx = 0,
            dy = -1,
            consumed = IntArray(2),
            type = ViewCompat.TYPE_NON_TOUCH,
        )
        repeat(5) {
            ShadowSystemClock.advanceBy(16, TimeUnit.MILLISECONDS)
            layout.computeScroll()
        }

        assertEquals(1, layout.currentOffset)
    }

    /**
     * 验证上拉到触发距离后松手会进入加载更多状态并回调监听器.
     */
    @Test
    fun nestedPullUp_triggersLoadMoreWhenOverThreshold() {
        val layout: KitRefreshLoadLayout = createLayout()
        val content: View = createChild(width = MATCH_PARENT, height = MATCH_PARENT)
        val footer: View = createChild(width = MATCH_PARENT, height = FOOTER_HEIGHT)
        var loadMoreCount: Int = 0

        layout.dragResistance = 1f
        layout.animationDurationMillis = 0L
        layout.loadMoreTriggerDistance = LOAD_MORE_TRIGGER_DISTANCE
        layout.addView(content)
        layout.addView(footer, roleParams(KitRefreshLoadLayout.ChildRole.Footer, MATCH_PARENT, FOOTER_HEIGHT))
        layout.setOnRefreshLoadListener { action: KitRefreshLoadLayout.RefreshLoadAction ->
            if (action == KitRefreshLoadLayout.RefreshLoadAction.LoadMore) {
                loadMoreCount += 1
            }
        }
        measureAndLayout(layout)

        val consumed: IntArray = IntArray(2)
        layout.onNestedScroll(
            target = content,
            dxConsumed = 0,
            dyConsumed = 0,
            dxUnconsumed = 0,
            dyUnconsumed = LOAD_MORE_DRAG_DISTANCE,
            type = ViewCompat.TYPE_TOUCH,
            consumed = consumed,
        )
        layout.onStopNestedScroll(content, ViewCompat.TYPE_TOUCH)

        assertEquals(LOAD_MORE_DRAG_DISTANCE, consumed[1])
        assertEquals(1, loadMoreCount)
        assertEquals(KitRefreshLoadLayout.RefreshLoadState.LoadingMore, layout.state)
        assertEquals(-FOOTER_HEIGHT, layout.currentOffset)
    }

    /**
     * 验证启用自动加载后, 内容滚动到底部会触发加载更多回调.
     */
    @Test
    fun autoLoadMore_triggersLoadMoreWhenContentScrollsToBottom() {
        val layout: KitRefreshLoadLayout = createLayout()
        val content: TestScrollableContentView = createScrollableContent(canScrollDown = false)
        val footer: View = createChild(width = MATCH_PARENT, height = FOOTER_HEIGHT)
        var loadMoreCount: Int = 0

        layout.animationDurationMillis = 0L
        layout.autoLoadMoreEnabled = true
        layout.addView(content)
        layout.addView(footer, roleParams(KitRefreshLoadLayout.ChildRole.Footer, MATCH_PARENT, FOOTER_HEIGHT))
        layout.setOnRefreshLoadListener { action: KitRefreshLoadLayout.RefreshLoadAction ->
            if (action == KitRefreshLoadLayout.RefreshLoadAction.LoadMore) {
                loadMoreCount += 1
            }
        }
        measureAndLayout(layout)

        layout.onNestedScroll(
            target = content,
            dxConsumed = 0,
            dyConsumed = AUTO_LOAD_SCROLL_DISTANCE,
            dxUnconsumed = 0,
            dyUnconsumed = 0,
            type = ViewCompat.TYPE_TOUCH,
            consumed = IntArray(2),
        )

        assertEquals(1, loadMoreCount)
        assertEquals(KitRefreshLoadLayout.RefreshLoadState.LoadingMore, layout.state)
        assertEquals(-FOOTER_HEIGHT, layout.currentOffset)
    }

    /**
     * 验证内容还能继续向下滚动时不会自动触发加载更多.
     */
    @Test
    fun autoLoadMore_doesNotTriggerBeforeContentReachesBottom() {
        val layout: KitRefreshLoadLayout = createLayout()
        val content: TestScrollableContentView = createScrollableContent(canScrollDown = true)
        val footer: View = createChild(width = MATCH_PARENT, height = FOOTER_HEIGHT)
        var loadMoreCount: Int = 0

        layout.animationDurationMillis = 0L
        layout.autoLoadMoreEnabled = true
        layout.addView(content)
        layout.addView(footer, roleParams(KitRefreshLoadLayout.ChildRole.Footer, MATCH_PARENT, FOOTER_HEIGHT))
        layout.setOnRefreshLoadListener { action: KitRefreshLoadLayout.RefreshLoadAction ->
            if (action == KitRefreshLoadLayout.RefreshLoadAction.LoadMore) {
                loadMoreCount += 1
            }
        }
        measureAndLayout(layout)

        layout.onNestedScroll(
            target = content,
            dxConsumed = 0,
            dyConsumed = AUTO_LOAD_SCROLL_DISTANCE,
            dxUnconsumed = 0,
            dyUnconsumed = 0,
            type = ViewCompat.TYPE_TOUCH,
            consumed = IntArray(2),
        )

        assertEquals(0, loadMoreCount)
        assertEquals(KitRefreshLoadLayout.RefreshLoadState.Idle, layout.state)
        assertEquals(0, layout.currentOffset)
    }

    /**
     * 验证自动加载中不会重复触发加载更多回调.
     */
    @Test
    fun autoLoadMore_doesNotTriggerAgainWhileLoadingMore() {
        val layout: KitRefreshLoadLayout = createLayout()
        val content: TestScrollableContentView = createScrollableContent(canScrollDown = false)
        val footer: View = createChild(width = MATCH_PARENT, height = FOOTER_HEIGHT)
        var loadMoreCount: Int = 0

        layout.animationDurationMillis = 0L
        layout.autoLoadMoreEnabled = true
        layout.addView(content)
        layout.addView(footer, roleParams(KitRefreshLoadLayout.ChildRole.Footer, MATCH_PARENT, FOOTER_HEIGHT))
        layout.setOnRefreshLoadListener { action: KitRefreshLoadLayout.RefreshLoadAction ->
            if (action == KitRefreshLoadLayout.RefreshLoadAction.LoadMore) {
                loadMoreCount += 1
            }
        }
        measureAndLayout(layout)

        repeat(2) {
            layout.onNestedScroll(
                target = content,
                dxConsumed = 0,
                dyConsumed = AUTO_LOAD_SCROLL_DISTANCE,
                dxUnconsumed = 0,
                dyUnconsumed = 0,
                type = ViewCompat.TYPE_TOUCH,
                consumed = IntArray(2),
            )
        }

        assertEquals(1, loadMoreCount)
        assertEquals(KitRefreshLoadLayout.RefreshLoadState.LoadingMore, layout.state)
    }

    /**
     * 验证加载更多中 content 继续跟随 footer 位移.
     */
    @Test
    fun loadingMore_movesContentWithFooter() {
        val layout: KitRefreshLoadLayout = createLayout()
        val content: View = createChild(width = MATCH_PARENT, height = MATCH_PARENT)
        val footer: View = createChild(width = MATCH_PARENT, height = FOOTER_HEIGHT)

        layout.dragResistance = 1f
        layout.animationDurationMillis = 0L
        layout.loadMoreTriggerDistance = LOAD_MORE_TRIGGER_DISTANCE
        layout.addView(content)
        layout.addView(footer, roleParams(KitRefreshLoadLayout.ChildRole.Footer, MATCH_PARENT, FOOTER_HEIGHT))
        measureAndLayout(layout)

        layout.onNestedScroll(
            target = content,
            dxConsumed = 0,
            dyConsumed = 0,
            dxUnconsumed = 0,
            dyUnconsumed = LOAD_MORE_DRAG_DISTANCE,
            type = ViewCompat.TYPE_TOUCH,
            consumed = IntArray(2),
        )
        layout.onStopNestedScroll(content, ViewCompat.TYPE_TOUCH)

        assertEquals(KitRefreshLoadLayout.RefreshLoadState.LoadingMore, layout.state)
        assertEquals(-FOOTER_HEIGHT.toFloat(), content.translationY, 0f)
        assertEquals(-FOOTER_HEIGHT.toFloat(), footer.translationY, 0f)

        layout.animationDurationMillis = ANIMATION_DURATION_MILLIS
        layout.finishLoadMore()

        assertEquals(KitRefreshLoadLayout.RefreshLoadState.Settling, layout.state)
        assertEquals(-FOOTER_HEIGHT.toFloat(), content.translationY, 0f)
    }

    /**
     * 验证加载中的 footer 会跟随 content 触摸滚动和惯性滚动离开屏幕.
     */
    @Test
    fun loadingMore_scrollsFooterOutWithContentScrollAndFling() {
        val layout: KitRefreshLoadLayout = createLayout()
        val content: View = createChild(width = MATCH_PARENT, height = MATCH_PARENT)
        val footer: View = createChild(width = MATCH_PARENT, height = FOOTER_HEIGHT)

        layout.dragResistance = 1f
        layout.animationDurationMillis = 0L
        layout.loadMoreTriggerDistance = LOAD_MORE_TRIGGER_DISTANCE
        layout.addView(content)
        layout.addView(footer, roleParams(KitRefreshLoadLayout.ChildRole.Footer, MATCH_PARENT, FOOTER_HEIGHT))
        measureAndLayout(layout)

        layout.onNestedScroll(
            target = content,
            dxConsumed = 0,
            dyConsumed = 0,
            dxUnconsumed = 0,
            dyUnconsumed = LOAD_MORE_DRAG_DISTANCE,
            type = ViewCompat.TYPE_TOUCH,
            consumed = IntArray(2),
        )
        layout.onStopNestedScroll(content, ViewCompat.TYPE_TOUCH)
        val touchConsumed: IntArray = IntArray(2)
        layout.onNestedPreScroll(
            target = content,
            dx = 0,
            dy = -FOOTER_HEIGHT / 2,
            consumed = touchConsumed,
            type = ViewCompat.TYPE_TOUCH,
        )

        assertEquals(-FOOTER_HEIGHT / 2, touchConsumed[1])
        assertEquals(-FOOTER_HEIGHT / 2, layout.currentOffset)
        assertEquals((-FOOTER_HEIGHT / 2).toFloat(), footer.translationY, 0f)
        assertEquals((-FOOTER_HEIGHT / 2).toFloat(), content.translationY, 0f)

        val flingConsumed: IntArray = IntArray(2)
        layout.onNestedPreScroll(
            target = content,
            dx = 0,
            dy = -FOOTER_HEIGHT,
            consumed = flingConsumed,
            type = ViewCompat.TYPE_NON_TOUCH,
        )

        assertEquals(-FOOTER_HEIGHT / 2, flingConsumed[1])
        assertEquals(0, layout.currentOffset)
        assertEquals(0f, footer.translationY, 0f)
        assertEquals(0f, content.translationY, 0f)
        assertEquals(KitRefreshLoadLayout.RefreshLoadState.LoadingMore, layout.state)

        val returnFlingConsumed: IntArray = IntArray(2)
        layout.onNestedPreScroll(
            target = content,
            dx = 0,
            dy = FOOTER_HEIGHT,
            consumed = returnFlingConsumed,
            type = ViewCompat.TYPE_NON_TOUCH,
        )

        assertEquals(FOOTER_HEIGHT, returnFlingConsumed[1])
        assertEquals(-FOOTER_HEIGHT, layout.currentOffset)
        assertEquals(-FOOTER_HEIGHT.toFloat(), footer.translationY, 0f)
        assertEquals(-FOOTER_HEIGHT.toFloat(), content.translationY, 0f)
    }

    /**
     * 验证同一轮触摸手势滚动到顶部后, 可以继续下拉触发刷新.
     */
    @Test
    fun nestedTouchScroll_continuesPullDownAfterContentReachesTop() {
        val layout: KitRefreshLoadLayout = createLayout()
        val header: View = createChild(width = MATCH_PARENT, height = HEADER_HEIGHT)
        val content: View = createChild(width = MATCH_PARENT, height = MATCH_PARENT)
        var refreshCount: Int = 0

        layout.dragResistance = 1f
        layout.animationDurationMillis = 0L
        layout.refreshTriggerDistance = REFRESH_TRIGGER_DISTANCE
        layout.addView(header, roleParams(KitRefreshLoadLayout.ChildRole.Header, MATCH_PARENT, HEADER_HEIGHT))
        layout.addView(content)
        layout.setOnRefreshLoadListener { action: KitRefreshLoadLayout.RefreshLoadAction ->
            if (action == KitRefreshLoadLayout.RefreshLoadAction.Refresh) {
                refreshCount += 1
            }
        }
        measureAndLayout(layout)

        val consumed: IntArray = IntArray(2)
        layout.onNestedScroll(
            target = content,
            dxConsumed = 0,
            dyConsumed = 20,
            dxUnconsumed = 0,
            dyUnconsumed = -REFRESH_DRAG_DISTANCE,
            type = ViewCompat.TYPE_TOUCH,
            consumed = consumed,
        )
        layout.onStopNestedScroll(content, ViewCompat.TYPE_TOUCH)

        assertEquals(-REFRESH_DRAG_DISTANCE, consumed[1])
        assertEquals(1, refreshCount)
        assertEquals(KitRefreshLoadLayout.RefreshLoadState.Refreshing, layout.state)
    }

    /**
     * 验证拖拽阻尼会随拉出距离增加.
     */
    @Test
    fun nestedPullDown_increasesResistanceWhenOffsetGrows() {
        val layout: KitRefreshLoadLayout = createLayout()
        val header: View = createChild(width = MATCH_PARENT, height = HEADER_HEIGHT)
        val content: View = createChild(width = MATCH_PARENT, height = MATCH_PARENT)

        layout.dragResistance = 1f
        layout.animationDurationMillis = 0L
        layout.refreshTriggerDistance = REFRESH_TRIGGER_DISTANCE
        layout.maxDragDistance = REFRESH_DRAG_DISTANCE * 4
        layout.addView(header, roleParams(KitRefreshLoadLayout.ChildRole.Header, MATCH_PARENT, HEADER_HEIGHT))
        layout.addView(content)
        measureAndLayout(layout)

        layout.onNestedScroll(
            target = content,
            dxConsumed = 0,
            dyConsumed = 0,
            dxUnconsumed = 0,
            dyUnconsumed = -REFRESH_DRAG_DISTANCE,
            type = ViewCompat.TYPE_TOUCH,
            consumed = IntArray(2),
        )
        val firstOffset: Int = layout.currentOffset
        layout.onNestedScroll(
            target = content,
            dxConsumed = 0,
            dyConsumed = 0,
            dxUnconsumed = 0,
            dyUnconsumed = -REFRESH_DRAG_DISTANCE,
            type = ViewCompat.TYPE_TOUCH,
            consumed = IntArray(2),
        )
        val secondDelta: Int = layout.currentOffset - firstOffset

        assertTrue(secondDelta < firstOffset)
    }

    /**
     * 验证反向收回 header 时不会重复套用拖拽阻尼.
     */
    @Test
    fun nestedPreScroll_collapsesPulledHeaderOneToOne() {
        val layout: KitRefreshLoadLayout = createLayout()
        val header: View = createChild(width = MATCH_PARENT, height = HEADER_HEIGHT)
        val content: View = createChild(width = MATCH_PARENT, height = MATCH_PARENT)

        layout.dragResistance = 2f
        layout.animationDurationMillis = 0L
        layout.refreshTriggerDistance = REFRESH_TRIGGER_DISTANCE
        layout.addView(header, roleParams(KitRefreshLoadLayout.ChildRole.Header, MATCH_PARENT, HEADER_HEIGHT))
        layout.addView(content)
        measureAndLayout(layout)

        layout.onNestedScroll(
            target = content,
            dxConsumed = 0,
            dyConsumed = 0,
            dxUnconsumed = 0,
            dyUnconsumed = -REFRESH_DRAG_DISTANCE,
            type = ViewCompat.TYPE_TOUCH,
            consumed = IntArray(2),
        )
        val beforeCollapseOffset: Int = layout.currentOffset
        val consumed: IntArray = IntArray(2)
        layout.onNestedPreScroll(
            target = content,
            dx = 0,
            dy = 10,
            consumed = consumed,
            type = ViewCompat.TYPE_TOUCH,
        )

        assertEquals(10, consumed[1])
        assertEquals(beforeCollapseOffset - 10, layout.currentOffset)
    }

    /**
     * 验证惯性滚动到边界后的未消费距离不会拉出 header 或 footer.
     */
    @Test
    fun nestedNonTouchScroll_doesNotStartRefreshOrLoadMoreDrag() {
        val layout: KitRefreshLoadLayout = createLayout()
        val header: View = createChild(width = MATCH_PARENT, height = HEADER_HEIGHT)
        val content: View = createChild(width = MATCH_PARENT, height = MATCH_PARENT)
        val footer: View = createChild(width = MATCH_PARENT, height = FOOTER_HEIGHT)

        layout.dragResistance = 1f
        layout.animationDurationMillis = 0L
        layout.refreshTriggerDistance = REFRESH_TRIGGER_DISTANCE
        layout.loadMoreTriggerDistance = LOAD_MORE_TRIGGER_DISTANCE
        layout.addView(header, roleParams(KitRefreshLoadLayout.ChildRole.Header, MATCH_PARENT, HEADER_HEIGHT))
        layout.addView(content)
        layout.addView(footer, roleParams(KitRefreshLoadLayout.ChildRole.Footer, MATCH_PARENT, FOOTER_HEIGHT))
        measureAndLayout(layout)

        val refreshConsumed: IntArray = IntArray(2)
        val loadMoreConsumed: IntArray = IntArray(2)
        layout.onNestedScroll(
            target = content,
            dxConsumed = 0,
            dyConsumed = 0,
            dxUnconsumed = 0,
            dyUnconsumed = -REFRESH_DRAG_DISTANCE,
            type = ViewCompat.TYPE_NON_TOUCH,
            consumed = refreshConsumed,
        )
        layout.onNestedScroll(
            target = content,
            dxConsumed = 0,
            dyConsumed = 0,
            dxUnconsumed = 0,
            dyUnconsumed = LOAD_MORE_DRAG_DISTANCE,
            type = ViewCompat.TYPE_NON_TOUCH,
            consumed = loadMoreConsumed,
        )
        layout.onStopNestedScroll(content, ViewCompat.TYPE_NON_TOUCH)

        assertEquals(0, refreshConsumed[1])
        assertEquals(0, loadMoreConsumed[1])
        assertEquals(KitRefreshLoadLayout.RefreshLoadState.Idle, layout.state)
        assertEquals(0, layout.currentOffset)
    }

    /**
     * 验证刷新中不会拦截内容列表的惯性滚动.
     */
    @Test
    fun nestedPreFling_doesNotConsumeWhenRefreshing() {
        val layout: KitRefreshLoadLayout = createLayout()
        val header: View = createChild(width = MATCH_PARENT, height = HEADER_HEIGHT)
        val content: View = createChild(width = MATCH_PARENT, height = MATCH_PARENT)

        layout.dragResistance = 1f
        layout.animationDurationMillis = 0L
        layout.refreshTriggerDistance = REFRESH_TRIGGER_DISTANCE
        layout.addView(header, roleParams(KitRefreshLoadLayout.ChildRole.Header, MATCH_PARENT, HEADER_HEIGHT))
        layout.addView(content)
        measureAndLayout(layout)

        layout.onNestedScroll(
            target = content,
            dxConsumed = 0,
            dyConsumed = 0,
            dxUnconsumed = 0,
            dyUnconsumed = -REFRESH_DRAG_DISTANCE,
            type = ViewCompat.TYPE_TOUCH,
            consumed = IntArray(2),
        )
        layout.onStopNestedScroll(content, ViewCompat.TYPE_TOUCH)

        assertEquals(KitRefreshLoadLayout.RefreshLoadState.Refreshing, layout.state)
        assertFalse(layout.onNestedPreFling(content, velocityX = 0f, velocityY = 2_000f))
    }

    /**
     * 验证加载更多中不会拦截内容列表的惯性滚动.
     */
    @Test
    fun nestedPreFling_doesNotConsumeWhenLoadingMore() {
        val layout: KitRefreshLoadLayout = createLayout()
        val content: View = createChild(width = MATCH_PARENT, height = MATCH_PARENT)
        val footer: View = createChild(width = MATCH_PARENT, height = FOOTER_HEIGHT)

        layout.dragResistance = 1f
        layout.animationDurationMillis = 0L
        layout.loadMoreTriggerDistance = LOAD_MORE_TRIGGER_DISTANCE
        layout.addView(content)
        layout.addView(footer, roleParams(KitRefreshLoadLayout.ChildRole.Footer, MATCH_PARENT, FOOTER_HEIGHT))
        measureAndLayout(layout)

        layout.onNestedScroll(
            target = content,
            dxConsumed = 0,
            dyConsumed = 0,
            dxUnconsumed = 0,
            dyUnconsumed = LOAD_MORE_DRAG_DISTANCE,
            type = ViewCompat.TYPE_TOUCH,
            consumed = IntArray(2),
        )
        layout.onStopNestedScroll(content, ViewCompat.TYPE_TOUCH)

        assertEquals(KitRefreshLoadLayout.RefreshLoadState.LoadingMore, layout.state)
        assertFalse(layout.onNestedPreFling(content, velocityX = 0f, velocityY = -2_000f))
    }

    /**
     * 验证拖拽中的 header 会拦截惯性滚动, 优先完成当前刷新手势.
     */
    @Test
    fun nestedPreFling_consumesWhenHeaderIsPulledButNotBusy() {
        val layout: KitRefreshLoadLayout = createLayout()
        val header: View = createChild(width = MATCH_PARENT, height = HEADER_HEIGHT)
        val content: View = createChild(width = MATCH_PARENT, height = MATCH_PARENT)
        val footer: View = createChild(width = MATCH_PARENT, height = FOOTER_HEIGHT)

        layout.dragResistance = 1f
        layout.animationDurationMillis = 0L
        layout.refreshTriggerDistance = REFRESH_TRIGGER_DISTANCE
        layout.loadMoreTriggerDistance = LOAD_MORE_TRIGGER_DISTANCE
        layout.addView(header, roleParams(KitRefreshLoadLayout.ChildRole.Header, MATCH_PARENT, HEADER_HEIGHT))
        layout.addView(content)
        layout.addView(footer, roleParams(KitRefreshLoadLayout.ChildRole.Footer, MATCH_PARENT, FOOTER_HEIGHT))
        measureAndLayout(layout)

        layout.onNestedScroll(
            target = content,
            dxConsumed = 0,
            dyConsumed = 0,
            dxUnconsumed = 0,
            dyUnconsumed = -REFRESH_TRIGGER_DISTANCE + 1,
            type = ViewCompat.TYPE_TOUCH,
            consumed = IntArray(2),
        )

        assertTrue(layout.onNestedPreFling(content, velocityX = 0f, velocityY = 2_000f))
    }

    /**
     * 验证完成刷新会收起 header, 且不会重复触发监听器.
     */
    @Test
    fun finishRefresh_collapsesHeaderWithoutInvokingListenerAgain() {
        val layout: KitRefreshLoadLayout = createLayout()
        val header: View = createChild(width = MATCH_PARENT, height = HEADER_HEIGHT)
        val content: View = createChild(width = MATCH_PARENT, height = MATCH_PARENT)
        var refreshCount: Int = 0

        layout.dragResistance = 1f
        layout.animationDurationMillis = 0L
        layout.refreshTriggerDistance = REFRESH_TRIGGER_DISTANCE
        layout.addView(header, roleParams(KitRefreshLoadLayout.ChildRole.Header, MATCH_PARENT, HEADER_HEIGHT))
        layout.addView(content)
        layout.setOnRefreshLoadListener {
            refreshCount += 1
        }
        measureAndLayout(layout)

        layout.onNestedScroll(
            target = content,
            dxConsumed = 0,
            dyConsumed = 0,
            dxUnconsumed = 0,
            dyUnconsumed = -REFRESH_DRAG_DISTANCE,
            type = ViewCompat.TYPE_TOUCH,
            consumed = IntArray(2),
        )
        layout.onStopNestedScroll(content, ViewCompat.TYPE_TOUCH)
        layout.finishRefresh(animate = false)

        assertEquals(1, refreshCount)
        assertEquals(KitRefreshLoadLayout.RefreshLoadState.Idle, layout.state)
        assertEquals(0, layout.currentOffset)
    }

    /**
     * 验证刷新完成开始收起 header 时, content 跟随 header 一起收起.
     */
    @Test
    fun finishRefresh_movesContentWithHeaderDuringSettle() {
        val layout: KitRefreshLoadLayout = createLayout()
        val header: View = createChild(width = MATCH_PARENT, height = HEADER_HEIGHT)
        val content: View = createChild(width = MATCH_PARENT, height = MATCH_PARENT)

        layout.dragResistance = 1f
        layout.animationDurationMillis = 0L
        layout.refreshTriggerDistance = REFRESH_TRIGGER_DISTANCE
        layout.addView(header, roleParams(KitRefreshLoadLayout.ChildRole.Header, MATCH_PARENT, HEADER_HEIGHT))
        layout.addView(content)
        measureAndLayout(layout)

        layout.onNestedScroll(
            target = content,
            dxConsumed = 0,
            dyConsumed = 0,
            dxUnconsumed = 0,
            dyUnconsumed = -REFRESH_DRAG_DISTANCE,
            type = ViewCompat.TYPE_TOUCH,
            consumed = IntArray(2),
        )
        layout.onStopNestedScroll(content, ViewCompat.TYPE_TOUCH)

        assertEquals(KitRefreshLoadLayout.RefreshLoadState.Refreshing, layout.state)
        assertEquals(HEADER_HEIGHT.toFloat(), content.translationY, 0f)

        layout.animationDurationMillis = ANIMATION_DURATION_MILLIS
        layout.finishRefresh()

        assertEquals(KitRefreshLoadLayout.RefreshLoadState.Settling, layout.state)
        assertEquals(HEADER_HEIGHT.toFloat(), content.translationY, 0f)
        assertEquals(HEADER_HEIGHT.toFloat(), header.translationY, 0f)
    }

    /**
     * 验证加载完成开始收起 footer 时, content 跟随 footer 一起收起.
     */
    @Test
    fun finishLoadMore_movesContentWithFooterDuringSettle() {
        val layout: KitRefreshLoadLayout = createLayout()
        val content: View = createChild(width = MATCH_PARENT, height = MATCH_PARENT)
        val footer: View = createChild(width = MATCH_PARENT, height = FOOTER_HEIGHT)

        layout.dragResistance = 1f
        layout.animationDurationMillis = 0L
        layout.loadMoreTriggerDistance = LOAD_MORE_TRIGGER_DISTANCE
        layout.addView(content)
        layout.addView(footer, roleParams(KitRefreshLoadLayout.ChildRole.Footer, MATCH_PARENT, FOOTER_HEIGHT))
        measureAndLayout(layout)

        layout.onNestedScroll(
            target = content,
            dxConsumed = 0,
            dyConsumed = 0,
            dxUnconsumed = 0,
            dyUnconsumed = LOAD_MORE_DRAG_DISTANCE,
            type = ViewCompat.TYPE_TOUCH,
            consumed = IntArray(2),
        )
        layout.onStopNestedScroll(content, ViewCompat.TYPE_TOUCH)

        assertEquals(KitRefreshLoadLayout.RefreshLoadState.LoadingMore, layout.state)
        assertEquals(-FOOTER_HEIGHT.toFloat(), content.translationY, 0f)

        layout.animationDurationMillis = ANIMATION_DURATION_MILLIS
        layout.finishLoadMore()

        assertEquals(KitRefreshLoadLayout.RefreshLoadState.Settling, layout.state)
        assertEquals(-FOOTER_HEIGHT.toFloat(), content.translationY, 0f)
        assertEquals(-FOOTER_HEIGHT.toFloat(), footer.translationY, 0f)
    }

    /**
     * 验证刷新中不能同时触发加载更多.
     */
    @Test
    fun nestedPullUp_doesNotTriggerLoadMoreWhenRefreshing() {
        val layout: KitRefreshLoadLayout = createLayout()
        val header: View = createChild(width = MATCH_PARENT, height = HEADER_HEIGHT)
        val content: View = createChild(width = MATCH_PARENT, height = MATCH_PARENT)
        val footer: View = createChild(width = MATCH_PARENT, height = FOOTER_HEIGHT)
        var loadMoreCount: Int = 0

        layout.dragResistance = 1f
        layout.animationDurationMillis = 0L
        layout.refreshTriggerDistance = REFRESH_TRIGGER_DISTANCE
        layout.loadMoreTriggerDistance = LOAD_MORE_TRIGGER_DISTANCE
        layout.addView(header, roleParams(KitRefreshLoadLayout.ChildRole.Header, MATCH_PARENT, HEADER_HEIGHT))
        layout.addView(content)
        layout.addView(footer, roleParams(KitRefreshLoadLayout.ChildRole.Footer, MATCH_PARENT, FOOTER_HEIGHT))
        layout.setOnRefreshLoadListener { action: KitRefreshLoadLayout.RefreshLoadAction ->
            if (action == KitRefreshLoadLayout.RefreshLoadAction.LoadMore) {
                loadMoreCount += 1
            }
        }
        measureAndLayout(layout)

        layout.onNestedScroll(
            target = content,
            dxConsumed = 0,
            dyConsumed = 0,
            dxUnconsumed = 0,
            dyUnconsumed = -REFRESH_DRAG_DISTANCE,
            type = ViewCompat.TYPE_TOUCH,
            consumed = IntArray(2),
        )
        layout.onStopNestedScroll(content, ViewCompat.TYPE_TOUCH)
        layout.onNestedScroll(
            target = content,
            dxConsumed = 0,
            dyConsumed = 0,
            dxUnconsumed = 0,
            dyUnconsumed = LOAD_MORE_DRAG_DISTANCE,
            type = ViewCompat.TYPE_TOUCH,
            consumed = IntArray(2),
        )
        layout.onStopNestedScroll(content, ViewCompat.TYPE_TOUCH)

        assertEquals(0, loadMoreCount)
        assertEquals(KitRefreshLoadLayout.RefreshLoadState.Refreshing, layout.state)
    }

    /**
     * 验证加载更多中不能同时触发刷新.
     */
    @Test
    fun nestedPullDown_doesNotTriggerRefreshWhenLoadingMore() {
        val layout: KitRefreshLoadLayout = createLayout()
        val header: View = createChild(width = MATCH_PARENT, height = HEADER_HEIGHT)
        val content: View = createChild(width = MATCH_PARENT, height = MATCH_PARENT)
        val footer: View = createChild(width = MATCH_PARENT, height = FOOTER_HEIGHT)
        var refreshCount: Int = 0

        layout.dragResistance = 1f
        layout.animationDurationMillis = 0L
        layout.refreshTriggerDistance = REFRESH_TRIGGER_DISTANCE
        layout.loadMoreTriggerDistance = LOAD_MORE_TRIGGER_DISTANCE
        layout.addView(header, roleParams(KitRefreshLoadLayout.ChildRole.Header, MATCH_PARENT, HEADER_HEIGHT))
        layout.addView(content)
        layout.addView(footer, roleParams(KitRefreshLoadLayout.ChildRole.Footer, MATCH_PARENT, FOOTER_HEIGHT))
        layout.setOnRefreshLoadListener { action: KitRefreshLoadLayout.RefreshLoadAction ->
            if (action == KitRefreshLoadLayout.RefreshLoadAction.Refresh) {
                refreshCount += 1
            }
        }
        measureAndLayout(layout)

        layout.onNestedScroll(
            target = content,
            dxConsumed = 0,
            dyConsumed = 0,
            dxUnconsumed = 0,
            dyUnconsumed = LOAD_MORE_DRAG_DISTANCE,
            type = ViewCompat.TYPE_TOUCH,
            consumed = IntArray(2),
        )
        layout.onStopNestedScroll(content, ViewCompat.TYPE_TOUCH)
        layout.onNestedScroll(
            target = content,
            dxConsumed = 0,
            dyConsumed = 0,
            dxUnconsumed = 0,
            dyUnconsumed = -REFRESH_DRAG_DISTANCE,
            type = ViewCompat.TYPE_TOUCH,
            consumed = IntArray(2),
        )
        layout.onStopNestedScroll(content, ViewCompat.TYPE_TOUCH)

        assertEquals(0, refreshCount)
        assertEquals(KitRefreshLoadLayout.RefreshLoadState.LoadingMore, layout.state)
    }

    /**
     * 验证禁用下拉刷新会收起正在显示的 header.
     */
    @Test
    fun refreshEnabledFalse_collapsesActiveHeader() {
        val layout: KitRefreshLoadLayout = createLayout()
        val header: View = createChild(width = MATCH_PARENT, height = HEADER_HEIGHT)
        val content: View = createChild(width = MATCH_PARENT, height = MATCH_PARENT)

        layout.dragResistance = 1f
        layout.animationDurationMillis = 0L
        layout.refreshTriggerDistance = REFRESH_TRIGGER_DISTANCE
        layout.addView(header, roleParams(KitRefreshLoadLayout.ChildRole.Header, MATCH_PARENT, HEADER_HEIGHT))
        layout.addView(content)
        measureAndLayout(layout)

        layout.onNestedScroll(
            target = content,
            dxConsumed = 0,
            dyConsumed = 0,
            dxUnconsumed = 0,
            dyUnconsumed = -REFRESH_DRAG_DISTANCE,
            type = ViewCompat.TYPE_TOUCH,
            consumed = IntArray(2),
        )
        layout.onStopNestedScroll(content, ViewCompat.TYPE_TOUCH)
        layout.refreshEnabled = false

        assertEquals(KitRefreshLoadLayout.RefreshLoadState.Idle, layout.state)
        assertEquals(0, layout.currentOffset)
    }

    /**
     * 验证禁用上拉加载会收起正在显示的 footer.
     */
    @Test
    fun loadMoreEnabledFalse_collapsesActiveFooter() {
        val layout: KitRefreshLoadLayout = createLayout()
        val content: View = createChild(width = MATCH_PARENT, height = MATCH_PARENT)
        val footer: View = createChild(width = MATCH_PARENT, height = FOOTER_HEIGHT)

        layout.dragResistance = 1f
        layout.animationDurationMillis = 0L
        layout.loadMoreTriggerDistance = LOAD_MORE_TRIGGER_DISTANCE
        layout.addView(content)
        layout.addView(footer, roleParams(KitRefreshLoadLayout.ChildRole.Footer, MATCH_PARENT, FOOTER_HEIGHT))
        measureAndLayout(layout)

        layout.onNestedScroll(
            target = content,
            dxConsumed = 0,
            dyConsumed = 0,
            dxUnconsumed = 0,
            dyUnconsumed = LOAD_MORE_DRAG_DISTANCE,
            type = ViewCompat.TYPE_TOUCH,
            consumed = IntArray(2),
        )
        layout.onStopNestedScroll(content, ViewCompat.TYPE_TOUCH)
        layout.loadMoreEnabled = false

        assertEquals(KitRefreshLoadLayout.RefreshLoadState.Idle, layout.state)
        assertEquals(0, layout.currentOffset)
    }

    /**
     * 验证 header 组件能收到状态和位移回调.
     */
    @Test
    fun headerComponent_receivesStateAndOffsetChanges() {
        val layout: KitRefreshLoadLayout = createLayout()
        val header = TestRefreshLoadComponentView()
        val content: View = createChild(width = MATCH_PARENT, height = MATCH_PARENT)

        layout.dragResistance = 1f
        layout.animationDurationMillis = 0L
        layout.refreshTriggerDistance = REFRESH_TRIGGER_DISTANCE
        layout.addView(header, roleParams(KitRefreshLoadLayout.ChildRole.Header, MATCH_PARENT, HEADER_HEIGHT))
        layout.addView(content)
        measureAndLayout(layout)

        layout.onNestedScroll(
            target = content,
            dxConsumed = 0,
            dyConsumed = 0,
            dxUnconsumed = 0,
            dyUnconsumed = -REFRESH_DRAG_DISTANCE,
            type = ViewCompat.TYPE_TOUCH,
            consumed = IntArray(2),
        )
        layout.onStopNestedScroll(content, ViewCompat.TYPE_TOUCH)

        assertTrue(header.states.contains(KitRefreshLoadLayout.RefreshLoadState.ReleaseToRefresh))
        assertTrue(header.states.contains(KitRefreshLoadLayout.RefreshLoadState.Refreshing))
        assertEquals(HEADER_HEIGHT, header.lastOffset)
        assertEquals(REFRESH_TRIGGER_DISTANCE, header.lastTriggerDistance)
        assertEquals(HEADER_HEIGHT, header.lastHoldDistance)
    }

    /**
     * 验证 footer 组件能收到状态和位移回调.
     */
    @Test
    fun footerComponent_receivesStateAndOffsetChanges() {
        val layout: KitRefreshLoadLayout = createLayout()
        val content: View = createChild(width = MATCH_PARENT, height = MATCH_PARENT)
        val footer = TestRefreshLoadComponentView()

        layout.dragResistance = 1f
        layout.animationDurationMillis = 0L
        layout.loadMoreTriggerDistance = LOAD_MORE_TRIGGER_DISTANCE
        layout.addView(content)
        layout.addView(footer, roleParams(KitRefreshLoadLayout.ChildRole.Footer, MATCH_PARENT, FOOTER_HEIGHT))
        measureAndLayout(layout)

        layout.onNestedScroll(
            target = content,
            dxConsumed = 0,
            dyConsumed = 0,
            dxUnconsumed = 0,
            dyUnconsumed = LOAD_MORE_DRAG_DISTANCE,
            type = ViewCompat.TYPE_TOUCH,
            consumed = IntArray(2),
        )
        layout.onStopNestedScroll(content, ViewCompat.TYPE_TOUCH)

        assertTrue(footer.states.contains(KitRefreshLoadLayout.RefreshLoadState.ReleaseToLoadMore))
        assertTrue(footer.states.contains(KitRefreshLoadLayout.RefreshLoadState.LoadingMore))
        assertEquals(-FOOTER_HEIGHT, footer.lastOffset)
        assertEquals(LOAD_MORE_TRIGGER_DISTANCE, footer.lastTriggerDistance)
        assertEquals(FOOTER_HEIGHT, footer.lastHoldDistance)
    }

    /**
     * 验证 active 手指抬起后, 其它手指可以继续当前拖拽.
     */
    @Test
    fun touchDrag_continuesWhenActivePointerGoesUp() {
        val layout: KitRefreshLoadLayout = createLayout()
        val header: View = createChild(width = MATCH_PARENT, height = HEADER_HEIGHT)
        val content: View = createChild(width = MATCH_PARENT, height = MATCH_PARENT)

        layout.dragResistance = 1f
        layout.addView(header, roleParams(KitRefreshLoadLayout.ChildRole.Header, MATCH_PARENT, HEADER_HEIGHT))
        layout.addView(content)
        measureAndLayout(layout)

        layout.onTouchEvent(touchEvent(MotionEvent.ACTION_DOWN, pointer(id = 0, y = 0f)))
        layout.onTouchEvent(touchEvent(MotionEvent.ACTION_MOVE, pointer(id = 0, y = REFRESH_DRAG_DISTANCE.toFloat())))
        val offsetBeforePointerUp: Int = layout.currentOffset
        layout.onTouchEvent(
            touchEvent(
                actionMasked = MotionEvent.ACTION_POINTER_DOWN,
                actionIndex = 1,
                pointer(id = 0, y = REFRESH_DRAG_DISTANCE.toFloat()),
                pointer(id = 1, y = REFRESH_DRAG_DISTANCE.toFloat()),
            ),
        )
        layout.onTouchEvent(
            touchEvent(
                actionMasked = MotionEvent.ACTION_POINTER_UP,
                actionIndex = 0,
                pointer(id = 0, y = REFRESH_DRAG_DISTANCE.toFloat()),
                pointer(id = 1, y = REFRESH_DRAG_DISTANCE.toFloat()),
            ),
        )
        layout.onTouchEvent(
            touchEvent(
                MotionEvent.ACTION_MOVE,
                pointer(id = 1, y = (REFRESH_DRAG_DISTANCE + 20).toFloat()),
            ),
        )

        assertTrue(layout.currentOffset > offsetBeforePointerUp)
    }

    /**
     * 验证缺少 header 时下拉刷新会自动禁用.
     */
    @Test
    fun layout_disablesRefreshWhenHeaderMissing() {
        val layout: KitRefreshLoadLayout = createLayout()

        layout.addView(createChild(width = MATCH_PARENT, height = MATCH_PARENT))
        measureAndLayout(layout)
        layout.refreshEnabled = true

        assertEquals(false, layout.refreshEnabled)
    }

    /**
     * 验证缺少 footer 时上拉加载会自动禁用.
     */
    @Test
    fun layout_disablesLoadMoreWhenFooterMissing() {
        val layout: KitRefreshLoadLayout = createLayout()

        layout.addView(createChild(width = MATCH_PARENT, height = MATCH_PARENT))
        measureAndLayout(layout)
        layout.loadMoreEnabled = true

        assertEquals(false, layout.loadMoreEnabled)
    }

    /**
     * 验证缺少 header 禁用后, 运行时补充 header 会恢复调用方启用意图.
     */
    @Test
    fun layout_restoresRefreshEnabledWhenHeaderAddedLater() {
        val layout: KitRefreshLoadLayout = createLayout()
        val header: View = createChild(width = MATCH_PARENT, height = HEADER_HEIGHT)

        layout.addView(createChild(width = MATCH_PARENT, height = MATCH_PARENT))
        measureAndLayout(layout)
        layout.refreshEnabled = true
        layout.addView(
            header,
            roleParams(
                KitRefreshLoadLayout.ChildRole.Header,
                MATCH_PARENT,
                HEADER_HEIGHT,
            ),
        )

        assertEquals(true, layout.refreshEnabled)
    }

    /**
     * 验证缺少 footer 禁用后, 运行时补充 footer 会恢复调用方启用意图.
     */
    @Test
    fun layout_restoresLoadMoreEnabledWhenFooterAddedLater() {
        val layout: KitRefreshLoadLayout = createLayout()
        val footer: View = createChild(width = MATCH_PARENT, height = FOOTER_HEIGHT)

        layout.addView(createChild(width = MATCH_PARENT, height = MATCH_PARENT))
        measureAndLayout(layout)
        layout.loadMoreEnabled = true
        layout.addView(
            footer,
            roleParams(
                KitRefreshLoadLayout.ChildRole.Footer,
                MATCH_PARENT,
                FOOTER_HEIGHT,
            ),
        )

        assertEquals(true, layout.loadMoreEnabled)
    }

    /**
     * 验证 Kotlin 动态创建时也会先补齐默认 header/footer, 再检查刷新加载能力.
     */
    @Test
    fun programmaticLayout_addsDefaultHeaderFooterBeforeDisablingFeatures() {
        val layout: KitRefreshLoadLayout = createLayout()
        applyDefaultIndicatorLayouts(layout)

        layout.addView(createChild(width = MATCH_PARENT, height = MATCH_PARENT))
        measureAndLayout(layout)
        measureAndLayout(layout)

        assertEquals(true, layout.refreshEnabled)
        assertEquals(true, layout.loadMoreEnabled)
        assertEquals(1, layout.countChildrenWithRole(KitRefreshLoadLayout.ChildRole.Header))
        assertEquals(1, layout.countChildrenWithRole(KitRefreshLoadLayout.ChildRole.Content))
        assertEquals(1, layout.countChildrenWithRole(KitRefreshLoadLayout.ChildRole.Footer))
    }

    /**
     * 验证运行时移除 header 后, 下次测量前会重新使用默认 header 兜底.
     */
    @Test
    fun layout_addsDefaultHeaderAfterHeaderRemoved() {
        val layout: KitRefreshLoadLayout = createLayout()
        val header: View = createChild(width = MATCH_PARENT, height = HEADER_HEIGHT)
        val content: View = createChild(width = MATCH_PARENT, height = MATCH_PARENT)
        applyDefaultIndicatorLayouts(layout)

        layout.addView(header, roleParams(KitRefreshLoadLayout.ChildRole.Header, MATCH_PARENT, HEADER_HEIGHT))
        layout.addView(content)
        measureAndLayout(layout)
        layout.removeView(header)

        measureAndLayout(layout)

        assertEquals(true, layout.refreshEnabled)
        assertEquals(1, layout.countChildrenWithRole(KitRefreshLoadLayout.ChildRole.Header))
        assertFalse(header === layout.childWithRole(KitRefreshLoadLayout.ChildRole.Header))
    }

    /**
     * 验证普通直接子 View 只能存在一个, 多个内容视图会抛出异常.
     */
    @Test
    fun addView_throwsWhenMoreThanOneContentChild() {
        val layout: KitRefreshLoadLayout = createLayout()

        layout.addView(createChild(width = MATCH_PARENT, height = MATCH_PARENT))

        assertThrows(IllegalStateException::class.java) {
            layout.addView(createChild(width = MATCH_PARENT, height = MATCH_PARENT))
        }
    }

    private fun createLayout(): KitRefreshLoadLayout =
        KitRefreshLoadLayout(RuntimeEnvironment.getApplication())

    private fun pointer(id: Int, y: Float): TestPointer =
        TestPointer(id = id, y = y)

    private fun touchEvent(
        actionMasked: Int,
        vararg pointers: TestPointer,
    ): MotionEvent =
        touchEvent(
            actionMasked = actionMasked,
            actionIndex = 0,
            pointers = pointers,
        )

    private fun touchEvent(
        actionMasked: Int,
        actionIndex: Int,
        vararg pointers: TestPointer,
    ): MotionEvent {
        val properties: Array<MotionEvent.PointerProperties> =
            Array(pointers.size) { index: Int ->
                MotionEvent.PointerProperties().apply {
                    id = pointers[index].id
                    toolType = MotionEvent.TOOL_TYPE_FINGER
                }
            }
        val coordinates: Array<MotionEvent.PointerCoords> =
            Array(pointers.size) { index: Int ->
                MotionEvent.PointerCoords().apply {
                    x = 0f
                    y = pointers[index].y
                }
            }
        val action: Int = actionMasked or (actionIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        return MotionEvent.obtain(
            0L,
            0L,
            action,
            pointers.size,
            properties,
            coordinates,
            0,
            0,
            1f,
            1f,
            0,
            0,
            0,
            0,
        )
    }

    private fun applyDefaultIndicatorLayouts(layout: KitRefreshLoadLayout) {
        setPrivateIntField(
            target = layout,
            fieldName = "defaultHeaderLayoutResId",
            value = android.R.layout.simple_list_item_1,
        )
        setPrivateIntField(
            target = layout,
            fieldName = "defaultFooterLayoutResId",
            value = android.R.layout.simple_list_item_1,
        )
    }

    private fun setPrivateIntField(
        target: Any,
        fieldName: String,
        value: Int,
    ) {
        val field: java.lang.reflect.Field = target.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        field.setInt(target, value)
    }

    private fun KitRefreshLoadLayout.countChildrenWithRole(role: KitRefreshLoadLayout.ChildRole): Int {
        var count: Int = 0
        for (index: Int in 0 until childCount) {
            val child: View = getChildAt(index)
            val layoutParams: KitRefreshLoadLayout.LayoutParams =
                child.layoutParams as KitRefreshLoadLayout.LayoutParams
            if (layoutParams.role == role) {
                count += 1
            }
        }
        return count
    }

    private fun KitRefreshLoadLayout.childWithRole(role: KitRefreshLoadLayout.ChildRole): View? {
        for (index: Int in 0 until childCount) {
            val child: View = getChildAt(index)
            val layoutParams: KitRefreshLoadLayout.LayoutParams =
                child.layoutParams as KitRefreshLoadLayout.LayoutParams
            if (layoutParams.role == role) {
                return child
            }
        }
        return null
    }

    private fun createChild(width: Int, height: Int): View =
        View(RuntimeEnvironment.getApplication()).apply {
            layoutParams = ViewGroup.LayoutParams(width, height)
        }

    private fun createScrollableContent(canScrollDown: Boolean): TestScrollableContentView =
        TestScrollableContentView(canScrollDown).apply {
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }

    private fun roleParams(
        role: KitRefreshLoadLayout.ChildRole,
        width: Int,
        height: Int,
    ): KitRefreshLoadLayout.LayoutParams =
        KitRefreshLoadLayout.LayoutParams(width, height).apply {
            this.role = role
        }

    private fun measureAndLayout(layout: KitRefreshLoadLayout) {
        layout.measure(
            View.MeasureSpec.makeMeasureSpec(LAYOUT_WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(LAYOUT_HEIGHT, View.MeasureSpec.EXACTLY),
        )
        layout.layout(0, 0, LAYOUT_WIDTH, LAYOUT_HEIGHT)
        assertTrue(layout.isLaidOut)
    }

    /**
     * 测试用刷新加载组件 View.
     */
    private class TestRefreshLoadComponentView :
        View(RuntimeEnvironment.getApplication()),
        KitRefreshLoadComponent {

        /**
         * 接收到的状态列表.
         */
        val states: MutableList<KitRefreshLoadLayout.RefreshLoadState> = mutableListOf()

        /**
         * 最后一次收到的位移.
         */
        var lastOffset: Int = 0

        /**
         * 最后一次收到的触发距离.
         */
        var lastTriggerDistance: Int = 0

        /**
         * 最后一次收到的保持距离.
         */
        var lastHoldDistance: Int = 0

        override fun onRefreshLoadStateChanged(
            layout: KitRefreshLoadLayout,
            state: KitRefreshLoadLayout.RefreshLoadState,
        ) {
            states += state
        }

        override fun onRefreshLoadOffsetChanged(
            layout: KitRefreshLoadLayout,
            offset: Int,
            triggerDistance: Int,
            holdDistance: Int,
        ) {
            lastOffset = offset
            lastTriggerDistance = triggerDistance
            lastHoldDistance = holdDistance
        }
    }

    /**
     * 测试用可滚动内容 View.
     */
    private class TestScrollableContentView(
        private val canScrollDown: Boolean,
    ) : View(RuntimeEnvironment.getApplication()) {

        override fun canScrollVertically(direction: Int): Boolean =
            direction > 0 && canScrollDown
    }

    /**
     * 测试用触摸指针.
     *
     * @property id 指针 id.
     * @property y 指针 y 坐标.
     */
    private data class TestPointer(
        val id: Int,
        val y: Float,
    )

    private companion object {

        /**
         * 父容器测试宽度.
         */
        private const val LAYOUT_WIDTH: Int = 300

        /**
         * 父容器测试高度.
         */
        private const val LAYOUT_HEIGHT: Int = 500

        /**
         * Header 测试高度.
         */
        private const val HEADER_HEIGHT: Int = 60

        /**
         * Footer 测试高度.
         */
        private const val FOOTER_HEIGHT: Int = 70

        /**
         * 刷新触发距离.
         */
        private const val REFRESH_TRIGGER_DISTANCE: Int = 50

        /**
         * 刷新拖拽距离.
         */
        private const val REFRESH_DRAG_DISTANCE: Int = 80

        /**
         * 加载更多触发距离.
         */
        private const val LOAD_MORE_TRIGGER_DISTANCE: Int = 45

        /**
         * 加载更多拖拽距离.
         */
        private const val LOAD_MORE_DRAG_DISTANCE: Int = 70

        /**
         * 自动加载测试滚动距离.
         */
        private const val AUTO_LOAD_SCROLL_DISTANCE: Int = 24

        /**
         * 测试动画时长.
         */
        private const val ANIMATION_DURATION_MILLIS: Long = 240L

        /**
         * 匹配父容器尺寸.
         */
        private const val MATCH_PARENT: Int = ViewGroup.LayoutParams.MATCH_PARENT
    }
}
