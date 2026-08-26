package com.whisper.architecture.ui.state

import com.whisper.architecture.model.ui.notice.NoticeImportance
import com.whisper.architecture.model.ui.notice.NoticeTone
import com.whisper.architecture.model.ui.notice.NoticeUiModel
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
     * 验证 UI 通知可以发送给已订阅的收集者.
     */
    @Test
    fun showNotice_emitsToActiveCollector() = runBlocking {
        val state: MutableArchitectureUiState = DefaultArchitectureUiState()
        val notice: NoticeUiModel = NoticeUiModel(
            content = "done",
            importance = NoticeImportance.LOW,
            tone = NoticeTone.SUCCESS,
        )
        val deferredNotice: Deferred<NoticeUiModel> = async {
            state.noticeFlow.first()
        }

        yield()
        state.showNotice(notice)

        assertEquals(notice, withTimeout(1_000) { deferredNotice.await() })
    }
}
