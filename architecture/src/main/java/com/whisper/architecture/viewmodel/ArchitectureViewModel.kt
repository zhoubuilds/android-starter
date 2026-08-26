package com.whisper.architecture.viewmodel

import androidx.lifecycle.ViewModel

/**
 * 支持 Architecture UI 状态的 ViewModel 基类.
 *
 * @aegis 保护基类继承/实现契约和 Architecture UI 状态职责边界.
 * @author whisper
 * @since 2026/07/24
 */
abstract class ArchitectureViewModel :
    ViewModel(),
    ArchitectureUiStateOwner
