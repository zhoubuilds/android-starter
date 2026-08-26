package com.whisper.architecture.ui.state

import com.whisper.architecture.model.ui.notice.NoticeUiModel

/**
 * 提供 Architecture UI 状态的修改能力.
 *
 * 页面或 ViewModel 可以通过该接口更新待处理任务计数和一次性 UI 通知.
 *
 * @aegis 保护可变状态接口和任务计数/通知更新契约.
 * @author whisper
 * @since 2026/07/24
 */
interface MutableArchitectureUiState : ArchitectureUiState {

    /**
     * 增加一个待处理任务.
     */
    fun onPendingTaskStarted()

    /**
     * 完成一个待处理任务.
     */
    fun onPendingTaskCompleted()

    /**
     * 发送需要 UI 展示的通知.
     *
     * @param notice UI 通知.
     */
    fun showNotice(notice: NoticeUiModel)
}
