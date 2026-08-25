package com.whisper.kit.view.refresh

import androidx.annotation.Px

/**
 * 刷新加载头尾组件.
 *
 * header 或 footer View 可以实现该接口, 由 [KitRefreshLoadLayout] 在拖拽距离和状态变化时回调,
 * 用于更新文案、进度或动画.
 * 该接口只感知刷新加载容器状态, 不要求内容 View 必须是 RecyclerView 或其它特定滚动实现.
 *
 * @author whisper
 * @since 2026/07/30
 */
interface KitRefreshLoadComponent {

    /**
     * 刷新加载状态变化回调.
     *
     * @param layout 所属刷新加载容器.
     * @param state 当前刷新加载状态.
     */
    fun onRefreshLoadStateChanged(
        layout: KitRefreshLoadLayout,
        state: KitRefreshLoadLayout.RefreshLoadState,
    )

    /**
     * 刷新加载拖拽距离变化回调.
     *
     * @param layout 所属刷新加载容器.
     * @param offset 当前内容位移, 单位 px. 正数表示 header 方向, 负数表示 footer 方向.
     * @param triggerDistance 当前方向触发刷新或加载的距离, 单位 px.
     * @param holdDistance 当前方向刷新或加载中保持显示的距离, 单位 px.
     */
    fun onRefreshLoadOffsetChanged(
        layout: KitRefreshLoadLayout,
        @Px
        offset: Int,
        @Px
        triggerDistance: Int,
        @Px
        holdDistance: Int,
    )
}
