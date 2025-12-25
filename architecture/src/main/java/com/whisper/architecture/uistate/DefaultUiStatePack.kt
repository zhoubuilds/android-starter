package com.whisper.architecture.uistate

import com.whisper.architecture.uimode.message.UiMessage
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update


/**
 *
 * @author whisper
 * @since 2025/9/26
 */
class DefaultUiStatePack : MutableArchUiStatePack {

    // 直接使用 MutableStateFlow 作为唯一的真相来源
    private val _workingCountFlow = MutableStateFlow(0)

    private val _uiMessageFlow: MutableSharedFlow<UiMessage> =
        MutableSharedFlow(
            replay = 0,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

    override val workingCountFlow: Flow<Int>
        get() = _workingCountFlow

    override val uiMessageFlow: Flow<UiMessage>
        get() = _uiMessageFlow

    override fun onWorkStarted() {
        // 使用 update 保证原子性：读取、加1、写入是一个原子操作
        _workingCountFlow.update { it + 1 }
    }

    override fun onWorkCompleted() {
        // 使用 update 保证原子性
        _workingCountFlow.update { (it - 1).coerceAtLeast(0) }
    }

    override fun showUiMessage(message: UiMessage) {
        _uiMessageFlow.tryEmit(message)
    }

}