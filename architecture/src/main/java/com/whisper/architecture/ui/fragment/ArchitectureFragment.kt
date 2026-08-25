package com.whisper.architecture.ui.fragment

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.whisper.architecture.ui.component.ArchitectureUiComponent
import com.whisper.architecture.ui.state.ArchitectureUiState
import com.whisper.architecture.viewmodel.ArchitectureUiStateOwner

/**
 * 自动绑定 Architecture UI 状态的 Fragment 基类.
 *
 * View 创建后将 ViewModel 提供的 Architecture UI 状态绑定到 Fragment 视图生命周期.
 *
 * @aegis 保护基类 API 和绑定到 `viewLifecycleOwner` 的生命周期语义.
 * @author whisper
 * @since 2026/07/24
 */
abstract class ArchitectureFragment<VM> : Fragment() where VM : ArchitectureUiStateOwner {

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
        architectureUiComponent.bind(boundArchitectureUiStates(), viewLifecycleOwner)
    }

    /**
     * 返回需要绑定到 Architecture UI 组件的状态集合.
     *
     * 子类可以覆盖该方法聚合多个 ViewModel 或页面级 Architecture UI 状态.
     *
     * @return Architecture UI 状态集合.
     */
    protected open fun boundArchitectureUiStates(): Iterable<ArchitectureUiState> =
        listOf(viewModel.architectureUiState)
}
