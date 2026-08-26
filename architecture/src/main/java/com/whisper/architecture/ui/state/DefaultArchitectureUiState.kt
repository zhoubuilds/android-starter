package com.whisper.architecture.ui.state

import com.whisper.architecture.model.ui.notice.NoticeUi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Architecture UI 状态的默认实现.
 *
 * 使用待处理任务计数支持多个并发任务, 使用共享流发送 UI 通知.
 *
 * @aegis 保护计数不小于零, 通知不重放及满缓冲区丢弃旧通知的行为.
 * @author whisper
 * @since 2026/07/24
 */
class DefaultArchitectureUiState : MutableArchitectureUiState {

    /**
     * 页面当前待处理任务数量的可变状态流.
     */
    private val mutablePendingTaskCountFlow: MutableStateFlow<Int> = MutableStateFlow(0)

    /**
     * 页面 UI 通知的可变共享流.
     *
     * 通知不会在无订阅者时重放, 调用方应只用于可丢弃的一次性展示事件.
     */
    private val mutableNoticeFlow: MutableSharedFlow<NoticeUi> =
        MutableSharedFlow(
            replay = 0,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    override val pendingTaskCountFlow: Flow<Int>
        get() = mutablePendingTaskCountFlow

    override val noticeFlow: Flow<NoticeUi>
        get() = mutableNoticeFlow

    override fun onPendingTaskStarted() {
        mutablePendingTaskCountFlow.update { count: Int -> count + 1 }
    }

    override fun onPendingTaskCompleted() {
        mutablePendingTaskCountFlow.update { count: Int -> (count - 1).coerceAtLeast(0) }
    }

    override fun showNotice(notice: NoticeUi) {
        mutableNoticeFlow.tryEmit(notice)
    }
}
