package com.whisper.architecture.ui.owner

import com.whisper.architecture.model.ui.notice.NoticeUiModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Architecture UI Owner 的默认实现.
 *
 * 使用持续状态保存正在进行的操作数量, 使用不重放的共享流发送一次性通知 Effect.
 *
 * @aegis 保护计数不小于零, 通知不重放及满缓冲区丢弃旧通知的行为.
 * @aegis-audit 2026-08-26 | whisper | 使用独立 StateFlow 和 SharedFlow 实现组合 Owner.
 * @aegis-audit 2026-08-26 | whisper | 将后端任务计数调整为通用操作计数并统一 Flow 后缀.
 * @aegis-audit 2026-08-26 | whisper | 将默认实现从 ViewModel 包迁移到 UI Owner 能力域.
 *
 * @author whisper
 * @since 2026/08/26
 */
class DefaultArchitectureUiOwner : MutableArchitectureUiOwner {

    /**
     * 页面正在进行的操作数量的可变 UI 持续状态流.
     */
    private val _activeOperationCountFlow: MutableStateFlow<Int> =
        MutableStateFlow(0)

    /**
     * 页面一次性通知 Effect 的可变共享流.
     *
     * 通知不会在无订阅者时重放, 调用方应只用于可丢弃的一次性展示行为.
     */
    private val _noticeUiEffectFlow: MutableSharedFlow<NoticeUiModel> =
        MutableSharedFlow(
            replay = 0,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    override val noticeUiEffectFlow: SharedFlow<NoticeUiModel> =
        _noticeUiEffectFlow.asSharedFlow()

    override val activeOperationCountFlow: StateFlow<Int> =
        _activeOperationCountFlow.asStateFlow()

    override fun notice(notice: NoticeUiModel) {
        _noticeUiEffectFlow.tryEmit(notice)
    }

    override fun onOperationStarted() {
        _activeOperationCountFlow.update { count: Int -> count + 1 }
    }

    override fun onOperationCompleted() {
        _activeOperationCountFlow.update { count: Int ->
            (count - 1).coerceAtLeast(0)
        }
    }
}
