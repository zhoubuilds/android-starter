package com.whisper.architecture.viewmodel

import com.whisper.architecture.ui.state.ArchitectureUiState

/**
 * 提供 Architecture UI 状态.
 *
 * Activity, Fragment 和 ViewModel 通过该接口共享页面级通用 UI 状态.
 *
 * @aegis 保护状态所有者接口和只读 Architecture UI 状态暴露契约.
 * @author whisper
 * @since 2026/07/24
 */
interface ArchitectureUiStateOwner {

    /**
     * Architecture UI 状态.
     */
    val architectureUiState: ArchitectureUiState
}
