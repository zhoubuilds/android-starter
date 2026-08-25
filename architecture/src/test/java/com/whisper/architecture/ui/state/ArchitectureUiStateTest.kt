package com.whisper.architecture.ui.state

import com.whisper.architecture.ui.message.UiMessage
import com.whisper.architecture.ui.message.UiMessageImportance
import com.whisper.architecture.ui.message.UiMessageTone
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 验证 Architecture UI 状态的默认实现.
 *
 * @author whisper
 * @since 2026/07/24
 */
class ArchitectureUiStateTest {

    /**
     * 验证待处理任务计数会随开始和完成事件更新.
     */
    @Test
    fun pendingTaskCount_updatesAndNeverBelowZero() = runBlocking {
        val state: MutableArchitectureUiState = DefaultArchitectureUiState()

        assertEquals(0, state.pendingTaskCountFlow.first())

        state.onPendingTaskStarted()
        assertEquals(1, state.pendingTaskCountFlow.first())

        state.onPendingTaskStarted()
        assertEquals(2, state.pendingTaskCountFlow.first())

        state.onPendingTaskCompleted()
        assertEquals(1, state.pendingTaskCountFlow.first())

        state.onPendingTaskCompleted()
        assertEquals(0, state.pendingTaskCountFlow.first())

        state.onPendingTaskCompleted()
        assertEquals(0, state.pendingTaskCountFlow.first())
    }

    /**
     * 验证 UI 消息可以发送给已订阅的收集者.
     */
    @Test
    fun showUiMessage_emitsToActiveCollector() = runBlocking {
        val state: MutableArchitectureUiState = DefaultArchitectureUiState()
        val message: UiMessage = UiMessage(
            message = "done",
            importance = UiMessageImportance.LOW,
            tone = UiMessageTone.SUCCESS,
        )
        val deferredMessage: Deferred<UiMessage> = async {
            state.uiMessageFlow.first()
        }

        yield()
        state.showUiMessage(message)

        assertEquals(message, withTimeout(1_000) { deferredMessage.await() })
    }
}
