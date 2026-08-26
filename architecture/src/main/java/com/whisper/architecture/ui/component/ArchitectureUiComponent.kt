package com.whisper.architecture.ui.component

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.whisper.architecture.model.ui.notice.NoticeUiModel
import com.whisper.architecture.ui.state.ArchitectureUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * 将 Architecture UI 状态绑定到 UI 生命周期.
 *
 * 在 UI 进入 STARTED 状态时收集待处理任务计数和通知, 离开 STARTED 状态时停止收集.
 *
 * @aegis 保护组件 API, STARTED 收集边界, 多状态合并和重复绑定语义.
 * @author whisper
 * @since 2026/07/24
 */
abstract class ArchitectureUiComponent(
    /**
     * 用于展示 Architecture UI 状态的上下文.
     */
    protected val context: Context,
) {

    /**
     * 当前绑定的生命周期持有者.
     */
    private var bindingLifecycleOwner: LifecycleOwner? = null

    /**
     * 当前状态绑定任务.
     */
    private var bindingJob: Job? = null

    /**
     * 页面待处理任务总数变化时更新 UI.
     *
     * @param count 页面待处理任务总数.
     */
    abstract fun onPendingTaskCountChanged(count: Int)

    /**
     * 展示 UI 通知.
     *
     * @param notice UI 通知.
     */
    abstract fun handleNotice(notice: NoticeUiModel)

    /**
     * 将 Architecture UI 状态绑定到指定生命周期.
     *
     * @param states 需要合并收集的 Architecture UI 状态.
     * @param lifecycleOwner UI 生命周期持有者.
     */
    open fun bind(states: Iterable<ArchitectureUiState>, lifecycleOwner: LifecycleOwner) {
        val stateList: List<ArchitectureUiState> = states.toList()
        val existingBindingJob: Job? = bindingJob
        if (existingBindingJob?.isActive == true) {
            // 仅允许同一个 LifecycleOwner 重复绑定, 其它 owner 复用同一个组件属于使用错误.
            check(bindingLifecycleOwner === lifecycleOwner) {
                "ArchitectureUiComponent can only bind one active LifecycleOwner."
            }
            return
        }

        // 旧绑定任务结束后允许重新绑定, 用于支持 Fragment View 销毁后再次创建.
        val newBindingJob: Job = lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val totalPendingTaskCountFlow: Flow<Int> = if (stateList.isNotEmpty()) {
                    combine(stateList.map { state: ArchitectureUiState -> state.pendingTaskCountFlow }) {
                            counts: Array<Int> ->
                        counts.sum()
                    }
                } else {
                    flowOf(0)
                }
                launch {
                    totalPendingTaskCountFlow.collect { count: Int ->
                        onPendingTaskCountChanged(count)
                    }
                }

                val mergedNoticeFlow: Flow<NoticeUiModel> = if (stateList.isNotEmpty()) {
                    merge(*stateList.map { state: ArchitectureUiState -> state.noticeFlow }.toTypedArray())
                } else {
                    emptyFlow()
                }
                launch {
                    mergedNoticeFlow.collect { notice: NoticeUiModel ->
                        handleNotice(notice)
                    }
                }
            }
        }
        bindingLifecycleOwner = lifecycleOwner
        bindingJob = newBindingJob
        newBindingJob.invokeOnCompletion {
            // 只清理当前任务对应的记录, 避免旧任务结束时误清理新的绑定.
            if (bindingJob === newBindingJob) {
                bindingLifecycleOwner = null
                bindingJob = null
            }
        }
    }
}
