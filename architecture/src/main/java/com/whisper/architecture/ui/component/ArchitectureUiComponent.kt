package com.whisper.architecture.ui.component

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.whisper.architecture.model.ui.notice.NoticeUiModel
import com.whisper.architecture.ui.effect.NoticeUiEffect
import com.whisper.architecture.ui.state.ActiveOperationCountUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * 将 Architecture UI 状态和 Effect 绑定到 UI 生命周期.
 *
 * 在 UI 进入 STARTED 状态时收集正在进行的操作数量和通知 Effect, 离开 STARTED 状态时停止收集.
 *
 * @aegis 保护组件 API, STARTED 收集边界, 多状态合并和重复绑定语义.
 * @aegis-audit 2026-08-26 | whisper | 支持持续状态与一次性 Effect 分开绑定并保留 Owner 便捷入口.
 * @aegis-audit 2026-08-26 | whisper | 将后端任务聚合调整为通用操作数量聚合并统一 Flow 后缀.
 * @aegis-audit 2026-08-26 | whisper | 移除 Owner 绑定重载, 组件仅依赖状态与 Effect 窄契约.
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
     * 页面正在进行的操作总数变化时更新 UI.
     *
     * @param count 页面正在进行的操作总数.
     */
    abstract fun onActiveOperationCountChanged(count: Int)

    /**
     * 展示 UI 通知.
     *
     * @param notice UI 通知.
     */
    abstract fun handleNotice(notice: NoticeUiModel)

    /**
     * 将独立的 Architecture UI 状态和 Effect 来源绑定到指定生命周期.
     *
     * @param activeOperationCountUiStates 需要组合的正在进行操作数量 UI 状态.
     * @param noticeUiEffects 需要合并的通知 UI Effect.
     * @param lifecycleOwner UI 生命周期持有者.
     */
    open fun bind(
        activeOperationCountUiStates: Iterable<ActiveOperationCountUiState>,
        noticeUiEffects: Iterable<NoticeUiEffect>,
        lifecycleOwner: LifecycleOwner,
    ) {
        val activeOperationCountUiStateList: List<ActiveOperationCountUiState> =
            activeOperationCountUiStates.toList()
        val noticeUiEffectList: List<NoticeUiEffect> =
            noticeUiEffects.toList()
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
                val totalActiveOperationCountFlow: Flow<Int> =
                    if (activeOperationCountUiStateList.isNotEmpty()) {
                        combine(
                            activeOperationCountUiStateList.map { source: ActiveOperationCountUiState ->
                                source.activeOperationCountFlow
                            },
                        ) { counts: Array<Int> ->
                            counts.sum()
                        }
                    } else {
                        flowOf(0)
                    }
                launch {
                    totalActiveOperationCountFlow.collect { count: Int ->
                        onActiveOperationCountChanged(count)
                    }
                }

                val mergedNoticeUiEffectFlow: Flow<NoticeUiModel> =
                    if (noticeUiEffectList.isNotEmpty()) {
                        noticeUiEffectList
                            .map { source: NoticeUiEffect -> source.noticeUiEffectFlow }
                            .merge()
                    } else {
                        emptyFlow()
                    }
                launch {
                    mergedNoticeUiEffectFlow.collect { notice: NoticeUiModel ->
                        handleNotice(notice)
                    }
                }
            }
        }
        bindingLifecycleOwner = lifecycleOwner
        bindingJob = newBindingJob
        newBindingJob.invokeOnCompletion {
            // 只清理当前任务对应的记录, 避免旧任务结束时误清理新绑定.
            if (bindingJob === newBindingJob) {
                bindingLifecycleOwner = null
                bindingJob = null
            }
        }
    }
}
