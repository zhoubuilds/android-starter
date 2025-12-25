package com.whisper.architecture.component

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.whisper.architecture.uimode.message.UiMessage
import com.whisper.architecture.uistate.ArchitectureUiStatePack
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.merge
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
abstract class ArchitectureUiStateHandler(protected val context: Context) {

    abstract fun onWorkingCountChanged(count: Int)

    abstract fun handleUiMessage(message: UiMessage)

    open fun bind(packs: Iterable<ArchitectureUiStatePack>, lifecycleOwner: LifecycleOwner) {
        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val totalWorkingCountFlow = if (packs.iterator().hasNext()) {
                    combine(packs.map { it.workingCountFlow }) { it.sum() }
                } else {
                    flowOf(0)
                }
                launch {
                    totalWorkingCountFlow.collectLatest {
                        onWorkingCountChanged(it)
                    }
                }

                val mergedUiMessageFlow =
                    merge(*packs.map { it.uiMessageFlow }.toTypedArray())
                launch {
                    mergedUiMessageFlow.collectLatest { message ->
                        handleUiMessage(message)
                    }
                }
            }
        }
    }
}
