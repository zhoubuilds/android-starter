package com.whisper.architecture.ui.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.whisper.architecture.component.ArchitectureUiStateHandler
import com.whisper.architecture.uistate.ArchitectureUiStatePack
import com.whisper.architecture.viewmodel.ArchitectureUiStateOwner

/**
 * 架构组件的Activity基类
 *
 * 应该在可以决定样式的地方对[architectureUiStateHandler]进行实现
 *
 * @author whisper
 * @since 2025/9/2
 */
abstract class ArchitectureActivity<VM> : AppCompatActivity() where VM : ArchitectureUiStateOwner {

    protected abstract val viewModel: VM
    protected abstract val architectureUiStateHandler: ArchitectureUiStateHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        architectureUiStateHandler.bind(architectureUiStatePacks(), this)
    }

    open fun architectureUiStatePacks(): Iterable<ArchitectureUiStatePack> =
        listOf(viewModel.architectureUiStatePack)

}