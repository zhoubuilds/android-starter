package com.whisper.architecture.ui.owner

import com.whisper.architecture.ui.effect.NoticeUiEffect
import com.whisper.architecture.ui.state.ActiveOperationCountUiState

/**
 * 组合 Architecture 提供的通用 UI 状态与 Effect.
 *
 * 消费方只需要其中一种能力时应依赖对应的窄接口, 常规页面可以直接依赖该 Owner.
 *
 * @aegis 保护 Architecture UI 状态与 Effect 的只读组合契约.
 * @aegis-audit 2026-08-26 | whisper | 将持续状态与一次性 Effect 拆分后通过 Owner 组合.
 * @aegis-audit 2026-08-26 | whisper | 将组合契约从 ViewModel 包迁移到 UI Owner 能力域.
 * @author whisper
 * @since 2026/08/26
 */
interface ArchitectureUiOwner : NoticeUiEffect, ActiveOperationCountUiState
