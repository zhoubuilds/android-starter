package com.whisper.architecture.ui.fragment

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.whisper.architecture.ui.component.ArchitectureUiComponent
import com.whisper.architecture.ui.effect.NoticeUiEffect
import com.whisper.architecture.ui.state.ActiveOperationCountUiState

/**
 * 自动绑定 Architecture UI 状态与 Effect 的 Fragment 基类.
 *
 * View 创建后将 ViewModel 提供的持续状态与一次性 Effect 分别绑定到 Fragment 视图生命周期.
 *
 * @aegis 保护基类 API 和绑定到 `viewLifecycleOwner` 的生命周期语义.
 * @aegis-audit 2026-08-26 | whisper | 将自动绑定契约迁移到状态与 Effect 的组合 Owner.
 * @aegis-audit 2026-08-26 | whisper | 基类改为分别依赖状态与 Effect 窄契约, Owner 保持可选组合.
 *
 * @author whisper
 * @since 2026/07/24
 */
abstract class ArchitectureFragment<VM> :
    Fragment() where VM : NoticeUiEffect, VM : ActiveOperationCountUiState {

    /**
     * 页面 ViewModel.
     */
    protected abstract val viewModel: VM

    /**
     * Architecture UI 状态组件.
     */
    protected abstract val architectureUiComponent: ArchitectureUiComponent

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        architectureUiComponent.bind(
            activeOperationCountUiStates = boundActiveOperationCountUiStates(),
            noticeUiEffects = boundNoticeUiEffects(),
            lifecycleOwner = viewLifecycleOwner,
        )
    }

    /**
     * 返回需要绑定到 Architecture UI 组件的操作数量状态集合.
     *
     * 子类可以覆盖该方法聚合多个 ViewModel 或页面级操作数量状态.
     *
     * @return 正在进行的操作数量状态集合.
     */
    protected open fun boundActiveOperationCountUiStates(): Iterable<ActiveOperationCountUiState> =
        listOf(viewModel)

    /**
     * 返回需要绑定到 Architecture UI 组件的通知 Effect 集合.
     *
     * 子类可以覆盖该方法聚合多个 ViewModel 或页面级通知 Effect.
     *
     * @return 通知 Effect 集合.
     */
    protected open fun boundNoticeUiEffects(): Iterable<NoticeUiEffect> =
        listOf(viewModel)
}
