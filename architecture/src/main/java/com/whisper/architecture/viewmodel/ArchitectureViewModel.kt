package com.whisper.architecture.viewmodel

import androidx.lifecycle.ViewModel
import com.whisper.architecture.ui.owner.ArchitectureUiOwner

/**
 * 支持 Architecture UI Owner 的 ViewModel 基类.
 *
 * @aegis 保护基类继承/实现契约和 Architecture UI 状态职责边界.
 * @aegis-audit 2026-08-26 | whisper | 将 ViewModel 契约迁移到状态与 Effect 的组合 Owner.
 * @author whisper
 * @since 2026/07/24
 */
abstract class ArchitectureViewModel :
    ViewModel(),
    ArchitectureUiOwner
