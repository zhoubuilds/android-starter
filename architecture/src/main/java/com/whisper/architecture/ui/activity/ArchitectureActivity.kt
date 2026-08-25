package com.whisper.architecture.ui.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.whisper.architecture.ui.component.ArchitectureUiComponent
import com.whisper.architecture.ui.state.ArchitectureUiState
import com.whisper.architecture.viewmodel.ArchitectureUiStateOwner

/**
 * 自动绑定 Architecture UI 状态的 Activity 基类.
 *
 * 页面创建后将 ViewModel 提供的 Architecture UI 状态绑定到 Activity 生命周期.
 *
 * @aegis 保护基类 API 和在 `onCreate()` 中绑定 Architecture UI 状态的生命周期语义.
 * @author whisper
 * @since 2026/07/24
 */
abstract class ArchitectureActivity<VM> : AppCompatActivity() where VM : ArchitectureUiStateOwner {

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
        architectureUiComponent.bind(boundArchitectureUiStates(), this)
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
