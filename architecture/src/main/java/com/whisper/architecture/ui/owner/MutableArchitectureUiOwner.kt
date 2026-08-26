package com.whisper.architecture.ui.owner

import com.whisper.architecture.model.ui.notice.NoticeUiModel

/**
 * 提供 Architecture UI 状态与 Effect 的更新能力.
 *
 * @aegis 保护正在进行的操作数量更新和通知 Effect 发送契约.
 * @aegis-audit 2026-08-26 | whisper | 将可变 UI 容器迁移为 State 与 Effect 的组合 Owner.
 * @aegis-audit 2026-08-26 | whisper | 将后端任务更新协议调整为通用操作生命周期协议.
 * @aegis-audit 2026-08-26 | whisper | 将更新协议从 ViewModel 包迁移到 UI Owner 能力域.
 * @author whisper
 * @since 2026/08/26
 */
interface MutableArchitectureUiOwner : ArchitectureUiOwner {

    /**
     * 发送页面一次性通知 Effect.
     *
     * @param notice 通知的 UI 渲染模型.
     */
    fun notice(notice: NoticeUiModel)

    /**
     * 记录一个操作开始.
     */
    fun onOperationStarted()

    /**
     * 记录一个操作完成.
     */
    fun onOperationCompleted()
}
