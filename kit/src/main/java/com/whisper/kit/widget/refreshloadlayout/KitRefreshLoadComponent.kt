package com.whisper.kit.widget.refreshloadlayout

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
     * 新增或替换组件首次完成布局后, 也会同步当前状态. 回调允许同步调用完成或禁用 API;
     * 如果这使当前操作失效, 容器不会继续提交该操作或向其它组件发送过期状态.
     * 首次状态通知若引起尺寸变化, 容器等待重新布局后补位移快照和初始展开位置, 不重复通知同一状态.
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
     * 新增或替换组件首次完成布局后, 也会同步当前位移; 当前显示方向与组件角色不同时为 0.
     * 运行时有效距离变化时也会同步, 包括布局后的组件高度或纵向 margin 变化.
     * 回调请求重新布局后, 等待尺寸就绪; 相对该组件上次收到的快照, 只有宽高、角色、位移或有效距离变化时才补发,
     * 更新相同文案引起的重布局不会重复回调或阻止初始展开.
     *
     * @param layout 所属刷新加载容器.
     * @param offset 当前内容位移, 单位 px. 正数表示 header 方向, 负数表示 footer 方向.
     * @param triggerDistance 当前方向触发刷新或加载的距离, 单位 px.
     * @param holdDistance 当前方向刷新或加载中保持显示的距离, 含上下 margin, 单位 px.
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
