package com.whisper.architecture.ui.state

import kotlinx.coroutines.flow.StateFlow

/**
 * 提供页面正在进行的操作数量这一只读 UI 持续状态.
 *
 * @aegis 保护正在进行的操作数量通过 `StateFlow` 暴露的只读契约.
 * @aegis-audit 2026-08-26 | whisper | 将后端任务计数从混合容器拆分为独立 UiState.
 * @aegis-audit 2026-08-26 | whisper | 将后端任务语义调整为通用的正在进行操作数量.
 * @author whisper
 * @since 2026/08/26
 */
interface ActiveOperationCountUiState {

    /**
     * 页面正在进行的操作数量状态流.
     */
    val activeOperationCountFlow: StateFlow<Int>

}
