package com.whisper.architecture.uistate

import com.whisper.architecture.uimode.message.UiMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.atomic.AtomicInteger


/**
 *
 * @author whisper
 * @since 2025/9/26
 */
class DefaultUiStatePack : MutableArchUiStatePack {

    private val _workingCount = AtomicInteger(0)
    private val _workingCountFlow: MutableStateFlow<Int> = MutableStateFlow(_workingCount.get())

    private val _uiMessageFlow: MutableSharedFlow<UiMessage?> = MutableSharedFlow(1)

    override val workingCountFlow: Flow<Int>
        get() = _workingCountFlow
    override val uiMessageFlow: Flow<UiMessage?>
        get() = _uiMessageFlow

    override fun onWorkStarted() {
        _workingCountFlow.value = _workingCount.incrementAndGet()
    }

    override fun onWorkCompleted() {
        _workingCountFlow.value = _workingCount.decrementAndGet()
    }

    override fun showUiMessage(message: UiMessage) {
        _uiMessageFlow.tryEmit(message)
    }

    override fun consumeMessage(uiMessage: UiMessage) {
        val cache: UiMessage? = _uiMessageFlow.replayCache.firstOrNull()
        if (uiMessage === cache) {
            _uiMessageFlow.tryEmit(null)
        }
    }

}