package com.whisper.architecture.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.whisper.architecture.component.ArchUiStateHandler
import com.whisper.architecture.viewmodel.ArchUiStateOwner

/**
 * 架构组件的Activity基类
 *
 * 应该在可以决定样式的地方对[archUiStateHandler]进行实现
 *
 * @author whisper
 * @since 2025/9/2
 */
abstract class ArchActivity<VM> : AppCompatActivity() where VM : ArchUiStateOwner {

    protected abstract val viewModel: VM
    protected abstract val archUiStateHandler: ArchUiStateHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        archUiStateHandler.bind(viewModel.archUiStatePack, this)
    }

}