package com.whisper.architecture.component

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.whisper.architecture.uimode.message.UiMessage
import com.whisper.architecture.uistate.ArchUiStatePack
import kotlinx.coroutines.launch


/**
 *
 * 基础的UiState处理器, 应该能否处理进度和消息([android.widget.Toast], [com.google.android.material.snackbar.Snackbar])
 *
 * 没有业务实现, 应该在可以决定样式的地方有一个中间实现
 *
 * @author whisper
 * @since 2025/9/2
 */
abstract class ArchUiStateHandler(private val context: Context) {

    abstract fun onBackgroundCountChanged(count: Int)

    abstract fun handleUiMessage(message: UiMessage)

    open fun bind(provider: ArchUiStatePack, lifecycleOwner: LifecycleOwner) {
        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    provider.workingCountFlow.collect {
                        onBackgroundCountChanged(it)
                    }
                }
                launch {
                    provider.uiMessageFlow.collect { message ->
                        message?.let { m -> handleUiMessage(m) }
                    }
                }
            }
        }
    }
}
