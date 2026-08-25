package com.whisper.architecture.ui.state

import com.whisper.architecture.ui.message.UiMessage
import kotlinx.coroutines.flow.Flow

/**
 * 对外提供 Architecture UI 状态.
 *
 * 包含页面待处理任务计数和一次性 UI 消息, 不承载具体业务页面状态.
 *
 * @aegis 保护只读状态接口和待处理任务/一次性消息 Flow 契约.
 * @author whisper
 * @since 2026/07/24
 */
interface ArchitectureUiState {

    /**
     * 页面当前待处理任务数量.
     */
    val pendingTaskCountFlow: Flow<Int>

    /**
     * 页面需要展示的 UI 消息.
     */
    val uiMessageFlow: Flow<UiMessage>
}
