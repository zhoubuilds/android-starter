package com.whisper.architecture.ui.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.whisper.architecture.ui.component.ArchitectureUiComponent
import com.whisper.architecture.ui.effect.NoticeUiEffect
import com.whisper.architecture.ui.state.ActiveOperationCountUiState

/**
 * 自动绑定 Architecture UI 状态与 Effect 的 Activity 基类.
 *
 * 页面创建后将 ViewModel 提供的持续状态与一次性 Effect 分别绑定到 Activity 生命周期.
 *
 * @aegis 保护基类 API 和在 `onCreate()` 中绑定 Architecture UI 状态的生命周期语义.
 * @aegis-audit 2026-08-26 | whisper | 将自动绑定契约迁移到状态与 Effect 的组合 Owner.
 * @aegis-audit 2026-08-26 | whisper | 基类改为分别依赖状态与 Effect 窄契约, Owner 保持可选组合.
 * @author whisper
 * @since 2026/07/24
 */
abstract class ArchitectureActivity<VM> :
    AppCompatActivity() where VM : NoticeUiEffect, VM : ActiveOperationCountUiState {

    /**
     * 页面 ViewModel.
     */
    protected abstract val viewModel: VM

    /**
     * Architecture UI 状态组件.
     */
    protected abstract val architectureUiComponent: ArchitectureUiComponent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        architectureUiComponent.bind(
            activeOperationCountUiStates = boundActiveOperationCountUiStates(),
            noticeUiEffects = boundNoticeUiEffects(),
            lifecycleOwner = this,
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
